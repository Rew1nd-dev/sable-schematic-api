package dev.rew1nd.sableschematicapi.blueprint;

import com.mojang.serialization.DataResult;
import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintDiagnosticCategory;
import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintDiagnosticReport;
import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintDiagnosticStage;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.util.SableNBTUtils;
import dev.rew1nd.sableschematicapi.blueprint.preview.SableBlueprintPreview;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class SableBlueprint {
    public static final int VERSION = 1;

    private final Vector3d origin;
    private final BoundingBox3d rootBounds;
    private final List<SubLevelData> subLevels;
    private final CompoundTag globalExtraData;
    @Nullable
    private final SableBlueprintPreview preview;

    public SableBlueprint(final Vector3dc origin,
                          final BoundingBox3d rootBounds,
                          final List<SubLevelData> subLevels,
                          final CompoundTag globalExtraData) {
        this(origin, rootBounds, subLevels, globalExtraData, null);
    }

    public SableBlueprint(final Vector3dc origin,
                          final BoundingBox3d rootBounds,
                          final List<SubLevelData> subLevels,
                          final CompoundTag globalExtraData,
                          @Nullable final SableBlueprintPreview preview) {
        this.origin = new Vector3d(origin);
        this.rootBounds = new BoundingBox3d(rootBounds);
        this.subLevels = List.copyOf(subLevels);
        this.globalExtraData = globalExtraData.copy();
        this.preview = preview;
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

    public @Nullable SableBlueprintPreview preview() {
        return this.preview;
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
        if (this.preview != null) {
            tag.put("preview", this.preview.save());
        }

        for (final SubLevelData subLevel : this.subLevels) {
            subLevelTags.add(subLevel.save());
        }

        tag.put("sub_levels", subLevelTags);
        return tag;
    }

    public static SableBlueprint load(final CompoundTag tag) {
        return loadWithDiagnostics(tag).blueprint();
    }

    public static SableBlueprintDecodeResult loadWithDiagnostics(final CompoundTag tag) {
        final BlueprintDiagnosticReport.Builder diagnostics = BlueprintDiagnosticReport.builder();
        final int version = tag.getInt("version");
        if (version != VERSION) {
            throw new IllegalArgumentException("Unsupported Sable blueprint version: " + version);
        }

        final Vector3d origin = SableNBTUtils.readVector3d(tag.getCompound("origin"));
        final BoundingBox3d rootBounds = SableNBTUtils.readBoundingBox(tag.getCompound("root_bounds"));
        final CompoundTag globalExtraData = tag.getCompound("global_extra_data");
        SableBlueprintPreview preview = null;
        if (tag.contains("preview", Tag.TAG_COMPOUND)) {
            try {
                preview = SableBlueprintPreview.load(tag.getCompound("preview"));
            } catch (final RuntimeException e) {
                diagnostics.warn(
                        BlueprintDiagnosticStage.DECODE,
                        BlueprintDiagnosticCategory.INVALID_BLOCK_DATA,
                        null,
                        null,
                        null,
                        "preview",
                        "Skipped invalid blueprint preview.",
                        "Skipped invalid blueprint preview.",
                        e
                );
                preview = null;
            }
        }
        final List<SubLevelData> subLevels = new ObjectArrayList<>();
        final ListTag subLevelTags = tag.getList("sub_levels", Tag.TAG_COMPOUND);

        for (int i = 0; i < subLevelTags.size(); i++) {
            try {
                subLevels.add(SubLevelData.load(subLevelTags.getCompound(i), diagnostics));
            } catch (final RuntimeException e) {
                if (e instanceof IllegalArgumentException && e.getMessage() != null && e.getMessage().contains("Legacy plot payloads")) {
                    throw e;
                }
                diagnostics.warn(
                        BlueprintDiagnosticStage.DECODE,
                        BlueprintDiagnosticCategory.INVALID_BLOCK_DATA,
                        null,
                        null,
                        null,
                        "sub-level #" + i,
                        "Skipped invalid blueprint sub-level.",
                        "Skipped invalid blueprint sub-level #" + i + ".",
                        e
                );
            }
        }

        return new SableBlueprintDecodeResult(
                new SableBlueprint(origin, rootBounds, subLevels, globalExtraData, preview),
                diagnostics.build()
        );
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

        private static @Nullable BlockData load(final CompoundTag tag,
                                                final int subLevelId,
                                                final int blockIndex,
                                                final int paletteSize,
                                                final int blockEntityCount,
                                                final BlueprintDiagnosticReport.Builder diagnostics) {
            final @Nullable BlockPos localPos = readRequiredBlockPos(tag, "local_pos");
            if (localPos == null) {
                diagnostics.warn(
                        BlueprintDiagnosticStage.DECODE,
                        BlueprintDiagnosticCategory.INVALID_BLOCK_DATA,
                        subLevelId,
                        null,
                        null,
                        "block #" + blockIndex,
                        "Skipped a malformed blueprint block.",
                        "Skipped malformed blueprint block #" + blockIndex + " because local_pos is missing or invalid.",
                        null
                );
                return null;
            }

            if (!tag.contains("palette_id", Tag.TAG_INT)) {
                diagnostics.warn(
                        BlueprintDiagnosticStage.DECODE,
                        BlueprintDiagnosticCategory.INVALID_PALETTE_REFERENCE,
                        subLevelId,
                        localPos,
                        null,
                        "block #" + blockIndex,
                        "Skipped a blueprint block with no palette reference.",
                        "Skipped blueprint block #" + blockIndex + " because palette_id is missing.",
                        null
                );
                return null;
            }

            final int paletteId = tag.getInt("palette_id");
            if (paletteId < 0 || paletteId >= paletteSize) {
                diagnostics.warn(
                        BlueprintDiagnosticStage.DECODE,
                        BlueprintDiagnosticCategory.INVALID_PALETTE_REFERENCE,
                        subLevelId,
                        localPos,
                        null,
                        "palette #" + paletteId,
                        "Skipped a blueprint block with an invalid palette reference.",
                        "Skipped blueprint block #" + blockIndex + " because palette id " + paletteId + " is outside palette size " + paletteSize + ".",
                        null
                );
                return null;
            }

            int blockEntityDataId = NO_BLOCK_ENTITY_DATA;
            if (tag.contains("block_entity_data_id", Tag.TAG_INT)) {
                final int rawId = tag.getInt("block_entity_data_id");
                if (rawId >= 0 && rawId < blockEntityCount) {
                    blockEntityDataId = rawId;
                } else {
                    diagnostics.warn(
                            BlueprintDiagnosticStage.DECODE,
                            BlueprintDiagnosticCategory.INVALID_BLOCK_ENTITY_REFERENCE,
                            subLevelId,
                            localPos,
                            null,
                            "block entity tag #" + rawId,
                            "Ignored invalid block entity data for a blueprint block.",
                            "Ignored block_entity_data_id " + rawId + " for blueprint block #" + blockIndex + " because block entity tag count is " + blockEntityCount + ".",
                            null
                    );
                }
            }

            return new BlockData(localPos, paletteId, blockEntityDataId);
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

        private static @Nullable EntityData load(final CompoundTag wrapper,
                                                 final int subLevelId,
                                                 final int entityIndex,
                                                 final BlueprintDiagnosticReport.Builder diagnostics) {
            try {
                if (wrapper.contains("entity", Tag.TAG_COMPOUND)) {
                    if (!wrapper.contains("local_pos", Tag.TAG_COMPOUND)) {
                        diagnostics.warn(
                                BlueprintDiagnosticStage.DECODE,
                                BlueprintDiagnosticCategory.ENTITY_SKIPPED,
                                subLevelId,
                                null,
                                null,
                                "entity #" + entityIndex,
                                "Skipped a malformed blueprint entity.",
                                "Skipped blueprint entity #" + entityIndex + " because local_pos is missing.",
                                null
                        );
                        return null;
                    }

                    return new EntityData(
                            SableNBTUtils.readVector3d(wrapper.getCompound("local_pos")),
                            wrapper.getCompound("entity")
                    );
                }

                return new EntityData(readEntityPos(wrapper), wrapper);
            } catch (final RuntimeException e) {
                diagnostics.warn(
                        BlueprintDiagnosticStage.DECODE,
                        BlueprintDiagnosticCategory.ENTITY_SKIPPED,
                        subLevelId,
                        null,
                        null,
                        "entity #" + entityIndex,
                        "Skipped a malformed blueprint entity.",
                        "Skipped malformed blueprint entity #" + entityIndex + ".",
                        e
                );
                return null;
            }
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
                               List<Tag> rawBlockPalette,
                               Set<Integer> unavailablePaletteIds,
                               List<BlockData> blocks,
                               List<CompoundTag> blockEntities,
                               List<EntityData> entities,
                               CompoundTag extraData,
                               @Nullable String name) {
        public SubLevelData(final int id,
                            final UUID sourceUuid,
                            final Pose3d relativePose,
                            final BoundingBox3i localBounds,
                            final BlockPos blocksOrigin,
                            final List<BlockState> blockPalette,
                            final List<BlockData> blocks,
                            final List<CompoundTag> blockEntities,
                            final List<EntityData> entities,
                            final CompoundTag extraData,
                            @Nullable final String name) {
            this(
                    id,
                    sourceUuid,
                    relativePose,
                    localBounds,
                    blocksOrigin,
                    blockPalette,
                    encodePaletteTags(blockPalette),
                    Set.of(),
                    blocks,
                    blockEntities,
                    entities,
                    extraData,
                    name
            );
        }

        public SubLevelData {
            relativePose = new Pose3d(relativePose);
            localBounds = new BoundingBox3i(localBounds);
            blocksOrigin = blocksOrigin.immutable();
            blockPalette = List.copyOf(blockPalette);
            rawBlockPalette = normalizeRawBlockPalette(blockPalette, rawBlockPalette);
            unavailablePaletteIds = Set.copyOf(unavailablePaletteIds);
            blocks = List.copyOf(blocks);
            blockEntities = blockEntities.stream().map(CompoundTag::copy).toList();
            entities = entities.stream()
                    .map(entity -> new EntityData(entity.localPos(), entity.tag()))
                    .toList();
            extraData = extraData.copy();
        }

        public boolean isPaletteAvailable(final int paletteId) {
            return paletteId >= 0 && paletteId < this.blockPalette.size() && !this.unavailablePaletteIds.contains(paletteId);
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

            for (int i = 0; i < this.blockPalette.size(); i++) {
                if (this.unavailablePaletteIds.contains(i) && i < this.rawBlockPalette.size()) {
                    paletteTag.add(this.rawBlockPalette.get(i).copy());
                } else {
                    paletteTag.add(BlockState.CODEC.encodeStart(NbtOps.INSTANCE, this.blockPalette.get(i)).getOrThrow());
                }
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
            if (!this.unavailablePaletteIds.isEmpty()) {
                tag.putIntArray("unavailable_palette_ids", this.unavailablePaletteIds.stream().mapToInt(Integer::intValue).toArray());
            }
            tag.put("blocks", blocksTag);
            tag.put("block_entities", blockEntitiesTag);
            tag.put("entities", entitiesTag);
            tag.put("extra_data", this.extraData.copy());
            if (this.name != null) {
                tag.putString("name", this.name);
            }

            return tag;
        }

        private static SubLevelData load(final CompoundTag tag, final BlueprintDiagnosticReport.Builder diagnostics) {
            if (tag.contains("plot", Tag.TAG_COMPOUND)) {
                throw new IllegalArgumentException("Legacy plot payloads are not supported by Sable blueprint v1");
            }

            final int id = tag.getInt("id");
            final List<BlockState> blockPalette = new ObjectArrayList<>();
            final List<Tag> rawBlockPalette = new ObjectArrayList<>();
            final Set<Integer> unavailablePaletteIds = new HashSet<>();
            final List<BlockData> blocks = new ObjectArrayList<>();
            final List<CompoundTag> blockEntities = new ObjectArrayList<>();
            final List<EntityData> entities = new ObjectArrayList<>();

            final ListTag paletteTag = tag.getList("block_palette", Tag.TAG_COMPOUND);
            for (int i = 0; i < paletteTag.size(); i++) {
                final Tag rawPaletteEntry = paletteTag.get(i).copy();
                rawBlockPalette.add(rawPaletteEntry);
                final Optional<BlockState> state = decodeBlockState(rawPaletteEntry, id, i, diagnostics);
                if (state.isPresent()) {
                    blockPalette.add(state.get());
                } else {
                    blockPalette.add(Blocks.AIR.defaultBlockState());
                    unavailablePaletteIds.add(i);
                }
            }

            unavailablePaletteIds.addAll(readUnavailablePaletteIds(tag, blockPalette.size()));

            final ListTag blockEntitiesTag = tag.getList("block_entities", Tag.TAG_COMPOUND);
            for (int i = 0; i < blockEntitiesTag.size(); i++) {
                blockEntities.add(blockEntitiesTag.getCompound(i).copy());
            }

            final ListTag blocksTag = tag.getList("blocks", Tag.TAG_COMPOUND);
            for (int i = 0; i < blocksTag.size(); i++) {
                final @Nullable BlockData block = BlockData.load(
                        blocksTag.getCompound(i),
                        id,
                        i,
                        blockPalette.size(),
                        blockEntities.size(),
                        diagnostics
                );
                if (block != null) {
                    blocks.add(block);
                }
            }

            final ListTag entitiesTag = tag.getList("entities", Tag.TAG_COMPOUND);
            for (int i = 0; i < entitiesTag.size(); i++) {
                final @Nullable EntityData entity = EntityData.load(entitiesTag.getCompound(i), id, i, diagnostics);
                if (entity != null) {
                    entities.add(entity);
                }
            }

            final String name = tag.contains("name", Tag.TAG_STRING) ? tag.getString("name") : null;
            return new SubLevelData(
                    id,
                    tag.getUUID("source_uuid"),
                    SableNBTUtils.readPose3d(tag.getCompound("relative_pose")),
                    readBoundingBox(tag.getCompound("local_bounds")),
                    readBlockPos(tag.getCompound("blocks_origin")),
                    blockPalette,
                    rawBlockPalette,
                    unavailablePaletteIds,
                    blocks,
                    blockEntities,
                    entities,
                    tag.getCompound("extra_data"),
                    name
            );
        }
    }

    private static List<Tag> encodePaletteTags(final List<BlockState> blockPalette) {
        return blockPalette.stream()
                .map(state -> BlockState.CODEC.encodeStart(NbtOps.INSTANCE, state).getOrThrow())
                .map(Tag::copy)
                .toList();
    }

    private static List<Tag> normalizeRawBlockPalette(final List<BlockState> blockPalette, final List<Tag> rawBlockPalette) {
        if (rawBlockPalette.size() != blockPalette.size()) {
            return encodePaletteTags(blockPalette);
        }

        return rawBlockPalette.stream().map(Tag::copy).toList();
    }

    private static Optional<BlockState> decodeBlockState(final Tag rawPaletteEntry,
                                                        final int subLevelId,
                                                        final int paletteIndex,
                                                        final BlueprintDiagnosticReport.Builder diagnostics) {
        try {
            final DataResult<BlockState> result = BlockState.CODEC.parse(NbtOps.INSTANCE, rawPaletteEntry);
            final Optional<BlockState> state = result.resultOrPartial(message -> diagnostics.warn(
                    BlueprintDiagnosticStage.DECODE,
                    BlueprintDiagnosticCategory.MISSING_BLOCK_STATE,
                    subLevelId,
                    null,
                    null,
                    "palette #" + paletteIndex,
                    "Missing or invalid blueprint block state; related blocks will be skipped.",
                    "Failed to decode block palette #" + paletteIndex + " from " + describeTag(rawPaletteEntry) + ": " + message,
                    null
            ));
            if (state.isPresent()) {
                return state;
            }
        } catch (final RuntimeException e) {
            diagnostics.warn(
                    BlueprintDiagnosticStage.DECODE,
                    BlueprintDiagnosticCategory.MISSING_BLOCK_STATE,
                    subLevelId,
                    null,
                    null,
                    "palette #" + paletteIndex,
                    "Missing or invalid blueprint block state; related blocks will be skipped.",
                    "Failed to decode block palette #" + paletteIndex + " from " + describeTag(rawPaletteEntry) + ".",
                    e
            );
        }

        return Optional.empty();
    }

    private static Set<Integer> readUnavailablePaletteIds(final CompoundTag tag, final int paletteSize) {
        if (!tag.contains("unavailable_palette_ids", Tag.TAG_INT_ARRAY)) {
            return Set.of();
        }

        final Set<Integer> ids = new HashSet<>();
        for (final int id : tag.getIntArray("unavailable_palette_ids")) {
            if (id >= 0 && id < paletteSize) {
                ids.add(id);
            }
        }
        return ids;
    }

    private static @Nullable BlockPos readRequiredBlockPos(final CompoundTag tag, final String key) {
        if (!tag.contains(key, Tag.TAG_COMPOUND)) {
            return null;
        }

        final CompoundTag pos = tag.getCompound(key);
        if (!pos.contains("x", Tag.TAG_INT) || !pos.contains("y", Tag.TAG_INT) || !pos.contains("z", Tag.TAG_INT)) {
            return null;
        }
        return readBlockPos(pos);
    }

    private static String describeTag(final Tag tag) {
        final String text = tag.toString();
        return text.length() <= 160 ? text : text.substring(0, 157) + "...";
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
