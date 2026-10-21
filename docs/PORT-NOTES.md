# SGU 服务端打印机 → litematica-printer(Yur1Ca) 框架移植说明

本工程把 **Melationin/SGU-litematica-printer**（原「服务端打印机」）的打印行为，移植到
**Yur1Ca/litematica-printer** 的框架上。目标：**只保留打印**，放置与切换物品逻辑与原服务端打印机一致。

客户端与服务端的连接方式沿用 SGU 的做法：**同一个 jar**（`environment: "*"`），客户端入口负责打印，
主入口注册两条自定义通道，服务端可装可不装 —— 不装时客户端完全退回原版行为，装了就获得
「远处容器代开」与「放置/破坏结果回执」两项服务端能力。所有原版数据包（放置、破坏、容器点击）的
格式与流向都没有改动。

- 基线：Yur1Ca/litematica-printer master（0.7.2.1-Hana）
- 参考：ref-sgu（SGU dev2）、ref-yurica（原框架）
- 目标版本：Minecraft **26.1** 与 **26.2**（`versions/26.1`、`versions/26.2`）

## 1. 保留 / 删除的功能

保留（打印链路及其材料供给链）：

- 打印模式本体、选区类型、工作半径、扫描/排序
- 逐方块放置（guide）、错误方块/多余方块/错误状态方块的自动破坏
- 材料供给：玩家背包 → TakeItOut → 虚空仓库 → 快捷潜影盒 → ChestTracker
- **物品有序存放**（OrderedStorageController，沿用框架实现）
- 快捷潜影盒、容器同步、HUD / 缺失材料 HUD、RTT 自适应间隔、保留耗材

删除（原框架的其它工作模式与专用逻辑）：

- 挖掘 / 填充 / 铺盖 / 排流体 / 破基岩 五个模块与配置页
- 多模式调度（`modeSwitch`、`printerMode`、各模块独立开关）与相关 HUD 分支
- 与上述模块耦合的 mixin 分支、配置项、语言项、HUD 行

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
3. **guide 框架语义**：`Guide.buildAction` 的前置门控（`canSurvive`、双层方块上半、需水方块）
   由框架统一处理；`Result` 为 record（`PASS`/`SKIP`/`success(Action)`）。
4. **水/海草**：由 `WaterPrintTask` + `WaterGuide` 承担（SGU 是独立 `FluidGuide`/`SeagrassGuide`）。
5. 框架额外能力保留：保留耗材（`printReserveItems`）、RTT 自适应间隔、容器同步、虚空仓库补货、
   `printSortTargets`/`printSortSides`。

## 4. 构建

前置：

- JDK **25**（`C:\Program Files\Zulu\zulu-25`）；Gradle **9.7.1**
  （`~/.gradle/wrapper/dists/gradle-9.7.1-bin/.../gradle-9.7.1/bin/gradle.bat`；wrapper 里的 9.5.1 不可用）
- `libs/` 下需要两个外部模组 jar（构建脚本按 URL 下载，网络不稳时手动放入可跳过）：
  `chesttracker-2.8.3%2B26.1.2.jar` / `chesttracker-2.8.3%2B26.2.jar`、
  `quickshulker-3.1.0-26.1.jar` / `quickshulker-3.1.0-26.2.jar`（`libs/` 被 .gitignore 忽略）
- 开发环境跑客户端时，ChestTracker 还需要 `yet_another_config_lib_v3`：把
  `yet_another_config_lib_v3-3.9.4+26.1-fabric.jar` 放进 `libs/` 即可（构建脚本检测到文件才加依赖，
  发布出去的模组本身不依赖它）。ChestTracker 2.8.3 的 litematica 兼容 mixin 与本仓 litematica 版本
  不匹配时，开发客户端会在标题界面崩在 `chesttracker.mixins.json:compat.litematica.GuiMaterialListMixin`，
  这是第三方 mixin 的问题；把 `chesttracker` 依赖临时改成 `compileOnly` 即可验证「没装 ChestTracker 也能启动」。
- 想自动连本机开发服时给客户端加环境变量 `PRINTER_QUICKPLAY=localhost:25565`，构建脚本会追加
  `--quickPlayMultiplayer`。

命令（工程根目录）：

```powershell
$env:JAVA_HOME='C:\Program Files\Zulu\zulu-25'
& 'C:\Users\Admin\.gradle\wrapper\dists\gradle-9.7.1-bin\1w1c7tv4s851m17nbqdsro2tv\gradle-9.7.1\bin\gradle.bat' :26.1:build :26.2:build --console=plain
```

产物：`versions/26.1/build/libs/litematica-printer-26.1-*.jar`、`versions/26.2/build/libs/litematica-printer-26.2-*.jar`。

## 5. 运行依赖

Fabric Loader + Fabric API + MaLiLib + Litematica（版本见各 `versions/*/gradle.properties`）；
可选：Tweakeroo、ChestTracker、Quick Shulker、carpet-Neko-Addition（虚空仓库）。

**专用服务端**只需要 Fabric Loader + Fabric API（MaLiLib / Litematica 在 `fabric.mod.json` 里是
`suggests`，服务端不必安装）；装同一个 jar 即可。

## 6. 服务端部分（本工程新增）

### 6.1 通道

全部挂在 `litematica-printer` 命名空间下，两端各自登记编码器：

| 通道 | 方向 | 内容 |
| --- | --- | --- |
| `litematica-printer:server_hello` | S2C | `(remoteContainer, interactionAck, maxContainerRange)`，玩家进服时发一次 |
| `litematica-printer:interaction_ack` | S2C | `(pos, kind, accepted)`，一次放置/破坏在服务端的处理结果 |
| `litematica-printer:open_container` | C2S | `(dimension, pos, nonce)`，请求服务端代开远处容器 |
| `litematica-printer:open_container_result` | S2C | `(nonce, status)`，`OK / NOT_CONTAINER / REJECTED / UNAVAILABLE / DISABLED / TOO_FAR` |

### 6.2 远处容器代开（对应 SGU 的 `OpenInventoryPacket`）

1. 客户端 `ChestTrackerAdapter` 选中候选容器后，若区块未加载或距离超过 5 格，
   走 `RemoteContainerClient.request(...)`，而不是发一次注定被服务端拒绝的右键包。
2. 服务端 `ServerRemoteContainer`：校验维度/世界边界/距离（默认 128 格）/请求频率（5 tick 一次）→
   `addTicketWithRadius(TicketType.UNKNOWN, chunk, 2)` 加载区块 → 调用
   `BlockState#useWithoutItem`，和玩家自己右键完全同一条原版路径。
   原版随即发出 `ClientboundOpenScreenPacket`，客户端拿到真实的容器界面，后续取物仍是原版点击包。
3. 让界面活下来的三处放行（少一处就会「请求成功但界面立刻消失」）：
   - `MixinServerPlayer`：`ServerPlayer#tick` 里的 `AbstractContainerMenu#stillValid` 对本模组会话直接返回 true；
   - `MixinContainer`：`Container#stillValidBlockEntity` 对本模组会话放行（箱子自身的 4 格校验）；
   - `MixinChestMenu`：`ChestMenu#removed` 里跳过 `Container#stopOpen`，避免远处刷一次开合事件。
4. 会话在玩家关界面 / 掉线 / 容器方块消失时清除（`MixinServerPlayer` 的 `disconnect`、`doCloseContainer`，
   以及 `MixinServerLevel` 每 tick 的方块检查），区块票据随后释放。

### 6.3 放置 / 破坏回执

`MixinServerPlayerGameMode` 在 `useItemOn`、`destroyBlock` 返回时发回执。客户端只在
「服务端明确拒绝 + 本地正等这一格确认」时行动：记一次失败、让扫描缓存失效、把该格放进
`placeCooldown` 冷却，避免对着保护区/越权位置每秒重试几十次。成功回执不做事 —— 方块的权威状态仍以
区块更新为准。

### 6.4 服务端配置

`config/litematica-printer-server.json`（服务端独立配置，不依赖 MaLiLib）：

```json
{
  "remoteContainer": true,
  "remoteContainerMaxRange": 128,
  "interactionAck": true,
  "chunkLoadTimeoutTicks": 100
}
```

关掉 `remoteContainer` 后客户端会收到 `DISABLED` 并放弃该候选；服务端未装本模组时客户端连
`open_container` 都发不出去（`canSend` 为 false），行为与纯客户端版完全一致。

### 6.5 安全边界

- 只对 `Container` 方块实体生效，且必须通过世界边界与距离校验；
- 每玩家 5 tick 最多一次远程开容器请求；
- 没有 OP/白名单判断 —— 与 SGU 一致，服务端默认信任装有本模组的客户端；
  需要更严格的控制时关掉 `remoteContainer` 或调小 `remoteContainerMaxRange`。
