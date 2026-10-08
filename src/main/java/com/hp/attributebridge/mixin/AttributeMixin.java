package com.hp.attributebridge.mixin;

import com.hp.attributebridge.BridgeRuntime;
import net.minecraft.world.entity.ai.attributes.Attribute;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Attribute.class)
public abstract class AttributeMixin {
    @Inject(method = "getDescriptionId", at = @At("HEAD"), cancellable = true)
    private void bridge$description(CallbackInfoReturnable<String> cir) {
        Attribute attribute = (Attribute) (Object) this;
        Attribute target = BridgeRuntime.translationTarget(attribute);
        if (target != attribute) cir.setReturnValue(target.getDescriptionId());
    }
}
