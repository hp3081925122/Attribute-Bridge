package com.hp.attributebridge;

import net.minecraft.world.entity.ai.attributes.*;
import java.util.Collection;

public interface AttributeMapBridge {
    boolean bridge$isClient();
    void bridge$setClient(boolean client);
    AttributeInstance bridge$existing(Attribute attribute);
    Collection<AttributeInstance> bridge$instances();
    void bridge$invalidate(BridgeConfig.Group group, AttributeInstance source);
}
