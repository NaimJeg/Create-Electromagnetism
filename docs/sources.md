# 来源与可复核快照

原调查日期：2026-11-02；本次修订：2026-11-03。以下提交及源码结论继承自旧文档，本次没有重新完成这些仓库的全面源码审查。旧记录称曾读取固定快照，且第三方快照位于工作区外；不能将这项历史记录表述为本次已经再次读取。本次未启动模组进行游戏内验证。

| 代号 | 仓库 / 分支 | 固定提交 | 可信度与用途 |
|---|---|---|---|
| CEE | `george8188625/Create-Electro-Energetics` / `1.21.1` | `4828c3487a9fc0703717b415eb07d005c7a48653` | 作者仓库；提交时间 2026-09-20，源码版本声明 `1.21.1-1.2.0` |
| PG | `patryk3211/PowerGrid` / `architectury-1.20.1/dev` | `58422d565289a597b410f47d0545171af32d0808` | 作者仓库；提交时间 2026-10-31；用于设计参考，不代表本项目 1.21.1 可直接兼容 |
| AW-R | `vigasilva/Create-Airwaves` / `master` | `35c29981b8020bb77649286a3f3128ca13592b3a` | 社区从 `0.1.1-atm10` 二进制重建；不是原作者正式源码，注释和局部命名可能经过整理 |
| AW-C | `Radioheads-of-Create/Airwaves` / `master` | `2a25beb1f15969b8c8380843fe2f24ac7e2d79af` | 后续社区重写；提交时间 2026-11-01，不等同原版发布内容 |

## C1

CEE 定位和版本：

- [README](https://github.com/george8188625/Create-Electro-Energetics/blob/4828c3487a9fc0703717b415eb07d005c7a48653/README.md)
- [gradle.properties](https://github.com/george8188625/Create-Electro-Energetics/blob/4828c3487a9fc0703717b415eb07d005c7a48653/gradle.properties)

## C2

CEE 调度、MNA、micro tick 和结果：

- [SimulationTicker.java](https://github.com/george8188625/Create-Electro-Energetics/blob/4828c3487a9fc0703717b415eb07d005c7a48653/src/main/java/com/george_vi/electroenergetics/simulation/simulator/SimulationTicker.java)
- [Network.java](https://github.com/george8188625/Create-Electro-Energetics/blob/4828c3487a9fc0703717b415eb07d005c7a48653/src/main/java/com/george_vi/electroenergetics/simulation/simulator/Network.java)
- [CSimulation.java](https://github.com/george8188625/Create-Electro-Energetics/blob/4828c3487a9fc0703717b415eb07d005c7a48653/src/main/java/com/george_vi/electroenergetics/config/CSimulation.java)
- [SimulationResults.java](https://github.com/george8188625/Create-Electro-Energetics/blob/4828c3487a9fc0703717b415eb07d005c7a48653/src/main/java/com/george_vi/electroenergetics/simulation/SimulationResults.java)
- [电气模型目录](https://github.com/george8188625/Create-Electro-Energetics/tree/4828c3487a9fc0703717b415eb07d005c7a48653/src/main/java/com/george_vi/electroenergetics/simulation/electrical_properties)
- [基础元件模型目录](https://github.com/george8188625/Create-Electro-Energetics/tree/4828c3487a9fc0703717b415eb07d005c7a48653/src/main/java/com/george_vi/electroenergetics/foundation/electrical_properties)

## P1

Power Grid 的求解器分层与元件：

- [README](https://github.com/patryk3211/PowerGrid/blob/58422d565289a597b410f47d0545171af32d0808/README.md)
- [求解器目录：IMNA、JavaMNA、NativeMNA 等](https://github.com/patryk3211/PowerGrid/tree/58422d565289a597b410f47d0545171af32d0808/src/main/java/org/patryk3211/powergrid/electricity/sim/solver)
- [CapacitorWire.java](https://github.com/patryk3211/PowerGrid/blob/58422d565289a597b410f47d0545171af32d0808/src/main/java/org/patryk3211/powergrid/electricity/sim/special/CapacitorWire.java)
- [电路板元件目录](https://github.com/patryk3211/PowerGrid/tree/58422d565289a597b410f47d0545171af32d0808/src/main/java/org/patryk3211/powergrid/circuits/components)
- [NOTICE](https://github.com/patryk3211/PowerGrid/blob/58422d565289a597b410f47d0545171af32d0808/NOTICE)

## A1

Airwaves 原发布版本的社区重建：

- [README：重建来源、版本及许可说明](https://github.com/vigasilva/Create-Airwaves/blob/35c29981b8020bb77649286a3f3128ca13592b3a/README.md)
- [RadioNetwork.java：quality、best、broadcast、send](https://github.com/vigasilva/Create-Airwaves/blob/35c29981b8020bb77649286a3f3128ca13592b3a/src/main/java/com/airwaves/logic/RadioNetwork.java)
- [Tuning.java](https://github.com/vigasilva/Create-Airwaves/blob/35c29981b8020bb77649286a3f3128ca13592b3a/src/main/java/com/airwaves/Tuning.java)
- [AntennaUtil.java](https://github.com/vigasilva/Create-Airwaves/blob/35c29981b8020bb77649286a3f3128ca13592b3a/src/main/java/com/airwaves/logic/AntennaUtil.java)

## A2

Airwaves 电路板和音频路径：

- [BoardAnalysis.java：pair、Program、Stage](https://github.com/vigasilva/Create-Airwaves/blob/35c29981b8020bb77649286a3f3128ca13592b3a/src/main/java/com/airwaves/logic/BoardAnalysis.java)
- [BoardDesign.java](https://github.com/vigasilva/Create-Airwaves/blob/35c29981b8020bb77649286a3f3128ca13592b3a/src/main/java/com/airwaves/logic/BoardDesign.java)
- [VoiceClient.java](https://github.com/vigasilva/Create-Airwaves/blob/35c29981b8020bb77649286a3f3128ca13592b3a/src/main/java/com/airwaves/client/VoiceClient.java)
- [AirwavesVoicechatPlugin.java](https://github.com/vigasilva/Create-Airwaves/blob/35c29981b8020bb77649286a3f3128ca13592b3a/src/main/java/com/airwaves/compat/svc/AirwavesVoicechatPlugin.java)
- [方块实体目录：接收、发射、麦克风、扬声器、中继等](https://github.com/vigasilva/Create-Airwaves/tree/35c29981b8020bb77649286a3f3128ca13592b3a/src/main/java/com/airwaves/be)

## A3

Airwaves 社区重写，不能与 A1/A2 混用：

- [README](https://github.com/Radioheads-of-Create/Airwaves/blob/2a25beb1f15969b8c8380843fe2f24ac7e2d79af/README.md)
- [Modulation.java](https://github.com/Radioheads-of-Create/Airwaves/blob/2a25beb1f15969b8c8380843fe2f24ac7e2d79af/src/main/java/dev/radioheads/airwaves/api/radio/Modulation.java)
- [AirwaveModulations.java](https://github.com/Radioheads-of-Create/Airwaves/blob/2a25beb1f15969b8c8380843fe2f24ac7e2d79af/src/main/java/dev/radioheads/airwaves/registry/AirwaveModulations.java)
- [LICENSE.md](https://github.com/Radioheads-of-Create/Airwaves/blob/2a25beb1f15969b8c8380843fe2f24ac7e2d79af/LICENSE.md)

## I1

ngspice：工作点、瞬态、小信号 AC 的基准参考。用于离线对照，不表示运行时将嵌入 ngspice。

- [官方文档入口](https://ngspice.sourceforge.io/docs.html)
- [小信号 AC 手册页](https://nmg.gitlab.io/ngspice-manual/analysesandoutputcontrol_batchmode/analyses/ac_small-signalacanalysis.html)

## I2

Keysight ADS：电路包络方法。

- [Circuit Envelope Simulation，ADS 2011 方法文档](https://edadownload.software.keysight.com/eedl/ads/2011/pdf/cktsimenv.pdf)
- [Circuit Envelope 产品方法说明](https://www.keysight.com/zz/en/assets/7018-02109/flyers/5990-3809.pdf)

前者是历史算法资料，不作为当前产品版本或性能依据。

## I3

MathWorks：包络假设、多载波和传播方法。

- [Circuit Envelope Basics](https://www.mathworks.com/help/simrf/gs/circuit-envelope-basics.html)
- [Free-space path loss：远场适用范围](https://www.mathworks.com/help/phased/ref/fspl.html)
- [Antenna：发射与接收功率波接口](https://www.mathworks.com/help/simrf/ref/antenna.html)

## I4

Sandia Xyce：可公开核查的工业级 SPICE 参考，覆盖 DC、瞬态、AC、HB 等。

- [About Xyce](https://xyce.sandia.gov/about-xyce/)
- [Xyce 7.8 用户手册，7.7 Harmonic Balance Analysis](https://xyce.sandia.gov/files/xyce/Xyce_Users_Guide_7.8.pdf)

## I5

Ansys：区分电路模型与三维电磁场求解。

- [HFSS 数学方法：有限元与端口 S 矩阵](https://ansyshelp.ansys.com/public/views/secured/electronics/v261/en/subsystems/HFSS/Subsystems/An%20Introduction%20to%20HFSS/Content/MathematicalMethodUsedinHFSS.htm)

## 调查边界

没有获得原作者正式的 Airwaves 源码发布，也没有对重建仓库与原 JAR 做逐方法等价验证。文中相应结论限定为“该重建快照的实现”。没有使用论坛传言判断原作者意图。工业资料用于方法选择；本项目性能和精度指标是待验证设计目标。

## Local baseline

本次实际核验：本地 `docs/` 全部正文、`build.gradle`、模组元数据模板、项目 CEE 使用入口，以及 Gradle 缓存制品的元数据和类目录。

| 项目 | 结果 |
|---|---|
| 编译依赖坐标 | `curse.maven:create-electro-energetics-1443327:9173792` |
| 缓存 JAR 自报版本 | `1.21.1-1.1.4` |
| JAR SHA-256 | `2362decf60ace66037eb2ae79f34fed57b5f152e0435164f5f91912ea837052f` |
| 类目录核验 | 有 `MicroTickingElectricalProperties`、`FinishElectricSimulationEvent`；未发现 Norton 类 |
| 本项目运行依赖范围 | 模板声明 `[1.1.1,)`；根 README 声称 `1.2.0-130` 及以上，尚未统一 |
| 制品对应源码提交 | 待确认，不能以 C1/C2 的开发快照代替 |
| 构建／行为状态 | 本次未重跑构建、游戏、数值回归或性能基准 |

本次成功读取[CEE 官方交流说明](https://github.com/george8188625/Create-Electro-Energetics/wiki/Alternating-Current)，确认上游将 AC 标为实验性，介绍微步、三相发电、仪表、同步和变压器连接。该 Wiki 不是目标 JAR 的 API 合同。

本次请求 C2 中固定提交的 `Network.java` 与 `SimulationTicker.java` 原始正文未成功；相关实现细节保留为历史调查及待复核项，不标为本次源码复核结果。

## I6

CircuitJS 元件接入与执行阶段参考，本次已读取：

- [INTERNALS.md](https://github.com/pfalstad/circuitjs1/blob/master/INTERNALS.md)

重点参考元件方程贡献、时间步准备、非线性子迭代和步完成的区分，不整体移植运行时。

## I7

ngspice 验证与混合信号设计参考，本次已读取：

- [测试与质量保证](https://ngspice.sourceforge.io/quality.html)：离线电路与回归测试组织。
- [XSPICE](https://ngspice.sourceforge.io/xspice.html)：模拟与事件驱动计算协调、行为功能块及用户定义节点类型。

本次也读取 [About Xyce](https://xyce.sandia.gov/about-xyce/)，用于区分瞬态、AC、HB 等分析能力；不表示引入原生运行时。

## I8

无线电抽象候选参考：

- [GNU Radio I/Q 教程](https://wiki.gnuradio.org/index.php/IQ_Complex_Tutorial)：本次请求返回 403，未取得正文，不作为本次已复核来源。
- [liquid-dsp 文档](https://liquidsdr.org/doc/)：后续原型参考入口，本次未读取。

包络、调制、滤波、同步和信道应通过独立原型验证；这些候选资料不证明本项目已具备相应实现。

## I9

线性代数后端候选入口，本次未进行库评测或接口核验：

- [EJML](https://ejml.org/wiki/index.php?title=Main_Page)
- [SuiteSparse / KLU](https://github.com/DrTimothyAldenDavis/SuiteSparse)

只有基准证明线性求解占主导后，才比较精度、稀疏结构与数值分解复用、原生部署及维护成本；数学库不替代设备模型、时间推进或世界生命周期。
