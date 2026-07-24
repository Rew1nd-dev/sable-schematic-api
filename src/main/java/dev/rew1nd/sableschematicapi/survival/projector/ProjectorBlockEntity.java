package dev.rew1nd.sableschematicapi.survival.projector;

import dev.rew1nd.sableschematicapi.survival.SableSchematicApiBlockEntities;
import dev.rew1nd.sableschematicapi.survival.film.FilmItem;
import dev.rew1nd.sableschematicapi.survival.film.FilmState;
import dev.rew1nd.sableschematicapi.tool.SableSchematicApiItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

public final class ProjectorBlockEntity extends BlockEntity {
    private static final String KEY_INVENTORY = "inventory";
    private static final String KEY_PHASE = "phase";
    private static final String KEY_STATUS = "status";
    private static final String KEY_FILM_NAME = "film_name";
    private static final String KEY_BODY_COUNT = "body_count";
    private static final String KEY_CHECKED = "checked";
    private static final String KEY_TOTAL = "total";
    private static final String KEY_REVEAL_END = "reveal_end";

    private final ItemStackHandler inventory = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(final int slot, final ItemStack stack) {
            return slot == 0
                    && stack.is(SableSchematicApiItems.FILM.get())
                    && FilmItem.state(stack).isRecorded();
        }

        @Override
        public ItemStack insertItem(final int slot, final ItemStack stack, final boolean simulate) {
            return ProjectorBlockEntity.this.phase.busy()
                    ? stack
                    : super.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(final int slot, final int amount, final boolean simulate) {
            return ProjectorBlockEntity.this.phase.busy()
                    ? ItemStack.EMPTY
                    : super.extractItem(slot, amount, simulate);
        }

        @Override
        protected void onContentsChanged(final int slot) {
            if (!ProjectorBlockEntity.this.phase.busy()) {
                ProjectorBlockEntity.this.refreshFilmSummary();
            }
            ProjectorBlockEntity.this.markDirtyAndSync();
        }
    };

    private ProjectorPhase phase = ProjectorPhase.IDLE;
    private String status = "";
    private String filmName = "";
    private int bodyCount;
    private long checked;
    private long total;
    private long revealEnd;
    @Nullable
    private transient ProjectorAdmission admission;
    @Nullable
    private transient ProjectorPlacementHandle placementHandle;

    public ProjectorBlockEntity(final BlockPos pos, final BlockState state) {
        super(SableSchematicApiBlockEntities.PROJECTOR.get(), pos, state);
    }

    public static void serverTick(final Level level,
                                  final BlockPos pos,
                                  final BlockState state,
                                  final ProjectorBlockEntity projector) {
        if (level instanceof final ServerLevel serverLevel) {
            projector.tickServer(serverLevel);
        }
    }

    private void tickServer(final ServerLevel level) {
        if (this.phase == ProjectorPhase.REVEALING) {
            if (this.placementHandle == null || !this.placementHandle.valid(level)) {
                this.stopReveal("sable_schematic_api.projector.status.anchor_moved");
            } else if (level.getGameTime() >= this.revealEnd) {
                this.finishReveal();
            }
        }
    }

    public ItemStackHandler inventory() {
        return this.inventory;
    }

    public IItemHandler automationInventory() {
        return this.inventory;
    }

    public ProjectorPhase phase() {
        return this.phase;
    }

    public String status() {
        return this.status;
    }

    public String filmName() {
        return this.filmName;
    }

    public int bodyCount() {
        return this.bodyCount;
    }

    public long checked() {
        return this.checked;
    }

    public long total() {
        return this.total;
    }

    public long revealEnd() {
        return this.revealEnd;
    }

    public void operate(final ServerPlayer player) {
        if (this.phase.admission()) {
            this.cancelAdmission();
            return;
        }
        if (this.phase == ProjectorPhase.REVEALING) {
            this.stopReveal("sable_schematic_api.projector.status.stopped");
            return;
        }
        this.begin(player);
    }

    private void begin(final ServerPlayer player) {
        if (!(this.level instanceof final ServerLevel serverLevel)) {
            return;
        }
        final ItemStack stack = this.inventory.getStackInSlot(0);
        final FilmState film = FilmItem.state(stack);
        if (!film.isRecorded()) {
            this.fail("sable_schematic_api.projector.status.no_film");
            return;
        }
        this.phase = ProjectorPhase.VALIDATING;
        this.status = "sable_schematic_api.projector.status.validating";
        this.checked = 0;
        this.total = 0;
        this.markDirtyAndSync();
        try {
            this.admission = ProjectorAdmission.begin(serverLevel, this.worldPosition, player, film);
            this.bodyCount = this.admission.blueprint().subLevels().size();
            this.total = this.admission.totalChecks();
            this.phase = ProjectorPhase.SCANNING_TERRAIN;
            this.status = "sable_schematic_api.projector.status.scanning";
            ProjectorTerrainScanScheduler.add(this);
            this.markDirtyAndSync();
        } catch (final ProjectorAdmissionException exception) {
            this.fail(exception.translationKey());
        } catch (final RuntimeException exception) {
            dev.rew1nd.sableschematicapi.SableSchematicApi.LOGGER.error("Projector admission failed", exception);
            this.fail("sable_schematic_api.projector.status.invalid_blueprint");
        }
    }

    int scan(final int budget) {
        if (!(this.level instanceof final ServerLevel level)
                || this.phase != ProjectorPhase.SCANNING_TERRAIN
                || this.admission == null) {
            return 0;
        }
        if (!this.admission.validForPlacement(level, this.worldPosition)) {
            this.fail("sable_schematic_api.projector.status.anchor_moved");
            return 0;
        }
        final int used = this.admission.scanTerrain(level, budget);
        this.checked = this.admission.checked();
        if (this.admission.collided()) {
            this.fail("sable_schematic_api.projector.status.terrain_collision");
        } else if (this.admission.complete()) {
            this.place(level);
        } else if (used > 0) {
            this.markDirtyAndSync();
        }
        return used;
    }

    private void place(final ServerLevel level) {
        final ProjectorAdmission task = this.admission;
        if (task == null) {
            this.fail("sable_schematic_api.projector.status.invalid_blueprint");
            return;
        }
        this.phase = ProjectorPhase.PLACING;
        this.status = "sable_schematic_api.projector.status.placing";
        this.markDirtyAndSync();
        try {
            if (!FilmItem.state(this.inventory.getStackInSlot(0)).equals(task.film())) {
                throw new ProjectorAdmissionException("sable_schematic_api.projector.status.film_changed");
            }
            this.placementHandle = ProjectorPlacementHandle.place(level, this.worldPosition, task);
            if (this.placementHandle.placedBodyIds().isEmpty()) {
                throw new ProjectorAdmissionException("sable_schematic_api.projector.status.no_bodies");
            }
            this.phase = ProjectorPhase.REVEALING;
            this.status = "sable_schematic_api.projector.status.revealing";
            this.revealEnd = level.getGameTime() + this.placementHandle.durationTicks();
            ProjectorRevealService.start(level, this.worldPosition, this.placementHandle, this.revealEnd);
            final ItemStack remaining = this.inventory.getStackInSlot(0).copy();
            remaining.shrink(1);
            this.inventory.setStackInSlot(0, remaining);
            this.admission = null;
            this.markDirtyAndSync();
        } catch (final ProjectorAdmissionException exception) {
            this.rollbackFailedPlacement(level);
            this.fail(exception.translationKey());
        } catch (final RuntimeException exception) {
            this.rollbackFailedPlacement(level);
            dev.rew1nd.sableschematicapi.SableSchematicApi.LOGGER.error("Projector placement failed", exception);
            this.fail("sable_schematic_api.projector.status.place_failed");
        }
    }

    private void rollbackFailedPlacement(final ServerLevel level) {
        if (this.placementHandle == null) {
            return;
        }
        ProjectorRevealGroups.stop(level, this.placementHandle, true);
        this.placementHandle.rollback(level);
        this.placementHandle = null;
    }

    private void finishReveal() {
        if (this.level instanceof final ServerLevel level && this.placementHandle != null) {
            ProjectorRevealService.finish(level, this.placementHandle, false);
        }
        this.placementHandle = null;
        this.phase = ProjectorPhase.COMPLETED;
        this.status = "sable_schematic_api.projector.status.completed";
        this.revealEnd = 0;
        this.markDirtyAndSync();
    }

    private void stopReveal(final String reason) {
        if (this.level instanceof final ServerLevel level && this.placementHandle != null) {
            ProjectorRevealService.finish(level, this.placementHandle, true);
        }
        this.placementHandle = null;
        this.phase = ProjectorPhase.COMPLETED;
        this.status = reason;
        this.revealEnd = 0;
        this.markDirtyAndSync();
    }

    private void cancelAdmission() {
        ProjectorTerrainScanScheduler.remove(this);
        if (this.admission != null) {
            this.admission.close();
        }
        this.admission = null;
        this.phase = ProjectorPhase.IDLE;
        this.status = "sable_schematic_api.projector.status.cancelled";
        this.checked = 0;
        this.total = 0;
        this.markDirtyAndSync();
    }

    private void fail(final String translationKey) {
        ProjectorTerrainScanScheduler.remove(this);
        if (this.admission != null) {
            this.admission.close();
        }
        this.admission = null;
        this.phase = ProjectorPhase.FAILED;
        this.status = translationKey;
        this.markDirtyAndSync();
    }

    public void abortForRemoval() {
        if (this.phase == ProjectorPhase.REVEALING) {
            this.stopReveal("sable_schematic_api.projector.status.stopped");
        } else if (this.phase.admission()) {
            this.cancelAdmission();
        }
    }

    @Override
    public void setRemoved() {
        this.abortForRemoval();
        super.setRemoved();
    }

    public Component statusLine() {
        return Component.translatable(this.status.isBlank()
                ? "sable_schematic_api.projector.status.idle"
                : this.status);
    }

    public Component filmLine() {
        return Component.translatable("sable_schematic_api.projector.ui.film",
                this.filmName.isBlank() ? "-" : this.filmName);
    }

    public Component bodyLine() {
        return Component.translatable("sable_schematic_api.projector.ui.bodies", this.bodyCount);
    }

    public Component progressLine() {
        if (this.phase == ProjectorPhase.SCANNING_TERRAIN) {
            return Component.translatable("sable_schematic_api.projector.ui.scan", this.checked, this.total);
        }
        if (this.phase == ProjectorPhase.REVEALING && this.level != null) {
            final long remaining = Math.max(0L, this.revealEnd - this.level.getGameTime());
            return Component.translatable("sable_schematic_api.projector.ui.reveal_remaining",
                    String.format(java.util.Locale.ROOT, "%.1f", remaining / 20.0D));
        }
        return Component.translatable("sable_schematic_api.projector.ui.phase", this.phase.name());
    }

    private void refreshFilmSummary() {
        final FilmState film = FilmItem.state(this.inventory.getStackInSlot(0));
        this.filmName = film.snapshot().map(snapshot -> snapshot.name()).orElse("");
        this.bodyCount = film.snapshot().map(snapshot -> snapshot.summary().subLevels()).orElse(0);
    }

    private void markDirtyAndSync() {
        this.setChanged();
        if (this.level instanceof final ServerLevel serverLevel) {
            serverLevel.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(KEY_INVENTORY, this.inventory.serializeNBT(registries));
        // Tasks are intentionally runtime-only. Persist only display information and inventory.
        tag.putString(KEY_PHASE, this.phase.busy() ? ProjectorPhase.IDLE.name() : this.phase.name());
        tag.putString(KEY_STATUS, this.phase.busy() ? "" : this.status);
        tag.putString(KEY_FILM_NAME, this.filmName);
        tag.putInt(KEY_BODY_COUNT, this.bodyCount);
    }

    @Override
    protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(KEY_INVENTORY)) {
            this.inventory.deserializeNBT(registries, tag.getCompound(KEY_INVENTORY));
        }
        try {
            this.phase = ProjectorPhase.valueOf(tag.getString(KEY_PHASE));
        } catch (final IllegalArgumentException ignored) {
            this.phase = ProjectorPhase.IDLE;
        }
        this.status = tag.getString(KEY_STATUS);
        this.filmName = tag.getString(KEY_FILM_NAME);
        this.bodyCount = tag.getInt(KEY_BODY_COUNT);
        this.checked = tag.getLong(KEY_CHECKED);
        this.total = tag.getLong(KEY_TOTAL);
        this.revealEnd = tag.getLong(KEY_REVEAL_END);
    }

    @Override
    public CompoundTag getUpdateTag(final HolderLookup.Provider registries) {
        final CompoundTag tag = new CompoundTag();
        tag.putString(KEY_PHASE, this.phase.name());
        tag.putString(KEY_STATUS, this.status);
        tag.putString(KEY_FILM_NAME, this.filmName);
        tag.putInt(KEY_BODY_COUNT, this.bodyCount);
        tag.putLong(KEY_CHECKED, this.checked);
        tag.putLong(KEY_TOTAL, this.total);
        tag.putLong(KEY_REVEAL_END, this.revealEnd);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
