# SGU 服务端打印机 → litematica-printer(Yur1Ca) 框架移植说明

本工程把 **Melationin/SGU-litematica-printer**（原「服务端打印机」）的打印行为，移植到
**Yur1Ca/litematica-printer** 的框架上。目标：**只保留打印**，放置与切换物品逻辑与原服务端打印机一致。

客户端与服务端的连接方式沿用 SGU 的做法：**同一个 jar**（`environment: "*"`），客户端入口负责打印，
主入口注册自定义通道。新版两端互联时，打印点击带上规划时的目标/支撑块/主手快照，
服务端检查通过后再调用原版 `handleUseItemOn`；原版权限、距离与放置规则仍然生效。
破坏、容器点击，以及连接旧服务端时的放置数据包保持原路径。

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
| 数据包放置的节奏（SGU 同款） | 侧面放置时**本 tick 只转身、下一 tick 才发 `ServerboundUseItemOnPacket`**：`PrintPlacementExecutor#adjustHorizontalLook` 在数据包模式下保留 `waitForHorizontalLook`，`ActionManager#shouldWaitForServerLook` 返回 `WAITING_FOR_LOOK`，`TickScheduler#advancePendingLookQueue` 下一 tick 重发队列并暂停扫描 |
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

### 3.1 性能：每个 tick 的打印工作量有硬上限

不要把 `每刻放置方块数` 设成 0 之后期待「越快越好」——那样每个 tick 会把队列里能放的方块一次性发完，
客户端 tick 被拉长到几十毫秒，表现就是打印时一卡一卡地掉帧。当前实现有三道闸：

1. `扫描时间预算`（`scanTimeBudgetMs`，默认 2ms）：每 tick 打印机的主线程耗时预算。
   设 `每刻放置方块数 = 0`（不限）时，**动作耗时也计入这个预算**，所以单 tick 大约只做 2ms 的活。
2. 硬上限 = 预算 × 8（默认 16ms）：任何配置下单个 tick 的打印工作都不会超过这个值。
3. 投影「手持物品高亮」的整张重排最多 20 tick 一次（材料出入会让可用物品集合频繁抖动，
   逐 tick 重排所有已加载的投影区块是另一处明显掉帧源）。

只有在确实需要更高吞吐时才调大 `scanTimeBudgetMs`（上限 10ms），并且优先把 `每刻放置方块数`
设成 2–4 这样的小数值，比设 0 更稳。若仍然卡顿，日志里会出现
`[print] slow client tick: N ms (feature modules M ms)`（每 5 秒最多一条），可据此判断是模块内部还是外部（渲染）导致。

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

新版另有两个通道（保持旧通道编解码不变）：

- `guarded_use_item_on_v1`（C2S）：原版点击包、批次号、实际目标坐标、规划时的目标及被点击方块状态、主手物品与槽位。
- `guarded_placement_ack_v1`（S2C）：原版点击序号、实际目标坐标、处理后的权威目标状态、是否通过过期检查。

客户端通过 C2S 通道可用性自动启用校验；旧服务端继续走原版点击。新版客户端与新版服务端需要一起更新。

### 6.2 放置 / 破坏回执

兼容旧客户端的 `MixinServerPlayerGameMode` 在 `useItemOn`、`destroyBlock` 返回时发回执（`accepted` 判定：
`InteractionResult != FAIL` / `destroyBlock` 返回 true）。客户端两侧都用上：

- **被拒绝**：记一次失败、让扫描缓存失效、把该格放进 `placeCooldown` 冷却，避免对着保护区/越权位置每秒重试几十次。
- **被接受**：延长该格的「等待确认」窗口。点击被接受只代表服务端跑完了 `useItemOn`，权威方块状态仍要等区块更新；
  不延长的话，高延迟下本地世界还没更新就会再点同一格 —— 第二下会点到刚放好的方块上，把门、拉杆、箱子之类的
  可交互方块切换成错误状态（表现为「错放」）。
- 等待确认超时（`print` 模式下数据包放置至少 20 tick）仍没有区块更新时，**保留冷却**而不是清掉冷却，
  同样是为了避免立即补点。

成功回执不改变世界状态 —— 方块的权威状态仍以区块更新为准。服务端未装本模组时 `canSend` 为 false，客户端行为与
纯客户端版完全一致（只靠本地预测与超时兜底）。

新版打印点击使用 `ServerGuardedPlacement`：

1. 目标或被点击方块已变化、主手材料/组件或选中槽位与客户端快照不同，则不执行点击，回发当前状态及背包同步。
   因此过期的“对空气放置”不会落到已经放好的实心方块上，再向相邻格多放一次。
2. 通过检查的点击交给原版包处理器；不直接设置投影状态、不增加距离或权限。
3. 回执使用真实放置目标及原版序号，解决贴着支撑块放置时回执坐标与 pending 坐标不一致的问题。
4. 普通区块更新及旧通道回执不能解除有序号的等待；仅最后一次点击的回执可以结算该目标，
   避免邻居强制更新或连续点击的中间状态提前解除等待。失败/超时保留冷却。
5. 音符盒等需要有意连续点击的动作共享一个批次，后续点击按上一点击的服务端结果校验；首个点击失败时，后续点击也会被拒绝。

### 6.3 服务端配置

`config/litematica-printer-server.json`（服务端独立配置，不依赖 MaLiLib）：

```json
{
  "interactionAck": true
}
```

`interactionAck` 控制旧通道的提示回执。新版有序号回执属于校验请求的必需响应，不受此开关影响。

### 6.4 安全边界

- 回执只发送给装有本模组客户端的玩家（`ServerPlayNetworking.canSend`）；
- 不引入任何新的服务端权限：回执只是把原版已经发生的放置/破坏结果告知发起者。

## 7. 高延迟服务器上的建议设置

远端服务器（RTT 上百毫秒）上，「多放 / 错放」可能来自客户端本地世界还没收到方块更新，
就再次对同一格发包，第二下点到刚放好的方块上 —— 对门、活板门、拉杆、箱子这类可交互方块就是「切换状态」。
新版两端还会在服务端拦截这类过期请求；它不能修复所有 guide 的朝向算法或代替 Servux 的轻松放置协议。
配置上仍建议：

| 配置 | 建议 | 原因 |
| --- | --- | --- |
| `placeBlocksPerTick` | `2`–`4`（不要 `0`） | `0` = 不限，等于把整个扫描预算内的点击一 tick 全发出去，服务端会先处理一批「按旧世界算出来的」点击 |
| `placeInterval` | `1`–`2` | 每 tick 发包之间留出服务端处理时间 |
| `placeRttAdaptiveInterval` | `true` | 自动把间隔抬到不低于一次往返，正是为「发包快于服务端确认」设计的 |
| `placeUsePacket` | 保持默认 `false`；需要减少幽灵方块时再开 | 本地预测会让世界立刻更新，天然不会重复点击；数据包模式依赖上面的等待窗口兜底 |
| `easyPlaceProtocol` | 仅当服务端装了 Servux（或单人）时开 | 轻松放置协议靠命中向量编码方块状态，服务端不解码就会得到错误朝向/半砖位置 |
