package com.hp.attributebridge;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public final class BridgeConfigFiles {
    private BridgeConfigFiles() {}

    public static BridgeConfig load(Path directory, Path legacy) throws IOException {
        Files.createDirectories(directory);
        Path replace = directory.resolve("replace.json");
        Path combine = directory.resolve("combine.json");
        if (!Files.exists(replace) && !Files.exists(combine) && Files.exists(legacy)) {
            JsonObject original = read(legacy);
            BridgeConfig.parse(BridgeConfig.GSON.toJson(original));
            write(replace, split(original, "replace"));
            write(combine, split(original, "combine"));
            BridgeRuntime.LOGGER.info("Migrated legacy attribute bridge rules to {}; legacy file is retained and ignored", directory);
        }
        if (!Files.exists(replace)) write(replace, JsonParser.parseString(BridgeConfig.EMPTY_JSON).getAsJsonObject());
        if (!Files.exists(combine)) write(combine, JsonParser.parseString(BridgeConfig.EMPTY_JSON).getAsJsonObject());
        JsonObject replaceRoot = read(replace);
        JsonObject combineRoot = read(combine);
        for (String key : new String[]{"add_missing_attributes", "sync_attributes"}) {
            if (replaceRoot.has(key) && !combineRoot.has(key)) combineRoot.add(key, replaceRoot.get(key).deepCopy());
            else if (combineRoot.has(key) && !replaceRoot.has(key)) replaceRoot.add(key, combineRoot.get(key).deepCopy());
        }
        BridgeConfig replacement = section(replaceRoot, "replace");
        BridgeConfig combination = section(combineRoot, "combine");
        if (replacement.addMissing() != combination.addMissing() || replacement.sync() != combination.sync()) {
            throw new IllegalArgumentException("add_missing_attributes and sync_attributes must match in replace.json and combine.json");
        }
        JsonObject merged = JsonParser.parseString(replacement.json()).getAsJsonObject();
        JsonArray groups = merged.getAsJsonArray("groups");
        for (JsonElement group : JsonParser.parseString(combination.json()).getAsJsonObject().getAsJsonArray("groups")) groups.add(group);
        return BridgeConfig.parse(BridgeConfig.GSON.toJson(merged));
    }

    private static BridgeConfig section(JsonObject root, String mode) {
        JsonObject normalized = root.deepCopy();
        if (normalized.has("groups") && normalized.get("groups").isJsonArray()) {
            int index = 0;
            for (JsonElement element : normalized.getAsJsonArray("groups")) {
                if (element.isJsonObject()) {
                    JsonObject group = element.getAsJsonObject();
                    if (group.has("mode")) throw new IllegalArgumentException("Do not specify mode inside " + mode + ".json");
                    group.addProperty("mode", mode);
                    if (!group.has("name")) group.addProperty("name", mode + "_" + index);
                }
                index++;
            }
        }
        return BridgeConfig.parse(BridgeConfig.GSON.toJson(normalized));
    }

    private static JsonObject split(JsonObject original, String mode) {
        JsonObject result = original.deepCopy();
        JsonArray groups = new JsonArray();
        for (JsonElement element : original.getAsJsonArray("groups")) {
            JsonObject group = element.getAsJsonObject();
            JsonElement configuredMode = group.get("mode");
            String groupMode = configuredMode != null && configuredMode.isJsonPrimitive() && configuredMode.getAsJsonPrimitive().isString()
                    && configuredMode.getAsString().equals("replace") ? "replace" : "combine";
            if (mode.equals(groupMode)) {
                JsonObject copy = group.deepCopy();
                copy.remove("mode");
                groups.add(copy);
            }
        }
        result.add("groups", groups);
        return result;
    }

    private static JsonObject read(Path path) throws IOException {
        if (Files.size(path) > 65536) throw new IllegalArgumentException("Configuration exceeds 64 KiB: " + path);
        JsonElement element = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
        if (!element.isJsonObject()) throw new IllegalArgumentException("Configuration must be an object: " + path);
        return element.getAsJsonObject();
    }

    private static void write(Path path, JsonObject object) throws IOException {
        String json = BridgeConfig.GSON.toJson(object);
        if (json.getBytes(StandardCharsets.UTF_8).length > 65536) throw new IllegalArgumentException("Configuration exceeds 64 KiB: " + path);
        Files.writeString(path, json, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }
}
