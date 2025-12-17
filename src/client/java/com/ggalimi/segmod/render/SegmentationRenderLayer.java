package com.ggalimi.segmod.render;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

/**
 * Custom RenderLayer for segmentation rendering.
 * Uses the "segmod:segmentation" shader.
 */
public class SegmentationRenderLayer extends RenderLayer {

    public SegmentationRenderLayer(String name, VertexFormat vertexFormat, VertexFormat.DrawMode drawMode, int expectedBufferSize, boolean hasCrumbling, boolean translucent, Runnable startAction, Runnable endAction) {
        super(name, vertexFormat, drawMode, expectedBufferSize, hasCrumbling, translucent, startAction, endAction);
    }

    public static RenderLayer getLayer() {
        // System.out.println("[SEGMOD LAYER] Creating SegmentationRenderLayer");
        RenderLayer layer = RenderLayer.of(
            "segmod_segmentation",
            VertexFormats.POSITION_TEXTURE_COLOR,
            VertexFormat.DrawMode.QUADS,
            256,
            false,
            false,
            RenderLayer.MultiPhaseParameters.builder()
                .program(new RenderPhase.ShaderProgram(() -> SegmentationShaderManager.getInstance().getProgram()))
                .texture(MIPMAP_BLOCK_ATLAS_TEXTURE)
                .transparency(NO_TRANSPARENCY)
                .writeMaskState(ALL_MASK)
                .cull(ENABLE_CULLING) // Enable culling to prevent z-fighting and improve performance
                .build(false)
        );
        // System.out.println("[SEGMOD LAYER] Created RenderLayer: " + layer);
        return layer;
    }
    
    public static RenderLayer getEntityLayer() {
        // System.out.println("[SEGMOD LAYER] Creating SegmentationEntityRenderLayer");
        RenderLayer layer = RenderLayer.of(
            "segmod_segmentation_entity",
            VertexFormats.POSITION_COLOR,
            VertexFormat.DrawMode.QUADS,
            256,
            false,
            false,
            RenderLayer.MultiPhaseParameters.builder()
                .program(new RenderPhase.ShaderProgram(() -> SegmentationShaderManager.getInstance().getEntityProgram()))
                .transparency(NO_TRANSPARENCY)
                .writeMaskState(ALL_MASK)
                .cull(DISABLE_CULLING) // Entities often need double-sided rendering
                .build(false)
        );
        // System.out.println("[SEGMOD LAYER] Created EntityRenderLayer: " + layer);
        return layer;
    }
}
