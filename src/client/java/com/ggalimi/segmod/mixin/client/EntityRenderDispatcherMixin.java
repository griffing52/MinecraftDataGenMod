package com.ggalimi.segmod.mixin.client;

import com.ggalimi.segmod.render.GpuSegmentationRenderer;
import com.ggalimi.segmod.render.SegmentationVertexConsumerProvider;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {

    @ModifyVariable(
        method = "render(Lnet/minecraft/entity/Entity;DDDFFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
        at = @At("HEAD"),
        ordinal = 0,
        argsOnly = true
    )
    private VertexConsumerProvider wrapVertexConsumerProvider(VertexConsumerProvider original) {
        if (GpuSegmentationRenderer.isSegmentationPass()) {
            System.out.println("[SEGMOD MIXIN] EntityRenderDispatcher wrapping VertexConsumerProvider");
            return new SegmentationVertexConsumerProvider(original);
        }
        return original;
    }
}
