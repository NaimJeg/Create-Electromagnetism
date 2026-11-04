# 设备运行时与 CEE 矩阵扩展

## 所有权与时间

`core.model`、`core.runtime` 只依赖 JDK。`DeviceModel` 分离参数、已接受历史与数值试探状态；`DeviceRuntime` 提供 prepareStep、linearize、commitStep、discardStep。迭代不推进温度或能量。提交先计算完整快照，异常或未知结果保留旧状态并标记过期；相同 token 不可重复提交，重接线与关闭作废待提交结果。

CEE 9173792 原始 worker 执行顺序为：全部支路 tick → 每岛 formMatrix/线性求解/getResults → 全部 afterTick；每游戏刻完成微步后，由 Future.get 屏障回到世界线程 postTick。tick 参数顺序为 `(voltages, microtick, count, firstNode, secondNode)`，afterTick 则为 `(voltages, firstNode, secondNode, microtick, count)`。verifyCeeContract 固定该 worker 签名与调用顺序。

加热线圈的运行时由世界线程拥有，在 worker 完成后顺序消费这一刻的已完成微步观测。时间来自世界游戏刻与 CEE 微步数，未增加第二套电气时钟。批次 UUID 是本地观测身份，不冒充上游网络 UUID。

## 结果接受与能量

`SimulationEvidenceMixin` 现在接管固定版本 worker 的执行，保留 CEE 拓扑、岛矩阵及 LU 后端，以 `CeeTransientEngine` 统一进行候选求解与提交。`CeeStepEvidence` 关联结果身份和实际接受区间；独立线性求解入口仍可验证 LU/Cholesky。求解前复制稀疏系数和 RHS，因为固定制品的后端会原地修改输入。线性接受条件为每行：

`abs(Ax-b) <= 1e-9 + 1e-7 * (abs(b) + Σabs(Aij*xj))`

该条件证明冻结线性方程的残差满足约定，不证明矩阵满秩或解唯一，也不冒充一般非线性收敛。缺失批次证据、非有限值或残差失败使本批观测失效。失败候选不更新已接受的电容、电感、二极管或通用模型历史，也不推进电气时间。已经接受的子步保留；后续失败时批次观测保守标记无效，既有世界线程采样消费者不为不完整批次计能。尚未迁移的 CEE 世界线程 postTick 消费者不自动获得完整的瞬态能量核算保证。

加热线圈在 preTick 固定电阻、热容、额定功率与损坏配置。每个有效微步以真实端电压计算 `V²/R × dt`，同一份采样推进 `ThermalResistorModel`。集中热模型对恒功率和线性散热精确积分；600°C 为额定功率平衡点，不再硬截断超载温度。Temperature/Burned 键保留，读取拒绝非有限温度。未知批次不加热、不损坏；拆除和卸载时不访问未加载方块，重载从已保存状态继续，不追赶丢失时间。`LoadedBlockAccess` 直接使用 getChunkNow 查询实体/状态，身份检查不会加载区块或刷新 UNKNOWN 票据。

## 三端非线性扩展

BJT 与 MOS 设备已迁移到纯 `BjtModel` / `MosfetModel`，端序分别 B/C/E 与 G/D/S。`ThreeTerminalDevice` 只添加零电导拓扑边，完整物理贡献由 `NonlinearNetworkMixin` 接入同一 CEE 岛矩阵。

含这些器件的岛保留全部端子，禁用该岛的拓扑消元。`NonlinearNetworkSolver` 在 CEE 已建立的岛矩阵上添加完整雅可比和仿射项，调用 CEE LU，在未限幅的新端电压上重新计算真实多端电流残差；最多 64 次迭代。BJT 数值限制器仅属于本次试探，每个微步重置。本项目三端模型检查真实非线性电流；原生 CEE 二极管（包括结电容）也进入同一步 Newton 及真实残差检查；电容、电感历史在迭代期间冻结。不存在独立支路调用顺序依赖，不丢掉跨导项，也不把有限电压本身当作收敛证据。使用的仍是同一电网、同一时刻、同一组节点方程，未另求电网再覆盖节点。

模型沿用简化分区、反接和保护限流；MOS 仅饱和区限流的边界不连续仍是已知模型限制。没有结电容、载流子历史或器件级 RF 响应。未知微步器件在执行回调前明确拒绝，防止无法回滚的状态变更。

## 验证与成本

verifyModels 检查解析热学、拆步一致性、能量、迭代隔离、重复提交、未知结果、拓扑与关闭，以及半导体雅可比差分。verifySemiconductors 使用真实 CEE LU 运行带载、极性和偏置用例；WorldChecks 补实际岛映射和游戏生命周期。

稀疏快照、残差扫描、非线性重组矩阵均增加开销。当前没有性能上限承诺。只在 profiling 证明必要后考虑对象复用、按岛局部缓存或更换后端；不能通过放宽容差或忽略失败样本报告提速。

## 瞬态积分与事务接口

服务端 `preciseTransient` 默认 true：梯形积分，启动/历史缺失/拓扑或储能参数改变后的首步使用后向欧拉。false 为固定步长后向欧拉，仍执行事务与联合非线性检查，不是恢复原始无条件提交。

`StatefulDevice<P,S,T>` 支持可变端子数、真实 `TimeStep` 和不可变持久历史；`ThreeTerminalDevice` 保留旧构造接口。每次候选先生成全部新状态，提交只进行字段替换。`DeviceRuntime` 允许同一物理 sequence 使用递增 attempt 重试，仍拒绝旧 token 和与已接受区间重叠的步骤。它本身不是电网调度器。

每原微步覆盖 0.05/microTicks 秒。自适应使用一整步与两半步的端电压、MNA 支路电流和器件电流差异，接受两半步历史；探测不发布。默认相对容差 1e-4，电压绝对容差 1e-6 V、电流 1e-9 A；最多减半 12 次，增长不超过两倍，每 tick 最多 4096 次网络候选求解。耗尽后停在最后接受时刻，无追赶补算。所有岛共用时间与接受屏障。

`CeeStepEvidence.report(results)` 提供实际起止时间、求解次数、失败原因及已接受储能支路的 `TransientEnergy`：端口功、储能变化、物理耗散与平衡残差分开记录。它不把储能变化自动转换成热量。报告仅在当前会话保留，不新增存档键；缺失的高阶历史按首步规则重建。

已接入原生 CapacitorProperties、InductorProperties、DiodeProperties；现有加热线圈、示波器、麦克风、信号源、无线电支路显式标记为受审计采样/源。信号源和麦克风支持候选电气时间；无线电行为源在原微步内保持。AccumulatorProperties、PhaseWindingProperties 和第三方未知微步器件尚未提供事务适配，两个模式均明确拒绝，不自动降级。当前为保证探测安全禁用受管批次拓扑优化，可能增加大网络成本。

`verifyTransients` 覆盖 RC/RL 解析解、理想 LC 100 周期、二极管偏置/小信号检波及拒绝步隔离。开发服可通过 AutoJS 调用 `TransientWorldChecks.run(server)`：不放置方块，直接验证实际变换后的 CEE 网络 RC、LC、原生二极管、未知器件拒绝及完整 worker 对有状态双端模型的提交。
