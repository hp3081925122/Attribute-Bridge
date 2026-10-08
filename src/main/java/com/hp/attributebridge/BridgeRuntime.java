package com.hp.attributebridge;

import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.util.thread.EffectiveSide;
import org.slf4j.Logger;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class BridgeRuntime {
    public static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<AttributeMap, Boolean> MAPS = Collections.synchronizedMap(new WeakHashMap<>());
    private static volatile BridgeConfig server = BridgeConfig.EMPTY;
    private static volatile BridgeConfig client = BridgeConfig.EMPTY;

    private BridgeRuntime() {}

    public static void track(AttributeMap map) { MAPS.put(map, Boolean.TRUE); }
    public static BridgeConfig serverConfig() { return server; }
    public static BridgeConfig config(AttributeMap map) { return ((AttributeMapBridge) map).bridge$isClient() ? client : server; }

    public static Attribute translationTarget(Attribute attribute) {
        boolean serverSide = FMLEnvironment.dist == Dist.DEDICATED_SERVER || EffectiveSide.get() == LogicalSide.SERVER;
        return (serverSide ? server : client).translationTarget(attribute);
    }

    public static BridgeConfig.Group group(AttributeMap map, Attribute attribute) {
        BridgeConfig config = config(map);
        BridgeConfig.Group group = config.group(attribute);
        return group != null && group.available(map::hasAttribute, config.addMissing()) ? group : null;
    }

    public static int reload() {
        Path path = FMLPaths.CONFIGDIR.get().resolve("attribute_bridge");
        BridgeConfig previous = server;
        try {
            BridgeConfig parsed = BridgeConfigFiles.load(path, FMLPaths.CONFIGDIR.get().resolve("attribute_bridge.json"));
            if (parsed.json().getBytes(StandardCharsets.UTF_8).length > 65536) throw new IllegalArgumentException("Normalized configuration exceeds 64 KiB");
            server = parsed;
            apply(parsed, false);
            LOGGER.info("Loaded {} attribute modifier bridge groups from {}", parsed.groups().size(), path);
            return parsed.groups().size();
        } catch (Exception exception) {
            server = previous;
            try { apply(previous, false); } catch (Exception rollbackFailure) { exception.addSuppressed(rollbackFailure); }
            LOGGER.error("Failed to load attribute bridge configuration; previous rules remain active", exception);
            throw new IllegalArgumentException(exception.getMessage(), exception);
        }
    }

    public static void receive(String json) {
        BridgeConfig parsed = BridgeConfig.parse(json);
        client = parsed;
        apply(parsed, true);
    }

    public static void disconnect() {
        client = BridgeConfig.EMPTY;
        apply(client, true);
    }

    private static void apply(BridgeConfig config, boolean clientSide) {
        if (config.sync()) for (BridgeConfig.Group group : config.groups()) for (Attribute attribute : group.attributes()) attribute.setSyncable(true);
        List<AttributeMap> maps;
        synchronized (MAPS) { maps = new ArrayList<>(MAPS.keySet()); }
        for (AttributeMap map : maps) {
            AttributeMapBridge bridge = (AttributeMapBridge) map;
            if (bridge.bridge$isClient() != clientSide) continue;
            for (AttributeInstance instance : bridge.bridge$instances()) ((AttributeInstanceBridge) instance).bridge$markDirty();
        }
    }

    public static void changed(AttributeMap map, AttributeInstance instance) {
        BridgeConfig.Group group = group(map, instance.getAttribute());
        if (group != null) ((AttributeMapBridge) map).bridge$invalidate(group, instance);
    }

    public static Collection<AttributeModifier> effective(AttributeMap map, Attribute attribute, Collection<AttributeModifier> original) {
        BridgeConfig.Group group = group(map, attribute);
        if (group == null) return original;
        if (group.suppresses(attribute)) return List.of();
        List<Collection<AttributeModifier>> sources = new ArrayList<>();
        sources.add(original);
        for (Attribute member : group.attributes()) {
            if (member == attribute || group.ratio(member) == 0) continue;
            AttributeInstance instance = map.getInstance(member);
            if (instance != null) {
                double ratio = group.ratio(member);
                sources.add(ratio == 1 ? instance.getModifiers() : instance.getModifiers().stream()
                        .map(modifier -> new AttributeModifier(modifier.getId(), modifier.getName(), modifier.getAmount() * ratio, modifier.getOperation())).toList());
            }
        }
        return ModifierCombiner.merge(sources, group.preferSelf());
    }
}
