package com.hp.attributebridge.mixin;

import com.hp.attributebridge.*;
import net.minecraft.world.entity.ai.attributes.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Collection;
import java.util.Set;

@Mixin(AttributeInstance.class)
public abstract class AttributeInstanceMixin implements AttributeInstanceBridge {
    @Shadow @Final private Attribute attribute;
    @Shadow protected abstract void setDirty();
    @Shadow public abstract Set<AttributeModifier> getModifiers();
    @Unique private AttributeMap bridge$owner;

    @Override
    public void bridge$setOwner(AttributeMap owner) { bridge$owner = owner; }

    @Override
    public void bridge$markDirty() { setDirty(); }

    @Inject(method = "getModifiersOrEmpty", at = @At("RETURN"), cancellable = true)
    private void bridge$modifiers(AttributeModifier.Operation operation, CallbackInfoReturnable<Collection<AttributeModifier>> cir) {
        if (bridge$owner != null && BridgeRuntime.config(bridge$owner).group(attribute) != null) {
            cir.setReturnValue(BridgeRuntime.effective(bridge$owner, attribute, getModifiers()).stream().filter(modifier -> modifier.getOperation() == operation).toList());
        }
    }
}
