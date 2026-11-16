// /sablejs <pose|doubles> <name> <x> <y> <z> <rotationDegrees>
// Reads run/Sable-Schematics/<name>.nbt. Requires permission level 2.
const SableJsCommands = Java.loadClass('net.minecraft.commands.Commands')
const SableJsString = Java.loadClass('com.mojang.brigadier.arguments.StringArgumentType')
const SableJsDouble = Java.loadClass('com.mojang.brigadier.arguments.DoubleArgumentType')
const SableJsFiles = Java.loadClass('dev.rew1nd.sableschematicapi.blueprint.SableBlueprintRootFiles')
const SableJsPlan = Java.loadClass('dev.rew1nd.sableschematicapi.survival.BlueprintPlacementPlan')
const SableJsPlacer = Java.loadClass('dev.rew1nd.sableschematicapi.blueprint.SableBlueprintPlacer')
const SableJsPose = Java.loadClass('dev.ryanhcode.sable.companion.math.Pose3d')
const SableJsVector = Java.loadClass('org.joml.Vector3d')
const SableJsQuaternion = Java.loadClass('org.joml.Quaterniond')
const SableJsComponent = Java.loadClass('net.minecraft.network.chat.Component')

ServerEvents.commandRegistry(event => {
    var root = SableJsCommands.literal('sablejs').requires(source => source.hasPermission(2))
    // Each callback captures its own mode; use function-scoped locals for Rhino.
    ;['pose', 'doubles'].forEach(mode => {
        root.then(SableJsCommands.literal(mode)
            .then(SableJsCommands.argument('name', SableJsString.word())
                .then(SableJsCommands.argument('x', SableJsDouble.doubleArg())
                    .then(SableJsCommands.argument('y', SableJsDouble.doubleArg())
                        .then(SableJsCommands.argument('z', SableJsDouble.doubleArg())
                            .then(SableJsCommands.argument('rotation', SableJsDouble.doubleArg())
                                .executes(ctx => {
                                    var source = ctx.getSource()
                                    var stage = 'load'
                                    try {
                                        var blueprint = SableJsFiles.load(source.getServer(), SableJsString.getString(ctx, 'name'))
                                        var x = SableJsDouble.getDouble(ctx, 'x')
                                        var y = SableJsDouble.getDouble(ctx, 'y')
                                        var z = SableJsDouble.getDouble(ctx, 'z')
                                        var rotation = SableJsDouble.getDouble(ctx, 'rotation')
                                        stage = 'forPose (' + mode + ')'
                                        var plan
                                        if (mode === 'pose') {
                                            stage = 'Pose3d constructor'
                                            var pose = new SableJsPose(
                                                new SableJsVector(x, y, z),
                                                new SableJsQuaternion().rotationY(rotation * Math.PI / 180),
                                                new SableJsVector(0, 0, 0),
                                                new SableJsVector(1, 1, 1))
                                            stage = 'forPose (pose)'
                                            plan = SableJsPlan.forPose(blueprint, pose)
                                        } else {
                                            plan = SableJsPlan.forPose(blueprint, x, y, z, rotation)
                                        }
                                        stage = 'place'
                                        // Select the plan overload explicitly; KubeJS also offers Vec3 conversion.
                                        SableJsPlacer['place(net.minecraft.server.level.ServerLevel,dev.rew1nd.sableschematicapi.blueprint.SableBlueprint,dev.rew1nd.sableschematicapi.survival.BlueprintPlacementPlan)'](
                                            source.getLevel(), blueprint, plan)
                                        source.sendSuccess(() => SableJsComponent.literal('Blueprint placed (' + mode + ')'), false)
                                        return 1
                                    } catch (error) {
                                        console.error('[sablejs] Failed at ' + stage + ': ' + error)
                                        console.error(error)
                                        source.sendFailure(SableJsComponent.literal('Failed at ' + stage + ': ' + error))
                                        return 0
                                    }
                                })))))))
    })
    event.register(root)
})
