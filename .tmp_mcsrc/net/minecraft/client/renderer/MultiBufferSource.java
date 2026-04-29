package net.minecraft.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.objects.Object2ObjectSortedMaps;
import java.util.HashMap;
import java.util.Map;
import java.util.SequencedMap;
import javax.annotation.Nullable;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface MultiBufferSource {
    static MultiBufferSource.BufferSource immediate(ByteBufferBuilder sharedBuffer) {
        return immediateWithBuffers(Object2ObjectSortedMaps.emptyMap(), sharedBuffer);
    }

    static MultiBufferSource.BufferSource immediateWithBuffers(SequencedMap<RenderType, ByteBufferBuilder> fixedBuffers, ByteBufferBuilder sharedBuffer) {
        return new MultiBufferSource.BufferSource(sharedBuffer, fixedBuffers);
    }

    VertexConsumer getBuffer(RenderType renderType);

    @OnlyIn(Dist.CLIENT)
    public static class BufferSource implements MultiBufferSource {
        protected final ByteBufferBuilder sharedBuffer;
        protected final SequencedMap<RenderType, ByteBufferBuilder> fixedBuffers;
        protected final Map<RenderType, BufferBuilder> startedBuilders = new HashMap<>();
        @Nullable
        protected RenderType lastSharedType;

        protected BufferSource(ByteBufferBuilder sharedBuffer, SequencedMap<RenderType, ByteBufferBuilder> fixedBuffers) {
            this.sharedBuffer = sharedBuffer;
            this.fixedBuffers = fixedBuffers;
        }

        @Override
        public VertexConsumer getBuffer(RenderType renderType) {
            BufferBuilder bufferbuilder = this.startedBuilders.get(renderType);
            if (bufferbuilder != null && !renderType.canConsolidateConsecutiveGeometry()) {
                this.endBatch(renderType, bufferbuilder);
                bufferbuilder = null;
            }

            if (bufferbuilder != null) {
                return bufferbuilder;
            } else {
                ByteBufferBuilder bytebufferbuilder = this.fixedBuffers.get(renderType);
                if (bytebufferbuilder != null) {
                    bufferbuilder = new BufferBuilder(bytebufferbuilder, renderType.mode(), renderType.format());
                } else {
                    if (this.lastSharedType != null) {
                        this.endBatch(this.lastSharedType);
                    }

                    bufferbuilder = new BufferBuilder(this.sharedBuffer, renderType.mode(), renderType.format());
                    this.lastSharedType = renderType;
                }

                this.startedBuilders.put(renderType, bufferbuilder);
                return bufferbuilder;
            }
        }

        public void endLastBatch() {
            if (this.lastSharedType != null) {
                this.endBatch(this.lastSharedType);
                this.lastSharedType = null;
            }
        }

        public void endBatch() {
            this.endLastBatch();

            for (RenderType rendertype : this.fixedBuffers.keySet()) {
                this.endBatch(rendertype);
            }
        }

        public void endBatch(RenderType renderType) {
            BufferBuilder bufferbuilder = this.startedBuilders.remove(renderType);
            if (bufferbuilder != null) {
                this.endBatch(renderType, bufferbuilder);
            }
        }

        private void endBatch(RenderType renderType, BufferBuilder builder) {
            MeshData meshdata = builder.build();
            if (meshdata != null) {
                if (renderType.sortOnUpload()) {
                    ByteBufferBuilder bytebufferbuilder = this.fixedBuffers.getOrDefault(renderType, this.sharedBuffer);
                    meshdata.sortQuads(bytebufferbuilder, RenderSystem.getVertexSorting());
                }

                renderType.draw(meshdata);
            }

            if (renderType.equals(this.lastSharedType)) {
                this.lastSharedType = null;
            }
        }
    }
}
