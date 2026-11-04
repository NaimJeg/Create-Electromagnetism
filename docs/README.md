# 设备模型与电磁扩展

更新日期：2026-11-03。当前按 P0—P5 交付低频设备、观测与 AM 行为原型；验收命令与证据见 [roadmap](roadmap.md)。

同一相连电网由 CEE 求解。纯模型不导入游戏类型，适配层向 CEE 的同一个岛矩阵贡献方程；无线采用复包络，通过供电与有限输出端口耦合。

| 文档 | 内容 |
|---|---|
| [包结构](architecture/package-layout.md) | 依赖方向、世界与客户端边界 |
| [模拟核心](architecture/simulation-core.md) | 时间、能量、完整三端迭代与失败语义 |
| [信号与示波器](architecture/signal-foundation.md) | 有界缓冲、触发、订阅和传输预算 |
| [无线电与仪器](architecture/radio-and-instruments.md) | 当前 AM 原型的功率、频带和空间规则 |
| [路线与验收](roadmap.md) | P0—P5 交付与可重复检查 |
| [ADR](decisions/0001-independent-simulation.md) | CEE 复用优先的决策 |
| [来源](sources.md) | 制品指纹、历史调查与证据边界 |

加载器精确固定 CEE `1.21.1-1.1.4`；构建检查制品 SHA-256 和关键字节码接口。该制品未公开确认对应源码提交，不能把历史开发快照当作当前接口。旧 EPG 命名存档迁移不属于本次重构约束；现有 Temperature/Burned 与半导体参数 NBT 键保留。
