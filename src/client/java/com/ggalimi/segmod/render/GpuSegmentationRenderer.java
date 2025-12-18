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
            
            // Do NOT copy depth from main framebuffer to avoid z-fighting artifacts
            // segmentationFbo.copyDepthFrom(mainFbo);
            
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
    
    // Reusable buffer to prevent memory churn/leaks
    private static net.minecraft.client.util.BufferAllocator bufferAllocator = null;
    private static net.minecraft.client.render.VertexConsumerProvider.Immediate immediate = null;

    private static void ensureBuffers() {
        if (bufferAllocator == null) {
            bufferAllocator = new net.minecraft.client.util.BufferAllocator(256);
        }
        if (immediate == null) {
            immediate = net.minecraft.client.render.VertexConsumerProvider.immediate(bufferAllocator);
        }
    }

    // Reflection cache for ChunkInfo access
    private static java.lang.reflect.Field chunkInfoChunkField = null;

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
        
        // Ensure buffers are initialized
        ensureBuffers();
        
        // Create our custom provider that forces the segmentation shader
        SegmentationVertexConsumerProvider segProvider = new SegmentationVertexConsumerProvider(immediate);
        
        net.minecraft.util.math.Vec3d cameraPos = camera.getPos();
        double camX = cameraPos.x;
        double camY = cameraPos.y;
        double camZ = cameraPos.z;
        
        // === Block Rendering ===
        net.minecraft.client.render.block.BlockRenderManager blockManager = client.getBlockRenderManager();
        net.minecraft.util.math.BlockPos.Mutable mutablePos = new net.minecraft.util.math.BlockPos.Mutable();
        
        // MEGA OPTIMIZATION: Use WorldRenderer's visible chunk list
        // This skips all frustum culling calculations and only iterates chunks that are actually visible
        com.ggalimi.segmod.mixin.client.WorldRendererAccessor worldRenderer = (com.ggalimi.segmod.mixin.client.WorldRendererAccessor) client.worldRenderer;
        
        boolean renderedViaChunkInfos = false;
        
        try {
            // Try to get visible chunks from WorldRenderer
            // We use ObjectArrayList<Object> because ChunkInfo is private/package-private
            @SuppressWarnings("unchecked")
            it.unimi.dsi.fastutil.objects.ObjectArrayList<Object> chunkInfos = (it.unimi.dsi.fastutil.objects.ObjectArrayList<Object>) worldRenderer.getChunkInfos();
            
            if (chunkInfos != null && !chunkInfos.isEmpty()) {
                System.out.println("[SEGMOD DEBUG] Found " + chunkInfos.size() + " visible chunks");
                int chunksProcessed = 0;
                
                for (Object info : chunkInfos) {
                    // Use reflection to get the 'chunk' field from ChunkInfo
                    if (chunkInfoChunkField == null) {
                        try {
                            System.out.println("[SEGMOD DEBUG] Inspecting ChunkInfo fields:");
                            // Find the field that returns a BuiltChunk
                            for (java.lang.reflect.Field f : info.getClass().getDeclaredFields()) {
                                System.out.println("  " + f.getName() + " : " + f.getType().getName());
                                if (f.getType().getName().contains("BuiltChunk") || 
                                    f.getType().getName().contains("ChunkBuilder$BuiltChunk") ||
                                    f.getType().getName().contains("class_846$class_851")) { // Intermediary name for BuiltChunk
                                    f.setAccessible(true);
                                    chunkInfoChunkField = f;
                                    System.out.println("[SEGMOD DEBUG] Found chunk field: " + f.getName());
                                    break;
                                }
                            }
                        } catch (Exception e) {
                            System.err.println("[SEGMOD ERROR] Failed to find chunk field in ChunkInfo: " + e.getMessage());
                        }
                    }
                    
                    if (chunkInfoChunkField == null) {
                         System.out.println("[SEGMOD DEBUG] chunkInfoChunkField is null, skipping");
                         continue;
                    }
                    
                    net.minecraft.client.render.chunk.ChunkBuilder.BuiltChunk builtChunk = 
                        (net.minecraft.client.render.chunk.ChunkBuilder.BuiltChunk) chunkInfoChunkField.get(info);
                    
                    // Skip if chunk has no rendered blocks (optimization)
                    if (builtChunk.getData().isEmpty()) {
                        // System.out.println("[SEGMOD DEBUG] Chunk empty");
                        continue;
                    }
                    
                    net.minecraft.util.math.BlockPos origin = builtChunk.getOrigin();
                    int minX = origin.getX();
                    int minY = origin.getY();
                    int minZ = origin.getZ();
                    int maxX = minX + 16;
                    int maxY = minY + 16;
                    int maxZ = minZ + 16;
                    
                    // Iterate blocks in this render chunk (16x16x16)
                    for (int x = minX; x < maxX; x++) {
                        for (int y = minY; y < maxY; y++) {
                            for (int z = minZ; z < maxZ; z++) {
                                mutablePos.set(x, y, z);
                                net.minecraft.block.BlockState state = client.world.getBlockState(mutablePos);
                                
                                if (!state.isAir()) { // && state.shouldDrawSide(...) - handled by renderBlock
                                    setCurrentBlock(state.getBlock());
                                    matrices.push();
                                    matrices.translate(x - camX, y - camY, z - camZ);
                                    
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
                    
                    chunksProcessed++;
                    // Flush buffer every 4 chunks to manage memory
                    if (chunksProcessed % 4 == 0) {
                        // Reset ModelView for draw, then restore
                        Matrix4f originalModelView = new Matrix4f(RenderSystem.getModelViewMatrix());
                        RenderSystem.getModelViewMatrix().identity();
                        RenderSystem.applyModelViewMatrix();
                        
                        // Ensure Projection
                        Matrix4f originalProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
                        RenderSystem.setProjectionMatrix(context.projectionMatrix(), com.mojang.blaze3d.systems.VertexSorter.BY_Z);
                        
                        immediate.draw();
                        
                        // Restore
                        RenderSystem.setProjectionMatrix(originalProjection, com.mojang.blaze3d.systems.VertexSorter.BY_Z);
                        RenderSystem.getModelViewMatrix().set(originalModelView);
                        RenderSystem.applyModelViewMatrix();
                    }
                }
                renderedViaChunkInfos = true;
            }
            
            // Final flush
            // We need to ensure the ModelView matrix is Identity because the vertices are already transformed into View Space
            // by the MatrixStack passed to renderBlock.
            // If we don't reset this, the shader will apply the View Matrix AGAIN (Double Transform).
            Matrix4f originalModelView = new Matrix4f(RenderSystem.getModelViewMatrix());
            RenderSystem.getModelViewMatrix().identity();
            RenderSystem.applyModelViewMatrix();
            
            // Also ensure Projection Matrix is correct
            Matrix4f originalProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
            RenderSystem.setProjectionMatrix(context.projectionMatrix(), com.mojang.blaze3d.systems.VertexSorter.BY_Z);
            
            immediate.draw();
            
            // Restore matrices
            RenderSystem.setProjectionMatrix(originalProjection, com.mojang.blaze3d.systems.VertexSorter.BY_Z);
            RenderSystem.getModelViewMatrix().set(originalModelView);
            RenderSystem.applyModelViewMatrix();
            
        } catch (Exception e) {
            System.err.println("[SEGMOD ERROR] Failed to access visible chunks: " + e.getMessage());
            e.printStackTrace();
        }
        
        // Fallback if Mega Optimization failed
        if (!renderedViaChunkInfos) {
            System.out.println("[SEGMOD WARNING] Fallback to radius-based rendering");
            // Use actual render distance, but clamp it to avoid OOM/Crash on high distances
            int renderDistance = Math.min(client.options.getClampedViewDistance(), 12);
            
            // Frustum culling optimization
            net.minecraft.client.render.Frustum frustum = new net.minecraft.client.render.Frustum(matrices.peek().getPositionMatrix(), context.projectionMatrix());
            frustum.setPosition(camX, camY, camZ);
            
            net.minecraft.client.world.ClientWorld world = client.world;
            net.minecraft.util.math.ChunkPos cameraChunkPos = new net.minecraft.util.math.ChunkPos(camera.getBlockPos());
            
            for (int cx = -renderDistance; cx <= renderDistance; cx++) {
                for (int cz = -renderDistance; cz <= renderDistance; cz++) {
                    int chunkX = cameraChunkPos.x + cx;
                    int chunkZ = cameraChunkPos.z + cz;
                    
                    net.minecraft.world.chunk.WorldChunk chunk = world.getChunk(chunkX, chunkZ);
                    if (chunk == null || chunk.isEmpty()) continue;
                    
                    double minX = chunkX * 16;
                    double minZ = chunkZ * 16;
                    double minY = world.getBottomY();
                    double maxX = minX + 16;
                    double maxZ = minZ + 16;
                    double maxY = world.getTopY();
                    
                    if (!frustum.isVisible(new net.minecraft.util.math.Box(minX, minY, minZ, maxX, maxY, maxZ))) {
                        continue;
                    }
                    
                    net.minecraft.world.chunk.ChunkSection[] sections = chunk.getSectionArray();
                    for (int i = 0; i < sections.length; i++) {
                        net.minecraft.world.chunk.ChunkSection section = sections[i];
                        if (section == null || section.isEmpty()) continue;
                        
                        int sectionY = world.sectionIndexToCoord(i);
                        int minSectionY = sectionY * 16;
                        int maxSectionY = minSectionY + 16;
                        
                        if (!frustum.isVisible(new net.minecraft.util.math.Box(minX, minSectionY, minZ, maxX, maxSectionY, maxZ))) {
                            continue;
                        }
                        
                        for (int x = 0; x < 16; x++) {
                            for (int y = 0; y < 16; y++) {
                                for (int z = 0; z < 16; z++) {
                                    net.minecraft.block.BlockState state = section.getBlockState(x, y, z);
                                    if (!state.isAir()) {
                                        int worldX = chunkX * 16 + x;
                                        int worldY = minSectionY + y;
                                        int worldZ = chunkZ * 16 + z;
                                        
                                        mutablePos.set(worldX, worldY, worldZ);
                                        
                                        setCurrentBlock(state.getBlock());
                                        matrices.push();
                                        matrices.translate(worldX - camX, worldY - camY, worldZ - camZ);
                                        
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
                    }
                    
                    // Reset ModelView for draw, then restore (for the fallback loop)
                    Matrix4f originalModelView = new Matrix4f(RenderSystem.getModelViewMatrix());
                    RenderSystem.getModelViewMatrix().identity();
                    RenderSystem.applyModelViewMatrix();
                    
                    Matrix4f originalProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
                    RenderSystem.setProjectionMatrix(context.projectionMatrix(), com.mojang.blaze3d.systems.VertexSorter.BY_Z);
                    
                    immediate.draw();
                    
                    RenderSystem.setProjectionMatrix(originalProjection, com.mojang.blaze3d.systems.VertexSorter.BY_Z);
                    RenderSystem.getModelViewMatrix().set(originalModelView);
                    RenderSystem.applyModelViewMatrix();
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
