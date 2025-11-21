package com.ggalimi.segmod.render;

import net.minecraft.client.render.VertexConsumer;

public class SegmentationVertexConsumer implements VertexConsumer {
    private final VertexConsumer delegate;
    private final boolean safeMode;

    public SegmentationVertexConsumer(VertexConsumer delegate, boolean safeMode) {
        this.delegate = delegate;
        this.safeMode = safeMode;
        GpuSegmentationRenderer.incrementVertexConsumerWrapCount();
        System.out.println("[SEGMOD WRAPPER] Created SegmentationVertexConsumer #" + GpuSegmentationRenderer.getVertexConsumerWrapCount() + " (safeMode=" + safeMode + ")");
    }

    public SegmentationVertexConsumer(VertexConsumer delegate) {
        this(delegate, false);
    }

    @Override
    public VertexConsumer vertex(float x, float y, float z) {
        return delegate.vertex(x, y, z);
    }

    private static boolean firstColorCall = true;
    
    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha) {
        // Use the current entity/block ID color
        float[] color = GpuSegmentationRenderer.getCurrentEntityColor();
        // If no entity, try block
        if (GpuSegmentationRenderer.getCurrentEntity() == null) {
            color = GpuSegmentationRenderer.getCurrentBlockColor();
        }
        
        return delegate.color(
            (int)(color[0] * 255), 
            (int)(color[1] * 255), 
            (int)(color[2] * 255), 
            255
        );
    }

    @Override
    public VertexConsumer texture(float u, float v) {
        if (safeMode) return this;
        return delegate.texture(u, v);
    }

    @Override
    public VertexConsumer overlay(int u, int v) {
        if (safeMode) return this;
        return delegate.overlay(u, v);
    }

    @Override
    public VertexConsumer light(int u, int v) {
        if (safeMode) return this;
        return delegate.light(u, v);
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        if (safeMode) return this;
        return delegate.normal(x, y, z);
    }
}
