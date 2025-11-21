package com.ggalimi.segmod.mixin.client;

import com.ggalimi.segmod.render.GpuSegmentationRenderer;
import com.ggalimi.segmod.render.SegmentationVertexConsumerProvider;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to track which entity is currently being rendered and override its color.
 */
@Mixin(EntityRenderer.class)
public class EntityRendererMixin {
    
    @Inject(
        method = "render(Lnet/minecraft/entity/Entity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
        at = @At("HEAD")
    )
    private void onEntityRenderStart(Entity entity, float yaw, float tickDelta, MatrixStack matrices,
                                     VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        if (GpuSegmentationRenderer.isSegmentationPass()) {
            GpuSegmentationRenderer.setCurrentEntity(entity);
        }
    }
    
    @Inject(
        method = "render(Lnet/minecraft/entity/Entity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
        at = @At("RETURN")
    )
    private void onEntityRenderEnd(Entity entity, float yaw, float tickDelta, MatrixStack matrices,
                                   VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        if (GpuSegmentationRenderer.isSegmentationPass()) {
            GpuSegmentationRenderer.setCurrentEntity(null);
        }
    }
}
