package dev.rew1nd.sableschematicapi.survival;

import dev.rew1nd.sableschematicapi.api.blueprint.survival.BlueprintBuildPhase;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.BlueprintBuildStatus;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.BlueprintBuildStepResult;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.BlueprintBuildTickBudget;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.BlueprintSummary;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.CostLine;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.CostQuote;
import dev.rew1nd.sableschematicapi.blueprint.SableBlueprint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class BlueprintCannonBlockEntity extends BlockEntity {
    public static final int BLUEPRINT_SLOT = 0;
    public static final int SLOT_COUNT = 1;
    private static final int DEFAULT_PRINT_INTERVAL_TICKS = 4;
    private static final int MIN_PRINT_INTERVAL_TICKS = 1;
    private static final int MAX_PRINT_INTERVAL_TICKS = 200;
    private static final double PRINT_PADDING_BLOCKS = 1.0D;

    private static final String KEY_INVENTORY = "inventory";
    private static final String KEY_PROGRESS = "progress";
    private static final String KEY_ACTIVE = "active";
    private static final String KEY_LAST_BLUEPRINT_HASH = "last_blueprint_hash";
    private static final String KEY_LAST_STATUS = "last_status";
    private static final String KEY_LAST_MESSAGE = "last_message";
    private static final String KEY_LAST_MISSING = "last_missing";
    private static final String KEY_LAST_AFFECTED_BLOCKS = "last_affected_blocks";
    private static final String KEY_LAST_SOURCE_COUNT = "last_source_count";
    private static final String KEY_PRINT_INTERVAL_TICKS = "print_interval_ticks";
    private static final String KEY_PRINT_COOLDOWN_TICKS = "print_cooldown_ticks";
    private static final String KEY_ESTIMATED_BUDGET = "estimated_budget";

    private final ItemStackHandler inventory = new ItemStackHandler(SLOT_COUNT) {
        @Override
        public int getSlotLimit(final int slot) {
            return slot == BLUEPRINT_SLOT ? 1 : super.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(final int slot, final ItemStack stack) {
            return slot == BLUEPRINT_SLOT && BlueprintDataItem.isBlueprintItem(stack);
        }

        @Override
        protected void onContentsChanged(final int slot) {
            BlueprintCannonBlockEntity.this.onInventoryChanged(slot);
        }
    };

    private BlueprintBuildProgress progress = new BlueprintBuildProgress();
    private boolean active;
    private String lastBlueprintHash = "";
    private BlueprintBuildStatus lastStatus = BlueprintBuildStatus.IDLE;
    private String lastMessage = "";
    private String lastMissing = "";
    private int lastAffectedBlocks;
    private int lastSourceCount;
    private int printIntervalTicks = DEFAULT_PRINT_INTERVAL_TICKS;
    private int printCooldownTicks;
    private List<BudgetLine> estimatedBudget = List.of();
    @Nullable
    private SableBlueprint cachedBlueprint;
    private String cachedBlueprintHash = "";

    public BlueprintCannonBlockEntity(final BlockPos pos, final BlockState state) {
        super(SableSchematicApiBlockEntities.BLUEPRINT_CANNON.get(), pos, state);
    }

    public static void serverTick(final Level level,
                                  final BlockPos pos,
                                  final BlockState state,
                                  final BlueprintCannonBlockEntity blockEntity) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        blockEntity.tickServer(serverLevel, state);
    }

    public ItemStackHandler inventory() {
        return this.inventory;
    }

    public boolean isActive() {
        return this.active;
    }

    public void setActive(final boolean active) {
        if (this.active == active) {
            return;
        }
        this.active = active;
        if (active) {
            this.printCooldownTicks = 0;
        }
        this.updateLastResult(BlueprintBuildStatus.IDLE, active ? "Blueprint cannon started." : "Blueprint cannon paused.", "", 0);
        this.markDirtyAndSync();
    }

    public void resetProgress() {
        if (this.active) {
            this.updateLastResult(BlueprintBuildStatus.FAILED, "Pause the blueprint cannon before resetting progress.", "", 0);
            this.markDirtyAndSync();
            return;
        }
        if (!this.progress.placedSubLevels().isEmpty() && this.progress.phase() != BlueprintBuildPhase.DONE && this.progress.phase() != BlueprintBuildPhase.FAILED) {
            this.updateLastResult(BlueprintBuildStatus.FAILED, "Cannot reset after sub-level allocation without cleanup support.", "", 0);
            this.markDirtyAndSync();
            return;
        }

        this.progress = new BlueprintBuildProgress();
        this.lastAffectedBlocks = 0;
        this.printCooldownTicks = 0;
        this.updateLastResult(BlueprintBuildStatus.IDLE, "Blueprint cannon progress reset.", "", 0);
        this.markDirtyAndSync();
    }

    public ItemStack extractInsertedRemainder(final ItemStack stack) {
        if (stack.isEmpty() || !BlueprintDataItem.isBlueprintItem(stack)) {
            return stack;
        }
        final ItemStack single = stack.copyWithCount(1);
        final ItemStack remainingSingle = this.inventory.insertItem(BLUEPRINT_SLOT, single, false);
        if (remainingSingle.isEmpty()) {
            final ItemStack remainder = stack.copy();
            remainder.shrink(1);
            return remainder;
        }
        return stack;
    }

    public void ejectBlueprint(final Player player) {
        if (this.active) {
            this.updateLastResult(BlueprintBuildStatus.FAILED, "Pause the blueprint cannon before ejecting the blueprint.", "", 0);
            this.markDirtyAndSync();
            return;
        }

        final ItemStack stack = this.inventory.getStackInSlot(BLUEPRINT_SLOT);
        if (stack.isEmpty()) {
            this.updateLastResult(BlueprintBuildStatus.IDLE, "No blueprint to eject.", "", 0);
            this.markDirtyAndSync();
            return;
        }

        this.inventory.setStackInSlot(BLUEPRINT_SLOT, ItemStack.EMPTY);
        if (!player.getInventory().add(stack.copy()) && this.level != null) {
            player.drop(stack.copy(), false);
        }
        this.progress = new BlueprintBuildProgress();
        this.printCooldownTicks = 0;
        this.updateLastResult(BlueprintBuildStatus.IDLE, "Blueprint ejected.", "", 0);
        this.markDirtyAndSync();
    }

    public int printIntervalTicks() {
        return this.printIntervalTicks;
    }

    public void debugSpeedUp() {
        this.setPrintIntervalTicks(this.printIntervalTicks - 1);
    }

    public void debugSlowDown() {
        this.setPrintIntervalTicks(this.printIntervalTicks + 1);
    }

    public Component speedLine() {
        return Component.translatable("sable_schematic_api.blueprint_cannon.ui.speed", this.printIntervalTicks);
    }

    public Component sourceLine() {
        return Component.translatable("sable_schematic_api.blueprint_cannon.ui.source_count", this.lastSourceCount);
    }

    public Component insertHintLine() {
        final ItemStack stack = this.inventory.getStackInSlot(BLUEPRINT_SLOT);
        if (BlueprintDataItem.hasPayload(stack)) {
            return Component.translatable("sable_schematic_api.blueprint_cannon.ui.blueprint_loaded");
        }
        return Component.translatable("sable_schematic_api.blueprint_cannon.ui.insert_hint");
    }

    public Component blueprintLine() {
        final ItemStack stack = this.inventory.getStackInSlot(BLUEPRINT_SLOT);
        if (!BlueprintDataItem.hasPayload(stack)) {
            return Component.translatable("sable_schematic_api.blueprint_cannon.ui.no_blueprint");
        }

        final BlueprintSummary summary = BlueprintDataItem.summary(stack);
        final String name = BlueprintDataItem.blueprintName(stack);
        return Component.translatable(
                "sable_schematic_api.blueprint_cannon.ui.blueprint_summary",
                name.isBlank() ? stack.getHoverName() : Component.literal(name),
                summary.subLevels(),
                summary.blocks(),
                summary.blockEntityTags(),
                summary.entities()
        );
    }

    public Component statusLine() {
        if (!BlueprintDataItem.hasPayload(this.inventory.getStackInSlot(BLUEPRINT_SLOT))) {
            return Component.translatable("sable_schematic_api.blueprint_cannon.status.no_blueprint");
        }
        if (!this.lastMessage.isBlank()) {
            return Component.literal(this.lastMessage);
        }
        return Component.translatable("sable_schematic_api.blueprint_cannon.status." + this.lastStatus.name().toLowerCase(java.util.Locale.ROOT));
    }

    public Component progressLine() {
        final BlueprintSummary summary = BlueprintDataItem.summary(this.inventory.getStackInSlot(BLUEPRINT_SLOT));
        return Component.translatable(
                "sable_schematic_api.blueprint_cannon.ui.progress",
                this.progress.phase().name(),
                this.progress.currentSubLevelIndex() + 1,
                Math.max(1, summary.subLevels()),
                this.progress.currentBlockIndex(),
                summary.blocks()
        );
    }

    public Component missingLine() {
        if (this.lastMissing.isBlank()) {
            return Component.translatable("sable_schematic_api.blueprint_cannon.ui.missing_none");
        }
        return Component.translatable("sable_schematic_api.blueprint_cannon.ui.missing", this.lastMissing);
    }

    public List<BudgetLine> estimatedBudget() {
        return this.estimatedBudget;
    }

    public void refreshEstimatedBudget() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        final ItemStack blueprintStack = this.inventory.getStackInSlot(BLUEPRINT_SLOT);
        if (!BlueprintDataItem.hasPayload(blueprintStack)) {
            this.estimatedBudget = List.of();
            this.markDirtyAndSync();
            return;
        }

        final Optional<SableBlueprint> blueprint = this.resolveBlueprint(
                (ServerLevel) this.level, blueprintStack);
        if (blueprint.isEmpty()) {
            this.estimatedBudget = List.of();
            this.markDirtyAndSync();
            return;
        }

        final CostQuote quote = SableBlueprintIncrementalPlacer.estimateRemainingCost(
                (ServerLevel) this.level, blueprint.get(), this.progress);
        final List<BudgetLine> lines = new java.util.ArrayList<>();
        for (final CostLine costLine : quote.lines()) {
            final ItemStack stack = costLine.stack();
            final int available = BlueprintCannonMaterialBudget.countAvailable(
                    (ServerLevel) this.level, this.worldPosition, stack.copyWithCount(1));
            lines.add(new BudgetLine(stack.copyWithCount(1), stack.getCount(), available));
        }
        this.estimatedBudget = List.copyOf(lines);
        this.markDirtyAndSync();
    }

    private void tickServer(final ServerLevel level, final BlockState state) {
        final int sourceCount = BlueprintCannonMaterialBudget.countMaterialSources(level, this.worldPosition);
        if (sourceCount != this.lastSourceCount) {
            this.lastSourceCount = sourceCount;
            this.markDirtyAndSync();
        }

        final ItemStack blueprintStack = this.inventory.getStackInSlot(BLUEPRINT_SLOT);
        final String hash = BlueprintDataItem.hashString(blueprintStack);
        if (!Objects.equals(hash, this.lastBlueprintHash)) {
            this.onBlueprintPayloadChanged(hash);
        }

        if (!BlueprintDataItem.hasPayload(blueprintStack)) {
            if (this.active || this.lastStatus != BlueprintBuildStatus.IDLE) {
                this.active = false;
                this.updateLastResult(BlueprintBuildStatus.IDLE, "Insert a survival blueprint.", "", 0);
                this.markDirtyAndSync();
            }
            return;
        }

        if (!this.active) {
            return;
        }

        if (this.printCooldownTicks > 0) {
            this.printCooldownTicks--;
            this.setChanged();
            return;
        }

        final Optional<SableBlueprint> blueprint = this.resolveBlueprint(level, blueprintStack);
        if (blueprint.isEmpty()) {
            this.active = false;
            this.progress.setPhase(BlueprintBuildPhase.FAILED);
            this.printCooldownTicks = 0;
            this.updateLastResult(BlueprintBuildStatus.FAILED, "Failed to read blueprint item payload.", "", 0);
            this.markDirtyAndSync();
            return;
        }

        final BlueprintBuildStepResult result = SableBlueprintIncrementalPlacer.step(
                level,
                blueprint.get(),
                BlueprintPlacementPlan.forCannon(blueprint.get(), this.worldPosition, this.progress, PRINT_PADDING_BLOCKS),
                this.progress,
                new BlueprintBuildTickBudget(1, BlueprintBuildTickBudget.DEFAULT.maxBlockEntities(), BlueprintBuildTickBudget.DEFAULT.maxPostProcessOperations(), BlueprintBuildTickBudget.DEFAULT.maxNanos()),
                new BlueprintCannonMaterialBudget(level, this.worldPosition)
        );

        final String missing = describeMissing(result);
        this.updateLastResult(result.status(), result.message().getString(), missing, result.affectedBlocks());
        if (result.status() == BlueprintBuildStatus.DONE || result.status() == BlueprintBuildStatus.FAILED) {
            this.active = false;
            this.printCooldownTicks = 0;
        } else if (result.status() == BlueprintBuildStatus.CONTINUE || result.affectedBlocks() > 0) {
            this.printCooldownTicks = Math.max(0, this.printIntervalTicks - 1);
        }
        this.markDirtyAndSync();
    }

    private Optional<SableBlueprint> resolveBlueprint(final ServerLevel level, final ItemStack stack) {
        final String hash = BlueprintDataItem.hashString(stack);
        if (this.cachedBlueprint != null && Objects.equals(hash, this.cachedBlueprintHash)) {
            return Optional.of(this.cachedBlueprint);
        }

        final Optional<SableBlueprint> decoded = BlueprintDataItem.readBlueprint(level, stack);
        this.cachedBlueprint = decoded.orElse(null);
        this.cachedBlueprintHash = decoded.isPresent() ? hash : "";
        return decoded;
    }

    private void onInventoryChanged(final int slot) {
        if (slot == BLUEPRINT_SLOT) {
            this.cachedBlueprint = null;
            this.cachedBlueprintHash = "";
        }
        this.markDirtyAndSync();
    }

    private void onBlueprintPayloadChanged(final String hash) {
        this.lastBlueprintHash = hash;
        this.cachedBlueprint = null;
        this.cachedBlueprintHash = "";
        this.progress = new BlueprintBuildProgress();
        this.active = false;
        this.lastAffectedBlocks = 0;
        this.printCooldownTicks = 0;
        this.updateLastResult(BlueprintBuildStatus.IDLE, hash.isBlank() ? "Insert a survival blueprint." : "Blueprint payload changed.", "", 0);
        this.refreshEstimatedBudget();
    }

    private void setPrintIntervalTicks(final int ticks) {
        final int sanitized = sanitizePrintInterval(ticks);
        if (this.printIntervalTicks == sanitized) {
            return;
        }
        this.printIntervalTicks = sanitized;
        this.printCooldownTicks = Math.min(this.printCooldownTicks, Math.max(0, sanitized - 1));
        this.updateLastResult(this.lastStatus, "Print interval: 1 block / " + sanitized + " tick(s).", this.lastMissing, this.lastAffectedBlocks);
        this.markDirtyAndSync();
    }

    private void updateLastResult(final BlueprintBuildStatus status,
                                  final String message,
                                  final String missing,
                                  final int affectedBlocks) {
        this.lastStatus = status;
        this.lastMessage = message == null ? "" : message;
        this.lastMissing = missing == null ? "" : missing;
        this.lastAffectedBlocks = affectedBlocks;
    }

    private static String describeMissing(final BlueprintBuildStepResult result) {
        if (result.status() != BlueprintBuildStatus.WAITING_FOR_MATERIALS || result.missingCost().isEmpty()) {
            return "";
        }
        return result.missingCost().compact().lines().stream()
                .limit(4)
                .map(BlueprintCannonBlockEntity::describeCostLine)
                .collect(Collectors.joining(", "));
    }

    private static String describeCostLine(final CostLine line) {
        final ItemStack stack = line.stack();
        return stack.getHoverName().getString() + " x" + stack.getCount();
    }

    private void markDirtyAndSync() {
        this.setChanged();
        if (this.level != null && !this.level.isClientSide()) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 2);
        }
    }

    @Override
    protected void saveAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(KEY_INVENTORY, this.inventory.serializeNBT(registries));
        tag.put(KEY_PROGRESS, this.progress.save());
        this.writeStatus(tag, registries);
    }

    @Override
    protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(KEY_INVENTORY)) {
            this.inventory.deserializeNBT(registries, tag.getCompound(KEY_INVENTORY));
        }
        if (tag.contains(KEY_PROGRESS)) {
            this.progress = BlueprintBuildProgress.load(tag.getCompound(KEY_PROGRESS));
        }
        this.readStatus(tag, registries);
        this.cachedBlueprint = null;
        this.cachedBlueprintHash = "";
    }

    @Override
    public CompoundTag getUpdateTag(final HolderLookup.Provider registries) {
        final CompoundTag tag = super.getUpdateTag(registries);
        this.writeStatus(tag, registries);
        if (this.inventory.getStackInSlot(BLUEPRINT_SLOT).isEmpty()) {
            return tag;
        }
        tag.put(KEY_PROGRESS, this.progress.save());
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private void writeStatus(final CompoundTag tag, final HolderLookup.Provider registries) {
        tag.putBoolean(KEY_ACTIVE, this.active);
        tag.putString(KEY_LAST_BLUEPRINT_HASH, this.lastBlueprintHash);
        tag.putString(KEY_LAST_STATUS, this.lastStatus.name());
        tag.putString(KEY_LAST_MESSAGE, this.lastMessage);
        tag.putString(KEY_LAST_MISSING, this.lastMissing);
        tag.putInt(KEY_LAST_AFFECTED_BLOCKS, this.lastAffectedBlocks);
        tag.putInt(KEY_LAST_SOURCE_COUNT, this.lastSourceCount);
        tag.putInt(KEY_PRINT_INTERVAL_TICKS, this.printIntervalTicks);
        tag.putInt(KEY_PRINT_COOLDOWN_TICKS, this.printCooldownTicks);

        final net.minecraft.nbt.ListTag budgetList = new net.minecraft.nbt.ListTag();
        for (final BudgetLine line : this.estimatedBudget) {
            final CompoundTag entry = new CompoundTag();
            entry.put("item", line.item().saveOptional(registries));
            entry.putInt("required", line.required());
            entry.putInt("available", line.available());
            budgetList.add(entry);
        }
        tag.put(KEY_ESTIMATED_BUDGET, budgetList);
    }

    private void readStatus(final CompoundTag tag, final HolderLookup.Provider registries) {
        this.active = tag.getBoolean(KEY_ACTIVE);
        this.lastBlueprintHash = tag.getString(KEY_LAST_BLUEPRINT_HASH);
        try {
            this.lastStatus = BlueprintBuildStatus.valueOf(tag.getString(KEY_LAST_STATUS));
        } catch (final IllegalArgumentException ignored) {
            this.lastStatus = BlueprintBuildStatus.IDLE;
        }
        this.lastMessage = tag.getString(KEY_LAST_MESSAGE);
        this.lastMissing = tag.getString(KEY_LAST_MISSING);
        this.lastAffectedBlocks = tag.getInt(KEY_LAST_AFFECTED_BLOCKS);
        this.lastSourceCount = tag.getInt(KEY_LAST_SOURCE_COUNT);
        this.printIntervalTicks = sanitizePrintInterval(tag.contains(KEY_PRINT_INTERVAL_TICKS) ? tag.getInt(KEY_PRINT_INTERVAL_TICKS) : DEFAULT_PRINT_INTERVAL_TICKS);
        this.printCooldownTicks = Math.max(0, Math.min(tag.getInt(KEY_PRINT_COOLDOWN_TICKS), Math.max(0, this.printIntervalTicks - 1)));

        this.estimatedBudget = List.of();
        if (tag.contains(KEY_ESTIMATED_BUDGET)) {
            final net.minecraft.nbt.ListTag budgetList = tag.getList(KEY_ESTIMATED_BUDGET, net.minecraft.nbt.Tag.TAG_COMPOUND);
            final java.util.List<BudgetLine> lines = new java.util.ArrayList<>(budgetList.size());
            for (int i = 0; i < budgetList.size(); i++) {
                final CompoundTag entry = budgetList.getCompound(i);
                final ItemStack item = ItemStack.parseOptional(registries, entry.getCompound("item"));
                if (item.isEmpty()) {
                    continue;
                }
                lines.add(new BudgetLine(item, entry.getInt("required"), entry.getInt("available")));
            }
            this.estimatedBudget = List.copyOf(lines);
        }
    }

    private static int sanitizePrintInterval(final int ticks) {
        return Math.max(MIN_PRINT_INTERVAL_TICKS, Math.min(MAX_PRINT_INTERVAL_TICKS, ticks));
    }
}
