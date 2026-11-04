# Electromagnetism

面向 Minecraft 1.21.1 / NeoForge 的 Create 电磁、信号与仪器扩展。当前固定 Create 6.0.10 制品与 CEE `1.21.1-1.1.4`，由 CEE 保持单一电网求解权威。

## 当前功能

- 灯具、加热线圈、换向开关、励磁与电铃。
- NPN/PNP、N/P MOS 的纯三端模型及完整雅可比 CEE 适配。
- 麦克风测试源、带 1 MΩ 输入的示波器、未来上升沿单次触发、RMS/频率与历史浏览。
- AM 测试发射机与接收机：2 Hz 确定性输入、复包络信道、供电与输出能量限制。

AM 是行为级原型，不逐周期求解 RF 载波，也不采集真实语音。发射机和接收机的 0/1 端子接电源；接收机的 2/3 端子接负载或示波器。双手空时右键打开观察界面，空手潜行右键在 1000、1001、1100 Hz 间调谐；接收通带为中心频率 ±8 Hz，1001 Hz 可用于观察频偏与双源拍频。发射机面板为红色，接收机为铜色；可从创造栏或合成取得。

## 验证

```powershell
.\gradlew.bat build --offline --console=plain
.\gradlew.bat runServer --offline --console=plain
python scripts/test-world.py --endpoint http://127.0.0.1:25586/mcp
```

第二个命令保持开发服务器运行，第三个命令在另一终端执行；端口使用该服务器日志中的 AutoJS MCP 地址。世界夹具使用 (8..14,150,8) 与远端 (16008,150,16008)，只在空白位置启动，结束后移除测试方块，结果保存到 `build/reports/world-checks.json`。请在开发世界运行。测试辅助代码不进入发布 JAR。

完整能力、契约、验证记录与后续范围见 [docs](docs/README.md) 和 [开发路线](docs/roadmap.md)。
