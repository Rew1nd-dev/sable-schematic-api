package dev.rew1nd.sableschematicapi.api.blueprint;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Immutable query view over blocks placed from a blueprint.
 *
 * <p>Global placement events can use this view after block states are placed.
 * Block entity instances are attached after block entity loading completes.</p>
 */
public final class BlueprintPlacedBlocksView {
    private final List<BlueprintPlacedBlock> blocks;

    BlueprintPlacedBlocksView(final List<BlueprintPlacedBlock> blocks) {
        this.blocks = List.copyOf(blocks);
    }

    /**
     * Returns all placed blocks in placement order.
     *
     * @return all placed blocks in placement order
     */
    public List<BlueprintPlacedBlock> blocks() {
        return this.blocks;
    }

    /**
     * Finds placed blocks whose state belongs to the given block.
     *
     * @param block block to match
     * @return matching placed blocks
     */
    public List<BlueprintPlacedBlock> blocksOf(final Block block) {
        return this.statesMatching(state -> state.is(block));
    }

    /**
     * Finds placed blocks of the given block inside one blueprint sub-level.
     *
     * @param blueprintSubLevelId blueprint-local sub-level id
     * @param block               block to match
     * @return matching placed blocks
     */
    public List<BlueprintPlacedBlock> blocksOf(final int blueprintSubLevelId, final Block block) {
        return this.blocksMatching(placedBlock -> placedBlock.blueprintSubLevelId() == blueprintSubLevelId && placedBlock.state().is(block));
    }

    /**
     * Finds placed blocks inside one blueprint-local sub-level id.
     *
     * @param blueprintSubLevelId blueprint-local sub-level id
     * @return matching placed blocks
     */
    public List<BlueprintPlacedBlock> blocksInSubLevel(final int blueprintSubLevelId) {
        return this.blocksMatching(block -> block.blueprintSubLevelId() == blueprintSubLevelId);
    }

    /**
     * Finds placed blocks that originated from one source sub-level UUID.
     *
     * @param sourceSubLevelUuid source sub-level UUID at save time
     * @return matching placed blocks
     */
    public List<BlueprintPlacedBlock> blocksInSubLevel(final UUID sourceSubLevelUuid) {
        return this.blocksMatching(block -> block.sourceSubLevelUuid().equals(sourceSubLevelUuid));
    }

    /**
     * Finds placed blocks inside one newly allocated placed sub-level UUID.
     *
     * @param placedSubLevelUuid placed sub-level UUID
     * @return matching placed blocks
     */
    public List<BlueprintPlacedBlock> blocksInPlacedSubLevel(final UUID placedSubLevelUuid) {
        return this.blocksMatching(block -> block.placedSubLevelUuid().equals(placedSubLevelUuid));
    }

    /**
     * Filters placed blocks with a custom predicate.
     *
     * @param predicate block predicate
     * @return matching placed blocks
     */
    public List<BlueprintPlacedBlock> blocksMatching(final Predicate<BlueprintPlacedBlock> predicate) {
        return this.blocks.stream()
                .filter(predicate)
                .toList();
    }

    /**
     * Filters placed blocks by block state.
     *
     * @param predicate block state predicate
     * @return matching placed blocks
     */
    public List<BlueprintPlacedBlock> statesMatching(final Predicate<BlockState> predicate) {
        return this.blocks.stream()
                .filter(block -> predicate.test(block.state()))
                .toList();
    }

    /**
     * Returns placed blocks that have stored block entity payloads.
     *
     * @return placed blocks that have stored block entity payloads
     */
    public List<BlueprintPlacedBlock> blockEntities() {
        return this.blocks.stream()
                .filter(BlueprintPlacedBlock::hasBlockEntityData)
                .toList();
    }

    /**
     * Finds placed blocks whose loaded block entity has the given type.
     *
     * @param type block entity type to match
     * @return matching placed blocks
     */
    public List<BlueprintPlacedBlock> blockEntitiesOf(final BlockEntityType<?> type) {
        return this.blocks.stream()
                .filter(block -> block.blockEntity() != null && block.blockEntity().getType() == type)
                .toList();
    }
}
