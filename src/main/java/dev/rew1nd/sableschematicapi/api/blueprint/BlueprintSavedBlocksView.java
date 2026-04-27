package dev.rew1nd.sableschematicapi.api.blueprint;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Immutable query view over blocks already written into a blueprint.
 *
 * <p>Global save events can use this view to discover saved block references
 * after block and block entity mappers have run.</p>
 */
public final class BlueprintSavedBlocksView {
    private final List<BlueprintSavedBlock> blocks;

    BlueprintSavedBlocksView(final List<BlueprintSavedBlock> blocks) {
        this.blocks = List.copyOf(blocks);
    }

    /**
     * Returns all saved blocks in export order.
     *
     * @return all saved blocks in export order
     */
    public List<BlueprintSavedBlock> blocks() {
        return this.blocks;
    }

    /**
     * Finds saved blocks whose state belongs to the given block.
     *
     * @param block block to match
     * @return matching saved blocks
     */
    public List<BlueprintSavedBlock> blocksOf(final Block block) {
        return this.statesMatching(state -> state.is(block));
    }

    /**
     * Finds saved blocks of the given block inside one blueprint sub-level.
     *
     * @param blueprintSubLevelId blueprint-local sub-level id
     * @param block               block to match
     * @return matching saved blocks
     */
    public List<BlueprintSavedBlock> blocksOf(final int blueprintSubLevelId, final Block block) {
        return this.blocksMatching(savedBlock -> savedBlock.blueprintSubLevelId() == blueprintSubLevelId && savedBlock.state().is(block));
    }

    /**
     * Finds saved blocks inside one blueprint-local sub-level id.
     *
     * @param blueprintSubLevelId blueprint-local sub-level id
     * @return matching saved blocks
     */
    public List<BlueprintSavedBlock> blocksInSubLevel(final int blueprintSubLevelId) {
        return this.blocksMatching(block -> block.blueprintSubLevelId() == blueprintSubLevelId);
    }

    /**
     * Finds saved blocks originating from one source sub-level UUID.
     *
     * @param sourceSubLevelUuid source sub-level UUID at save time
     * @return matching saved blocks
     */
    public List<BlueprintSavedBlock> blocksInSubLevel(final UUID sourceSubLevelUuid) {
        return this.blocksMatching(block -> block.sourceSubLevelUuid().equals(sourceSubLevelUuid));
    }

    /**
     * Filters saved blocks with a custom predicate.
     *
     * @param predicate block predicate
     * @return matching saved blocks
     */
    public List<BlueprintSavedBlock> blocksMatching(final Predicate<BlueprintSavedBlock> predicate) {
        return this.blocks.stream()
                .filter(predicate)
                .toList();
    }

    /**
     * Filters saved blocks by block state.
     *
     * @param predicate block state predicate
     * @return matching saved blocks
     */
    public List<BlueprintSavedBlock> statesMatching(final Predicate<BlockState> predicate) {
        return this.blocks.stream()
                .filter(block -> predicate.test(block.state()))
                .toList();
    }

    /**
     * Returns saved blocks that have stored block entity payloads.
     *
     * @return saved blocks that have stored block entity payloads
     */
    public List<BlueprintSavedBlock> blockEntities() {
        return this.blocks.stream()
                .filter(BlueprintSavedBlock::hasBlockEntityData)
                .toList();
    }

    /**
     * Finds saved blocks whose runtime block entity has the given type.
     *
     * @param type block entity type to match
     * @return matching saved blocks
     */
    public List<BlueprintSavedBlock> blockEntitiesOf(final BlockEntityType<?> type) {
        return this.blocks.stream()
                .filter(block -> block.blockEntity() != null && block.blockEntity().getType() == type)
                .toList();
    }
}
