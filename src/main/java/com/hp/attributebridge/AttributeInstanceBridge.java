package com.hp.attributebridge;

import net.minecraft.world.entity.ai.attributes.AttributeMap;

public interface AttributeInstanceBridge {
    void bridge$setOwner(AttributeMap owner);
    void bridge$markDirty();
}
