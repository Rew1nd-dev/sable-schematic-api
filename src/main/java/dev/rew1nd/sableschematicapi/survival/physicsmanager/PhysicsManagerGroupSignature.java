package dev.rew1nd.sableschematicapi.survival.physicsmanager;

import dev.rew1nd.sableschematicapi.sublevel.SubLevelGroupRecord;

public final class PhysicsManagerGroupSignature {
    private PhysicsManagerGroupSignature() {
    }

    public static String compute(final SubLevelGroupRecord group) {
        return PhysicsManagerAlgorithms.groupSignature(
                group.members().getFirst().dimension().location().toString(),
                group.groupId(),
                group.memberIds()
        );
    }
}
