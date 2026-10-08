package com.hp.attributebridge;

import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ModifierCombinerTest {
    private final UUID shared = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void keepsDistinctAttributeContributionsWithTheSameUuid() {
        AttributeModifier own = new AttributeModifier(shared, "own", 2, AttributeModifier.Operation.ADDITION);
        AttributeModifier imported = new AttributeModifier(shared, "imported", 0.5, AttributeModifier.Operation.MULTIPLY_TOTAL);
        assertEquals(List.of(own, imported), ModifierCombiner.merge(List.of(List.of(own), List.of(imported)), false));
    }

    @Test
    void prefersOwnUuidAcrossDifferentOperations() {
        AttributeModifier own = new AttributeModifier(shared, "own", 2, AttributeModifier.Operation.ADDITION);
        AttributeModifier imported = new AttributeModifier(shared, "imported", 0.5, AttributeModifier.Operation.MULTIPLY_TOTAL);
        List<AttributeModifier> result = ModifierCombiner.merge(List.of(List.of(own), List.of(imported)), true);
        assertEquals(1, result.size());
        assertSame(own, result.get(0));
    }

    @Test
    void removalsAndReplacementsAreReflectedWithoutMutatingSources() {
        List<AttributeModifier> source = new ArrayList<>();
        AttributeModifier modifier = new AttributeModifier(shared, "source", -0.25, AttributeModifier.Operation.MULTIPLY_BASE);
        source.add(modifier);
        List<AttributeModifier> before = ModifierCombiner.merge(List.of(List.of(), source), false);
        assertEquals(List.of(modifier), source);
        source.clear();
        assertTrue(ModifierCombiner.merge(List.of(List.of(), source), false).isEmpty());
        assertEquals(List.of(modifier), before);
    }
}
