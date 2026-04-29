package com.mojang.blaze3d.vertex;

import com.google.common.primitives.Floats;
import it.unimi.dsi.fastutil.ints.IntArrays;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Vector3f;

@OnlyIn(Dist.CLIENT)
public interface VertexSorting {
    VertexSorting DISTANCE_TO_ORIGIN = byDistance(0.0F, 0.0F, 0.0F);
    VertexSorting ORTHOGRAPHIC_Z = byDistance(p_277433_ -> -p_277433_.z());

    static VertexSorting byDistance(float x, float y, float z) {
        return byDistance(new Vector3f(x, y, z));
    }

    static VertexSorting byDistance(Vector3f vector) {
        return byDistance(vector::distanceSquared);
    }

    static VertexSorting byDistance(VertexSorting.DistanceFunction distanceFunction) {
        return p_278083_ -> {
            float[] afloat = new float[p_278083_.length];
            int[] aint = new int[p_278083_.length];

            for (int i = 0; i < p_278083_.length; aint[i] = i++) {
                afloat[i] = distanceFunction.apply(p_278083_[i]);
            }

            IntArrays.mergeSort(aint, (p_277443_, p_277864_) -> Floats.compare(afloat[p_277864_], afloat[p_277443_]));
            return aint;
        };
    }

    int[] sort(Vector3f[] vectors);

    @OnlyIn(Dist.CLIENT)
    public interface DistanceFunction {
        float apply(Vector3f vector);
    }
}
