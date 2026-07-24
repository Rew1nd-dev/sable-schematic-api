package dev.rew1nd.sableschematicapi.survival.filmprinter;

import dev.rew1nd.sableschematicapi.blueprint.SableBlueprint;
import dev.rew1nd.sableschematicapi.survival.BlueprintCannonMaterialBudget;
import dev.rew1nd.sableschematicapi.survival.BlueprintPayloads;
import dev.rew1nd.sableschematicapi.survival.BudgetLine;
import dev.rew1nd.sableschematicapi.survival.SableSchematicApiBlockEntities;
import dev.rew1nd.sableschematicapi.survival.film.FilmBlueprintFreezer;
import dev.rew1nd.sableschematicapi.survival.film.FilmItem;
import dev.rew1nd.sableschematicapi.survival.film.FilmState;
import dev.rew1nd.sableschematicapi.compat.create.CreateBlueprintCompat;
import dev.rew1nd.sableschematicapi.survival.manuscript.BlueprintAlias;
import dev.rew1nd.sableschematicapi.survival.manuscript.BlueprintManuscriptItem;
import dev.rew1nd.sableschematicapi.survival.manuscript.BlueprintManuscriptState;
import dev.rew1nd.sableschematicapi.survival.manuscript.BlueprintWorldStore;
import dev.rew1nd.sableschematicapi.tool.SableSchematicApiItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class FilmPrinterBlockEntity extends BlockEntity {
    public static final int MANUSCRIPT_SLOT = 0;
    public static final int INPUT_FILM_SLOT = 1;
    public static final int OUTPUT_FILM_SLOT = 2;
    public static final int CLIPBOARD_SLOT = 3;
    public static final int SLOT_COUNT = 4;
    private static final int QUOTE_WORK_PER_TICK = 4096;
    private static final int RECHECK_INTERVAL = 20;
    private static final Direction[] MATERIAL_DIRECTIONS = {
            Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.DOWN
    };

    private static final String KEY_INVENTORY = "inventory";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_PHASE = "phase";
    private static final String KEY_TASK_NAME = "task_name";
    private static final String KEY_TASK_HASH = "task_hash";
    private static final String KEY_TOTAL_WORK = "total_work";
    private static final String KEY_COMPLETED_WORK = "completed_work";
    private static final String KEY_TASK_FILM = "task_film";
    private static final String KEY_RECEIPT = "receipt";
    private static final String KEY_REFUND = "refund";
    private static final String KEY_FAILURE = "failure";
    private static final String KEY_REFUNDABLE = "refundable";
    private static final String KEY_NEXT_BUDGET = "next_budget";
    private static final String KEY_QUOTE_ISSUE = "quote_issue";
    private static final String KEY_QUOTE_READY = "quote_ready";

    private final ItemStackHandler inventory = new ItemStackHandler(SLOT_COUNT) {
        @Override
        public int getSlotLimit(final int slot) {
            return switch (slot) {
                case MANUSCRIPT_SLOT, CLIPBOARD_SLOT -> 1;
                default -> 64;
            };
        }

        @Override
        public boolean isItemValid(final int slot, final ItemStack stack) {
            return switch (slot) {
                case MANUSCRIPT_SLOT -> isFilledManuscript(stack);
                case INPUT_FILM_SLOT -> isEmptyFilm(stack);
                case OUTPUT_FILM_SLOT -> isRecordedFilm(stack);
                case CLIPBOARD_SLOT -> ModList.get().isLoaded("create")
                        && dev.rew1nd.sableschematicapi.compat.create.CreateBlueprintCompat.isClipboard(stack);
                default -> false;
            };
        }

        @Override
        protected void onContentsChanged(final int slot) {
            FilmPrinterBlockEntity.this.onInventoryChanged(slot);
        }

        @Override
        protected void onLoad() {
            if (this.stacks.size() == SLOT_COUNT) {
                return;
            }
            final List<ItemStack> old = new ArrayList<>(this.stacks);
            this.setSize(SLOT_COUNT);
            for (int i = 0; i < Math.min(old.size(), SLOT_COUNT); i++) {
                this.stacks.set(i, old.get(i));
            }
        }
    };
    private final IItemHandler automationInventory = new AutomationInventory();

    private boolean enabled;
    private FilmPrinterPhase phase = FilmPrinterPhase.IDLE;
    private String taskName = "";
    private String taskHash = "";
    private long totalWork;
    private long completedWork;
    private ItemStack taskFilm = ItemStack.EMPTY;
    private PaymentReceipt receipt = PaymentReceipt.EMPTY;
    private final List<ItemStack> refundQueue = new ArrayList<>();
    private String failure = "";
    private boolean refundable;
    private int recheckTicks;
    private int quoteSourceCheckTicks;
    private boolean paymentCheckDue = true;

    @Nullable
    private BlueprintMaterialQuoteCompiler quoteCompiler;
    @Nullable
    private BlueprintMaterialQuote nextQuote;
    private String quoteSubject = "";
    private List<BudgetLine> nextBudget = List.of();
    private CompoundTag materialSnapshot = new CompoundTag();
    private String materialSnapshotHash = "";

    // Client-only transient cache. It is populated exclusively by the
    // hash-check snapshot payload and is never cleared by ordinary BE updates.
    private transient List<BudgetLine> clientMaterialBudget = List.of();
    private transient String clientMaterialSnapshotHash = "";
    private transient boolean clientQuoteReady;
    private transient String clientQuoteIssue = "";

    public FilmPrinterBlockEntity(final BlockPos pos, final BlockState state) {
        super(SableSchematicApiBlockEntities.FILM_PRINTER.get(), pos, state);
    }

    public static void serverTick(final Level level,
                                  final BlockPos pos,
                                  final BlockState state,
                                  final FilmPrinterBlockEntity printer) {
        if (level instanceof final ServerLevel serverLevel) {
            printer.tickServer(serverLevel);
        }
    }

    public ItemStackHandler inventory() {
        return this.inventory;
    }

    public IItemHandler automationInventory() {
        return this.automationInventory;
    }

    public boolean enabled() {
        return this.enabled;
    }

    public void setEnabled(final boolean enabled) {
        if (this.enabled == enabled) {
            return;
        }
        this.enabled = enabled;
        this.recheckTicks = 0;
        this.paymentCheckDue = true;
        this.markDirtyAndSync();
    }

    public FilmPrinterPhase phase() {
        return this.phase;
    }

    public String taskName() {
        return this.taskName;
    }

    public String taskHash() {
        return this.taskHash;
    }

    public long totalWork() {
        return this.totalWork;
    }

    public long completedWork() {
        return this.completedWork;
    }

    public String failure() {
        return this.failure;
    }

    public List<BudgetLine> nextBudget() {
        return this.level != null && this.level.isClientSide()
                ? this.clientMaterialBudget
                : this.nextBudget;
    }

    public boolean quoteReady() {
        return this.level != null && this.level.isClientSide()
                ? this.clientQuoteReady
                : this.nextQuote != null && this.nextQuote.admitted();
    }

    public String quoteIssue() {
        if (this.level != null && this.level.isClientSide()) {
            return this.clientQuoteIssue;
        }
        return this.nextQuote == null
                ? ""
                : this.nextQuote.issue().map(issue -> issue.message().getString()).orElse("");
    }

    public List<ItemStack> refundQueue() {
        return this.refundQueue.stream().map(ItemStack::copy).toList();
    }

    public boolean isPausedBySignal() {
        return this.level != null && this.level.hasNeighborSignal(this.worldPosition);
    }

    public boolean canRequestRefund() {
        return this.refundable && ((this.phase == FilmPrinterPhase.MANUFACTURING
                && (!this.enabled || this.isPausedBySignal()))
                || this.phase == FilmPrinterPhase.FAILED);
    }

    public boolean requestRefund() {
        if (!this.canRequestRefund()) {
            return false;
        }
        for (final ItemStack item : this.receipt.items()) {
            PaymentReceipt.merge(this.refundQueue, item);
        }
        PaymentReceipt.merge(this.refundQueue, new ItemStack(SableSchematicApiItems.FILM.get()));
        this.clearTask();
        this.phase = FilmPrinterPhase.REFUNDING;
        this.enabled = false;
        this.failure = "";
        this.recheckTicks = 0;
        this.markDirtyAndSync();
        return true;
    }

    public void forceMaterialSnapshotRefresh(final ServerLevel level) {
        this.recheckTicks = RECHECK_INTERVAL;
        this.paymentCheckDue = true;
        if (this.nextQuote != null && this.nextQuote.admitted()) {
            this.refreshBudget(level);
        } else {
            this.quoteSourceCheckTicks = 0;
        }
        this.ensureMaterialSnapshotPublished(level.registryAccess());
    }

    public String clientMaterialSnapshotHash() {
        return this.clientMaterialSnapshotHash;
    }

    public String materialSnapshotHash() {
        return this.materialSnapshotHash;
    }

    public CompoundTag materialSnapshot() {
        return this.materialSnapshot.copy();
    }

    public void ensureMaterialSnapshotPublished(final HolderLookup.Provider registries) {
        if (this.materialSnapshotHash.isBlank()) {
            this.publishMaterialSnapshot(registries);
        }
    }

    public void acceptClientMaterialSnapshot(final String hash, final CompoundTag snapshot) {
        if (this.level == null || !this.level.isClientSide() || hash == null || hash.isBlank()) {
            return;
        }
        this.clientMaterialBudget = readBudget(snapshot, this.level.registryAccess());
        this.clientQuoteReady = snapshot.getBoolean(KEY_QUOTE_READY);
        this.clientQuoteIssue = snapshot.getString(KEY_QUOTE_ISSUE);
        this.clientMaterialSnapshotHash = hash;
    }

    public void writeBudgetToClipboard() {
        if (this.level == null || this.level.isClientSide()
                || this.phase == FilmPrinterPhase.REFUNDING
                || !ModList.get().isLoaded("create")) {
            return;
        }
        final ItemStack clipboard = this.inventory.getStackInSlot(CLIPBOARD_SLOT);
        if (clipboard.isEmpty() || !CreateBlueprintCompat.isClipboard(clipboard)) {
            return;
        }
        if (CreateBlueprintCompat.writeBudgetToClipboard(clipboard, this.nextBudget)) {
            this.inventory.setStackInSlot(CLIPBOARD_SLOT, clipboard);
            this.markDirtyAndSync();
        }
    }

    public Component statusLine() {
        if (this.phase == FilmPrinterPhase.REFUNDING) {
            return Component.translatable("sable_schematic_api.film_printer.ui.refunding");
        }
        if (this.phase == FilmPrinterPhase.OUTPUT_BLOCKED) {
            return Component.translatable("sable_schematic_api.film_printer.ui.output_blocked");
        }
        if (this.phase == FilmPrinterPhase.FAILED) {
            return Component.translatable("sable_schematic_api.film_printer.ui.failed", this.failure);
        }
        if (!this.enabled) {
            return Component.translatable("sable_schematic_api.film_printer.ui.paused");
        }
        if (this.isPausedBySignal()) {
            return Component.translatable("sable_schematic_api.film_printer.ui.redstone_paused");
        }
        return this.phase == FilmPrinterPhase.MANUFACTURING
                ? Component.translatable("sable_schematic_api.film_printer.ui.manufacturing")
                : Component.translatable("sable_schematic_api.film_printer.ui.ready");
    }

    public Component taskLine() {
        return this.taskName.isBlank()
                ? Component.translatable("sable_schematic_api.film_printer.ui.no_task")
                : Component.translatable("sable_schematic_api.film_printer.ui.task", this.taskName);
    }

    public Component hashLine() {
        return this.taskHash.isBlank()
                ? Component.translatable("sable_schematic_api.film_printer.ui.hash_none")
                : Component.translatable(
                        "sable_schematic_api.film_printer.ui.hash",
                        this.taskHash.substring(0, Math.min(12, this.taskHash.length()))
                );
    }

    public Component progressLine() {
        if (this.taskName.isBlank()) {
            return Component.translatable("sable_schematic_api.film_printer.ui.progress_none");
        }
        return Component.translatable(
                "sable_schematic_api.film_printer.ui.progress",
                this.completedWork,
                Math.max(1L, this.totalWork)
        );
    }

    public Component nextLine() {
        final BlueprintManuscriptState state =
                BlueprintManuscriptItem.state(this.inventory.getStackInSlot(MANUSCRIPT_SLOT));
        if (!state.isFilled()) {
            return Component.translatable("sable_schematic_api.film_printer.ui.next_none");
        }
        final String name = state.alias().orElseThrow().blueprintName();
        if (!this.quoteIssue().isBlank()) {
            return Component.translatable(
                    "sable_schematic_api.film_printer.ui.next_rejected",
                    name,
                    this.quoteIssue()
            );
        }
        return Component.translatable(
                this.quoteReady()
                        ? "sable_schematic_api.film_printer.ui.next_ready"
                        : "sable_schematic_api.film_printer.ui.next_quoting",
                name
        );
    }

    private void tickServer(final ServerLevel level) {
        this.updateVisualState(level);
        if (this.phase == FilmPrinterPhase.REFUNDING) {
            if (this.recheckTicks-- <= 0) {
                this.recheckTicks = RECHECK_INTERVAL;
                this.pushRefunds(level);
            }
            return;
        }
        if (this.phase == FilmPrinterPhase.OUTPUT_BLOCKED) {
            this.tryDepositOutput(level);
            return;
        }

        if (this.recheckTicks-- <= 0) {
            this.recheckTicks = RECHECK_INTERVAL;
            this.paymentCheckDue = true;
            this.refreshBudget(level);
        }

        final boolean paused = !this.enabled || level.hasNeighborSignal(this.worldPosition);
        if (!paused) {
            this.advanceQuote(level);
            if (this.phase == FilmPrinterPhase.MANUFACTURING) {
                this.advanceManufacturing(level);
            } else if (this.phase == FilmPrinterPhase.IDLE) {
                this.tryStartTask(level);
            }
        }

    }

    private void advanceQuote(final ServerLevel level) {
        if (this.quoteCompiler != null) {
            this.advanceActiveQuote(level);
            return;
        }
        final BlueprintManuscriptState manuscript =
                BlueprintManuscriptItem.state(this.inventory.getStackInSlot(MANUSCRIPT_SLOT));
        if (!manuscript.isFilled()) {
            if (this.quoteCompiler != null || this.nextQuote != null || !this.quoteSubject.isBlank()) {
                this.invalidateQuote();
                this.publishMaterialSnapshot(level.registryAccess());
                this.markDirtyAndSync();
            }
            return;
        }
        if (this.nextQuote != null && this.quoteSourceCheckTicks-- > 0) {
            return;
        }

        final BlueprintAlias alias = manuscript.alias().orElseThrow();
        final String currentSha;
        try {
            currentSha = BlueprintWorldStore.currentSha256(level.getServer(), alias);
        } catch (final IOException exception) {
            this.finishRejectedQuote("source_unavailable", exception.getMessage());
            this.quoteSourceCheckTicks = RECHECK_INTERVAL;
            return;
        }
        final String subject = currentSha + ":" + manuscript.rotation().degrees();
        if (currentSha.isBlank()) {
            this.finishRejectedQuote("source_missing", "The manuscript blueprint no longer exists.");
            this.quoteSubject = subject;
            this.quoteSourceCheckTicks = RECHECK_INTERVAL;
            return;
        }
        this.quoteSourceCheckTicks = RECHECK_INTERVAL;
        if (!Objects.equals(subject, this.quoteSubject)) {
            this.invalidateQuote();
            this.quoteSubject = subject;
            try {
                final CompoundTag root = BlueprintWorldStore.readRoot(
                        BlueprintWorldStore.uploadPath(level.getServer(), alias.ownerId(), alias.blueprintName()));
                final SableBlueprint blueprint = SableBlueprint.loadWithDiagnostics(root).blueprint();
                this.quoteCompiler = new BlueprintMaterialQuoteCompiler(level, this.worldPosition, blueprint);
            } catch (final IOException | RuntimeException exception) {
                this.finishRejectedQuote("source_invalid", exception.getMessage());
                return;
            }
        }

        this.advanceActiveQuote(level);
    }

    private void advanceActiveQuote(final ServerLevel level) {
        if (this.quoteCompiler == null) {
            return;
        }
        final BlueprintMaterialQuoteCompiler.StepResult step =
                this.quoteCompiler.step(QUOTE_WORK_PER_TICK);
        if (step.done()) {
            this.nextQuote = this.quoteCompiler.result();
            this.quoteCompiler = null;
            this.paymentCheckDue = true;
            this.refreshBudget(level);
            this.markDirtyAndSync();
        }
    }

    private void tryStartTask(final ServerLevel level) {
        if (!this.paymentCheckDue || this.nextQuote == null || !this.nextQuote.admitted()) {
            return;
        }
        this.paymentCheckDue = false;
        final ItemStack manuscriptStack = this.inventory.getStackInSlot(MANUSCRIPT_SLOT);
        final BlueprintManuscriptState manuscript = BlueprintManuscriptItem.state(manuscriptStack);
        if (!manuscript.isFilled() || !this.hasEmptyFilm()) {
            return;
        }
        final BlueprintAlias alias = manuscript.alias().orElseThrow();
        try {
            final String currentSha = BlueprintWorldStore.currentSha256(level.getServer(), alias);
            final String subject = currentSha + ":" + manuscript.rotation().degrees();
            if (!Objects.equals(subject, this.quoteSubject)) {
                this.invalidateQuote();
                this.paymentCheckDue = true;
                return;
            }
            final FilmBlueprintFreezer.FrozenBlueprint frozen =
                    FilmBlueprintFreezer.freeze(level.getServer(), manuscript);
            final ItemStack output = frozen.createFilmStack();
            if (!this.canAcceptOutput(output)) {
                return;
            }
            if (!FilmPrinterPayment.canAfford(level, this.worldPosition, this.nextQuote)) {
                return;
            }
            final FilmPrinterPayment.Result payment =
                    FilmPrinterPayment.consume(level, this.worldPosition, this.nextQuote);
            if (payment.status() != FilmPrinterPayment.Status.SUCCESS) {
                if (payment.status() == FilmPrinterPayment.Status.PARTIAL_FAILURE) {
                    for (final ItemStack item : payment.receipt().items()) {
                        PaymentReceipt.merge(this.refundQueue, item);
                    }
                    this.phase = FilmPrinterPhase.REFUNDING;
                    this.enabled = false;
                }
                this.markDirtyAndSync();
                return;
            }

            final ItemStack consumedFilm = this.inventory.extractItem(INPUT_FILM_SLOT, 1, false);
            if (!isEmptyFilm(consumedFilm)) {
                for (final ItemStack item : payment.receipt().items()) {
                    PaymentReceipt.merge(this.refundQueue, item);
                }
                this.phase = FilmPrinterPhase.REFUNDING;
                this.enabled = false;
                this.markDirtyAndSync();
                return;
            }

            this.phase = FilmPrinterPhase.MANUFACTURING;
            this.taskName = alias.blueprintName();
            this.taskHash = frozen.filmState().objectSha256().orElse("");
            this.totalWork = this.nextQuote.workUnits();
            this.completedWork = 0L;
            this.taskFilm = output;
            this.receipt = payment.receipt();
            this.refundable = true;
            this.failure = "";
            this.invalidateQuote();
            this.recheckTicks = 0;
            this.paymentCheckDue = true;
            this.markDirtyAndSync();
        } catch (final IOException | RuntimeException exception) {
            this.failure = exception.getMessage() == null ? "Failed to freeze blueprint." : exception.getMessage();
            this.markDirtyAndSync();
        }
    }

    private void advanceManufacturing(final ServerLevel level) {
        final long target = Math.max(1L, this.totalWork);
        this.completedWork = Math.min(target, this.completedWork + FilmPrinterConfig.BLOCKS_PER_TICK.get());
        this.setChanged();
        if (this.completedWork < target) {
            return;
        }
        this.phase = FilmPrinterPhase.OUTPUT_BLOCKED;
        this.receipt = PaymentReceipt.EMPTY;
        this.refundable = false;
        level.playSound(
                null,
                this.worldPosition,
                SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT,
                SoundSource.BLOCKS,
                0.8F,
                1.0F
        );
        this.tryDepositOutput(level);
        this.markDirtyAndSync();
    }

    private void tryDepositOutput(final ServerLevel level) {
        if (this.taskFilm.isEmpty()) {
            this.phase = FilmPrinterPhase.FAILED;
            this.failure = "Recorded film output is missing.";
            this.markDirtyAndSync();
            return;
        }
        if (!this.canAcceptOutput(this.taskFilm)) {
            return;
        }
        final ItemStack remainder =
                this.inventory.insertItem(OUTPUT_FILM_SLOT, this.taskFilm.copy(), false);
        if (!remainder.isEmpty()) {
            return;
        }
        this.clearTask();
        this.phase = FilmPrinterPhase.IDLE;
        this.failure = "";
        this.recheckTicks = 0;
        this.paymentCheckDue = true;
        this.markDirtyAndSync();
    }

    private void pushRefunds(final ServerLevel level) {
        boolean changed = false;
        for (int queueIndex = 0; queueIndex < this.refundQueue.size();) {
            ItemStack remainder = this.refundQueue.get(queueIndex);
            for (final Direction direction : MATERIAL_DIRECTIONS) {
                if (remainder.isEmpty()) {
                    break;
                }
                final BlockPos targetPos = this.worldPosition.relative(direction);
                final BlockState state = level.getBlockState(targetPos);
                final BlockEntity blockEntity = level.getBlockEntity(targetPos);
                final Direction side = direction == Direction.DOWN ? Direction.UP : direction.getOpposite();
                final IItemHandler handler =
                        Capabilities.ItemHandler.BLOCK.getCapability(level, targetPos, state, blockEntity, side);
                if (handler != null) {
                    remainder = ItemHandlerHelper.insertItem(handler, remainder, false);
                }
            }
            if (remainder.isEmpty()) {
                this.refundQueue.remove(queueIndex);
                changed = true;
            } else {
                if (remainder.getCount() != this.refundQueue.get(queueIndex).getCount()) {
                    this.refundQueue.set(queueIndex, remainder);
                    changed = true;
                }
                queueIndex++;
            }
        }
        if (this.refundQueue.isEmpty()) {
            this.phase = FilmPrinterPhase.IDLE;
            this.failure = "";
            changed = true;
        }
        if (changed) {
            this.markDirtyAndSync();
        }
    }

    private void refreshBudget(final ServerLevel level) {
        if (this.nextQuote == null || !this.nextQuote.admitted()) {
            return;
        }
        final List<BudgetLine> updated = new ArrayList<>();
        for (final var line : this.nextQuote.lines()) {
            final ItemStack required = line.stack();
            final BlueprintCannonMaterialBudget.Availability availability =
                    BlueprintCannonMaterialBudget.availability(
                            level, this.worldPosition, required.copyWithCount(1));
            updated.add(new BudgetLine(
                    required.copyWithCount(1),
                    required.getCount(),
                    availability.available(),
                    availability.unlimited()
            ));
        }
        final List<BudgetLine> snapshot = List.copyOf(updated);
        if (!budgetEquals(this.nextBudget, snapshot)) {
            this.nextBudget = snapshot;
        }
        this.publishMaterialSnapshot(level.registryAccess());
    }

    private void finishRejectedQuote(final String code, @Nullable final String message) {
        final String detail = message == null || message.isBlank() ? "Blueprint quote failed." : message;
        this.nextQuote = BlueprintMaterialQuote.rejected(BlueprintQuoteIssue.of(code, detail));
        this.quoteCompiler = null;
        this.nextBudget = List.of();
        if (this.level instanceof final ServerLevel serverLevel) {
            this.publishMaterialSnapshot(serverLevel.registryAccess());
        }
        this.markDirtyAndSync();
    }

    private void invalidateQuote() {
        this.quoteCompiler = null;
        this.nextQuote = null;
        this.quoteSubject = "";
        this.nextBudget = List.of();
        this.quoteSourceCheckTicks = 0;
    }

    private void publishMaterialSnapshot(final HolderLookup.Provider registries) {
        final CompoundTag snapshot = new CompoundTag();
        snapshot.putString("subject", this.quoteSubject);
        snapshot.putBoolean(KEY_QUOTE_READY, this.nextQuote != null && this.nextQuote.admitted());
        snapshot.putString(
                KEY_QUOTE_ISSUE,
                this.nextQuote == null
                        ? ""
                        : this.nextQuote.issue().map(issue -> issue.message().getString()).orElse("")
        );
        snapshot.put(KEY_NEXT_BUDGET, writeBudget(this.nextBudget, registries));
        this.materialSnapshot = snapshot;
        this.materialSnapshotHash = hashSnapshot(snapshot);
    }

    private static String hashSnapshot(final CompoundTag snapshot) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            NbtIo.writeCompressed(snapshot, output);
            return BlueprintPayloads.hashString(BlueprintPayloads.sha256(output.toByteArray()));
        } catch (final IOException exception) {
            throw new IllegalStateException("Failed to hash film-printer material snapshot.", exception);
        }
    }

    private static ListTag writeBudget(final List<BudgetLine> budget,
                                       final HolderLookup.Provider registries) {
        final ListTag list = new ListTag();
        for (final BudgetLine line : budget) {
            final CompoundTag entry = new CompoundTag();
            entry.put("item", line.item().saveOptional(registries));
            entry.putInt("required", line.required());
            entry.putInt("available", line.available());
            entry.putBoolean("unlimited", line.unlimited());
            list.add(entry);
        }
        return list;
    }

    private static List<BudgetLine> readBudget(final CompoundTag snapshot,
                                               final HolderLookup.Provider registries) {
        final ListTag budget = snapshot.getList(KEY_NEXT_BUDGET, Tag.TAG_COMPOUND);
        final List<BudgetLine> lines = new ArrayList<>(budget.size());
        for (int i = 0; i < budget.size(); i++) {
            final CompoundTag entry = budget.getCompound(i);
            final ItemStack item = ItemStack.parseOptional(registries, entry.getCompound("item"));
            if (!item.isEmpty()) {
                lines.add(new BudgetLine(
                        item,
                        entry.getInt("required"),
                        entry.getInt("available"),
                        entry.getBoolean("unlimited")
                ));
            }
        }
        return List.copyOf(lines);
    }

    private boolean hasEmptyFilm() {
        return isEmptyFilm(this.inventory.getStackInSlot(INPUT_FILM_SLOT));
    }

    private boolean canAcceptOutput(final ItemStack output) {
        final ItemStack existing = this.inventory.getStackInSlot(OUTPUT_FILM_SLOT);
        return existing.isEmpty()
                || (ItemStack.isSameItemSameComponents(existing, output)
                && existing.getCount() < existing.getMaxStackSize());
    }

    private void clearTask() {
        this.taskName = "";
        this.taskHash = "";
        this.totalWork = 0L;
        this.completedWork = 0L;
        this.taskFilm = ItemStack.EMPTY;
        this.receipt = PaymentReceipt.EMPTY;
        this.refundable = false;
    }

    public boolean hasPortableContents() {
        for (int slot = 0; slot < this.inventory.getSlots(); slot++) {
            if (!this.inventory.getStackInSlot(slot).isEmpty()) {
                return true;
            }
        }
        return this.phase != FilmPrinterPhase.IDLE
                || !this.taskFilm.isEmpty()
                || !this.receipt.items().isEmpty()
                || !this.refundQueue.isEmpty();
    }

    public CompoundTag savePortableData(final HolderLookup.Provider registries) {
        final CompoundTag tag = new CompoundTag();
        this.saveAdditional(tag, registries);
        tag.putBoolean(KEY_ENABLED, false);
        return tag;
    }

    public void restorePortableData(final CompoundTag tag, final HolderLookup.Provider registries) {
        final CompoundTag portable = tag.copy();
        portable.putBoolean(KEY_ENABLED, false);
        this.loadAdditional(portable, registries);
        this.enabled = false;
        this.markDirtyAndSync();
    }

    public FilmPrinterItemSummary itemSummary() {
        boolean hasInventory = false;
        for (int slot = 0; slot < this.inventory.getSlots(); slot++) {
            if (!this.inventory.getStackInSlot(slot).isEmpty()) {
                hasInventory = true;
                break;
            }
        }
        long refundItems = 0L;
        for (final ItemStack item : this.refundQueue) {
            refundItems = Math.min(Integer.MAX_VALUE, refundItems + item.getCount());
        }
        return new FilmPrinterItemSummary(
                this.taskName,
                this.phase.name(),
                this.completedWork,
                this.totalWork,
                hasInventory,
                (int) refundItems
        );
    }

    private void onInventoryChanged(final int slot) {
        if (slot == MANUSCRIPT_SLOT) {
            this.invalidateQuote();
            if (this.level instanceof final ServerLevel serverLevel
                    && !isFilledManuscript(this.inventory.getStackInSlot(MANUSCRIPT_SLOT))) {
                this.publishMaterialSnapshot(serverLevel.registryAccess());
            }
        }
        this.recheckTicks = 0;
        this.paymentCheckDue = true;
        this.updateVisualState(this.level);
        this.markDirtyAndSync();
    }

    private void updateVisualState(@Nullable final Level level) {
        if (level == null) {
            return;
        }
        final BlockState state = this.getBlockState();
        if (!(state.getBlock() instanceof FilmPrinterBlock)) {
            return;
        }
        final boolean book = isFilledManuscript(this.inventory.getStackInSlot(MANUSCRIPT_SLOT));
        final boolean tape = isRecordedFilm(this.inventory.getStackInSlot(OUTPUT_FILM_SLOT));
        if (state.getValue(FilmPrinterBlock.BOOK) != book || state.getValue(FilmPrinterBlock.TAPE) != tape) {
            level.setBlock(
                    this.worldPosition,
                    state.setValue(FilmPrinterBlock.BOOK, book).setValue(FilmPrinterBlock.TAPE, tape),
                    2
            );
        }
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
        tag.putBoolean(KEY_ENABLED, this.enabled);
        tag.putString(KEY_PHASE, this.phase.name());
        tag.putString(KEY_TASK_NAME, this.taskName);
        tag.putString(KEY_TASK_HASH, this.taskHash);
        tag.putLong(KEY_TOTAL_WORK, this.totalWork);
        tag.putLong(KEY_COMPLETED_WORK, this.completedWork);
        tag.put(KEY_TASK_FILM, this.taskFilm.saveOptional(registries));
        tag.put(KEY_RECEIPT, this.receipt.save(registries));
        tag.putString(KEY_FAILURE, this.failure);
        tag.putBoolean(KEY_REFUNDABLE, this.refundable);
        final ListTag refund = new ListTag();
        for (final ItemStack item : this.refundQueue) {
            refund.add(item.saveOptional(registries));
        }
        tag.put(KEY_REFUND, refund);
    }

    @Override
    protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(KEY_INVENTORY)) {
            this.inventory.deserializeNBT(registries, tag.getCompound(KEY_INVENTORY));
        }
        this.enabled = tag.getBoolean(KEY_ENABLED);
        try {
            this.phase = FilmPrinterPhase.valueOf(tag.getString(KEY_PHASE));
        } catch (final IllegalArgumentException exception) {
            this.phase = FilmPrinterPhase.IDLE;
        }
        this.taskName = tag.getString(KEY_TASK_NAME);
        this.taskHash = tag.getString(KEY_TASK_HASH);
        this.totalWork = Math.max(0L, tag.getLong(KEY_TOTAL_WORK));
        this.completedWork = Math.max(0L, tag.getLong(KEY_COMPLETED_WORK));
        this.taskFilm = ItemStack.parseOptional(registries, tag.getCompound(KEY_TASK_FILM));
        this.receipt = PaymentReceipt.load(tag.getCompound(KEY_RECEIPT), registries);
        this.failure = tag.getString(KEY_FAILURE);
        this.refundable = tag.getBoolean(KEY_REFUNDABLE);
        this.refundQueue.clear();
        final ListTag refund = tag.getList(KEY_REFUND, Tag.TAG_COMPOUND);
        for (int i = 0; i < refund.size(); i++) {
            final ItemStack item = ItemStack.parseOptional(registries, refund.getCompound(i));
            PaymentReceipt.merge(this.refundQueue, item);
        }
        this.invalidateQuote();
        this.recheckTicks = 0;
        this.paymentCheckDue = true;
    }

    @Override
    public CompoundTag getUpdateTag(final HolderLookup.Provider registries) {
        final CompoundTag tag = super.getUpdateTag(registries);
        tag.putBoolean(KEY_ENABLED, this.enabled);
        tag.putString(KEY_PHASE, this.phase.name());
        tag.putString(KEY_TASK_NAME, this.taskName);
        tag.putString(KEY_TASK_HASH, this.taskHash);
        tag.putLong(KEY_TOTAL_WORK, this.totalWork);
        tag.putLong(KEY_COMPLETED_WORK, this.completedWork);
        tag.putString(KEY_FAILURE, this.failure);
        tag.putBoolean(KEY_REFUNDABLE, this.refundable);
        tag.put(KEY_INVENTORY, this.inventory.serializeNBT(registries));
        final ListTag refund = new ListTag();
        for (final ItemStack item : this.refundQueue) {
            refund.add(item.saveOptional(registries));
        }
        tag.put(KEY_REFUND, refund);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private static boolean budgetEquals(final List<BudgetLine> left, final List<BudgetLine> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int i = 0; i < left.size(); i++) {
            final BudgetLine a = left.get(i);
            final BudgetLine b = right.get(i);
            if (a.required() != b.required()
                    || a.available() != b.available()
                    || a.unlimited() != b.unlimited()
                    || !ItemStack.isSameItemSameComponents(a.item(), b.item())) {
                return false;
            }
        }
        return true;
    }

    private static boolean isFilledManuscript(final ItemStack stack) {
        return stack.is(SableSchematicApiItems.BLUEPRINT_MANUSCRIPT.get())
                && BlueprintManuscriptItem.state(stack).isFilled();
    }

    private static boolean isEmptyFilm(final ItemStack stack) {
        return stack.is(SableSchematicApiItems.FILM.get()) && !FilmItem.state(stack).isRecorded();
    }

    private static boolean isRecordedFilm(final ItemStack stack) {
        return stack.is(SableSchematicApiItems.FILM.get()) && FilmItem.state(stack).isRecorded();
    }

    private final class AutomationInventory implements IItemHandler {
        @Override
        public int getSlots() {
            return 3;
        }

        @Override
        public ItemStack getStackInSlot(final int slot) {
            return inventory.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(final int slot, final ItemStack stack, final boolean simulate) {
            if (slot == OUTPUT_FILM_SLOT) {
                return stack;
            }
            return inventory.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(final int slot, final int amount, final boolean simulate) {
            if (slot != OUTPUT_FILM_SLOT) {
                return ItemStack.EMPTY;
            }
            return inventory.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(final int slot) {
            return inventory.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(final int slot, final ItemStack stack) {
            return slot != OUTPUT_FILM_SLOT && inventory.isItemValid(slot, stack);
        }
    }
}
