# 长期包结构

2026-11-03 全仓库重构基线。项目标识为 `electromagnetism`，根包为 `com.electromagnetism`，入口为 `ElectromagnetismMod`。本次以命名、分层和可构建为验收；现有内容是后续开发的承载代码，名称不保证与当前行为完全对应，旧玩法及存档兼容不是本轮约束。

| 包 | 长期职责 |
|---|---|
| `core.model` | 纯 Java 物理模型、端口方程、时间与能量 |
| `core.runtime` | 设备状态、步提交、生命周期身份与结果 |
| `core.signal` | 音频帧、信号时钟、缓冲与波形测量 |
| `registry` | `ModBlocks`、`ModItems`、`ModDeviceTypes` 等内容注册 |
| `content.acoustics` | 声学设备 |
| `content.electrical` | 电气连接与开关 |
| `content.electronics` | 电子器件，当前含 `bjt`、`mosfet` |
| `content.electromechanics` | 电磁机械与励磁 |
| `content.environment` | 环境电磁作用 |
| `content.lighting` | 灯泡、灯座、工厂灯 |
| `content.thermal` | 热设备 |
| `integration.cee` | CEE 电气接口、显示源、鸭子接口及 Mixin |
| `integration.create` | Create 加工等扩展接口 |
| `client` | 客户端初始化、声音、模型注册 |
| `config` | 模组配置 |
| `platform` | 游戏侧公共桥接；当前为无客户端类型依赖的 `ClientHooks` |

## 依赖方向与命名

- `core` 不导入 Minecraft、NeoForge、Create、CEE，也不依赖 `content`、`registry`、`client`。新增求解器适配放到 `integration`。
- 内容按领域组织，保留方块、方块实体等相邻类，避免全仓库按技术类型拆散。设备内仍有既有 CEE 调用，后续逻辑替换时向 `integration.cee` 集中。
- 注册类使用 `Mod` 前缀，项目入口使用完整项目名；删除旧 `EPG` 类名前缀。Mixin 成员使用 `electromagnetism$` / `ELECTROMAGNETISM$` 命名，鸭子接口位于 `integration.cee.api`，不放进 Mixin 包。
- 资源使用 `assets/electromagnetism`、`data/electromagnetism`，Mixin 配置包为 `com.electromagnetism.integration.cee.mixin`。这次还统一了旧内部 NBT 键和雷击节点名，不提供旧键迁移。
- `src/verification/java` 放独立可执行检查；`src/test/java` 留给将来的测试框架。`verifySignals` 加入 Gradle `check`，因此 `build` 会实际运行信号回归。

## 编译适配边界

构建继续固定 CEE 制品 `9173792`（声明版本 `1.21.1-1.1.4`）。此制品不提供旧代码引用的 Norton 类，现已改用其 `ElectricalProperties` 与 `MicroTickingElectricalProperties`：

- 雷击源通过 `ElectricalProperties(resistance, 0, current)` 表达。
- `CeeNonlinearBranch` 隔离旧半导体计算与固定制品的微步回调，使用上一微步电压更新支路系数。这不是原有牛顿迭代契约，不报告内核步收敛，也未验证方向、耦合或旧玩法的物理一致性。

验收命令：`./gradlew.bat clean build --offline --console=plain`。仅验证编译、资源打包及已有纯信号检查，不代表客户端/专用服务器启动或旧玩法回归通过。
