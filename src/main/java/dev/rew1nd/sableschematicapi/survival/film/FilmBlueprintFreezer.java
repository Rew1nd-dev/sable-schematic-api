package dev.rew1nd.sableschematicapi.survival.film;

import dev.rew1nd.sableschematicapi.SableSchematicApi;
import dev.rew1nd.sableschematicapi.survival.BlueprintPayloads;
import dev.rew1nd.sableschematicapi.survival.manuscript.BlueprintAlias;
import dev.rew1nd.sableschematicapi.survival.manuscript.BlueprintDocument;
import dev.rew1nd.sableschematicapi.survival.manuscript.BlueprintManuscriptState;
import dev.rew1nd.sableschematicapi.survival.manuscript.BlueprintWorldStore;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import dev.rew1nd.sableschematicapi.tool.SableSchematicApiItems;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Future film-printer admission point. Calling this freezes the currently
 * resolved alias and rotation before a machine consumes its first material.
 */
public final class FilmBlueprintFreezer {
    private static final String FROZEN_ROTATION = "sable_schematic_api_film_rotation";

    private FilmBlueprintFreezer() {
    }

    public static FrozenBlueprint freeze(final MinecraftServer server,
                                         final BlueprintManuscriptState manuscript) throws IOException {
        if (manuscript == null || manuscript.alias().isEmpty()) {
            throw new IOException("Cannot record film from an empty manuscript.");
        }

        final BlueprintAlias alias = manuscript.alias().orElseThrow();
        final Path source = BlueprintWorldStore.uploadPath(server, alias.ownerId(), alias.blueprintName());
        final BlueprintDocument document = BlueprintWorldStore.inspect(source);
        final CompoundTag root = BlueprintWorldStore.readRoot(source);
        root.remove("preview");
        root.putInt(FROZEN_ROTATION, manuscript.rotation().degrees());

        final byte[] objectData;
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            NbtIo.writeCompressed(root, output);
            objectData = output.toByteArray();
        }
        final String objectSha256 = BlueprintPayloads.hashString(BlueprintPayloads.sha256(objectData));
        BlueprintObjectStore.storeIfAbsent(server, objectSha256, objectData);
//        SableSchematicApi.LOGGER.info(
//                "Frozen blueprint film object {} from {}/{} at {} degrees",
//                objectSha256.substring(0, 12),
//                alias.ownerId(),
//                alias.blueprintName(),
//                manuscript.rotation().degrees()
//        );

        final FilmSnapshot snapshot = new FilmSnapshot(
                alias.blueprintName(),
                document.author(),
                alias.ownerId(),
                alias.ownerName(),
                document.summary()
        );
        final FilmState state = FilmState.recorded(objectSha256, manuscript.rotation(), snapshot);
        return new FrozenBlueprint(state, objectData.length);
    }

    /** Resolves only the immutable object named by film; aliases are never consulted. */
    public static ResolvedFilmBlueprint resolve(final MinecraftServer server,
                                                final FilmState film) throws IOException {
        if (film == null || !film.isRecorded()) {
            throw new IOException("Film is not recorded.");
        }
        final String sha256 = film.objectSha256().orElseThrow();
        return new ResolvedFilmBlueprint(film, BlueprintObjectStore.read(server, sha256));
    }

    public record FrozenBlueprint(FilmState filmState, int compressedBytes) {
        public ItemStack createFilmStack() {
            final ItemStack stack = new ItemStack(SableSchematicApiItems.FILM.get());
            FilmItem.setState(stack, this.filmState);
            return stack;
        }
    }

    public record ResolvedFilmBlueprint(FilmState filmState, byte[] compressedBlueprint) {
        public ResolvedFilmBlueprint {
            compressedBlueprint = compressedBlueprint.clone();
        }

        @Override
        public byte[] compressedBlueprint() {
            return this.compressedBlueprint.clone();
        }
    }
}
