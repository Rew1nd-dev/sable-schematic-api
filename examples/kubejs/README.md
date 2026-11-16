# Local KubeJS Blueprint Test

KubeJS is a Gradle `localRuntime` dependency. `runClient` copies these scripts into
`run/kubejs/server_scripts/sable_blueprint_test`. Edit the examples here; that
development subdirectory is refreshed on each launch.

1. Start `gradlew.bat runClient` and enter a test world with cheats enabled.
2. Put a blueprint named `test.nbt` in `run/Sable-Schematics`.
3. Test the original Pose3d construction:
   `/sablejs pose test 0 100 0 0`
4. Test the double overload at a separate position:
   `/sablejs doubles test 50 100 0 0`

Arguments are name, absolute X/Y/Z and positive-Y rotation in degrees. Coordinates
locate the basis origin, not the bottom of the blueprint. Each command places a
new copy. Both commands require permission level 2 and run on the server thread.
Errors report the failing stage in chat and the KubeJS server log. Check
`run/logs/kubejs/server.log` and `run/logs/latest.log`.

To refresh scripts while the client is open, run
`gradlew.bat prepareKubejsBlueprintTest`, then `/reload` in game.

The helper `placeSableBlueprint` now accepts a blueprint name instead of an
arbitrary file path. KubeJS blocks `java.io.File`; the helper uses the project's
`SableBlueprintRootFiles.load(server, name)` entry point instead.

The scripts select `place(ServerLevel,SableBlueprint,BlueprintPlacementPlan)` by
its full Java signature. An unqualified `place(...)` call is ambiguous in the
current KubeJS environment because the other overload accepts `Vec3`.
Function-local variables use `var` to avoid Rhino's `const` redeclaration error
inside the command callback's `try` block.

Dependency resolution and JavaScript syntax have been checked. In-game placement
still needs manual testing with the target blueprint and compatibility mods.
