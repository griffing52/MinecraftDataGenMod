package com.ggalimi.segmod.render;

import com.ggalimi.segmod.util.BlockClassMap;
import com.ggalimi.segmod.util.EntityClassMap;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;

/**
 * GPU-based segmentation renderer using custom framebuffer and shader-based rendering.
 * 
 * This performs a complete second render pass with the segmentation shader,
 * rendering all blocks and entities with flat ID colors.
 * 
 * Much faster than raycasting and renders actual entity geometry.
 */
public class GpuSegmentationRenderer {
    
    // Custom framebuffer for segmentation rendering
    private static SimpleFramebuffer segmentationFbo = null;
    
    // Flag to indicate we're in segmentation rendering mode
    private static boolean isSegmentationPass = false;
    
    // Current entity/block being rendered (for color lookup)
    private static Entity currentEntity = null;
    private static net.minecraft.block.Block currentBlock = null;
    private static float[] currentSegmentationColor = new float[]{1.0f, 0.0f, 1.0f, 1.0f}; // Default magenta
    
    // Debug counters
    private static int blockRenderCount = 0;
    private static int entityRenderCount = 0;
    private static int vertexConsumerWrapCount = 0;

    /**
     * Get the current global segmentation color.
     */
    public static float[] getCurrentSegmentationColor() {
        return currentSegmentationColor;
    }

    /**
     * Set the current global segmentation color manually.
     */
    public static void setCurrentSegmentationColor(float[] color) {
        currentSegmentationColor = color;
    }
    
    /**
     * Initialize or resize the segmentation framebuffer.
     */
    public static void ensureFramebuffer(int width, int height) {
        RenderSystem.assertOnRenderThread();
        
        if (segmentationFbo != null) {
            if (segmentationFbo.textureWidth != width || segmentationFbo.textureHeight != height) {
                segmentationFbo.delete();
                segmentationFbo = null;
            }
        }
        
        if (segmentationFbo == null) {
            segmentationFbo = new SimpleFramebuffer(width, height, true, MinecraftClient.IS_SYSTEM_MAC);
            segmentationFbo.setClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        }
    }
    
    /**
     * Clean up resources.
     */
    public static void cleanup() {
        if (segmentationFbo != null) {
            segmentationFbo.delete();
            segmentationFbo = null;
        }
    }
    
    /**
     * Check if we're currently in the segmentation rendering pass.
     */
    public static boolean isSegmentationPass() {
        return isSegmentationPass;
    }
    
    /**
     * Set the current entity being rendered (for mixins).
     */
    public static void setCurrentEntity(Entity entity) {
        currentEntity = entity;
        if (entity != null && isSegmentationPass) {
            entityRenderCount++;
            // Calculate color immediately
            int[] rgb = EntityClassMap.getEntityColor(entity);
            currentSegmentationColor = new float[]{
                rgb[0] / 255.0f,
                rgb[1] / 255.0f,
                rgb[2] / 255.0f,
                1.0f
            };
        }
    }
    
    /**
     * Get the current entity being rendered.
     */
    public static Entity getCurrentEntity() {
        return currentEntity;
    }
    
    /**
     * Set the current block being rendered (for mixins).
     */
    public static void setCurrentBlock(net.minecraft.block.Block block) {
        currentBlock = block;
        if (block != null && isSegmentationPass) {
            blockRenderCount++;
            // Calculate color immediately
            int[] rgb = BlockClassMap.getBlockColor(block);
            currentSegmentationColor = new float[]{
                rgb[0] / 255.0f,
                rgb[1] / 255.0f,
                rgb[2] / 255.0f,
                1.0f
            };
        }
    }
    
    /**
     * Get the current block being rendered.
     */
    public static net.minecraft.block.Block getCurrentBlock() {
        return currentBlock;
    }
    
    /**
     * Increment vertex consumer wrap counter (called by mixins).
     */
    public static void incrementVertexConsumerWrapCount() {
        vertexConsumerWrapCount++;
    }
    
    /**
     * Get vertex consumer wrap count.
     */
    public static int getVertexConsumerWrapCount() {
        return vertexConsumerWrapCount;
    }
    
    /**
     * Get the segmentation color for the current block as normalized floats.
     */
    public static float[] getCurrentBlockColor() {
        if (currentBlock == null) {
            return new float[]{0.0f, 0.0f, 0.0f, 1.0f};
        }
        int[] rgb = BlockClassMap.getBlockColor(currentBlock);
        return new float[]{
            rgb[0] / 255.0f,
            rgb[1] / 255.0f,
            rgb[2] / 255.0f,
            1.0f
        };
    }
    
    /**
     * Get the segmentation color for the current entity as normalized floats.
     */
    public static float[] getCurrentEntityColor() {
        if (currentEntity == null) {
            return new float[]{0.0f, 0.0f, 0.0f, 1.0f};
        }
        int[] rgb = EntityClassMap.getEntityColor(currentEntity);
        return new float[]{
            rgb[0] / 255.0f,
            rgb[1] / 255.0f,
            rgb[2] / 255.0f,
            1.0f
        };
    }
    
    /**
     * Main entry point: Render the segmentation mask using GPU rendering.
     * This will be called from the render loop after normal rendering.
     * 
     * @param context The world render context
     * @return BufferedImage containing the segmentation mask
     */
    public static BufferedImage renderSegmentationMask(net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext context) {
        RenderSystem.assertOnRenderThread();
        
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            System.err.println("[SEGMOD ERROR] MinecraftClient is null!");
            return new BufferedImage(1920, 1080, BufferedImage.TYPE_INT_RGB);
        }
        
        Framebuffer mainFbo = client.getFramebuffer();
        int width = mainFbo.textureWidth;
        int height = mainFbo.textureHeight;
        
        // Ensure segmentation framebuffer exists and is correct size
        ensureFramebuffer(width, height);
        
        try {
            // === BEGIN SEGMENTATION PASS ===
            isSegmentationPass = true;
            setCurrentEntity(null);
            setCurrentBlock(null);
            blockRenderCount = 0;
            entityRenderCount = 0;
            vertexConsumerWrapCount = 0;
            
            // System.out.println("[SEGMOD DEBUG] Starting GPU segmentation pass");
            long startTime = System.nanoTime();
            
            // Bind segmentation framebuffer
            long fboStart = System.nanoTime();
            segmentationFbo.clear(MinecraftClient.IS_SYSTEM_MAC);
            
            // Copy depth from main framebuffer to allow occlusion by blocks
            segmentationFbo.copyDepthFrom(mainFbo);
            
            segmentationFbo.beginWrite(true);
            long fboTime = (System.nanoTime() - fboStart) / 1_000_000;
            // System.out.println("[SEGMOD DEBUG] FBO bind took " + fboTime + "ms");
            
            // Set up OpenGL state for segmentation rendering
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            RenderSystem.disableCull(); // Render all faces
            
            // Re-render the world with segmentation shader
            // This will trigger our mixins to use segmentation colors
            long renderStart = System.nanoTime();
            renderWorldSegmentation(context);
            
            long renderTime = (System.nanoTime() - renderStart) / 1_000_000;
            // System.out.println("[SEGMOD DEBUG] World re-render took " + renderTime + "ms");
            // System.out.println("[SEGMOD DEBUG] Blocks rendered: " + blockRenderCount);
            // System.out.println("[SEGMOD DEBUG] Entities rendered: " + entityRenderCount);
            // System.out.println("[SEGMOD DEBUG] Vertex consumers wrapped: " + vertexConsumerWrapCount);
            
            // Read pixels from segmentation framebuffer
            long readStart = System.nanoTime();
            BufferedImage result = readFramebufferToImage(segmentationFbo, width, height);
            long readTime = (System.nanoTime() - readStart) / 1_000_000;
            // System.out.println("[SEGMOD DEBUG] Pixel readback took " + readTime + "ms");
            
            // === END SEGMENTATION PASS ===
            long totalTime = (System.nanoTime() - startTime) / 1_000_000;
            // System.out.println("[SEGMOD DEBUG] TOTAL GPU segmentation took " + totalTime + "ms");
            // System.out.println("[SEGMOD DEBUG] ========================================");
            
            isSegmentationPass = false;
            
            // Restore main framebuffer
            mainFbo.beginWrite(true);
            
            return result;
            
        } catch (Exception e) {
            e.printStackTrace();
            isSegmentationPass = false;
            
            // Restore main framebuffer on error
            mainFbo.beginWrite(true);
            
            return new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        }
    }
    
    /**
     * Render the world with segmentation colors.
     * This is called during the segmentation pass.
     */
    private static void renderWorldSegmentation(net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        Camera camera = context.camera();
        net.minecraft.client.render.RenderTickCounter tickCounter = context.tickCounter();
        
        // Use the matrix stack from the context which matches the player's view
        MatrixStack matrices = context.matrixStack();
        matrices.push();
        
        // Ensure projection matrix matches the main render
        // RenderSystem.setProjectionMatrix(context.projectionMatrix(), net.minecraft.client.render.VertexSorter.BY_Z);
        
        // Create our custom provider that forces the segmentation shader
        net.minecraft.client.render.VertexConsumerProvider.Immediate immediate = 
            net.minecraft.client.render.VertexConsumerProvider.immediate(new net.minecraft.client.util.BufferAllocator(256));
        SegmentationVertexConsumerProvider segProvider = new SegmentationVertexConsumerProvider(immediate);
        
        net.minecraft.util.math.Vec3d cameraPos = camera.getPos();
        double camX = cameraPos.x;
        double camY = cameraPos.y;
        double camZ = cameraPos.z;
        
        // === Block Rendering ===
        net.minecraft.client.render.block.BlockRenderManager blockManager = client.getBlockRenderManager();
        net.minecraft.util.math.BlockPos.Mutable mutablePos = new net.minecraft.util.math.BlockPos.Mutable();
        int radius = 16; // Render radius
        
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    mutablePos.set(camX + x, camY + y, camZ + z);
                    net.minecraft.block.BlockState state = client.world.getBlockState(mutablePos);
                    if (!state.isAir()) {
                        setCurrentBlock(state.getBlock());
                        matrices.push();
                        matrices.translate(mutablePos.getX() - camX, mutablePos.getY() - camY, mutablePos.getZ() - camZ);
                        
                        // Render block
                        blockManager.renderBlock(
                            state, 
                            mutablePos, 
                            client.world, 
                            matrices, 
                            segProvider.getBuffer(net.minecraft.client.render.RenderLayer.getSolid()), 
                            true, 
                            net.minecraft.util.math.random.Random.create(state.getRenderingSeed(mutablePos))
                        );
                        
                        matrices.pop();
                    }
                }
            }
        }
        setCurrentBlock(null);
        
        // Manually render all loaded entities
        for (Entity entity : client.world.getEntities()) {
            if (entity == camera.getFocusedEntity() && client.options.getPerspective().isFirstPerson()) {
                continue; // Don't render self in first person
            }
            
            // Calculate interpolated position relative to camera
            double x = net.minecraft.util.math.MathHelper.lerp(tickCounter.getTickDelta(true), entity.lastRenderX, entity.getX()) - camX;
            double y = net.minecraft.util.math.MathHelper.lerp(tickCounter.getTickDelta(true), entity.lastRenderY, entity.getY()) - camY;
            double z = net.minecraft.util.math.MathHelper.lerp(tickCounter.getTickDelta(true), entity.lastRenderZ, entity.getZ()) - camZ;
            float yaw = net.minecraft.util.math.MathHelper.lerp(tickCounter.getTickDelta(true), entity.prevYaw, entity.getYaw());
            
            // Set current entity for color lookup
            setCurrentEntity(entity);
            
            // Render the entity
            try {
                client.getEntityRenderDispatcher().render(
                    entity, 
                    x, y, z, 
                    yaw, 
                    tickCounter.getTickDelta(true), 
                    matrices, 
                    segProvider, 
                    0xF000F0 // Full brightness
                );
            } catch (Exception e) {
                // Ignore rendering errors for specific entities
            }
            
            setCurrentEntity(null);
        }
        
        // Draw all buffered vertices
        immediate.draw();
        
        matrices.pop();
    }
    
    /**
     * Read the segmentation framebuffer into a BufferedImage.
     */
    private static BufferedImage readFramebufferToImage(Framebuffer fbo, int width, int height) {
        fbo.beginRead();
        
        ByteBuffer buffer = ByteBuffer.allocateDirect(width * height * 3);
        GL11.glReadPixels(0, 0, width, height, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, buffer);
        
        fbo.endRead();
        
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int i = (x + y * width) * 3;
                int r = buffer.get(i) & 0xFF;
                int g = buffer.get(i + 1) & 0xFF;
                int b = buffer.get(i + 2) & 0xFF;
                
                // Flip vertically (OpenGL coordinates)
                image.setRGB(x, height - 1 - y, (r << 16) | (g << 8) | b);
            }
        }
        
        return image;
    }
}
