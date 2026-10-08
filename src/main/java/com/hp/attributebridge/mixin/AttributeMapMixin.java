package com.hp.attributebridge.mixin;

import com.hp.attributebridge.*;
import net.minecraft.world.entity.ai.attributes.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import java.util.*;

@Mixin(AttributeMap.class)
public abstract class AttributeMapMixin implements AttributeMapBridge {
    @Shadow @Final private Map<Attribute, AttributeInstance> attributes;
    @Shadow public abstract AttributeInstance getInstance(Attribute attribute);
    @Shadow private void onAttributeModified(AttributeInstance instance) { throw new AssertionError(); }
    @Unique private boolean bridge$client;
    @Unique private boolean bridge$invalidating;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void bridge$track(AttributeSupplier supplier, CallbackInfo ci) { BridgeRuntime.track((AttributeMap) (Object) this); }

    @Override
    public boolean bridge$isClient() { return bridge$client; }

    @Override
    public void bridge$setClient(boolean client) {
        bridge$client = client;
        for (AttributeInstance instance : bridge$instances()) ((AttributeInstanceBridge) instance).bridge$markDirty();
    }

    @Override
    public AttributeInstance bridge$existing(Attribute attribute) { return attributes.get(attribute); }

    @Override
    public Collection<AttributeInstance> bridge$instances() { return new ArrayList<>(attributes.values()); }

    @Override
    public void bridge$invalidate(BridgeConfig.Group group, AttributeInstance source) {
        if (bridge$invalidating) return;
        bridge$invalidating = true;
        try {
            for (Attribute attribute : group.attributes()) {
                AttributeInstance instance = attributes.get(attribute);
                if (instance != null && instance != source) ((AttributeInstanceBridge) instance).bridge$markDirty();
            }
        } finally { bridge$invalidating = false; }
    }

    @Inject(method = "getInstance", at = @At("RETURN"), cancellable = true)
    private void bridge$instance(Attribute attribute, CallbackInfoReturnable<AttributeInstance> cir) {
        AttributeInstance instance = cir.getReturnValue();
        BridgeConfig config = BridgeRuntime.config((AttributeMap) (Object) this);
        if (instance == null && config.addMissing() && config.group(attribute) != null) {
            instance = new AttributeInstance(attribute, this::onAttributeModified);
            attributes.put(attribute, instance);
            cir.setReturnValue(instance);
            onAttributeModified(instance);
        }
        if (instance != null) ((AttributeInstanceBridge) instance).bridge$setOwner((AttributeMap) (Object) this);
    }

    @Inject(method = "onAttributeModified", at = @At("RETURN"))
    private void bridge$changed(AttributeInstance instance, CallbackInfo ci) {
        ((AttributeInstanceBridge) instance).bridge$setOwner((AttributeMap) (Object) this);
        BridgeRuntime.changed((AttributeMap) (Object) this, instance);
    }

    @Inject(method = "getValue", at = @At("HEAD"), cancellable = true)
    private void bridge$value(Attribute attribute, CallbackInfoReturnable<Double> cir) {
        if (BridgeRuntime.config((AttributeMap) (Object) this).group(attribute) != null) {
            AttributeInstance instance = getInstance(attribute);
            if (instance != null) cir.setReturnValue(instance.getValue());
        }
    }

    @Inject(method = "getBaseValue", at = @At("HEAD"), cancellable = true)
    private void bridge$base(Attribute attribute, CallbackInfoReturnable<Double> cir) {
        BridgeConfig config = BridgeRuntime.config((AttributeMap) (Object) this);
        if (config.addMissing() && config.group(attribute) != null) {
            AttributeInstance instance = getInstance(attribute);
            if (instance != null) cir.setReturnValue(instance.getBaseValue());
        }
    }

    @Inject(method = "hasAttribute", at = @At("HEAD"), cancellable = true)
    private void bridge$has(Attribute attribute, CallbackInfoReturnable<Boolean> cir) {
        BridgeConfig config = BridgeRuntime.config((AttributeMap) (Object) this);
        if (config.addMissing() && config.group(attribute) != null) cir.setReturnValue(true);
    }
}
