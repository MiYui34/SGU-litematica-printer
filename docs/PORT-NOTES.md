# SGU 服务端打印机 → litematica-printer(Yur1Ca) 框架移植说明

本工程把 **Melationin/SGU-litematica-printer**（原「服务端打印机」）的打印行为，移植到
**Yur1Ca/litematica-printer** 的框架上。目标：**只保留打印**，放置与切换物品逻辑与原服务端打印机一致。

客户端与服务端的连接方式沿用 SGU 的做法：**同一个 jar**（`environment: "*"`），客户端入口负责打印，
主入口注册自定义通道。所有原版数据包（放置、破坏、容器点击）的格式与流向都没有改动。

- 基线：Yur1Ca/litematica-printer master（0.7.2.1-Hana）
- 参考：ref-sgu（SGU dev2）、ref-yurica（原框架）
- 目标版本：Minecraft **26.1** 与 **26.2**（`versions/26.1`、`versions/26.2`）

## 1. 保留 / 删除的功能

保留（打印链路及其材料供给）：

- 打印模式本体、选区类型、工作半径、扫描/排序
- 逐方块放置（guide）、错误方块/多余方块/错误状态方块的自动破坏
- 材料供给：玩家背包 → 快捷潜影盒（`QuickShulkerAdapter`）
- **物品有序存放**（`OrderedStorageController`，沿用框架实现）
- 快捷潜影盒、HUD / 缺失材料 HUD、RTT 自适应间隔、保留耗材

删除（原框架的其它工作模式与外部集成）：

- 挖掘 / 填充 / 铺盖 / 排流体 / 破基岩 五个模块与配置页
- 多模式调度（`modeSwitch`、`printerMode`、各模块独立开关）与相关 HUD 分支
- **ChestTracker**（取物整链、容器记忆、物品列表取物、投影选区容器缓存热键）
- **TakeItOut**（外部取物 provider 与可用物品刷新）
- **虚空仓库**（补货 provider、材料列表库存查询、`neko.voidwarehouse.restock` 外部 payload）
- **容器同步**（`ContainerSyncController`、同步热键、关闭/输入守卫 mixin）
- **远处容器代开**（服务端代开容器、`stillValid` 放行链、相关四个服务端 mixin）
- 与上述模块耦合的配置项、语言项、HUD 行

单模式化后，打印由总开关 `workingSwitch`（默认 CAPS_LOCK）控制，等价于原服务端打印机的单模式行为。

## 2. 从 SGU 移植回来的行为

| 项 | 实现位置 |
| --- | --- |
| 数据包放置（`placeUsePacket`，默认关） | `Configs.Placement.PRINT_USE_PACKET` → `ActionManager.sendQueue` → `MixinMultiPlayerGameMode#litematica_printer$useItemOn(localPrediction)` |
| 数据包破坏（`breakUsePacket`，默认关） | `Configs.Break.BREAK_USE_PACKET` → `InteractionUtils.continueDestroyBlock(...)` 的 `localPrediction` |
| 数据包模式下的物品切换 | `InventoryUtils.swapItemToMainHand` 增加 `ServerboundContainerClickPacket`（含 `HashedStack` 快照）分支 |
| 17 个 guide 的「错误状态方块破坏」 | `guide/guides/{Campfire,Candle,Chest,Comparator,Door,EndPortalFrame,FenceGate,FlowerBed,FlowerPot,Piston,Repeater,SeaPickle,Slab,Snow,Stair,TrapDoor,Vine}Guide` |
| 潜行判定 | `PrintPlacementExecutor#getUseShift`：交互方块必定潜行、`ClickAction` 不潜行、`printForcedSneak` 强制 |

17 个 guide 的分支保持原逻辑：铁门/铁活板门跳过 → 开关状态不符用 `ClickAction` 切换 → 朝向不符且
`printBreakWrongStateBlock` 开启时破坏 → `Result.SKIP`。破坏请求统一走框架的
`InteractionUtils.canBreakBlock` + `breakRestriction` 守卫。

## 3. 与 SGU 的已知差异（有意保留框架实现）

1. **迭代顺序**：SGU 用可配置的 `IterationOrder`（默认 YXZ）+ 三轴方向遍历工作盒；Yur1Ca 框架用
   扫描器 + 距离波前游标（`PlayerDistanceCursor` / `BoxDistanceCursor`）与可选的
   `printSortTargets` 目标优先级队列。放置的**方块、面、顺序约束（同 tick 上限、间隔、冷却、每格只放一次）**
   一致，但「先放哪一格」由框架的扫描/排序决定，不再有 48 种迭代顺序选项。
2. **材料获取是异步的**：SGU 纯同步扫槽位；框架用 `MaterialRequest` 令牌链 + 跨 tick 确认
   （`InventorySwitchGuard`），期间该格标记为「等待取货」而不是直接跳过。取到材料后自动继续。
   外部取物（ChestTracker / TakeItOut / 虚空仓库）已按需求移除，只剩背包与快捷潜影盒两段。
3. **guide 框架语义**：`Guide.buildAction` 的前置门控（`canSurvive`、双层方块上半、需水方块）
   由框架统一处理；`Result` 为 record（`PASS`/`SKIP`/`success(Action)`）。
4. **水/海草**：由 `WaterPrintTask` + `WaterGuide` 承担（SGU 是独立 `FluidGuide`/`SeagrassGuide`）。
5. 框架额外能力保留：保留耗材（`printReserveItems`）、RTT 自适应间隔、`printSortTargets`/`printSortSides`。

## 4. 构建

前置：

- JDK **25**（`C:\Program Files\Zulu\zulu-25`）；Gradle **9.7.1**
  （`~/.gradle/wrapper/dists/gradle-9.7.1-bin/.../gradle-9.7.1/bin/gradle.bat`；wrapper 里的 9.5.1 不可用）
- `libs/` 下需要外部模组 jar（构建脚本按 URL 下载，网络不稳时手动放入可跳过）：
  `quickshulker-3.1.0-26.1.jar` / `quickshulker-3.1.0-26.2.jar`（`libs/` 被 .gitignore 忽略）

命令（工程根目录）：

```powershell
$env:JAVA_HOME='C:\Program Files\Zulu\zulu-25'
& 'C:\Users\Admin\.gradle\wrapper\dists\gradle-9.7.1-bin\1w1c7tv4s851m17nbqdsro2tv\gradle-9.7.1\bin\gradle.bat' :26.1:build :26.2:build --console=plain
```

产物：`versions/26.1/build/libs/litematica-printer-26.1-*.jar`、`versions/26.2/build/libs/litematica-printer-26.2-*.jar`。

## 5. 运行依赖

Fabric Loader + Fabric API + MaLiLib + Litematica（版本见各 `versions/*/gradle.properties`）；
可选：Tweakeroo、Quick Shulker。

**专用服务端**只需要 Fabric Loader + Fabric API（MaLiLib / Litematica 在 `fabric.mod.json` 里是
`suggests`，服务端不必安装）；装同一个 jar 即可。

## 6. 服务端部分

### 6.1 通道

`litematica-printer:interaction_ack`（S2C）：`(pos, kind, accepted)`，一次放置/破坏在服务端的处理结果。
编码器在 `PrinterNetworking.initCommon()` 登记，客户端在 `initClient()` 注册收包器。

### 6.2 放置 / 破坏回执

`MixinServerPlayerGameMode` 在 `useItemOn`、`destroyBlock` 返回时发回执（`accepted` 判定：
`InteractionResult != FAIL` / `destroyBlock` 返回 true）。客户端只在「服务端明确拒绝 + 本地正等这一格确认」时
行动：记一次失败、让扫描缓存失效、把该格放进 `placeCooldown` 冷却，避免对着保护区/越权位置每秒重试几十次。
成功回执不做事 —— 方块的权威状态仍以区块更新为准。服务端未装本模组时 `canSend` 为 false，客户端行为与
纯客户端版完全一致。

### 6.3 服务端配置

`config/litematica-printer-server.json`（服务端独立配置，不依赖 MaLiLib）：

```json
{
  "interactionAck": true
}
```

### 6.4 安全边界

- 回执只发送给装有本模组客户端的玩家（`ServerPlayNetworking.canSend`）；
- 不引入任何新的服务端权限：回执只是把原版已经发生的放置/破坏结果告知发起者。
