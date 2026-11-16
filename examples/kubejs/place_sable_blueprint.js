// Place in kubejs/server_scripts and call from a server-thread event with a ServerLevel.
// Requires the BlueprintPlacementPlan.forPose(blueprint, x, y, z, rotationYDegrees) overload.
// Best-effort example: not runtime-tested with KubeJS.
const SableBlueprintRootFiles = Java.loadClass('dev.rew1nd.sableschematicapi.blueprint.SableBlueprintRootFiles')
const SableBlueprintPlacer = Java.loadClass('dev.rew1nd.sableschematicapi.blueprint.SableBlueprintPlacer')
const BlueprintPlacementPlan = Java.loadClass('dev.rew1nd.sableschematicapi.survival.BlueprintPlacementPlan')

// blueprintName is a file name in Sable-Schematics, not an arbitrary path.
// Missing files throw an IOException. KubeJS blocks direct java.io.File access.
function placeSableBlueprint(level, blueprintName, x, y, z, rotation) {
    if (rotation === undefined) rotation = 0
    var blueprint = SableBlueprintRootFiles.load(level.getServer(), blueprintName)
    // Basis origin in world coordinates; positive-Y rotation in degrees (not player yaw).
    var plan = BlueprintPlacementPlan.forPose(blueprint, x, y, z, rotation)
    // Avoid Rhino's ambiguous choice between the Vec3 and placement-plan overloads.
    return SableBlueprintPlacer['place(net.minecraft.server.level.ServerLevel,dev.rew1nd.sableschematicapi.blueprint.SableBlueprint,dev.rew1nd.sableschematicapi.survival.BlueprintPlacementPlan)'](
        level, blueprint, plan)
}
