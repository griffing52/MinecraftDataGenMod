package com.ggalimi.segmod.mixin.client;

import com.ggalimi.segmod.render.GpuSegmentationRenderer;
import com.ggalimi.segmod.render.SegmentationVertexConsumer;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.BlockModelRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to intercept block rendering and apply segmentation colors.
 */
@Mixin(BlockModelRenderer.class)
public class BlockModelRendererMixin {
    
    /**
     * Intercept the render method to set the current block being rendered.
     */
    @Inject(
        method = "render(Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/client/render/model/BakedModel;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;ZLnet/minecraft/util/math/random/Random;JI)V",
        at = @At("HEAD")
    )
    private void onBlockRenderStart(BlockRenderView world, BakedModel model, BlockState state, BlockPos pos,
                                    MatrixStack matrices, VertexConsumer vertexConsumer, boolean cull,
                                    Random random, long seed, int overlay, CallbackInfo ci) {
        try {
            if (GpuSegmentationRenderer.isSegmentationPass()) {
                GpuSegmentationRenderer.setCurrentBlock(state.getBlock());
            }
        } catch (Exception e) {
            // Ignore errors during initialization
        }
    }
    
    /**
     * Wrap the vertex consumer to override colors during segmentation pass.
     */
    @ModifyVariable(
        method = "render(Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/client/render/model/BakedModel;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;ZLnet/minecraft/util/math/random/Random;JI)V",
        at = @At("HEAD"),
        ordinal = 0,
        argsOnly = true
    )
    private VertexConsumer wrapVertexConsumer(VertexConsumer original) {
        try {
            if (GpuSegmentationRenderer.isSegmentationPass()) {
                System.out.println("[SEGMOD MIXIN] BlockModelRendererMixin wrapping VertexConsumer");
                GpuSegmentationRenderer.incrementVertexConsumerWrapCount();
                return new SegmentationVertexConsumer(original);
            }
        } catch (Exception e) {
            // Ignore errors during initialization
        }
        return original;
    }
}
