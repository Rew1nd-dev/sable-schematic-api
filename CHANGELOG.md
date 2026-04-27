# Changelog

## 0.1.0

Initial public draft of Sable Schematic API.

### Added

- Blueprint v1 NBT model for Sable sub-level data.
- Export and placement flow for Sable sub-level blocks and block entities.
- Entity save/load support with blueprint-local entity positions.
- `SableBlueprintBlockMapper` for block and block entity remapping.
- `SableBlueprintEntityMapper` for entity NBT remapping and entity skip rules.
- `SableBlueprintEvent` for global sidecar data used by multi-block or externally managed state.
- OP commands under `/sablebp` and `/sable_schematic_api`.
- LDLib2-backed `sable_schematic_api:blueprint_tool` for client-side selection, local blueprint files, save, and upload-to-place workflow.
- Create compatibility:
  - Contraption entity anchor remapping.
  - Super Glue save/restore through blueprint sidecar data.
- Simulated Project compatibility:
  - Swivel-bearing and plate reconnection.
  - Rope-winch / rope-connector rope strand restoration.
  - Launched plunger entity skip rule.

### Known Limitations

- Root world blocks are not part of the same reference mapping as Sable sub-level blocks.
- Rotation and mirror transforms are not currently exposed as stable blueprint placement features.
- Blueprint v1 rejects legacy plot payloads.
- Optional compatibility behavior depends on upstream Create and Simulated Project runtime data staying compatible with the targeted versions.

