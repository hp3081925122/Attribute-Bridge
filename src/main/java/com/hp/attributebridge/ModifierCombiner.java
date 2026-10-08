package com.hp.attributebridge;

import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import java.util.*;

public final class ModifierCombiner {
    private ModifierCombiner() {}

    public static List<AttributeModifier> merge(List<? extends Collection<AttributeModifier>> sources, boolean preferSelf) {
        List<AttributeModifier> result = new ArrayList<>();
        Set<UUID> seen = preferSelf ? new HashSet<>() : null;
        for (Collection<AttributeModifier> source : sources) {
            source.stream().sorted(Comparator.comparing(AttributeModifier::getId)).forEach(modifier -> {
                if (seen == null || seen.add(modifier.getId())) result.add(modifier);
            });
        }
        return result;
    }
}
