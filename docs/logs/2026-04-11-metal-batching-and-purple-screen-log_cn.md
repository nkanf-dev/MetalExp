# 2026-04-11 Metal 批量提交与紫屏卡死排障日志

## 摘要

这篇日志记录的是一次很典型的“局部优化看起来完全正确，但真正跑起来以后却把系统整体语义改坏了”的图形后端排障过程。

从外部看，这几轮工作的主题很简单：

- 修 GUI atlas 透明和按钮缺图
- 把 atlas 合成从 CPU 假路径改成 native Metal
- 继续把性能敏感路径留在 native
- 优化资源重载和启动时的卡顿

但实际走下来，这条线经历了三次不同性质的根因切换：

1. 最开始的 GUI 异常，根因并不在 shader 或 blend，而在 atlas 合成链路
2. atlas 合成虽然被接回了 native，但如果按“每个 sprite 一次独立提交”的方式实现，会把启动阶段拖慢到不可接受
3. 当我们进一步把提交模型改成批量提交后，又踩到了 Minecraft 自己的 `CommandEncoder` 使用语义，最终表现成一整屏紫色并且窗口完全卡死

对外来说，这篇文章是一次开发日志。  
对内来说，它也是一次完整的技术复盘，记录了这次 Metal backend 在 atlas、command submission、surface present 三层上的行为修正。

---

## 一、问题是怎么一路演化的

这几轮修复不是一开始就直奔“紫屏卡死”去的。

时间线大致可以概括成这样：

1. 先看到 GUI 某些按钮和面板区域像是“采到了透明像素”
2. 然后定位到 atlas 本身在 Metal 路径里没有被正确合成
3. 接着把 `animate_sprite_blit` 从 `no-op` 接成真实路径
4. 首版真实路径是 CPU-side atlas blit，图像能修好，但性能非常差
5. 于是把 atlas 合成下推到 native Metal
6. native 路径虽然正确，但每个 sprite 一次独立 command queue / command buffer / wait，atlas 构建变得更慢
7. 接下来把 GUI draw、atlas blit、surface blit 全部改成 command batching
8. batching 看起来把 atlas 阶段时间压下来了，但运行时又出现了紫屏卡死
9. 最后才确认，紫屏并不是 atlas 回归，而是 present 阶段的跨 `CommandEncoder` 提交语义被我们改坏了

如果只看最后一个现象，很容易会觉得：

- 是不是渲染线程死锁了
- 是不是 drawable 没拿到
- 是不是 surface present 出问题了
- 是不是 native command buffer 没 commit

这些方向都不算错，但真正有价值的地方是把“本轮症状”放回整个改动链里看。

因为紫屏不是孤立出现的，它是前一轮性能优化的直接后果。

---

## 二、先回顾一下：GUI atlas 最初为什么会坏

前一篇日志已经详细写过 atlas 修复的前半段，这里只保留和这次工作直接相关的部分。

最开始看到的外部症状是：

- Metal backend 能启动
- 主菜单大体能出来
- 但 GUI 某些区域明显像透明了

一开始最自然的怀疑都会落在渲染末端：

- fragment shader
- blend state
- premultiplied alpha
- 采样器状态
- GUI 顶点格式

后来通过直接检查 atlas 采样点内容，确认了一个关键事实：

- 问题不是 GUI draw pass 把正确的 atlas 画错了
- 而是 `gui.png` atlas 自己在 Metal 侧就是空的

继续往 atlas 生成链上追，最后定位到 `MetalRenderPassBackend` 对 `minecraft:pipeline/animate_sprite_blit` 的处理：

- 这条 pass 在我们的 Metal backend 里原来是直接 `return`
- atlas 纹理对象会创建成功
- 但 sprite 根本没有真正 blit 进 atlas

也就是说，GUI 最初“像透明采样”的根因，其实是 atlas 合成 pass 被我们自己跳过了。

这一步非常重要，因为它决定了后面优化不能停在 CPU 纹理写入或 shader 层，而必须真正面对 atlas 合成这条路径。

---

## 三、CPU atlas blit 为什么不够

在第一次把 `animate_sprite_blit` 修回来时，采用的是一个很直接的过渡方案：

- Java 侧按 `SpriteAnimationInfo` 解包出目标矩形、padding、mip level
- 在 `MetalRenderPassBackend` 里逐像素 sample sprite texture
- 把结果写进 atlas 对应区域
- 最后再把 atlas mip 同步回 native Metal texture

这个方案有一个明显优点：

- 它很快就能把“atlas 是空的”这个功能问题修掉

但代价也一样明显：

- atlas 合成完全发生在 CPU
- 还伴随着 `syncToNative()` 的整 mip 上传
- 如果 atlas 构建涉及很多 sprite，这条链路就会非常重

换句话说，它非常适合作为“先把画面修出来”的过渡实现，但并不符合项目本身的目标。

`MetalExp` 的目标不是写一个“Java 里用 CPU 模拟 atlas 合成，再回填 Metal 纹理”的后端。  
目标是把 Metal 真正作为 backend 语义的一部分接进去。

所以后面这轮工作才会继续往前推进：

- atlas 合成要下推到 native
- 性能敏感路径尽量留在 native
- 命令提交模型也要尽量靠近真实 GPU backend

---

## 四、为什么 native atlas blit 第一版反而更卡

把 `animate_sprite_blit` 改成严格 native 之后，图像结果是对的，但性能又出了问题。

从日志上看，最直观的现象是 atlas 创建阶段出现了明显的大空档：

- atlas 资源会一张张打印 `Created: ...`
- 但中间的时间间隔被拉长到了不可接受的程度

根因最后落在 native atlas blit 的提交模型上。

当时 `native/src/metalexp_sprite_blit.m` 的实现方式大致是：

1. 每次 `animate_sprite_blit` 进来
2. 创建一个新的 `MTLCommandQueue`
3. 创建一个新的 `MTLCommandBuffer`
4. 创建 render encoder
5. 把这一个 sprite sample 到 atlas 子区域
6. `commit`
7. `waitUntilCompleted`

这条路径单看每次调用都说得通，但放在 Minecraft atlas 构建语义里就不对了。

因为 atlas 的生成不是“偶尔一次 blit”，而是：

- 对 atlas 内很多 sprite
- 对每个 sprite
- 对每个需要的 mip level
- 持续做一连串 `ANIMATE_SPRITE_BLIT`

如果每个 sprite 都单独经历一次：

- queue 创建
- command buffer 创建
- encoder 创建
- 提交
- 同步等待

那么 atlas 构建就会退化成大量细粒度同步提交的串行流水线。

这也是为什么 native 版第一次落地之后，虽然功能正确，但整体体感反而更糟。

---

## 五、为什么这时候必须改命令提交模型

到这一步，问题已经不再是某一条 atlas pass 的局部实现细节了，而是 backend 语义本身。

我们手上的现状是：

- `MetalRenderPassBackend` 已经能把 GUI draw 委托到 native
- `animate_sprite_blit` 也已经有了严格 native 路径
- surface texture blit 同样有 native 路径

但这些 native 路径有一个共同问题：

- 它们都在“自己管自己的提交”

这意味着整个后端在逻辑上还是一个“很多个立即执行的小命令”的集合，而不是一个真正有 command recording / command submission 阶段划分的 GPU backend。

要继续优化，就必须把提交模型往真实 backend 靠：

- draw 不应该一条一条自己 `commit`
- atlas blit 也不应该一条一条自己 `wait`
- surface blit 更不应该脱离整体 command flow

所以这次最核心的一步，是把：

- GUI draw
- `animate_sprite_blit`
- surface texture blit

都改成先录进统一的 native command context，再在更高层统一提交。

---

## 六、这次做了什么：从“立即执行”改成“统一提交”

这轮改动的中心，是把 `MetalCommandEncoderBackend` 从一个几乎空壳的适配层，拉成真正承担 command batching 的骨架。

### 1. Java 侧

关键文件包括：

- `src/client/java/dev/nkanf/metalexp/client/backend/MetalCommandEncoderBackend.java`
- `src/client/java/dev/nkanf/metalexp/client/backend/MetalRenderPassBackend.java`
- `src/client/java/dev/nkanf/metalexp/client/backend/MetalSurfaceBackend.java`
- `src/client/java/dev/nkanf/metalexp/client/backend/MetalSurfaceLease.java`

新的思路是：

- `MetalCommandEncoderBackend` 提供 command context handle
- `MetalRenderPassBackend` 在 draw / drawIndexed / `animate_sprite_blit` 时，不再立即提交，而是把命令录到这个 context
- `MetalSurfaceBackend.blitFromTexture(...)` 对 native-backed texture 也尽量走同一个 context
- `CommandEncoder.submit()` 再统一触发 native command context 的提交

这一步带来的好处很直接：

- atlas 构建不再是一个 sprite 一次独立提交
- GUI draw 也不再是每个 draw 各自创建 command queue / command buffer
- native 路径终于开始具备“收集命令，再统一提交”的 backend 轮廓

### 2. Native 侧

这次 native bridge 也顺手做了重要的结构整理。

原先的单文件 `native/src/metalexp_bridge_probe.m` 已经被拆成多个职责清晰的模块：

- `native/src/metalexp_common.h`
- `native/src/metalexp_surface.m`
- `native/src/metalexp_texture.m`
- `native/src/metalexp_gui_pipeline.m`
- `native/src/metalexp_sprite_blit.m`
- `native/src/metalexp_jni.m`
- `native/src/metalexp_command_context.m`

其中和这轮 batching 直接相关的是：

- `metalexp_command_context.m`

这里新增了 native command context 概念，负责持有：

- `MTLCommandQueue`
- 活动中的 `MTLCommandBuffer`

并提供：

- create
- submit
- release
- “按 context 获取当前 command buffer”

这样 `drawGuiPass`、`blitAnimatedSprite`、`blitSurfaceTexture` 就都不需要再各自创建新的 command queue / command buffer，而是编码进同一个提交上下文。

### 3. 进一步的 queue 复用

在 command batching 接通之后，又顺手把 queue 层再压了一层：

- command context 不再每次自己新建 `MTLCommandQueue`
- 而是复用共享 queue

这样可以进一步减少创建开销，也避免把本来应该聚合的工作拆回去。

从日志表现上看，这一步和前一步叠加后，atlas 构建阶段的时间已经被明显压下来了。

---

## 七、atlas 变快了，但为什么会冒出紫屏卡死

如果故事到这里结束，这会是一篇很标准的“native batching 优化成功”日志。

但真正有价值的部分，恰恰是后面出现的回退。

在 atlas 创建阶段明显变快之后，运行时又出现了一个新问题：

- 窗口会停在一整屏紫色
- 程序看起来像完全卡死
- atlas 创建日志本身却又是正常推进的

这类症状最容易让人直觉地怀疑：

- drawable acquire / present
- main target clear
- surface blit
- command buffer commit
- render thread 死锁

这些方向都不能说错，但真正的突破点来自一个更具体的问题：

> Minecraft 自己到底是怎么使用 `CommandEncoder` 的？

如果不先回答这个问题，就很容易把注意力放在我们自己写的 native 代码上，而忽略上层调用约定已经变了。

---

## 八、真正的根因：Minecraft 的 present 流程会创建两个不同的 `CommandEncoder`

这次紫屏卡死的关键，不是猜出来的，而是直接通过反编译上游 client jar 确认的。

我反编译了 Minecraft `26.2-snapshot-1` 的相关类，重点看了：

- `com.mojang.blaze3d.systems.CommandEncoder`
- `com.mojang.blaze3d.systems.RenderPass`
- `com.mojang.blaze3d.systems.GpuSurface`
- `net.minecraft.client.Minecraft`

反编译结果里，present 这段逻辑非常关键：

1. `windowSurface.blitFromTexture(...)` 时，会新建一个 `CommandEncoder`
2. 之后真正 `submit()` 时，又会新建另一个 `CommandEncoder`
3. 然后才调用 `windowSurface.present()`

也就是说，Minecraft 并不是：

- 一个 encoder 录 blit
- 同一个 encoder 再 submit

而是：

- 第一只 encoder 负责 `blitFromTexture`
- 第二只 encoder 负责 `submit`

这和我们刚做完的 batching 假设正好冲突。

因为当时 pending command context 是挂在单个 `MetalCommandEncoderBackend` 实例上的。

于是问题就发生了：

- 第一只 encoder 录下了 onscreen blit
- 第二只 encoder 并不知道第一只 encoder 手里还有待提交命令
- 第二只 encoder 的 `submit()` 提交的是它自己的空 context
- 结果真正的 screen blit 从来没有被提交到 drawable

外部看到的效果，就变成了：

- atlas 资源正常构建
- 后台逻辑继续推进
- 但真正上屏的那一步没有发生
- 窗口只剩一个未被后续渲染覆盖的紫色底

这就是这次紫屏卡死的真正根因。

它不是单纯的 native 崩溃，也不是 atlas 合成坏了，而是我们对上游 `CommandEncoder` 生命周期的假设错了。

---

## 九、修复方式：把 pending command context 从 encoder 实例级提升到 lease 级共享状态

一旦根因确认，修法就非常明确了。

既然 Minecraft 同一帧里会创建多个不同的 `CommandEncoder`，那么 pending command context 就不能再只属于某一个 encoder 实例。

这次最终的修复是：

- 把 pending command context 从 `MetalCommandEncoderBackend` 实例内部移出去
- 提升到 `MetalSurfaceLease` 这层共享状态

这样同一帧里无论创建多少个 `CommandEncoder`：

- 它们最终都会拿到同一个 pending native command context
- 第一只 encoder 录下的 `blitFromTexture`
- 可以由第二只 encoder 的 `submit()`
- 在同一个 shared context 上真正提交出去

关键改动点包括：

- `src/client/java/dev/nkanf/metalexp/client/backend/MetalCommandEncoderBackend.java`
- `src/client/java/dev/nkanf/metalexp/client/backend/MetalSurfaceLease.java`

这里的职责调整后变成：

- `MetalCommandEncoderBackend.commandContextHandle()` 从 lease 取 shared pending context
- `MetalCommandEncoderBackend.submit()` 也提交 lease 持有的 shared pending context
- `MetalSurfaceLease` 负责这块 context 的创建、提交、释放

从 backend 语义上看，这比前一版也更合理：

- pending GPU work 属于“当前 surface / 当前 frame 的共享提交状态”
- 而不只是“某个 Java wrapper 实例的私有状态”

---

## 十、这次补了什么测试

如果这类问题只靠手动 `runClient` 看画面，很容易重复回归。

所以这次除了原有 atlas 回归和 batching 测试之外，又补了一个非常贴合本次 bug 形状的测试：

- 一个 `CommandEncoder` 只做 `surface.blitFromTexture(...)`
- 另一个全新的 `CommandEncoder` 再单独执行 `submit()`

期望行为是：

- 第一个 encoder 录下的 pending surface blit
- 能被第二个 encoder 的 `submit()` 真正 flush 出去

这个测试现在已经进了：

- `src/test/java/dev/nkanf/metalexp/client/backend/MetalDeviceBackendTest.java`

它锁住的是一条非常容易被忽略、但和 Minecraft 实际渲染语义高度相关的契约：

> “同一帧内跨 encoder 的 pending GPU 命令也必须能被正确提交。”

这类测试价值很高，因为它不是在测一个抽象 API，而是在测我们对上游调用方式的理解有没有偏。

---

## 十一、验证结果

这轮工作做完之后，主要验证了三类东西。

### 1. 单元和集成测试

运行过：

- `./gradlew test --tests dev.nkanf.metalexp.client.backend.MetalDeviceBackendTest`

结果通过。

### 2. 构建

运行过：

- `./gradlew build`

结果通过。  
构建里仍然只有现有的 Java native-access warning，没有新的失败。

### 3. 运行时行为

`./gradlew runClient` 做了多轮冒烟观察。

从这几轮日志来看，atlas 构建阶段已经从早前那种明显的大空档，压到了一个更合理的区间。

例如最近一轮里：

- `11:23:17` 同一秒内连续创建了
  - `particles.png-atlas`
  - `decorated_pot.png-atlas`
  - `armor_trims.png-atlas`
  - `paintings.png-atlas`
  - `shield_patterns.png-atlas`
  - `blocks.png-atlas`
  - `chest.png-atlas`
  - `celestials.png-atlas`
  - `banner_patterns.png-atlas`
  - `beds.png-atlas`
  - `items.png-atlas`
  - `gui.png-atlas`
  - `map_decorations.png-atlas`
  - `signs.png-atlas`
  - `shulker_boxes.png-atlas`

这和此前 atlas 创建被几十个同步小提交拖慢的状态相比，已经是明显改善。

更重要的是：

- 这次导致整屏紫色并卡住的跨 encoder 提交问题已经被修正
- 对应的行为也有回归测试覆盖

---

## 十二、这次工作的工程意义

如果只看 commit 列表，这几次改动可能会像一串普通修修补补：

- 修 atlas
- 做 native blit
- 做 batching
- 修紫屏

但从 backend 设计的角度看，这其实是一次很实在的边界收敛。

这次我们明确了三件事：

1. `animate_sprite_blit` 不能继续当作脚手架 pass 忽略  
   它是 atlas 合成链的一部分，不接上就会让整条 GUI atlas 路径名存实亡。

2. 性能敏感路径不能只“变成 native”，还要有正确的 submission model  
   否则 native 也一样会被大量同步小提交拖垮。

3. 对上游调用契约的理解和对本地实现同样重要  
   如果不知道 Minecraft 会在同一帧里创建多个不同的 `CommandEncoder`，就很容易把 batching 实现成一个只在自己假设里成立的系统。

这也是为什么我会觉得这次排障很有代表性。

它不是某个 isolated bug 的修补，而是一次把：

- atlas 语义
- native command submission
- surface present 语义

重新对齐到同一套 backend 模型里的过程。

---

## 十三、下一步

虽然这轮已经把最明显的问题解决了，但还远远没到“性能工作结束”的程度。

接下来更值得做的方向包括：

- 对 GUI / atlas / present 阶段分别加更明确的 native timing
- 继续检查是否还有不必要的 CPU-side texture sync
- 评估 command context 进一步向更完整的 frame scheduler 演进的可能
- 继续把性能敏感路径尽量沉到 native，而不是在 Java 侧堆更多临时逻辑

更直接一点说：

我们现在已经把系统从“功能能跑但 atlas 是空的”，推进到了“功能能跑、atlas 有内容、批量提交开始成型、并且修掉了跨 encoder 的 present 语义坑”。

这还不是终点，但已经足够说明一件事：

Metal backend 的问题，正在越来越少地表现成“完全没接上”，而越来越多地表现成“接上以后，必须认真处理 backend 语义和性能模型”。

这其实是个好迹象。

说明我们终于从“能不能跑”这层，开始进入“怎么把它做对”这层了。
