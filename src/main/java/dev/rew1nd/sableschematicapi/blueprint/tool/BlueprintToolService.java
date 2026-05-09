package dev.rew1nd.sableschematicapi.blueprint.tool;

import dev.rew1nd.sableschematicapi.SableSchematicApi;
import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintDiagnosticReport;
import dev.rew1nd.sableschematicapi.blueprint.SableBlueprint;
import dev.rew1nd.sableschematicapi.blueprint.SableBlueprintDecodeResult;
import dev.rew1nd.sableschematicapi.blueprint.SableBlueprintExporter;
import dev.rew1nd.sableschematicapi.blueprint.SableBlueprintFiles;
import dev.rew1nd.sableschematicapi.blueprint.SableBlueprintPlacer;
import dev.rew1nd.sableschematicapi.survival.BlueprintPlacementPlan;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public final class BlueprintToolService {
    private static final double TOOL_LOAD_SPACING = 5.0D;

    private BlueprintToolService() {
    }

    public static List<String> listBlueprints(final MinecraftServer server) throws IOException {
        return SableBlueprintFiles.list(server).stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    public static BlueprintToolResult inspect(final MinecraftServer server, final String name) {
        try {
            final SableBlueprintDecodeResult decoded = SableBlueprintFiles.loadWithDiagnostics(server, name);
            final SableBlueprint blueprint = decoded.blueprint();
            final BlueprintToolSummary summary = BlueprintToolSummary.of(blueprint);
            return BlueprintToolResult.success(
                    "Blueprint '%s': %s.".formatted(name, summary.describe()),
                    blueprint.subLevels().size(),
                    summary,
                    decoded.diagnostics()
            );
        } catch (final IOException | IllegalArgumentException e) {
            return BlueprintToolResult.failure("Failed to inspect Sable blueprint: " + e.getMessage());
        }
    }

    public static BlueprintToolResult save(final MinecraftServer server,
                                           final ServerLevel level,
                                           final Vec3 origin,
                                           final double radius,
                                           final String name) {
        if (radius <= 0.0) {
            return BlueprintToolResult.failure("Blueprint radius must be greater than 0.");
        }

        final SableBlueprint blueprint = SableBlueprintExporter.export(level, origin, radius);
        if (blueprint.isEmpty()) {
            return BlueprintToolResult.failure("No sub-levels found in blueprint radius.");
        }

        try {
            SableBlueprintFiles.save(server, name, blueprint);
            final Path path = SableBlueprintFiles.path(server, name);
            final BlueprintToolSummary summary = BlueprintToolSummary.of(blueprint);
            return BlueprintToolResult.success(
                    "Saved Sable blueprint '%s' with %s to %s".formatted(name, summary.describe(), path),
                    blueprint.subLevels().size(),
                    summary
            );
        } catch (final IOException | IllegalArgumentException e) {
            return BlueprintToolResult.failure("Failed to save Sable blueprint: " + e.getMessage());
        }
    }

    public static BlueprintToolFile saveSelectionToBytes(final ServerLevel level,
                                                         final Vec3 start,
                                                         final Vec3 end,
                                                         final String name) {
        if (name == null || name.isBlank()) {
            return BlueprintToolFile.failure("Blueprint name cannot be empty.");
        }

        final SableBlueprint blueprint = SableBlueprintExporter.export(level, start, end);
        if (blueprint.isEmpty()) {
            return BlueprintToolFile.failure("No sub-levels found in selected area.");
        }

        try {
            final byte[] data = writeToBytes(blueprint);
            final BlueprintToolSummary summary = BlueprintToolSummary.of(blueprint);
            return new BlueprintToolFile(
                    BlueprintToolResult.success("Saved Sable blueprint '%s' with %s.".formatted(name, summary.describe()), blueprint.subLevels().size(), summary),
                    data
            );
        } catch (final IOException e) {
            return BlueprintToolFile.failure("Failed to encode Sable blueprint: " + e.getMessage());
        }
    }

    public static BlueprintToolResult load(final MinecraftServer server,
                                           final ServerLevel level,
                                           final Vec3 origin,
                                           final String name) {
        try {
            final SableBlueprintDecodeResult decoded = SableBlueprintFiles.loadWithDiagnostics(server, name);
            final SableBlueprint blueprint = decoded.blueprint();
            final SableBlueprintPlacer.Result result = SableBlueprintPlacer.place(level, blueprint, origin);
            final BlueprintDiagnosticReport diagnostics = decoded.diagnostics().merge(result.diagnostics());
            diagnostics.logSummary(SableSchematicApi.LOGGER, "Loaded Sable blueprint '" + name + "'");
            final BlueprintToolSummary summary = BlueprintToolSummary.of(blueprint);
            return BlueprintToolResult.success(
                    "Loaded Sable blueprint '%s' with %s.".formatted(name, summary.describe()),
                    result.placedSubLevels(),
                    summary,
                    diagnostics
            );
        } catch (final IOException | IllegalArgumentException | IllegalStateException e) {
            return BlueprintToolResult.failure("Failed to load Sable blueprint: " + e.getMessage());
        }
    }

    public static BlueprintToolResult loadBytes(final ServerLevel level,
                                                final Vec3 origin,
                                                final byte[] data,
                                                final String name) {
        try {
            final SableBlueprintDecodeResult decoded = readFromBytes(data);
            final SableBlueprint blueprint = decoded.blueprint();
            final BlueprintPlacementPlan placementPlan = BlueprintPlacementPlan.forLookTarget(blueprint, origin, TOOL_LOAD_SPACING);
            final SableBlueprintPlacer.Result result = SableBlueprintPlacer.place(level, blueprint, placementPlan);
            final BlueprintDiagnosticReport diagnostics = decoded.diagnostics().merge(result.diagnostics());
            diagnostics.logSummary(SableSchematicApi.LOGGER, "Loaded uploaded Sable blueprint '" + name + "'");
            final BlueprintToolSummary summary = BlueprintToolSummary.of(blueprint);
            return BlueprintToolResult.success(
                    "Loaded Sable blueprint '%s' with %s.".formatted(name, summary.describe()),
                    result.placedSubLevels(),
                    summary,
                    diagnostics
            );
        } catch (final IOException | IllegalArgumentException | IllegalStateException e) {
            return BlueprintToolResult.failure("Failed to load Sable blueprint: " + e.getMessage());
        }
    }

    private static byte[] writeToBytes(final SableBlueprint blueprint) throws IOException {
        try (final ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
            NbtIo.writeCompressed(blueprint.save(), stream);
            return stream.toByteArray();
        }
    }

    private static SableBlueprintDecodeResult readFromBytes(final byte[] data) throws IOException {
        try (final ByteArrayInputStream stream = new ByteArrayInputStream(data)) {
            final CompoundTag tag = NbtIo.readCompressed(stream, NbtAccounter.unlimitedHeap());
            return SableBlueprint.loadWithDiagnostics(tag);
        }
    }
}
