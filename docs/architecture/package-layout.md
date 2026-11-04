# 长期包结构

2026-11-03 全仓库重构基线。项目标识为 `electromagnetism`，根包为 `com.electromagnetism`，入口为 `ElectromagnetismMod`。本次以命名、分层和可构建为验收；现有内容是后续开发的承载代码，名称不保证与当前行为完全对应，旧玩法及存档兼容不是本轮约束。

| 包 | 长期职责 |
|---|---|
| `core.model` | 纯 Java 物理模型、端口方程、时间与能量 |
| `core.runtime` | 设备状态、步提交、生命周期身份与结果 |
| `core.signal` | 音频帧、信号时钟、缓冲与波形测量 |
| `core.radio` | AM 包络、输出功率边界与储能 |
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

## 固定制品适配

构建固定 CEE 9173792（自报 `1.21.1-1.1.4`），verifyBaseline 校验 SHA-256，verifyCeeContract 校验 worker 字节码契约。该 JAR 无旧 Norton 接口；半导体现在由完整三端矩阵扩展实现，旧独立支路实现已删除。具体矩阵/时间/能量语义见 [simulation-core](simulation-core.md)。

`core.radio` 放 AM 包络、功率约束与储能纯模型；游戏收发设备和信道管理当前位于 `content.instruments`。`ScopeNetworking` 集中处理订阅与 payload。verification 源集只附加到开发运行，不进入发布 JAR。

验收命令与世界夹具见 [roadmap](../roadmap.md)。
