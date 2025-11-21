package com.ggalimi.segmod.render;

import com.ggalimi.segmod.util.DepthExtractor;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;

/**
 * Helper class to capture depth buffer at the right moment during rendering.
 * Called from GameRendererMixin after world is rendered.
 */
public class DepthCaptureHelper {
    
    private static boolean captureRequested = false;
    private static float[] capturedDepth = null;
    private static int capturedWidth = 0;
    private static int capturedHeight = 0;
    
    /**
     * Request that depth be captured on the next render.
     */
    public static void requestDepthCapture() {
        captureRequested = true;
        capturedDepth = null;
    }
    
    /**
     * Called from mixin immediately after world rendering.
     * This is when the depth buffer contains valid data.
     */
    public static void onWorldRendered() {
        if (!captureRequested) {
            return;
        }
        
        // Capture depth immediately
        try {
            RenderSystem.assertOnRenderThread();
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null) {
                return;
            }
            Framebuffer framebuffer = client.getFramebuffer();
            capturedWidth = framebuffer.textureWidth;
            capturedHeight = framebuffer.textureHeight;
            
            // Extract raw depth while it's still available
            capturedDepth = DepthExtractor.extractRawDepth(framebuffer, capturedWidth, capturedHeight);
            
            System.out.println("[SegMod] Depth captured successfully at render time: " + 
                             capturedWidth + "x" + capturedHeight + 
                             " (" + capturedDepth.length + " pixels)");
            
        } catch (Exception e) {
            System.err.println("[SegMod] Error capturing depth during render: " + e.getMessage());
            e.printStackTrace();
            capturedDepth = null;
        }
        
        captureRequested = false;
    }
    
    /**
     * Get the most recently captured depth data.
     * Returns null if no depth has been captured yet.
     */
    public static float[] getCapturedDepth() {
        return capturedDepth;
    }
    
    /**
     * Get the width of the captured depth buffer.
     */
    public static int getCapturedWidth() {
        return capturedWidth;
    }
    
    /**
     * Get the height of the captured depth buffer.
     */
    public static int getCapturedHeight() {
        return capturedHeight;
    }
    
    /**
     * Check if depth data is available.
     */
    public static boolean hasDepthData() {
        return capturedDepth != null && capturedDepth.length > 0;
    }
    
    /**
     * Clear captured depth data.
     */
    public static void clearCapturedDepth() {
        capturedDepth = null;
        capturedWidth = 0;
        capturedHeight = 0;
    }
}
