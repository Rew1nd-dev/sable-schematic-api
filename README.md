# Sable Schematic API

Sable Schematic API 是一个面向 [Sable](https://github.com/ryanhcode/sable) sub-level 的 blueprint / schematic 外置实验 mod。它把蓝图保存、加载、引用 remap、兼容层 sidecar 和一个轻量的游戏内蓝图工具放在独立 mod 中验证，等 API 边界稳定后，再把适合进入核心的部分整理回 Sable 上游。

当前版本仍是 `0.1.0` 草案 API：适合兼容性验证和小范围使用，但不承诺长期二进制稳定。

## 功能

- 保存并加载 Sable sub-level 蓝图。
- 保存方块、方块实体、普通实体和 Create contraption entity。
- 提供 block mapper、entity mapper 和 global blueprint event 三类兼容扩展点。
- 提供 OP 命令保存/加载蓝图。
- 提供 LDLib2 驱动的 `sable_schematic_api:blueprint_tool` 游戏内工具。
- Create 兼容：
  - remap contraption entity 的 `Contraption.Anchor`。
  - 通过 sidecar 保存并恢复 Super Glue。
- Simulated Project 兼容：
  - 保存并恢复 swivel-bearing 与 plate 的连接关系。
  - 保存并恢复 rope-winch / rope-connector 的 rope strand。
  - 跳过临时的 launched plunger entity，避免蓝图保存无效运行态。

## 版本与依赖

必要运行依赖：

- Minecraft `1.21.1`
- NeoForge `21.1.226+`
- Sable `1.1.3+`
- LDLib2 `2.2.6+`

可选兼容依赖：

- Create `6.0.10+`
- Simulated Project `1.1.3+`

只有安装对应可选 mod 时，相关兼容 mapper 和 event 才会注册。

## 使用

OP 命令：

```text
/sablebp save <pos> <radius> <name>
/sablebp load <name>
/sable_schematic_api save <pos> <radius> <name>
/sable_schematic_api load <name>
```

命令保存的蓝图位于世界目录下的 `sable_blueprints/<name>.nbt`。

游戏内工具：

```text
/give @p sable_schematic_api:blueprint_tool
```

- 手持工具左键依次选择 start / end。
- Shift + 左键清除当前选择和待加载蓝图。
- Tab 打开蓝图工具 UI。
- Save 会把选区导出为客户端本地 `Sable-Schematics/<name>.nbt`。
- 在 UI 中选择本地蓝图后，右键会把蓝图上传到服务器并放置到视线目标位置。

工具保存/加载同样要求玩家拥有 OP 权限，并且必须手持蓝图工具。

## Build Prerequisites

- JDK 21。
- Git。
- Gradle wrapper 文件已随仓库提供，干净克隆后可直接使用 `./gradlew` 或 `./gradlew.bat`。
- 从 Simulated-Project release 下载 `create-aeronautics-1.1.3.jar` 到本项目的 `libs/` 目录：

```text
libs/create-aeronautics-1.1.3.jar
```

这个 jar 只用于本地编译和运行 Simulated Project 兼容代码。它不是本项目源码的一部分，也不应被打包进本项目发布 jar。

构建：

```powershell
./gradlew.bat build
```

生成 Javadoc：

```powershell
./gradlew.bat javadoc
```

## 开发者入口

公共 API 位于：

```text
dev.rew1nd.sableschematicapi.api.blueprint
```

主要扩展点：

- `SableBlueprintBlockMapper`：修改或清理方块实体 NBT，并在加载后恢复运行态。
- `SableBlueprintEntityMapper`：修改、跳过或恢复实体 NBT。
- `SableBlueprintEvent`：为跨方块、跨实体或外部管理器状态保存 global sidecar。
- `BlueprintSaveSession` / `BlueprintPlaceSession`：提供蓝图内部引用、sub-level UUID 映射、block pos 映射和 UUID 分配。

当前开发者文档优先维护在源码 Javadoc 中，避免草案阶段的外部文档与 API 漂移。

## 已知限制

- 当前蓝图主要保存 Sable sub-level 内部内容，不把普通 root world blocks 纳入同一引用映射。
- 旋转和镜像尚未作为公开能力承诺；现有 compat 主要按整体平移处理。
- Blueprint NBT 格式当前为 v1，不支持旧的 legacy plot payload。
- 可选 mod 的 compat 依赖对应版本的运行时 NBT / API 结构，后续可能随上游变化调整。

## 许可

除非另有说明，本仓库源码采用 [PolyForm Shield License 1.0.0](LICENSE.md)。

你可以在非竞争性场景中使用、分发、打包到 modpack、作为服务端依赖或开发依赖使用本 mod。不要把本项目改名后作为替代品重新发布，不要冒充 Sable、Sable Schematic API 或其官方兼容版本。

第三方依赖和本地 `libs/` 中的 jar 不属于本项目源码许可覆盖范围，请遵循它们各自的许可证和发布条款。
