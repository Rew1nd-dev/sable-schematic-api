package dev.rew1nd.sableschematicapi.compat.create;

import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import dev.rew1nd.sableschematicapi.SableSchematicApi;
import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintPlaceSession;
import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintSaveSession;
import dev.rew1nd.sableschematicapi.api.blueprint.SableBlueprintEvent;
import dev.rew1nd.sableschematicapi.api.blueprint.SubLevelSaveFrame;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

final class CreateSuperGlueBlueprintEvent implements SableBlueprintEvent {
    static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SableSchematicApi.MODID, "create/super_glue");
    private static final String GLUE = "glue";
    private static final String SUB_LEVEL_ID = "sub_level_id";

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public void onSaveAfterBlocks(final BlueprintSaveSession session, final CompoundTag data) {
        final ListTag glue = new ListTag();

        for (final SubLevelSaveFrame frame : session.frames()) {
            final AABB frameBounds = frameBounds(frame.storageBounds());
            final BlockPos origin = frame.blocksOrigin();

            for (final SuperGlueEntity entity : session.level().getEntitiesOfClass(SuperGlueEntity.class, frameBounds)) {
                if (Sable.HELPER.getContaining(entity) != frame.subLevel()) {
                    continue;
                }

                final AABB cropped = frameBounds.intersect(entity.getBoundingBox());
                if (isEmptyOrSingleBlock(cropped)) {
                    continue;
                }

                final CompoundTag tag = new CompoundTag();
                tag.putInt(SUB_LEVEL_ID, frame.blueprintId());
                SuperGlueEntity.writeBoundingBox(tag, cropped.move(
                        -origin.getX(),
                        -origin.getY(),
                        -origin.getZ()
                ));
                glue.add(tag);
            }
        }

        if (!glue.isEmpty()) {
            data.put(GLUE, glue);
        }
    }

    @Override
    public void onPlaceAfterBlockEntities(final BlueprintPlaceSession session, final CompoundTag data) {
        final ListTag glue = data.getList(GLUE, Tag.TAG_COMPOUND);
        for (int i = 0; i < glue.size(); i++) {
            placeGlue(session, glue.getCompound(i));
        }
    }

    private static void placeGlue(final BlueprintPlaceSession session, final CompoundTag tag) {
        if (!tag.contains(SUB_LEVEL_ID, Tag.TAG_INT)) {
            return;
        }

        final BlockPos origin = session.placedBlockOrigin(tag.getInt(SUB_LEVEL_ID));
        if (origin == null) {
            return;
        }

        final AABB localBounds = SuperGlueEntity.readBoundingBox(tag);
        final AABB storageBounds = localBounds.move(origin.getX(), origin.getY(), origin.getZ());
        if (isEmptyOrSingleBlock(storageBounds)) {
            return;
        }

        session.level().addFreshEntity(new SuperGlueEntity(session.level(), storageBounds));
    }

    private static AABB frameBounds(final BoundingBox3i bounds) {
        return new AABB(
                bounds.minX(),
                bounds.minY(),
                bounds.minZ(),
                bounds.maxX() + 1.0,
                bounds.maxY() + 1.0,
                bounds.maxZ() + 1.0
        );
    }

    private static boolean isEmptyOrSingleBlock(final AABB bounds) {
        return bounds.getXsize() * bounds.getYsize() * bounds.getZsize() == 0.0
                || Mth.equal(bounds.getSize(), 1.0);
    }
}
