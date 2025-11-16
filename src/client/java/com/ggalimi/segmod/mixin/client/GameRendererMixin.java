package com.ggalimi.segmod.mixin.client;

import com.ggalimi.segmod.render.DepthCaptureHelper;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to capture the depth buffer immediately after world rendering,
 * before it gets cleared or modified.
 */
@Mixin(GameRenderer.class)
public class GameRendererMixin {
    
    /**
     * Inject after the world is rendered but before post-processing.
     * This is when the depth buffer still contains valid scene depth.
     */
    @Inject(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/GameRenderer;renderWorld(FJLnet/minecraft/client/util/math/MatrixStack;)V",
            shift = At.Shift.AFTER
        )
    )
    private void onAfterRenderWorld(float tickDelta, long startTime, boolean tick, CallbackInfo ci) {
        // Notify helper that we just finished rendering the world
        // Depth buffer is available now
        DepthCaptureHelper.onWorldRendered();
    }
}
