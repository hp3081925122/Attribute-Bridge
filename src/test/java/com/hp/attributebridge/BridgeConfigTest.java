package com.hp.attributebridge;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BridgeConfigTest {
    private final Attribute a = new RangedAttribute("test.a", 10, 0, 1000);
    private final Attribute b = new RangedAttribute("test.b", 20, 0, 1000);
    private final Map<ResourceLocation, Attribute> attributes = Map.of(new ResourceLocation("test:a"), a, new ResourceLocation("test:b"), b);

    private BridgeConfig parse(String groups) {
        return BridgeConfig.parse("{\"schema_version\":1,\"groups\":" + groups + "}", attributes::get);
    }

    @Test
    void replaceSuppressesOnlySourceModifiers() {
        BridgeConfig config = parse("[{\"name\":\"pair\",\"mode\":\"replace\",\"attributes\":[\"test:a\",\"test:b\"],\"target\":\"test:b\"}]");
        assertTrue(config.group(a).suppresses(a));
        assertFalse(config.group(b).suppresses(b));
        assertEquals(10, new AttributeInstance(a, ignored -> {}).getBaseValue());
        assertEquals(20, new AttributeInstance(b, ignored -> {}).getBaseValue());
    }

    @Test
    void combinePreservesBothChannelsAndDefaultsToKeepingAllContributions() {
        BridgeConfig config = parse("[{\"attributes\":[\"test:a\",\"test:b\"]}]");
        assertSame(config.group(a), config.group(b));
        assertFalse(config.group(a).suppresses(a));
        assertFalse(config.group(b).suppresses(b));
        assertFalse(config.group(a).preferSelf());
    }

    @Test
    void rejectsOverlappingGroupsAndDuplicateMembers() {
        assertThrows(IllegalArgumentException.class, () -> parse("[{\"attributes\":[\"test:a\",\"test:b\"]},{\"attributes\":[\"test:a\",\"test:b\"]}]"));
        assertThrows(IllegalArgumentException.class, () -> parse("[{\"attributes\":[\"test:a\",\"test:a\"]}]"));
    }

    @Test
    void rejectsMissingAttributesInvalidTargetsAndMisspelledFields() {
        assertThrows(IllegalArgumentException.class, () -> parse("[{\"attributes\":[\"test:a\",\"test:missing\"]}]"));
        assertThrows(IllegalArgumentException.class, () -> parse("[{\"mode\":\"replace\",\"attributes\":[\"test:a\",\"test:b\"],\"target\":\"test:c\"}]"));
        assertThrows(IllegalArgumentException.class, () -> parse("[{\"mod\":\"replace\",\"attributes\":[\"test:a\",\"test:b\"]}]"));
    }

    @Test
    void roundTripsNormalizedRulesAndIgnoresDisabledExamples() {
        BridgeConfig config = parse("[{\"attributes\":[\"test:a\",\"test:b\"]},{\"enabled\":false,\"attributes\":[\"example:a\",\"example:b\"]}]");
        BridgeConfig roundTrip = BridgeConfig.parse(config.json(), attributes::get);
        assertEquals(1, roundTrip.groups().size());
        assertEquals(config.group(a).ids(), roundTrip.group(a).ids());
    }

    @Test
    void skipsTheWholeGroupWhenAnEntityLacksAMemberUnlessOptedIn() {
        BridgeConfig.Group group = parse("[{\"attributes\":[\"test:a\",\"test:b\"]}]").group(a);
        assertFalse(group.available(attribute -> attribute == a, false));
        assertTrue(group.available(attribute -> true, false));
        assertTrue(group.available(attribute -> attribute == a, true));
    }

    @Test
    void skipsAnOptionalMissingGroupWithoutLosingTheFollowingUnnamedGroup() {
        String json = "{\"schema_version\":1,\"missing_attribute_policy\":\"skip_group\",\"groups\":[{\"attributes\":[\"test:a\",\"optional:missing\"]},{\"attributes\":[\"test:a\",\"test:b\"]}]}";
        BridgeConfig config = BridgeConfig.parse(json, attributes::get);
        assertEquals(1, config.groups().size());
        assertEquals("group_1", config.group(a).name());
        assertFalse(config.json().contains("optional:missing"));
        assertEquals("group_1", BridgeConfig.parse(config.json(), attributes::get).group(a).name());
    }
}
