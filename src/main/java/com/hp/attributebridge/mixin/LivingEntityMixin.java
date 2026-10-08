package com.hp.attributebridge.mixin;

import com.hp.attributebridge.AttributeMapBridge;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Shadow public abstract AttributeMap getAttributes();

    @Inject(method = "<init>", at = @At("RETURN"))
    private void bridge$side(EntityType<? extends LivingEntity> type, Level level, CallbackInfo ci) {
        ((AttributeMapBridge) getAttributes()).bridge$setClient(level.isClientSide);
    }
}
