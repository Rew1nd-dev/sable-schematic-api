# Sable Schematic API

这是一个 Sable schematic / blueprint API 的外置实验 mod。目标是先让其他 mod 以较小依赖的形式接入、测试 remapper 和蓝图放置流程，等 API 边界稳定后，再把适合进入核心的部分 PR 回 Sable 上游。

## 依赖关系

Sable 按 `E:\GitHub\sable\wiki\Home.md` 中的方式从 RyanHCode Maven 获取：

```groovy
repositories {
    exclusiveContent {
        forRepository {
            maven {
                url = "https://maven.ryanhcode.dev/releases"
                name = "RyanHCode Maven"
            }
        }
        filter {
            includeGroup("dev.ryanhcode.sable")
            includeGroup("dev.ryanhcode.sable-companion")
        }
    }
}

dependencies {
    compileOnlyApi("dev.ryanhcode.sable:sable-common-${project.minecraft_version}:${project.sable_version}")
    localRuntime("dev.ryanhcode.sable:sable-neoforge-${project.minecraft_version}:${project.sable_version}")
}
```

当前必要依赖：

- Sable `1.1.3+`
- Minecraft `1.21.1`
- NeoForge `21.1.226+`

当前兼容性依赖：

- Create: 默认启用为 `compileOnly` 和 `localRuntime`，用于后续编写并运行 Create remapper 测试。
- Simulated Project: 默认启用，依赖来源是本项目内的 `libs\create-aeronautics-1.1.3.jar`。

`libs\create-aeronautics-1.1.3.jar` 是 bundled jar，外层本身不暴露 `simulated`、`aeronautics`、`offroad` 的 class。为了让 Java 编译、IDE、mixin 和 remapper 注册都能看到真实类型，构建会通过 `extractCreateAeronauticsModules` 任务把 `META-INF/jarjar/*.jar` 解到 `build/compat/create-aeronautics-1.1.3/`，再把解出的三个真实 NeoForge mod jar 加入 `compileOnly` 和 `localRuntime`。

注意：Sable common 只放在 `compileOnlyApi`，本地运行只放 `sable-neoforge`。不要同时把 `sable-common` 和 `sable-neoforge` 放进 runtime classpath，否则 Gradle 会因为二者都提供 `dev.ryanhcode.sable:sable` capability 而冲突；Veil common/neoforge 也会出现同类冲突。

## 当前代码状态

- 已迁移 Sable 本地草稿中的 `api/blueprint` 包。
- 已迁移基础蓝图模型、导出、保存、读取、放置实现。
- 已把包名改为 `dev.rew1nd.sableschematicapi`。
- 已移除 NeoForge MDK 示例方块、物品、配置和 `examplemod` 资源。
- `mods.toml` 中 Sable 是 required dependency，Create 和 Simulated 是 optional dependency。
- 默认开发 classpath 已包含 Create，以及从 Create Aeronautics bundled jar 解出的 `simulated`、`aeronautics`、`offroad` 模块 jar，后续可以直接添加兼容 mapper 包。
- 已注册独立测试命令，不再改动 Sable 本体命令树。

测试命令：

- `/sablebp save <pos> <radius> <name>`
- `/sablebp load <name>`
- `/sable_schematic_api save <pos> <radius> <name>`
- `/sable_schematic_api load <name>`

## 构建验证

当前默认配置已通过：

```powershell
./gradlew.bat build
```

默认构建会启用 Sable、Create 和解包后的 Create Aeronautics 模块兼容 classpath。

## 下一步

- 为 Create 注册首批 `SableBlueprintBlockMapper`，验证常见 block entity NBT remap。
- 为 Simulated Project 注册首批 `SableBlueprintBlockMapper`，测试 block entity / UUID / 连接关系 remap。
- 补充实体 payload、旋转/镜像、跨 sub-level 引用修正的验证用例。
- 根据实际 remapper 使用情况收敛 API 名称、阶段、session 暴露面和格式版本。
