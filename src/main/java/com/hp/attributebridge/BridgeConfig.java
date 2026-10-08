package com.hp.attributebridge;

import com.google.gson.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

public final class BridgeConfig {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final String EMPTY_JSON = "{\"schema_version\":1,\"add_missing_attributes\":false,\"sync_attributes\":true,\"groups\":[]}";
    public static final BridgeConfig EMPTY = new BridgeConfig(false, true, List.of(), Map.of(), EMPTY_JSON);
    private final boolean addMissing;
    private final boolean sync;
    private final List<Group> groups;
    private final Map<Attribute, Group> byAttribute;
    private final String json;

    private BridgeConfig(boolean addMissing, boolean sync, List<Group> groups, Map<Attribute, Group> byAttribute, String json) {
        this.addMissing = addMissing;
        this.sync = sync;
        this.groups = List.copyOf(groups);
        this.byAttribute = Map.copyOf(byAttribute);
        this.json = json;
    }

    public static BridgeConfig parse(String json) {
        return parse(json, id -> BuiltInRegistries.ATTRIBUTE.getOptional(id).orElse(null));
    }

    static BridgeConfig parse(String json, Function<ResourceLocation, Attribute> resolver) {
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonObject()) throw new IllegalArgumentException("Configuration must be an object");
        JsonObject object = root.getAsJsonObject();
        rejectUnknown(object, Set.of("schema_version", "add_missing_attributes", "sync_attributes", "missing_attribute_policy", "groups"));
        if (!object.has("schema_version") || !object.get("schema_version").isJsonPrimitive()
                || !object.getAsJsonPrimitive("schema_version").isNumber() || object.get("schema_version").getAsDouble() != 1) {
            throw new IllegalArgumentException("schema_version must be 1");
        }
        boolean addMissing = bool(object, "add_missing_attributes", false);
        boolean sync = bool(object, "sync_attributes", true);
        String missingPolicy = string(object, "missing_attribute_policy", "error");
        if (!Set.of("error", "skip_group").contains(missingPolicy)) throw new IllegalArgumentException("Unknown missing_attribute_policy");
        if (!object.has("groups") || !object.get("groups").isJsonArray()) throw new IllegalArgumentException("groups must be an array");
        JsonArray array = object.getAsJsonArray("groups");
        if (array.size() > 256) throw new IllegalArgumentException("Too many groups (maximum 256)");
        List<Group> groups = new ArrayList<>();
        Map<Attribute, Group> index = new IdentityHashMap<>();
        Set<String> names = new HashSet<>();
        JsonArray normalizedGroups = new JsonArray();
        int entryIndex = 0;
        for (JsonElement element : array) {
            int currentIndex = entryIndex++;
            if (!element.isJsonObject()) throw new IllegalArgumentException("Each group must be an object");
            JsonObject entry = element.getAsJsonObject();
            rejectUnknown(entry, Set.of("enabled", "name", "mode", "attributes", "target", "duplicate_policy", "redirect_translation"));
            if (!bool(entry, "enabled", true)) continue;
            String name = string(entry, "name", "group_" + currentIndex);
            if (name.isBlank() || !names.add(name)) throw new IllegalArgumentException("Duplicate or empty group name: " + name);
            String mode = string(entry, "mode", "combine");
            if (!Set.of("replace", "combine").contains(mode)) throw new IllegalArgumentException("Unknown mode in " + name);
            String duplicate = string(entry, "duplicate_policy", "keep_all");
            if (!Set.of("keep_all", "prefer_self").contains(duplicate)) throw new IllegalArgumentException("Unknown duplicate_policy in " + name);
            boolean redirectTranslation = bool(entry, "redirect_translation", true);
            JsonObject members = new JsonObject();
            if (!entry.has("attributes")) throw new IllegalArgumentException("attributes must be an object in " + name);
            JsonElement configuredMembers = entry.get("attributes");
            if (configuredMembers.isJsonObject()) members = configuredMembers.getAsJsonObject();
            else if (configuredMembers.isJsonArray()) {
                for (JsonElement member : configuredMembers.getAsJsonArray()) {
                    if (!member.isJsonPrimitive() || !member.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("Invalid attribute ID in " + name);
                    String key = member.getAsString();
                    if (members.has(key)) throw new IllegalArgumentException("Duplicate attribute in " + name + ": " + key);
                    members.addProperty(key, 1.0);
                }
            } else throw new IllegalArgumentException("attributes must be an object in " + name);
            if (members.size() < 2 || members.size() > 64) throw new IllegalArgumentException("A group needs 2 to 64 attributes: " + name);
            List<Attribute> attributes = new ArrayList<>();
            List<ResourceLocation> ids = new ArrayList<>();
            Map<Attribute, Double> ratios = new IdentityHashMap<>();
            boolean missing = false;
            for (Map.Entry<String, JsonElement> member : members.entrySet()) {
                JsonElement value = member.getValue();
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("Conversion ratio must be a number: " + member.getKey());
                double ratio = value.getAsDouble();
                if (!Double.isFinite(ratio) || ratio < 0) throw new IllegalArgumentException("Conversion ratio must be finite and nonnegative: " + member.getKey());
                ResourceLocation id = id(member.getKey());
                if (ids.contains(id)) throw new IllegalArgumentException("Duplicate attribute in " + name + ": " + id);
                ids.add(id);
                Attribute attribute = resolver.apply(id);
                if (attribute == null) {
                    if (missingPolicy.equals("error")) throw new IllegalArgumentException("Unknown attribute: " + id);
                    missing = true;
                }
                attributes.add(attribute);
                if (attribute != null) ratios.put(attribute, ratio);
            }
            ResourceLocation targetId = mode.equals("replace") ? id(string(entry, "target", "")) : null;
            if (targetId != null && !ids.contains(targetId)) throw new IllegalArgumentException("target must be a group member in " + name);
            if (mode.equals("combine") && entry.has("target")) throw new IllegalArgumentException("combine groups cannot specify target: " + name);
            if (missing) {
                BridgeRuntime.LOGGER.warn("Skipping attribute bridge group '{}' because an attribute is not registered", name);
                continue;
            }
            Attribute target = targetId == null ? null : attributes.get(ids.indexOf(targetId));
            Group group = new Group(name, mode.equals("replace"), List.copyOf(attributes), List.copyOf(ids), target, duplicate.equals("prefer_self"), redirectTranslation, Map.copyOf(ratios));
            for (Attribute attribute : attributes) {
                if (index.putIfAbsent(attribute, group) != null) throw new IllegalArgumentException("An attribute cannot belong to multiple groups: " + name);
            }
            groups.add(group);
            JsonObject normalizedEntry = entry.deepCopy();
            normalizedEntry.addProperty("name", name);
            normalizedEntry.add("attributes", members.deepCopy());
            normalizedGroups.add(normalizedEntry);
        }
        JsonObject normalized = new JsonObject();
        normalized.addProperty("schema_version", 1);
        normalized.addProperty("add_missing_attributes", addMissing);
        normalized.addProperty("sync_attributes", sync);
        normalized.addProperty("missing_attribute_policy", "error");
        normalized.add("groups", normalizedGroups);
        return new BridgeConfig(addMissing, sync, groups, index, GSON.toJson(normalized));
    }

    private static ResourceLocation id(String value) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null || !value.contains(":")) throw new IllegalArgumentException("Invalid namespaced attribute ID: " + value);
        return id;
    }

    private static boolean bool(JsonObject object, String key, boolean fallback) {
        if (!object.has(key)) return fallback;
        JsonElement value = object.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException(key + " must be a boolean");
        return value.getAsBoolean();
    }

    private static String string(JsonObject object, String key, String fallback) {
        if (!object.has(key)) return fallback;
        JsonElement value = object.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException(key + " must be a string");
        return value.getAsString();
    }

    private static void rejectUnknown(JsonObject object, Set<String> allowed) {
        for (String key : object.keySet()) if (!allowed.contains(key)) throw new IllegalArgumentException("Unknown configuration key: " + key);
    }

    public Group group(Attribute attribute) { return byAttribute.get(attribute); }
    public Attribute translationTarget(Attribute attribute) {
        Group group = byAttribute.get(attribute);
        return group != null && group.redirectTranslation() && group.suppresses(attribute) ? group.target() : attribute;
    }
    public List<Group> groups() { return groups; }
    public boolean addMissing() { return addMissing; }
    public boolean sync() { return sync; }
    public String json() { return json; }

    public record Group(String name, boolean replace, List<Attribute> attributes, List<ResourceLocation> ids, Attribute target, boolean preferSelf, boolean redirectTranslation, Map<Attribute, Double> ratios) {
        public double ratio(Attribute attribute) { return ratios.get(attribute); }
        public boolean suppresses(Attribute attribute) { return replace && target != attribute; }
        public boolean available(Predicate<Attribute> hasAttribute, boolean addMissing) {
            return addMissing || attributes.stream().allMatch(hasAttribute);
        }
    }
}
