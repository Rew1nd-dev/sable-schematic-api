package com.mojang.blaze3d.vertex;

import com.google.common.collect.Queues;
import com.mojang.math.MatrixUtil;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.Util;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@OnlyIn(Dist.CLIENT)
public class PoseStack implements net.neoforged.neoforge.client.extensions.IPoseStackExtension {
    private final Deque<PoseStack.Pose> poseStack = Util.make(Queues.newArrayDeque(), p_85848_ -> {
        Matrix4f matrix4f = new Matrix4f();
        Matrix3f matrix3f = new Matrix3f();
        p_85848_.add(new PoseStack.Pose(matrix4f, matrix3f));
    });

    public void translate(double x, double y, double z) {
        this.translate((float)x, (float)y, (float)z);
    }

    public void translate(float x, float y, float z) {
        PoseStack.Pose posestack$pose = this.poseStack.getLast();
        posestack$pose.pose.translate(x, y, z);
    }

    public void scale(float x, float y, float z) {
        PoseStack.Pose posestack$pose = this.poseStack.getLast();
        posestack$pose.pose.scale(x, y, z);
        if (Math.abs(x) == Math.abs(y) && Math.abs(y) == Math.abs(z)) {
            if (x < 0.0F || y < 0.0F || z < 0.0F) {
                posestack$pose.normal.scale(Math.signum(x), Math.signum(y), Math.signum(z));
            }
        } else {
            posestack$pose.normal.scale(1.0F / x, 1.0F / y, 1.0F / z);
            posestack$pose.trustedNormals = false;
        }
    }

    public void mulPose(Quaternionf quaternion) {
        PoseStack.Pose posestack$pose = this.poseStack.getLast();
        posestack$pose.pose.rotate(quaternion);
        posestack$pose.normal.rotate(quaternion);
    }

    public void rotateAround(Quaternionf quaternion, float x, float y, float z) {
        PoseStack.Pose posestack$pose = this.poseStack.getLast();
        posestack$pose.pose.rotateAround(quaternion, x, y, z);
        posestack$pose.normal.rotate(quaternion);
    }

    public void pushPose() {
        this.poseStack.addLast(new PoseStack.Pose(this.poseStack.getLast()));
    }

    public void popPose() {
        this.poseStack.removeLast();
    }

    public PoseStack.Pose last() {
        return this.poseStack.getLast();
    }

    public boolean clear() {
        return this.poseStack.size() == 1;
    }

    public void setIdentity() {
        PoseStack.Pose posestack$pose = this.poseStack.getLast();
        posestack$pose.pose.identity();
        posestack$pose.normal.identity();
        posestack$pose.trustedNormals = true;
    }

    public void mulPose(Matrix4f pose) {
        PoseStack.Pose posestack$pose = this.poseStack.getLast();
        posestack$pose.pose.mul(pose);
        if (!MatrixUtil.isPureTranslation(pose)) {
            if (MatrixUtil.isOrthonormal(pose)) {
                posestack$pose.normal.mul(new Matrix3f(pose));
            } else {
                posestack$pose.computeNormalMatrix();
            }
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static final class Pose {
        final Matrix4f pose;
        final Matrix3f normal;
        boolean trustedNormals = true;

        Pose(Matrix4f pose, Matrix3f normal) {
            this.pose = pose;
            this.normal = normal;
        }

        Pose(PoseStack.Pose pose) {
            this.pose = new Matrix4f(pose.pose);
            this.normal = new Matrix3f(pose.normal);
            this.trustedNormals = pose.trustedNormals;
        }

        void computeNormalMatrix() {
            this.normal.set(this.pose).invert().transpose();
            this.trustedNormals = false;
        }

        public Matrix4f pose() {
            return this.pose;
        }

        public Matrix3f normal() {
            return this.normal;
        }

        public Vector3f transformNormal(Vector3f vector, Vector3f destination) {
            return this.transformNormal(vector.x, vector.y, vector.z, destination);
        }

        public Vector3f transformNormal(float x, float y, float z, Vector3f destination) {
            Vector3f vector3f = this.normal.transform(x, y, z, destination);
            return this.trustedNormals ? vector3f : vector3f.normalize();
        }

        public PoseStack.Pose copy() {
            return new PoseStack.Pose(this);
        }
    }
}
