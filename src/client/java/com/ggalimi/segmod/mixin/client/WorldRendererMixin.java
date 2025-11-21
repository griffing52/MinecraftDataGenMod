package com.ggalimi.segmod.mixin.client;

import com.ggalimi.segmod.render.GpuSegmentationRenderer;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to intercept world rendering for segmentation pass.
 */
@Mixin(WorldRenderer.class)
public class WorldRendererMixin {
    
    @Shadow
    private Frustum frustum;
    
    /**
     * During segmentation pass, we need to ensure blocks render with our custom colors.
     * 
     * Minecraft 1.21.1 signature: render(RenderTickCounter, boolean, Camera, GameRenderer, LightmapTextureManager, Matrix4f, Matrix4f)
     */
    @Inject(
        method = "renderLayer",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onRenderLayer(RenderLayer renderLayer, double x, double y, double z, Matrix4f matrix4f, Matrix4f matrix4f2, CallbackInfo ci) {
        if (GpuSegmentationRenderer.isSegmentationPass()) {
            // Disable block rendering for now to focus on entities
            ci.cancel();
        }
    }
}
