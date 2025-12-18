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
        
        if (layerStr.contains("solid") || layerStr.contains("cutout") || layerStr.contains("translucent")) {
             // Block layer
             targetLayer = SegmentationRenderLayer.getLayer();
             safeMode = false; // We need texture coords for blocks
        } else {
             // Entity layer
             targetLayer = SegmentationRenderLayer.getEntityLayer();
             safeMode = true; // Entity shader doesn't use texture coords
        }
        
        VertexConsumer result = new SegmentationVertexConsumer(delegate.getBuffer(targetLayer), safeMode);
        // System.out.println("[SEGMOD PROVIDER] Returning wrapped consumer: " + result);
        return result;
    }
}
