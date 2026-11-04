# P0—P5 实施与验收

更新日期：2026-11-03。这里的 P0—P5 对应本轮评估提出的开发项，不把任意 RF 电路、真实语音或求解器替换列作完成条件。

| 阶段 | 交付 | 自动验收 |
|---|---|---|
| P0 基线与 CEE 契约 | 完整版本范围、JAR 指纹、worker 签名、回调顺序、线性结果残差与批次身份 | verifyBaseline、verifyCeeContract、专用服务器启动 |
| P1 模型与运行时 | 热学解析积分、多端方程、参数冻结、一次提交、失败丢弃与拓扑失效 | verifyModels、verifyElectrical |
| P2 热设备纵向迁移 | 加热线圈微步能量、温度、损坏与世界/NBT 边界 | WorldChecks：带电、重复提交、未知批次冻结、NBT、旋转、拆除、真实区块卸载/重载 |
| P3 半导体迁移 | BJT/MOS 完整三端雅可比，CEE 岛级非线性残差迭代 | verifySemiconductors：N/P、偏置与负载扫描；WorldChecks：实际 CEE MOS 岛 |
| P4 示波器 | 1 MΩ 探头、质量标记、未来边沿触发、测量、订阅与有界增量传输 | verifySignals、verifyRadio 触发检查、verifyScopeCodec；真实客户端订阅/触发截图检查 |
| P5 AM 原型 | 2 Hz 输入→供电约束 AM→复包络信道→检波→有限输出；能量储备与账本 | verifyRadio：恢复、干扰、频偏、相位、断电、加载；WorldChecks：带电收发与断电、账本 |

`./gradlew.bat build --offline --console=plain` 执行所有离线检查。`runServer` 后执行 `python scripts/test-world.py --endpoint <该服务器日志中的本地 MCP 地址>` 运行世界夹具，JSON 结果写入 `build/reports/world-checks.json`。夹具通过真实的 CircuitBuilder、Network、微步回调与设备适配执行，不另建电网后端；各夹具的电源/负载是开发测试输入，未注册为玩家设备。发布 JAR 不包含 verification 源集。

已通过的服务器夹具包含：带电加热线圈的未知批次冻结、重复 postTick、NBT 恢复、旋转、拆除、远端区块实际卸载与重载；实际 CEE MOS 网络；AM 收发带 600 Ω 负载、供电中断后归零。离线检查覆盖解析解、Jacobian 差分、N/P 与负载扫描、AM 能量与干扰。

客户端重复检查：连接开发服务器并准备接好麦克风的示波器后，执行 `python scripts/test-client.py --endpoint <客户端本地 MCP 地址> --position x y z`，需要 Pillow，结果和截图写入 `build/reports/`。开发客户端可用 `./gradlew.bat runClient '-PverificationServerAddress=127.0.0.1:端口'` 连接，使用独立的 `build/client-verification` 目录；为它设置独立 AutoJS 端口避免与服务器冲突。

磁盘检查：在开发世界 (-2,150,8) 放置新加热线圈，执行 `python scripts/test-save.py prepare` 写入 100°C 检查点、保存并停止服务器；重启后执行 `python scripts/test-save.py verify`，按实际游戏刻差核对解析冷却并清理方块。该脚本使用本地开发服务器端点，报告为 `build/reports/save-checks.json`。

## 实现边界

- 线性网络采用逐行残差检查；不据此证明矩阵满秩或解唯一。三端模型另检查未限幅工作点上的真实非线性残差。
- 失败隔离目前保守地覆盖同一 worker 批次，失败岛可能使其他仪器也显示未收敛；不进行全网时间回滚。
- 带三端模型的岛禁用 CEE 拓扑优化，避免端子被消元；其他岛继续复用 CEE 优化。迭代上限 64；当前未发布性能规模保证。
- AM 为单一调制的低带宽功能块，不支持真实语音、任意晶体管 RF、HB、自适应积分、几何天线或近场求解。
- 本轮保留现有参数键，不为历史 EPG 名称存档增加迁移框架。

## 后续独立任务

性能基准、按岛失败隔离、更丰富的模型区域扫描、跨版本兼容、真实采音与播放、其他调制和开放 RF 电路，需要独立需求与验收。原生 SPICE、KLU、新电网内核及自适应步长仍须满足 ADR 启动条件。
