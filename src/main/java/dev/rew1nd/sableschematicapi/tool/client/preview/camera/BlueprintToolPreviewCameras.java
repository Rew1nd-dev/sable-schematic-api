package dev.rew1nd.sableschematicapi.tool.client.preview.camera;

import dev.rew1nd.sableschematicapi.blueprint.preview.SableBlueprintPreview;
import dev.rew1nd.sableschematicapi.tool.client.preview.util.BlueprintToolPreviewBounds;
import dev.rew1nd.sableschematicapi.tool.client.preview.util.BlueprintToolPreviewConstants;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.Pose3d;
import org.joml.Matrix4f;
import org.joml.Vector3d;

public final class BlueprintToolPreviewCameras {
    private BlueprintToolPreviewCameras() {
    }

    public static BlueprintToolPreviewCamera liveCamera(final BoundingBox3d bounds,
                                                        final Pose3d basisPose,
                                                        final SableBlueprintPreview.View view,
                                                        final int resolution) {
        final BlueprintToolPreviewViewAxes axes = viewAxes(view);
        final BlueprintToolPreviewViewFrame frame = viewFrame(bounds, axes, resolution);
        final double radius = cameraRadius(frame.halfSpan() * 2.0, frame.depth() * 2.0);
        final Vector3d cameraOffsetLocal = cameraOffset(view, radius);
        final float far = (float) (cameraOffsetLocal.length() + frame.depth() + 32.0);

        final Vector3d forwardWorld = worldDirection(basisPose, axes.forward());
        final Vector3d upWorld = worldDirection(basisPose, axes.up());
        final Vector3d cameraPosition = basisPose.transformPosition(new Vector3d(frame.center()).add(cameraOffsetLocal));
        final Matrix4f projection = new Matrix4f().ortho(-frame.halfSpan(), frame.halfSpan(), -frame.halfSpan(), frame.halfSpan(), 0.05F, far);
        final Matrix4f modelView = new Matrix4f().setLookAt(
                0.0F, 0.0F, 0.0F,
                (float) forwardWorld.x(), (float) forwardWorld.y(), (float) forwardWorld.z(),
                (float) upWorld.x(), (float) upWorld.y(), (float) upWorld.z()
        );

        return new BlueprintToolPreviewCamera(projection, modelView, cameraPosition);
    }

    public static BlueprintToolPreviewCamera storedCamera(final BoundingBox3d bounds,
                                                          final SableBlueprintPreview.View view,
                                                          final int resolution) {
        final BlueprintToolPreviewViewAxes axes = viewAxes(view);
        final BlueprintToolPreviewViewFrame frame = viewFrame(bounds, axes, resolution);
        final double radius = cameraRadius(frame.halfSpan() * 2.0, frame.depth() * 2.0);
        final Vector3d cameraOffsetLocal = cameraOffset(view, radius);
        final Vector3d cameraPosition = new Vector3d(frame.center()).add(cameraOffsetLocal);
        final float far = (float) (cameraOffsetLocal.length() + frame.depth() + 32.0);
        final Matrix4f projection = new Matrix4f().ortho(-frame.halfSpan(), frame.halfSpan(), -frame.halfSpan(), frame.halfSpan(), 0.05F, far);
        final Matrix4f modelView = new Matrix4f().setLookAt(
                0.0F, 0.0F, 0.0F,
                (float) axes.forward().x(), (float) axes.forward().y(), (float) axes.forward().z(),
                (float) axes.up().x(), (float) axes.up().y(), (float) axes.up().z()
        );

        return new BlueprintToolPreviewCamera(projection, modelView, cameraPosition);
    }

    public static Matrix4f cameraSpaceFromBasis(final BlueprintToolPreviewCamera camera) {
        return new Matrix4f().translation(
                (float) -camera.position().x(),
                (float) -camera.position().y(),
                (float) -camera.position().z()
        );
    }

    private static BlueprintToolPreviewViewAxes viewAxes(final SableBlueprintPreview.View view) {
        final Vector3d cameraOffset = new Vector3d(view.xSign(), 1.0, view.zSign());
        final Vector3d forward = new Vector3d(cameraOffset).negate().normalize();
        final Vector3d up = new Vector3d(0.0, 1.0, 0.0)
                .sub(new Vector3d(forward).mul(forward.y()))
                .normalize();
        final Vector3d right = new Vector3d(forward).cross(up).normalize();
        return new BlueprintToolPreviewViewAxes(right, up, forward);
    }

    private static BlueprintToolPreviewViewFrame viewFrame(final BoundingBox3d bounds,
                                                           final BlueprintToolPreviewViewAxes axes,
                                                           final int resolution) {
        final Vector3d center = new Vector3d(
                (bounds.minX() + bounds.maxX()) * 0.5,
                (bounds.minY() + bounds.maxY()) * 0.5,
                (bounds.minZ() + bounds.maxZ()) * 0.5
        );
        final BlueprintToolPreviewBounds.AxisRange rightRange = BlueprintToolPreviewBounds.boundsRange(bounds, axes.right());
        final BlueprintToolPreviewBounds.AxisRange upRange = BlueprintToolPreviewBounds.boundsRange(bounds, axes.up());
        final BlueprintToolPreviewBounds.AxisRange depthRange = BlueprintToolPreviewBounds.boundsRange(bounds, axes.forward());
        final double rangeU = Math.max(rightRange.size(), 1.0);
        final double rangeV = Math.max(upRange.size(), 1.0);
        final double drawable = Math.max(1.0, resolution - BlueprintToolPreviewConstants.PADDING * 2.0);
        final double paddedSpan = Math.max(rangeU, rangeV) * resolution / drawable;
        final double centerDepth = center.x() * axes.forward().x()
                + center.y() * axes.forward().y()
                + center.z() * axes.forward().z();
        final double depth = Math.max(
                Math.abs(depthRange.min() - centerDepth),
                Math.abs(depthRange.max() - centerDepth)
        );

        return new BlueprintToolPreviewViewFrame(center, (float) (paddedSpan * 0.5), Math.max(depth, 1.0));
    }

    private static Vector3d cameraOffset(final SableBlueprintPreview.View view, final double radius) {
        return new Vector3d(view.xSign() * radius, radius, view.zSign() * radius);
    }

    private static double cameraRadius(final double paddedSpan, final double depth) {
        return Math.max(16.0, Math.max(paddedSpan, depth) * 0.75 + 8.0);
    }

    private static Vector3d worldDirection(final Pose3d pose, final Vector3d localDirection) {
        final Vector3d direction = new Vector3d(localDirection).normalize();
        pose.orientation().transform(direction);
        return direction.normalize();
    }
}
