package com.ggalimi.segmod.mixin.client;

import com.ggalimi.segmod.render.DepthCaptureHelper;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.ggalimi.segmod.render.SegmentationShaderManager;
import net.minecraft.resource.ResourceFactory;

/**
 * Mixin to capture the depth buffer immediately after world rendering,
 * before it gets cleared or modified.
 */
@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Inject(method = "loadPrograms", at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z", ordinal = 0))
    private void onLoadPrograms(ResourceFactory factory, CallbackInfo ci) {
        try {
            SegmentationShaderManager.getInstance().load(factory);
            System.out.println("[SegMod] Loaded segmentation shader");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Inject after the world is rendered but before post-processing.
     * This is when the depth buffer still contains valid scene depth.
     * 
     * In Minecraft 1.21.1, we inject at the end of the render method.
     */
    @Inject(
        method = "render",
        at = @At("TAIL")
    )
    private void onAfterRender(net.minecraft.client.render.RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        // Notify helper that we just finished rendering
        // Depth buffer is available now
        DepthCaptureHelper.onWorldRendered();
    }
}
