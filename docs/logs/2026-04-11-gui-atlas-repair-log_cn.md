# 2026-04-11 GUI Atlas 修复纪实

## 从“按钮是透明的”到“atlas pass 被我们自己跳过了”

这篇日志记录一次非常典型、也非常容易把人绕进去的图形后端排障过程。

表面现象很简单：

- Metal 后端能够启动
- Minecraft 能进入主菜单
- GUI 管线也确实在跑
- 但一部分按钮、面板和 GUI 贴图表现得像“采样到了透明像素”

如果只看最后一帧，最自然的直觉会是：

1. shader 出错了
2. blend state 出错了
3. UV 算错了
4. 采样器状态和 OpenGL/Vulkan 不一致

这几个方向都合理，而且每一个都足够“像根因”。但这次真正的问题，最后落在了更早的一层：

不是 GUI draw pass 把正确的 atlas 采错了，而是 `gui.png` atlas 在 Metal 路径里根本没有被正确合成出来。

更准确地说，是 atlas 合成所依赖的 `animate_sprite_blit` render pass，在我们的 Metal backend 中被直接 `no-op` 掉了。

这篇文章会按时间顺序，把这次修复的来龙去脉完整写清楚。

---

## 一、最初症状：GUI 纹理看起来像透明了

问题最早不是从代码里发现的，而是从运行效果里发现的。

Metal 后端已经可以启动，资源加载也能过，主界面也能出来，但某些 GUI 元素明显不对：

- 按钮区域没有正常显示应有的底图
- 某些组件只剩下文字或轮廓
- 贴图表现不像“采样偏了”，更像“采到了 alpha 为 0 的像素”

在一个图形后端项目里，这种现象很危险，因为它会把人天然带向渲染末端：

- 会去怀疑 fragment shader
- 会去怀疑 blend equation
- 会去怀疑 premultiplied alpha
- 会去怀疑 sampler address mode
- 会去怀疑 GUI 顶点格式或索引提交

这些怀疑并不离谱。事实上，如果没有继续向前追踪数据来源，它们甚至是最“像样”的解释。

但这类问题有一个老规律：

如果你能直接在 CPU 侧或纹理内存侧看到“采样点本身就是透明”，那么后面的 shader、blend、render pass 通常都不是主因。

所以这次排障从一开始就决定，不在抽象层面来回猜，而是尽快去抓纹理内容本身。

---

## 二、第一轮假设：是不是 GUI draw pass 本身有问题

最开始优先检查的是 `MetalRenderPassBackend` 里的 GUI 提交流程。

这里的直觉是合理的，因为 GUI 渲染路径此前已经做过几次扩展：

- 支持普通 `GUI`
- 支持 `GUI_TEXTURED`
- 支持 `PANORAMA`
- 后来又补上了 `GUI_OPAQUE_TEXTURED_BACKGROUND`
- 再后来又补了 `GUI_TEXTURED_PREMULTIPLIED_ALPHA`

这些管线之间共享一部分状态，但又不完全相同。如果这里有任何一个地方在 Metal 桥接时做错，最终表现出来都可能是“看起来贴图透明了”。

因此第一轮排查做了几件事：

- 补齐 GUI 相关管线种类解析
- 补齐非 indexed draw 的 native bridge delegation
- 校验 `Sampler0` 绑定是否存在
- 校验 `dynamicTransforms` 等 uniform 是否被正确传递

这轮工作不是白做的。它确认了两件重要的事：

1. GUI native draw path 并没有在第一时间崩掉
2. `Sampler0` 确实指向了 `minecraft:textures/atlas/gui.png`

但它没有解释“为什么采到的是透明像素”。

换句话说，draw pass 至少不是唯一的问题。

---

## 三、第二轮假设：是不是写纹理时把 atlas 某些区域写丢了

接着往前追，就到了 `MetalTexture.writeRegion(...)` 这条 CPU 纹理写入路径。

这一层值得怀疑，是因为 atlas 上传和子区域上传通常都非常依赖这几个语义：

- 源缓冲区每像素字节数
- 源图总宽度
- 每行有效像素跨度
- `srcX/srcY`
- `dstX/dstY`
- mip level

只要“源图宽度”和“行跨度”这两个概念在实现里混掉一次，就很容易出现这种局面：

- 小纹理测试全都能过
- 简单整图上传能过
- 但 atlas 里某个偏移子矩形一写就错
- 错出来的结果往往不是花屏，而是读取到了意料之外的全零区域

当时很快在 `MetalTexture.writeRegion(...)` 里发现了一个明显的不一致：

- 方法签名有一层已经改成了 `sourceRowLength`
- 但内部转发和实现还残留着旧的 `sourceWidth` 语义

于是先做了一个表面上很像正确答案的修复：

- 把重载参数统一成 `sourceRowLength`
- 让 source stride 使用更保守的计算方式
- 补一个带偏移子区域上传的回归测试

这一轮并不是错误修复，它确实修掉了一个真实 bug。

新增的测试也证明了这一点：

- 偏移子区域上传不再错误读到前一段数据
- `writeRegion` 的行跨度语义比之前更稳

但问题在这里：

`./gradlew runClient` 之后，GUI 依然有问题。

也就是说，这个 bug 是真的，但它不是这次 GUI atlas 透明问题的最终根因。

这一步是整次排障里第一个“很像终点，但其实只是岔路口”的地方。

---

## 四、关键证据：直接读 `gui.png` atlas 内存，发现采样点就是 `(0,0,0,0)`

真正把方向扳正的，不是更多推理，而是一条非常直白的证据链。

在 `MetalRenderPassBackend` 里临时加了 sampler 调试日志，直接读取：

- 当前 GUI textured pipeline
- 当前绑定的 atlas label
- atlas 尺寸
- 一个按钮 UV 对应附近的采样点
- 这个采样点在我们 CPU 纹理存储里的 RGBA

目标点选的是接近主菜单按钮区域的一处采样位置：

- `u ≈ 0.80`
- `v ≈ 0.01`
- 落在 `gui.png` atlas 的 `(819, 10)` 附近

第一次抓到的结果非常关键：

`sample@(819,10)=(0,0,0,0)`

这条日志直接改变了问题的性质。

在这之前，问题看起来像：

- “draw pass 把正确 atlas 画错了”

在这之后，问题变成了：

- “atlas 本身在 Metal 侧就是空的”

这是整次排障的第一个真正意义上的铁证。

它意味着：

- shader 不是第一嫌疑
- blend 不是第一嫌疑
- GUI draw native bridge 不是第一嫌疑
- atlas 创建成功，不代表 atlas 内容被正确填充

如果一个采样点在 CPU 纹理存储里本身就是 0，那么后面所有图形学推理都没有必要先做了。

必须把视线继续往 atlas 生成路径前移。

---

## 五、又一个“几乎看起来像根因”的岔路：`depthOrLayers == 0`

在继续追 atlas 的过程中，又看到一个非常可疑的现象：

日志里 atlas 创建会打印类似：

- `Created: 1024x1024x0 minecraft:textures/atlas/gui.png-atlas`

这个最后的 `0` 很刺眼，因为在我们自己的 `MetalTexture` 实现里：

- `depthOrLayers` 会直接参与 mip storage 分配
- native texture 创建也会拿到这个参数
- `syncMipToNative()` 还会按 `getDepthOrLayers()` 循环上传 layer

如果普通 2D 纹理真的被当成了“0 层纹理”，那会是个严重问题。

所以这里也花了一轮时间去对：

- Mojang `GpuTexture`
- GL 侧实现
- Vulkan 侧实现
- 我们自己的 `MetalTexture`

最后确认下来：

- 这个现象值得继续警惕
- 但它并不能解释当前 `gui.png` atlas 为什么整个区域是空的
- 尤其是结合后续证据看，它不是这次最核心的断点

这一步很典型：排障时会遇到很多“看起来不对”的东西，但不等于它们就是当前症状的主因。

如果没有前面那条“采样点本身是 0”的证据，很容易在这里继续深挖半天。

---

## 六、真正的转折点：`animate_sprite_blit` 在 Metal backend 里被直接 `return`

真正的根因最终出现在 `MetalRenderPassBackend.draw(...)`。

代码里原来有这样一段专门判断：

- 如果当前 pipeline 是 `minecraft:pipeline/animate_sprite_blit`
- 直接 `return`

旁边甚至还有一条注释，意思非常直白：

“Sprite atlas blit still needs a dedicated native path. Keep this no-op for now.”

这句话本身就几乎是答案。

因为只要 atlas 的生成确实依赖这个 pipeline，那么“atlas 创建成功但内容没写进去”就完全说得通了。

接下来要确认的就只有一件事：

`gui.png` atlas 的生成，是不是真的走 `ANIMATE_SPRITE_BLIT`？

答案是：是的。

通过反编译和调用链追踪，最终确认：

1. `TextureAtlas.uploadInitialContents()` 会为每个 sprite 建立临时 `GpuTexture`
2. 每个 sprite 的首帧会先通过 `uploadFirstFrame()` 上传到自己的临时纹理
3. 然后 atlas 对每个 mip level 建立 render pass
4. render pass 使用 `RenderPipelines.ANIMATE_SPRITE_BLIT`
5. 每个 sprite 会绑定到 `Sprite` sampler
6. `SpriteAnimationInfo` uniform 会告诉 shader：
   - atlas projection matrix
   - sprite matrix
   - padding
   - mip level
7. 最后调用 `draw(0, 6)`，把 sprite blit 到 atlas

而我们的 Metal backend 在这一步做了什么？

- 它看见 `animate_sprite_blit`
- 然后什么都没做，直接返回

于是就得到一个非常干净的因果链：

- atlas 纹理对象被创建了
- sprite 临时纹理也被上传了
- 但 sprite 从未真正合成进 atlas
- 之后 GUI draw 再怎么正确采样，也只会从空 atlas 里采到透明值

到这里，问题第一次从“怀疑”变成了“闭环”。

---

## 七、修复策略：不等原生 atlas blit，先把缺失的 pass 补成 CPU 合成

既然问题已经定位到 `animate_sprite_blit` 被 no-op，下一步就不是“还猜什么”，而是“先用最小、最可验证的方式把它补起来”。

有两种大方向：

### 方案 A：立刻补一个新的 native Metal atlas blit 接口

优点：

- 理论上性能更好
- 更接近最终目标

缺点：

- 需要继续扩展 native bridge
- 需要同步增加 Objective-C/Metal 侧实现
- 调试面更大
- 当前里程碑里不是最快验证路径

### 方案 B：先在 Java/CPU 侧实现 atlas blit，然后同步回 native texture

优点：

- 改动面小
- 完全在现有 Java backend 范围内完成
- 最容易快速验证“症状是否消失”

缺点：

- 性能不够理想
- 只是过渡方案

在当前阶段，显然应该选 B。

因为这次最重要的不是一口气做完最终形态，而是先把 atlas 生成功能从“缺失”恢复成“正确”。

所以最终选择的修复思路是：

1. 在 `MetalRenderPassBackend.draw(...)` 里拦截 `animate_sprite_blit`
2. 从 `SpriteAnimationInfo` uniform 里解析：
   - `SpriteMatrix`
   - `UPadding`
   - `VPadding`
   - `MipMapLevel`
3. 根据 sprite 目标矩形和 padding，直接在 atlas 的 CPU 存储里做一遍 blit
4. 写完后把该 mip 同步回 native Metal texture

这是一条非常“工程化”的修法：

- 不浪漫
- 不最终
- 但有效

在 bootstrap 阶段，这样的修法反而是对的。

---

## 八、实现细节：这次具体改了什么

### 1. `MetalTexture.writeRegion(...)` 统一源步幅语义

虽然这不是最终根因，但这条修复保留下来了，因为它本身就是对的。

做的事情包括：

- 把 `sourceWidth` 重命名并统一为 `sourceRowLength`
- 修正重载之间的参数转发
- 让 source stride 在 `srcX + width` 超过传入跨度时自动扩展

这个修复解决了“偏移子区域上传时可能读错行”的问题。

### 2. `MetalTexture` 增加包内 `syncToNative(mipLevel)`

atlas 合成是在 CPU 侧直接写 `mipStorage`，所以需要一个明确入口把某个 mip 刷回原生纹理。

这次没有暴露更大的写接口，只是增加了一个很小的包内方法：

- `syncToNative(int mipLevel)`

它本质上只是把已有的 `syncMipToNative(...)` 暴露给同 package 的 render pass 使用。

### 3. `MetalRenderPassBackend.draw(...)` 不再把 `animate_sprite_blit` 当成 no-op

这是整次修复的核心。

之前逻辑是：

- 看到 `minecraft:pipeline/animate_sprite_blit`
- 直接返回

现在改成：

- 看到 `animate_sprite_blit`
- 调用 `drawAnimateSpriteBlit()`

### 4. 新增 `drawAnimateSpriteBlit()`

这个方法现在会：

- 读取 `Sprite` 绑定纹理
- 读取 `SpriteAnimationInfo` uniform buffer
- 解析源 mip、目标位置、目标尺寸、padding
- 用 `CLAMP_TO_EDGE + NEAREST` 的语义从 sprite 临时纹理采样
- 逐像素写入 atlas 的 CPU 纹理存储
- 完成后同步回 native mip

这套实现不是最优，但它和 Mojang 这条 pipeline 的语义是一致的：

- 顶点是 `gl_VertexID` 生成的全屏局部六边形
- `ProjectionMatrix * SpriteMatrix` 负责把局部 `0..1` quad 变换到 atlas 像素空间
- `UPadding/VPadding` 负责做边缘扩展采样

### 5. 补了两个关键测试

这次新加的测试有两类：

- `writeRegionExpandsUndersizedSourceRowLengthForOffsetSubregions`
  - 锁住源步幅/子区域上传语义

- `animateSpriteBlitDrawCopiesSpriteIntoAtlasTarget`
  - 直接验证 `ANIMATE_SPRITE_BLIT` 能把 source sprite 写进 atlas 目标

这两类测试分别防回归两个不同层次的问题：

- 普通纹理上传
- atlas 合成 pass

---

## 九、验证：同一个采样点从 `(0,0,0,0)` 变成了 `(111,111,111,255)`

修完之后，最重要的不是“感觉好像好了”，而是回到最初那条证据链上去验证。

仍然读取同一个位置：

- atlas：`minecraft:textures/atlas/gui.png`
- mip：`0`
- 采样点：`(819, 10)`

修复前：

`sample@(819,10)=(0,0,0,0)`

修复后：

`sample@(819,10)=(111,111,111,255)`

这条结果比“肉眼看着按钮回来了”更重要，因为它说明：

- atlas 内容本身已经不是空的
- 采样数据源头发生了实质性变化
- GUI draw pass 终于拿到了真正应该被采样的 atlas 数据

同时还完成了以下验证：

- `./gradlew test --tests dev.nkanf.metalexp.client.backend.MetalDeviceBackendTest`
- `./gradlew build`
- `./gradlew runClient`

都可以顺利通过或启动。

从排障角度说，这已经足够确认本次修复命中了真正根因。

---

## 十、为什么这次修复会让人感觉“终于对了”

因为它满足了一个排障中非常重要的标准：

不是“某个地方改完，现象减轻了”，而是：

1. 先抓到了可重复的、直接的坏数据
2. 再确认坏数据来源于哪条具体管线
3. 最后把那条缺失管线补回去
4. 再回到同一个观察点，看坏数据是否消失

这和“调一圈 blend state，最后好像差不多了”完全不是一回事。

这次真正闭环的证据链是：

- GUI 采样异常
- atlas 采样点本身为零
- atlas 生成依赖 `ANIMATE_SPRITE_BLIT`
- Metal backend 把该 pass `no-op`
- 补上该 pass
- 同一点采样恢复为非零

这是一条非常干净的链。

---

## 十一、还没有完成的部分

虽然这次已经把“GUI atlas 为空”这个关键问题修掉了，但它不是最终形态。

当前实现仍然有几个现实限制：

### 1. `animate_sprite_blit` 目前是 CPU 合成，不是 native Metal pass

这意味着：

- 正确性先恢复了
- 但性能可能还不够好

尤其在 atlas 创建、资源重载和动画帧更新频繁时，这条路径仍然可能带来明显开销。

### 2. 调试日志还留着

为了抓证据，这次在 `MetalRenderPassBackend` 里留下了 sampler 调试输出。

它们在定位问题时非常有用，但不该长期保留在默认运行路径里。

后续应该：

- 删除它们，或者
- 收敛到显式 diagnostics 开关下

### 3. `ANIMATE_SPRITE_INTERPOLATE` 也需要专门审视

这次解决的是 atlas blit 的核心缺口，但动画贴图插值路径也值得继续梳理：

- 它是否同样需要专门的 Metal fast path
- 是否应该复用同一套 atlas CPU 合成框架
- 是否会在运行时成为卡顿来源

这些都还是下一阶段工作。

---

## 十二、这次修复给项目的几个教训

### 教训 1：在 bootstrap 阶段，缺失的 pass 比错误的 shader 更常见

做一个后端 bootstrap 时，很容易把注意力全放在“是否能画出来”上。

但现代 Minecraft 的渲染管线里，很多纹理不是“从资源直接上传然后采样”这么简单。

atlas、动画、过渡合成、screen blit，本身就是完整的 render pass。

缺任何一条，都会表现成“像是采样错了”，但本质上是“资源根本没生成”。

### 教训 2：抓数据比猜状态更重要

这次如果没有直接读 atlas 内存，很可能会在以下方向上浪费更多时间：

- blend state
- premultiplied alpha
- GUI vertex format
- UV 变换
- sampler address mode

而直接去看：

- atlas label
- atlas 尺寸
- atlas 某个固定采样点的 RGBA

几分钟内就把问题性质改写了。

### 教训 3：过渡实现未必优雅，但必须可验证

当前 `drawAnimateSpriteBlit()` 不是最终理想实现。

但它有两个决定性优点：

- 很快可以写出来
- 很快可以验证是否命中根因

在实验性后端项目里，这往往比“一开始就追求漂亮的终态接口”更重要。

---

## 十三、下一步建议

如果把这次修复看成一个阶段性 checkpoint，那么后面最值得做的事情大致有三类：

### 1. 把 atlas CPU blit 优化成更便宜的路径

短期可以考虑：

- 避免逐像素 float unpack/pack
- 对无 padding 的区域走更直接的 row copy
- 对 `CLAMP_TO_EDGE + NEAREST` 情况走整数坐标 fast path

中期则可以考虑：

- 把 `animate_sprite_blit` 真正下沉到 native Metal bridge

### 2. 清理临时调试日志

确认问题彻底稳定后，应尽快移除：

- GUI sampler 诊断输出
- 临时的采样点观测逻辑

保留长期可维护的 diagnostics 开关，而不是默认打印。

### 3. 审视 atlas 与动画相关 pass 的完整覆盖率

这次暴露出的不是单点 bug，而是一类风险：

- 只要某条辅助 pass 没实现，就可能出现“主渲染能跑，但资源状态是错的”

后续应该系统性盘点：

- 还有哪些 pipeline 目前在 Metal backend 里被跳过
- 哪些会影响资源生成，哪些只是影响最终显示

这会比继续被动修症状更划算。

---

## 结语

这次修复最有价值的地方，不只是“按钮终于不透明了”。

更重要的是，它把一个非常模糊的问题，拆成了一条可重复、可验证、可回归测试的链：

- 现象
- 证据
- 缺失路径
- 修复
- 回证

在 `MetalExp` 这种还处于 bootstrap 阶段、很多路径还没全部落地的后端实验项目里，这种排障方式比单次修好一个 bug 更重要。

因为它会决定后面项目是继续靠“感觉”推进，还是靠证据推进。

这次幸运的是，我们最后拿到了足够硬的证据。

而且证据指向的地方，正好是一个我们可以在当前里程碑内稳稳补上的缺口。

这就够好了。
