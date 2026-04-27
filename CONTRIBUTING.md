# Contributing

Thank you for helping improve Sable Schematic API.

By submitting a pull request, you confirm that the contribution is your own original work, that you have the right to contribute it, and that it may be licensed as part of this project under the [PolyForm Shield License 1.0.0](LICENSE.md).

This project exists as an external Sable blueprint API experiment. Contributions to the API, blueprint format, exporter, placer, mapper lifecycle, or compatibility layer may later be adapted or proposed upstream to Sable under Sable's contribution and licensing expectations.

## Development Notes

- Use Java 21 and UTF-8 source files.
- Keep public extension points under `dev.rew1nd.sableschematicapi.api.blueprint`.
- Do not introduce LDLib2, item, UI, or client-only dependencies into the API package.
- Optional compatibility code must only register when the target mod is loaded.
- Keep local design notes under `doc/`; they are workspace drafts and are not part of the public repository.
- Do not commit third-party release jars under `libs/`.

Before opening a pull request, run:

```powershell
./gradlew.bat javadoc
./gradlew.bat build
```

