package dev.rew1nd.sableschematicapi.sublevel;

import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.storage.holding.SavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunk;
import dev.ryanhcode.sable.sublevel.storage.region.SubLevelRegionFile;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelStorage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class SubLevelDirectoryService {
    private SubLevelDirectoryService() {
    }

    public static List<SubLevelRecord> listAll(final MinecraftServer server) {
        final List<SubLevelRecord> records = new ArrayList<>();

        for (final ServerLevel level : server.getAllLevels()) {
            records.addAll(list(level));
        }

        records.sort(RECORD_ORDER);
        return records;
    }

    public static List<SubLevelRecord> list(final ServerLevel level) {
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return List.of();
        }

        final Map<UUID, SubLevelRecord> records = new LinkedHashMap<>();
        addLoadedRecords(level, container, records);
        addStoredRecords(level, container, records);

        final List<SubLevelRecord> sorted = new ArrayList<>(records.values());
        sorted.sort(RECORD_ORDER);
        return sorted;
    }

    public static Optional<SubLevelRecord> find(final MinecraftServer server,
                                                final ResourceKey<Level> dimension,
                                                final UUID uuid) {
        final ServerLevel level = server.getLevel(dimension);
        if (level == null) {
            return Optional.empty();
        }

        return list(level).stream()
                .filter(record -> record.uuid().equals(uuid))
                .findFirst();
    }

    public static Optional<SubLevelRecord> find(final MinecraftServer server, final UUID uuid) {
        return listAll(server).stream()
                .filter(record -> record.uuid().equals(uuid))
                .findFirst();
    }

    public static void invalidate(final ResourceKey<Level> dimension) {
        // Reserved for a later cached implementation.
    }

    private static void addLoadedRecords(final ServerLevel level,
                                         final ServerSubLevelContainer container,
                                         final Map<UUID, SubLevelRecord> records) {
        for (final ServerSubLevel subLevel : container.getAllSubLevels()) {
            if (subLevel.isRemoved()) {
                continue;
            }

            final List<UUID> dependencies = SubLevelHelper.getLoadingDependencyChain(subLevel).stream()
                    .map(ServerSubLevel::getUniqueId)
                    .toList();

            records.put(subLevel.getUniqueId(), new SubLevelRecord(
                    level.dimension(),
                    subLevel.getUniqueId(),
                    subLevel.getName(),
                    SubLevelLoadState.LOADED,
                    subLevel.logicalPose(),
                    new BoundingBox3d(subLevel.boundingBox()),
                    subLevel.getLastSerializationPointer(),
                    dependencies
            ));
        }
    }

    private static void addStoredRecords(final ServerLevel level,
                                         final ServerSubLevelContainer container,
                                         final Map<UUID, SubLevelRecord> records) {
        final SubLevelStorage storage = container.getHoldingChunkMap().getStorage();
        final File[] regionFiles = storage.getFolder().toFile()
                .listFiles((dir, name) -> name.endsWith(SubLevelRegionFile.FILE_EXTENSION));

        if (regionFiles == null) {
            return;
        }

        for (final File regionFile : regionFiles) {
            final RegionPos regionPos = parseRegionPos(regionFile.getName());
            if (regionPos == null) {
                continue;
            }

            for (int localX = 0; localX < SubLevelRegionFile.SIDE_LENGTH; localX++) {
                for (int localZ = 0; localZ < SubLevelRegionFile.SIDE_LENGTH; localZ++) {
                    final ChunkPos chunkPos = new ChunkPos(
                            regionPos.x * SubLevelRegionFile.SIDE_LENGTH + localX,
                            regionPos.z * SubLevelRegionFile.SIDE_LENGTH + localZ
                    );
                    addStoredRecordsFromChunk(level, storage, chunkPos, records);
                }
            }
        }
    }

    private static void addStoredRecordsFromChunk(final ServerLevel level,
                                                  final SubLevelStorage storage,
                                                  final ChunkPos chunkPos,
                                                  final Map<UUID, SubLevelRecord> records) {
        final SubLevelHoldingChunk holdingChunk = storage.attemptLoadHoldingChunk(chunkPos);
        if (holdingChunk == null) {
            return;
        }

        for (final SavedSubLevelPointer pointer : holdingChunk.getSubLevelPointers()) {
            final SubLevelData data = storage.attemptLoadSubLevel(chunkPos, pointer);
            if (data == null || records.containsKey(data.uuid())) {
                continue;
            }

            final GlobalSavedSubLevelPointer globalPointer = new GlobalSavedSubLevelPointer(
                    chunkPos,
                    pointer.storageIndex(),
                    pointer.subLevelIndex()
            );

            records.put(data.uuid(), new SubLevelRecord(
                    level.dimension(),
                    data.uuid(),
                    storedName(data),
                    SubLevelLoadState.STORED,
                    new Pose3d(data.pose()),
                    new BoundingBox3d(data.bounds()),
                    globalPointer,
                    data.dependencies()
            ));
        }
    }

    private static @Nullable String storedName(final SubLevelData data) {
        final CompoundTag tag = data.fullTag();
        return tag.contains("display_name", Tag.TAG_STRING) ? tag.getString("display_name") : null;
    }

    private static @Nullable RegionPos parseRegionPos(final String fileName) {
        final String extension = SubLevelRegionFile.FILE_EXTENSION;
        if (!fileName.endsWith(extension)) {
            return null;
        }

        final String withoutExtension = fileName.substring(0, fileName.length() - extension.length());
        final String[] parts = withoutExtension.split("\\.");
        if (parts.length != 3 || !"r".equals(parts[0])) {
            return null;
        }

        try {
            return new RegionPos(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        } catch (final NumberFormatException e) {
            return null;
        }
    }

    private static final Comparator<SubLevelRecord> RECORD_ORDER = Comparator
            .comparing((SubLevelRecord record) -> record.dimension().location().toString())
            .thenComparing(record -> record.loadState() == SubLevelLoadState.LOADED ? 0 : 1)
            .thenComparing(record -> record.displayName().toLowerCase(Locale.ROOT))
            .thenComparing(record -> record.uuid().toString());

    private record RegionPos(int x, int z) {
    }
}
