package dev.rew1nd.sableschematicapi.blueprint;

import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.util.SableNBTUtils;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.List;
import java.util.UUID;

public class SableBlueprint {
    public static final int VERSION = 1;

    private final Vector3d origin;
    private final BoundingBox3d rootBounds;
    private final List<SubLevelData> subLevels;
    private final CompoundTag globalExtraData;

    public SableBlueprint(final Vector3dc origin,
                          final BoundingBox3d rootBounds,
                          final List<SubLevelData> subLevels,
                          final CompoundTag globalExtraData) {
        this.origin = new Vector3d(origin);
        this.rootBounds = new BoundingBox3d(rootBounds);
        this.subLevels = List.copyOf(subLevels);
        this.globalExtraData = globalExtraData.copy();
    }

    public Vector3dc origin() {
        return this.origin;
    }

    public BoundingBox3d rootBounds() {
        return new BoundingBox3d(this.rootBounds);
    }

    public List<SubLevelData> subLevels() {
        return this.subLevels;
    }

    public CompoundTag globalExtraData() {
        return this.globalExtraData.copy();
    }

    public boolean isEmpty() {
        return this.subLevels.isEmpty();
    }

    public int blockCount() {
        return this.subLevels.stream().mapToInt(subLevel -> subLevel.blocks().size()).sum();
    }

    public int blockEntityCount() {
        return this.subLevels.stream().mapToInt(subLevel -> subLevel.blockEntities().size()).sum();
    }

    public int entityCount() {
        return this.subLevels.stream().mapToInt(subLevel -> subLevel.entities().size()).sum();
    }

    public CompoundTag save() {
        final CompoundTag tag = new CompoundTag();
        final ListTag subLevelTags = new ListTag();

        tag.putInt("version", VERSION);
        tag.put("origin", SableNBTUtils.writeVector3d(this.origin));
        tag.put("root_bounds", SableNBTUtils.writeBoundingBox(this.rootBounds));
        tag.put("global_extra_data", this.globalExtraData.copy());

        for (final SubLevelData subLevel : this.subLevels) {
            subLevelTags.add(subLevel.save());
        }

        tag.put("sub_levels", subLevelTags);
        return tag;
    }

    public static SableBlueprint load(final CompoundTag tag) {
        final int version = tag.getInt("version");
        if (version != VERSION) {
            throw new IllegalArgumentException("Unsupported Sable blueprint version: " + version);
        }

        final Vector3d origin = SableNBTUtils.readVector3d(tag.getCompound("origin"));
        final BoundingBox3d rootBounds = SableNBTUtils.readBoundingBox(tag.getCompound("root_bounds"));
        final CompoundTag globalExtraData = tag.getCompound("global_extra_data");
        final List<SubLevelData> subLevels = new ObjectArrayList<>();
        final ListTag subLevelTags = tag.getList("sub_levels", Tag.TAG_COMPOUND);

        for (int i = 0; i < subLevelTags.size(); i++) {
            subLevels.add(SubLevelData.load(subLevelTags.getCompound(i)));
        }

        return new SableBlueprint(origin, rootBounds, subLevels, globalExtraData);
    }

    public record BlockData(BlockPos localPos, int paletteId, int blockEntityDataId) {
        public static final int NO_BLOCK_ENTITY_DATA = -1;

        public BlockData {
            localPos = localPos.immutable();
        }

        public boolean hasBlockEntityData() {
            return this.blockEntityDataId >= 0;
        }

        private CompoundTag save() {
            final CompoundTag tag = new CompoundTag();
            tag.put("local_pos", writeBlockPos(this.localPos));
            tag.putInt("palette_id", this.paletteId);
            if (this.hasBlockEntityData()) {
                tag.putInt("block_entity_data_id", this.blockEntityDataId);
            }
            return tag;
        }

        private static BlockData load(final CompoundTag tag) {
            return new BlockData(
                    readBlockPos(tag.getCompound("local_pos")),
                    tag.getInt("palette_id"),
                    tag.contains("block_entity_data_id", Tag.TAG_INT) ? tag.getInt("block_entity_data_id") : NO_BLOCK_ENTITY_DATA
            );
        }
    }

    public record EntityData(Vector3dc localPos, CompoundTag tag) {
        public EntityData {
            localPos = new Vector3d(localPos);
            tag = tag.copy();
        }

        private CompoundTag save() {
            final CompoundTag wrapper = new CompoundTag();
            wrapper.put("local_pos", SableNBTUtils.writeVector3d(this.localPos));
            wrapper.put("entity", this.tag.copy());
            return wrapper;
        }

        private static EntityData load(final CompoundTag wrapper) {
            if (wrapper.contains("entity", Tag.TAG_COMPOUND)) {
                return new EntityData(
                        SableNBTUtils.readVector3d(wrapper.getCompound("local_pos")),
                        wrapper.getCompound("entity")
                );
            }

            return new EntityData(readEntityPos(wrapper), wrapper);
        }

        private static Vector3d readEntityPos(final CompoundTag tag) {
            final ListTag pos = tag.getList("Pos", Tag.TAG_DOUBLE);
            if (pos.size() >= 3) {
                return new Vector3d(pos.getDouble(0), pos.getDouble(1), pos.getDouble(2));
            }

            return new Vector3d();
        }
    }

    public record SubLevelData(int id,
                               UUID sourceUuid,
                               Pose3d relativePose,
                               BoundingBox3i localBounds,
                               BlockPos blocksOrigin,
                               List<BlockState> blockPalette,
                               List<BlockData> blocks,
                               List<CompoundTag> blockEntities,
                               List<EntityData> entities,
                               CompoundTag extraData,
                               @Nullable String name) {
        public SubLevelData {
            relativePose = new Pose3d(relativePose);
            localBounds = new BoundingBox3i(localBounds);
            blocksOrigin = blocksOrigin.immutable();
            blockPalette = List.copyOf(blockPalette);
            blocks = List.copyOf(blocks);
            blockEntities = blockEntities.stream().map(CompoundTag::copy).toList();
            entities = entities.stream()
                    .map(entity -> new EntityData(entity.localPos(), entity.tag()))
                    .toList();
            extraData = extraData.copy();
        }

        private CompoundTag save() {
            final CompoundTag tag = new CompoundTag();
            final ListTag paletteTag = new ListTag();
            final ListTag blocksTag = new ListTag();
            final ListTag blockEntitiesTag = new ListTag();
            final ListTag entitiesTag = new ListTag();

            tag.putInt("id", this.id);
            tag.putUUID("source_uuid", this.sourceUuid);
            tag.put("relative_pose", SableNBTUtils.writePose3d(this.relativePose));
            tag.put("local_bounds", writeBoundingBox(this.localBounds));
            tag.put("blocks_origin", writeBlockPos(this.blocksOrigin));

            for (final BlockState state : this.blockPalette) {
                paletteTag.add(BlockState.CODEC.encodeStart(NbtOps.INSTANCE, state).getOrThrow());
            }

            for (final BlockData block : this.blocks) {
                blocksTag.add(block.save());
            }

            for (final CompoundTag blockEntity : this.blockEntities) {
                blockEntitiesTag.add(blockEntity.copy());
            }

            for (final EntityData entity : this.entities) {
                entitiesTag.add(entity.save());
            }

            tag.put("block_palette", paletteTag);
            tag.put("blocks", blocksTag);
            tag.put("block_entities", blockEntitiesTag);
            tag.put("entities", entitiesTag);
            tag.put("extra_data", this.extraData.copy());
            if (this.name != null) {
                tag.putString("name", this.name);
            }

            return tag;
        }

        private static SubLevelData load(final CompoundTag tag) {
            if (tag.contains("plot", Tag.TAG_COMPOUND)) {
                throw new IllegalArgumentException("Legacy plot payloads are not supported by Sable blueprint v1");
            }

            final List<BlockState> blockPalette = new ObjectArrayList<>();
            final List<BlockData> blocks = new ObjectArrayList<>();
            final List<CompoundTag> blockEntities = new ObjectArrayList<>();
            final List<EntityData> entities = new ObjectArrayList<>();

            final ListTag paletteTag = tag.getList("block_palette", Tag.TAG_COMPOUND);
            for (int i = 0; i < paletteTag.size(); i++) {
                blockPalette.add(BlockState.CODEC.parse(NbtOps.INSTANCE, paletteTag.get(i)).getOrThrow());
            }

            final ListTag blocksTag = tag.getList("blocks", Tag.TAG_COMPOUND);
            for (int i = 0; i < blocksTag.size(); i++) {
                blocks.add(BlockData.load(blocksTag.getCompound(i)));
            }

            final ListTag blockEntitiesTag = tag.getList("block_entities", Tag.TAG_COMPOUND);
            for (int i = 0; i < blockEntitiesTag.size(); i++) {
                blockEntities.add(blockEntitiesTag.getCompound(i).copy());
            }

            final ListTag entitiesTag = tag.getList("entities", Tag.TAG_COMPOUND);
            for (int i = 0; i < entitiesTag.size(); i++) {
                entities.add(EntityData.load(entitiesTag.getCompound(i)));
            }

            final String name = tag.contains("name", Tag.TAG_STRING) ? tag.getString("name") : null;
            return new SubLevelData(
                    tag.getInt("id"),
                    tag.getUUID("source_uuid"),
                    SableNBTUtils.readPose3d(tag.getCompound("relative_pose")),
                    readBoundingBox(tag.getCompound("local_bounds")),
                    readBlockPos(tag.getCompound("blocks_origin")),
                    blockPalette,
                    blocks,
                    blockEntities,
                    entities,
                    tag.getCompound("extra_data"),
                    name
            );
        }
    }

    public static CompoundTag writeBlockPos(final BlockPos pos) {
        final CompoundTag tag = new CompoundTag();
        tag.putInt("x", pos.getX());
        tag.putInt("y", pos.getY());
        tag.putInt("z", pos.getZ());
        return tag;
    }

    public static BlockPos readBlockPos(final CompoundTag tag) {
        return new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
    }

    public static CompoundTag writeBoundingBox(final BoundingBox3i bounds) {
        final CompoundTag tag = new CompoundTag();
        tag.putInt("min_x", bounds.minX());
        tag.putInt("min_y", bounds.minY());
        tag.putInt("min_z", bounds.minZ());
        tag.putInt("max_x", bounds.maxX());
        tag.putInt("max_y", bounds.maxY());
        tag.putInt("max_z", bounds.maxZ());
        return tag;
    }

    public static BoundingBox3i readBoundingBox(final CompoundTag tag) {
        return new BoundingBox3i(
                tag.getInt("min_x"),
                tag.getInt("min_y"),
                tag.getInt("min_z"),
                tag.getInt("max_x"),
                tag.getInt("max_y"),
                tag.getInt("max_z")
        );
    }
}
