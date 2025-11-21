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
        System.out.println("[SEGMOD PROVIDER] getBuffer called for layer: " + layer);
        // Force use of our segmentation shader layer
        // This ensures flat colors and no textures
        // We ignore the requested layer (which might have textures/lighting)
        // Enable safeMode to ignore texture/light/overlay calls which are not supported by POSITION_COLOR format
        VertexConsumer result = new SegmentationVertexConsumer(delegate.getBuffer(SegmentationRenderLayer.getLayer()), true);
        System.out.println("[SEGMOD PROVIDER] Returning wrapped consumer: " + result);
        return result;
    }
}
