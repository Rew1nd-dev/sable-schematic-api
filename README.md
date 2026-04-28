# Sable Schematic API

Language: English | [简体中文](README.zh-CN.md)

Sable Schematic API is an experimental external blueprint / schematic mod for [Sable](https://github.com/ryanhcode/sable) sub-levels. It keeps blueprint save/load logic, reference remapping, compatibility sidecars, and a lightweight in-game blueprint tool in a separate mod while the API boundary is being tested. Once the API is stable enough, suitable pieces can be prepared for upstreaming back into Sable.

Version `0.1.0` is still a draft API. It is suitable for compatibility testing and controlled use, but it does not promise long-term binary stability yet.

## Features

- Save and load Sable sub-level blueprints.
- Save blocks, block entities, regular entities, and Create contraption entities.
- Provide block mapper, entity mapper, and global blueprint event extension points.
- Provide OP-only commands for saving and loading blueprints.
- Provide an LDLib2-powered `sable_schematic_api:blueprint_tool` in-game tool.
- Create compatibility:
  - Remaps contraption entity `Contraption.Anchor` data.
  - Saves and restores Super Glue through sidecar data.
- Simulated Project compatibility:
  - Saves and restores swivel-bearing / plate connections.
  - Saves and restores rope-winch / rope-connector rope strands.
  - Skips temporary launched plunger entities to avoid saving invalid runtime state.

## Versions And Dependencies

Required runtime dependencies:

- Minecraft `1.21.1`
- NeoForge `21.1.226+`
- Sable `1.1.3+`
- LDLib2 `2.2.6+`

Optional compatibility dependencies:

- Create `6.0.10+`
- Simulated Project `1.1.3+`
- Create: Copycats+ `3.0.4+`

Compatibility mappers and events are only registered when the corresponding optional mod is loaded.

## Usage

OP commands:

```text
/sablebp save <pos> <radius> <name>
/sablebp load <name>
```

Command-created blueprints are stored in the world directory under `sable_blueprints/<name>.nbt`.

In-game tool:

```text
/give @p sable_schematic_api:blueprint_tool
```

- Hold the tool and left click to select the start and end points.
- Shift + left click clears the current selection and selected load blueprint.
- Press Tab while holding the tool to open the blueprint UI.
- Save exports the selected area to the client-side `Sable-Schematics/<name>.nbt` folder.
- Select a local blueprint in the UI, then right click to upload it to the server and place it at the look target.

Tool-based saving and loading also require OP permission, and the player must be holding the blueprint tool.

## Build Prerequisites

- JDK 21.
- Git.
- The Gradle wrapper is included, so clean clones can use `./gradlew` or `./gradlew.bat`.
- Download the following optional compatibility test jars into this project's `libs/` directory:

```text
libs/create-aeronautics-1.1.3.jar
libs/copycats.jar
```

These jars are only used for local compilation and development runs of optional compatibility code. They are not part of this project's source code and should not be bundled into this project's release jar.

Build:

```powershell
./gradlew.bat build
```

Generate Javadocs:

```powershell
./gradlew.bat javadoc
```

## Developer API

Public API package:

```text
dev.rew1nd.sableschematicapi.api.blueprint
```

Main extension points:

- `SableBlueprintBlockMapper`: modify or clear block entity NBT, then restore runtime state after loading.
- `SableBlueprintEntityMapper`: modify, skip, or restore entity NBT.
- `SableBlueprintEvent`: store global sidecar data for cross-block, cross-entity, or manager-owned state.
- `BlueprintSaveSession` / `BlueprintPlaceSession`: expose blueprint-local references, sub-level UUID mappings, block position mappings, and UUID allocation.

Developer documentation is currently maintained in source Javadocs to keep it close to the draft API.

## Known Limitations

- Current blueprints mainly save Sable sub-level contents. Regular root-world blocks are not part of the same reference mapping.
- Rotation and mirroring are not exposed as stable features yet. Existing compatibility behavior mainly assumes translation.
- Blueprint NBT format is currently v1 and does not support legacy plot payloads.
- Optional compatibility depends on the runtime NBT / API structure of the targeted mod versions and may change with upstream updates.

## License

Unless otherwise stated, this repository's source code is licensed under the [PolyForm Shield License 1.0.0](LICENSE.md).

You may use, distribute, include in modpacks, run on servers, or depend on this mod in non-competing contexts. Do not rename and republish this project as a replacement, and do not impersonate Sable, Sable Schematic API, or an official compatibility build.

Third-party dependencies and jars placed under `libs/` are not covered by this project's source license. Follow their own licenses and distribution terms.
