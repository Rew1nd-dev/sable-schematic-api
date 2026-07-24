package dev.rew1nd.sableschematicapi.survival.filmprinter;

import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintPlaceSession;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.BlueprintBlockCostContext;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.BlueprintBuildBlockPayload;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.BlueprintNbtLoadMode;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.CostLine;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.CostQuote;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.CostSeverity;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.operation.AdmissionResult;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.operation.BlueprintIncrementalPostProcessCostStrategy;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.operation.BlueprintPostProcessAdmissionContext;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.operation.BlueprintPostProcessCostContext;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.operation.BlueprintPostProcessCostStrategy;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.operation.BlueprintPostProcessMapper;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.operation.BlueprintPostProcessOperation;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.operation.BlueprintPostProcessOperationParser;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.operation.BlueprintPostProcessRegistry;
import dev.rew1nd.sableschematicapi.blueprint.SableBlueprint;
import dev.rew1nd.sableschematicapi.survival.BlueprintBlockCostRules;
import dev.rew1nd.sableschematicapi.survival.BlueprintBuildPayloads;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Incremental, read-only preflight compiler for one exact blueprint version.
 */
public final class BlueprintMaterialQuoteCompiler {
    private final SableBlueprint blueprint;
    private final BlueprintBlockCostContext blockContext;
    private final BlueprintPostProcessAdmissionContext admissionContext;
    private final BlueprintPostProcessCostContext operationCostContext;
    private final QuoteAccumulator accumulator = new QuoteAccumulator();
    private final Set<String> operationKeys = new HashSet<>();
    private final List<BlueprintPostProcessOperation> operations = new ArrayList<>();

    private Phase phase = Phase.BLOCKS;
    private int subLevelIndex;
    private int blockIndex;
    private int operationIndex;
    private long workUnits;
    private BlueprintIncrementalPostProcessCostStrategy.Cursor operationCursor;
    private BlueprintQuoteIssue issue;

    public BlueprintMaterialQuoteCompiler(final ServerLevel level,
                                          final BlockPos machinePos,
                                          final SableBlueprint blueprint) {
        this.blueprint = blueprint;
        final Vec3 origin = Vec3.atCenterOf(machinePos);
        this.blockContext = new BlueprintBlockCostContext(level, origin);
        final BlueprintPlaceSession session = new BlueprintPlaceSession(
                level,
                new Vector3d(origin.x, origin.y, origin.z),
                blueprint.globalExtraData()
        );
        this.admissionContext = new BlueprintPostProcessAdmissionContext(session, blueprint, level);
        this.operationCostContext = new BlueprintPostProcessCostContext(session, blueprint, level);
    }

    public StepResult step(final int maxWorkUnits) {
        if (maxWorkUnits <= 0) {
            throw new IllegalArgumentException("maxWorkUnits must be positive");
        }
        if (this.isDone()) {
            return new StepResult(0, true);
        }

        int processed = 0;
        try {
            while (processed < maxWorkUnits && !this.isDone()) {
                if (this.phase == Phase.BLOCKS) {
                    processed += this.stepBlock();
                } else if (this.phase == Phase.PREPARE_OPERATIONS) {
                    this.prepareOperations();
                } else if (this.phase == Phase.OPERATIONS) {
                    processed += this.stepOperation(maxWorkUnits - processed);
                }
            }
        } catch (final RuntimeException exception) {
            this.reject("quote_exception", exception.getMessage() == null
                    ? "Blueprint quote failed."
                    : "Blueprint quote failed: " + exception.getMessage());
        }
        return new StepResult(processed, this.isDone());
    }

    public boolean isDone() {
        return this.phase == Phase.DONE || this.phase == Phase.FAILED;
    }

    public BlueprintMaterialQuote result() {
        if (!this.isDone()) {
            throw new IllegalStateException("Blueprint quote is not complete");
        }
        return this.issue == null
                ? BlueprintMaterialQuote.admitted(this.accumulator.lines(), this.workUnits)
                : BlueprintMaterialQuote.rejected(this.issue);
    }

    private int stepBlock() {
        final List<SableBlueprint.SubLevelData> subLevels = this.blueprint.subLevels();
        while (this.subLevelIndex < subLevels.size()) {
            final SableBlueprint.SubLevelData subLevel = subLevels.get(this.subLevelIndex);
            if (this.blockIndex >= subLevel.blocks().size()) {
                this.subLevelIndex++;
                this.blockIndex = 0;
                continue;
            }

            final SableBlueprint.BlockData block = subLevel.blocks().get(this.blockIndex++);
            final BlueprintBuildBlockPayload payload =
                    BlueprintBuildPayloads.resolve(this.blueprint, subLevel, block);
            if (payload.nbtDecision().mode() == BlueprintNbtLoadMode.DENY) {
                this.reject("nbt_denied", "A blueprint block is denied by the survival NBT policy.");
                return 1;
            }
            if (!this.addQuote(BlueprintBlockCostRules.quotePlacement(payload, this.blockContext))) {
                return 1;
            }
            if (payload.nbtDecision().loadsNbt()
                    && !this.addQuote(BlueprintBlockCostRules.quoteNbtLoad(payload, this.blockContext))) {
                return 1;
            }
            this.workUnits = Math.addExact(this.workUnits, 1L);
            return 1;
        }
        this.phase = Phase.PREPARE_OPERATIONS;
        return 0;
    }

    private void prepareOperations() {
        final List<ResourceLocation> sidecarIds =
                BlueprintPostProcessRegistry.registeredSidecarIds().stream()
                        .sorted(Comparator.comparing(ResourceLocation::toString))
                        .toList();
        final BlueprintPlaceSession session = this.operationCostContext.session();
        for (final ResourceLocation sidecarId : sidecarIds) {
            final BlueprintPostProcessOperationParser parser = BlueprintPostProcessRegistry.parser(sidecarId);
            if (parser == null) {
                this.reject("missing_parser", "Missing post-process parser for " + sidecarId + ".");
                return;
            }
            final CompoundTag sidecarData = this.blueprint.globalExtraData().getCompound(sidecarId.toString());
            final List<? extends BlueprintPostProcessOperation> parsed =
                    parser.parse(session, sidecarData, this.blueprint);
            for (final BlueprintPostProcessOperation operation : parsed) {
                if (this.operationKeys.add(operation.stableKey())) {
                    this.operations.add(operation);
                }
            }
        }
        this.phase = Phase.OPERATIONS;
    }

    private int stepOperation(final int remainingWork) {
        if (this.operationIndex >= this.operations.size()) {
            this.phase = Phase.DONE;
            return 0;
        }

        final BlueprintPostProcessOperation operation = this.operations.get(this.operationIndex);
        final BlueprintPostProcessMapper<BlueprintPostProcessOperation> mapper =
                BlueprintPostProcessRegistry.mapper(operation.type());
        if (mapper == null) {
            this.reject("missing_mapper", "Missing post-process mapper for " + operation.type() + ".");
            return 0;
        }
        final AdmissionResult admission = mapper.admit(operation, this.admissionContext);
        if (!admission.admitted()) {
            this.issue = new BlueprintQuoteIssue(
                    "admission_denied",
                    admission.reason() == null
                            ? net.minecraft.network.chat.Component.literal(
                                    "Post-process admission denied for " + operation.type() + ".")
                            : admission.reason()
            );
            this.phase = Phase.FAILED;
            return 0;
        }

        final BlueprintPostProcessCostStrategy<BlueprintPostProcessOperation> strategy =
                BlueprintPostProcessRegistry.cost(operation.type());
        if (strategy == null) {
            this.reject("missing_cost", "Missing post-process cost strategy for " + operation.type() + ".");
            return 0;
        }

        if (strategy instanceof final BlueprintIncrementalPostProcessCostStrategy<?> incrementalRaw) {
            @SuppressWarnings("unchecked")
            final BlueprintIncrementalPostProcessCostStrategy<BlueprintPostProcessOperation> incremental =
                    (BlueprintIncrementalPostProcessCostStrategy<BlueprintPostProcessOperation>) incrementalRaw;
            if (this.operationCursor == null) {
                this.operationCursor = incremental.begin(operation, this.operationCostContext);
            }
            final BlueprintIncrementalPostProcessCostStrategy.Step step =
                    this.operationCursor.step(Math.max(1, remainingWork));
            if (!this.addQuote(step.quote())) {
                return step.workUnits();
            }
            if (!step.done() && step.workUnits() == 0) {
                this.reject("stalled_cost", "Incremental post-process quote made no progress.");
                return 0;
            }
            this.workUnits = Math.addExact(this.workUnits, step.workUnits());
            if (step.done()) {
                this.operationCursor = null;
                this.operationIndex++;
            }
            return step.workUnits();
        }

        if (!this.addQuote(strategy.quote(operation, this.operationCostContext))) {
            return 0;
        }
        this.operationIndex++;
        return 0;
    }

    private boolean addQuote(final CostQuote quote) {
        if (quote.severity() == CostSeverity.DENY) {
            this.reject("cost_denied", "A blueprint cost rule denied film printing.");
            return false;
        }
        this.accumulator.add(quote);
        return true;
    }

    private void reject(final String code, final String message) {
        this.issue = BlueprintQuoteIssue.of(code, message);
        this.phase = Phase.FAILED;
    }

    public record StepResult(int processedWorkUnits, boolean done) {
    }

    private enum Phase {
        BLOCKS,
        PREPARE_OPERATIONS,
        OPERATIONS,
        DONE,
        FAILED
    }

    private static final class QuoteAccumulator {
        private final List<CostLine> lines = new ArrayList<>();

        private void add(final CostQuote quote) {
            for (final CostLine incoming : quote.lines()) {
                final ItemStack stack = incoming.stack();
                if (stack.isEmpty() || stack.getCount() <= 0) {
                    continue;
                }
                int matching = -1;
                for (int i = 0; i < this.lines.size(); i++) {
                    if (ItemStack.isSameItemSameComponents(this.lines.get(i).stack(), stack)) {
                        matching = i;
                        break;
                    }
                }
                if (matching < 0) {
                    this.lines.add(incoming);
                    continue;
                }
                final CostLine existing = this.lines.get(matching);
                final ItemStack merged = existing.stack();
                merged.setCount(Math.addExact(merged.getCount(), stack.getCount()));
                this.lines.set(matching, new CostLine(existing.ruleId(), merged, existing.description()));
            }
        }

        private List<CostLine> lines() {
            return List.copyOf(this.lines);
        }
    }
}
