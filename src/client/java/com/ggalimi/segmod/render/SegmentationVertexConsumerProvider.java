package com.ggalimi.segmod.render;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;

/**
 * Wraps a VertexConsumerProvider to return SegmentationVertexConsumers.
 * This ensures that all rendering done through this provider uses our color-overriding vertex consumer.
 */
public class SegmentationVertexConsumerProvider implements VertexConsumerProvider {
    private final VertexConsumerProvider delegate;

    public SegmentationVertexConsumerProvider(VertexConsumerProvider delegate) {
        this.delegate = delegate;
    }

    @Override
    public VertexConsumer getBuffer(RenderLayer layer) {
        // System.out.println("[SEGMOD PROVIDER] getBuffer called for layer: " + layer);
        
        // Determine if this is an entity layer or a block layer
        // Block layers usually use the block atlas
        // Entity layers usually use specific textures
        
        // Simple heuristic: If it's a solid/cutout block layer, use the block segmentation layer
        // Otherwise (entities), use the entity segmentation layer
        
        RenderLayer targetLayer;
        boolean safeMode;
        
        String layerStr = layer.toString();
        
        // Suppress shadows
        if (layerStr.contains("shadow")) {
            return NoOpVertexConsumer.INSTANCE;
        }
        
        // Smart Layer Detection
        boolean isEntityCutout = layerStr.contains("entity_cutout") || layerStr.contains("entity_translucent");
        boolean isMobTexture = layerStr.contains("textures/entity/");
        
        // Debug print removed
        
        if (isEntityCutout && !isMobTexture) {
             // Dropped Items (e.g. Egg, Sapling) or other non-mob entities
             // Use CutoutLayer: Block Shader (Alpha Test) + No Culling
             // This ensures the transparent parts of the item sprite are discarded
             targetLayer = SegmentationRenderLayer.getCutoutLayer();
             safeMode = false; 
        } else if (layerStr.contains("entity")) {
             // Mobs (Spider, Zombie, etc.) - identified by "textures/entity/" OR "entity_solid"
             // Use EntityLayer: Solid Color Shader (No Texture)
             // This ensures they render as solid shapes without erosion
             targetLayer = SegmentationRenderLayer.getEntityLayer();
             safeMode = true; 
        } else if (layerStr.contains("solid") || layerStr.contains("cutout") || layerStr.contains("translucent")) {
             // Dropped Items (use Block Atlas but are Entities)
             // Use CutoutLayer: Block Shader (Alpha Test) + No Culling
             targetLayer = SegmentationRenderLayer.getCutoutLayer();
             safeMode = false; 
        } else if (layerStr.contains("entity")) {
             // Mobs/Players (Use specific entity textures)
             // Use EntityLayer: Solid Color Shader (No Texture)
             targetLayer = SegmentationRenderLayer.getEntityLayer();
             safeMode = true; 
        } else if (layerStr.contains("solid") || layerStr.contains("cutout") || layerStr.contains("translucent")) {
             // Standard Blocks
             // Use Standard Layer: Block Shader (Alpha Test) + Culling
             targetLayer = SegmentationRenderLayer.getLayer();
             safeMode = false; 
        } else {
             // Fallback (likely an entity or other non-block item)
             targetLayer = SegmentationRenderLayer.getEntityLayer();
             safeMode = true;
        }
        
        VertexConsumer result = new SegmentationVertexConsumer(delegate.getBuffer(targetLayer), safeMode);
        // System.out.println("[SEGMOD PROVIDER] Returning wrapped consumer: " + result);
        return result;
    }
}
