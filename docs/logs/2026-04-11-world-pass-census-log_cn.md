# 2026-04-11 世界主路径 pass census 日志

## 采样方式

- 日期：2026-04-11
- 命令：`./gradlew runClient --args='--quickPlaySingleplayer "New World"'`
- 目标世界：`run/saves/New World`
- 结果：客户端成功启动、完成资源重载、启动集成服，并进入 world load 流程；随后在首个 strict fail-fast blocker 处崩溃退出。

## 首轮阻塞项

1. `minecraft:blur/0`
   - passLabel=`Post pass minecraft:blur/0`
   - colorFormat=`RGBA8_UNORM`
   - depthFormat=`D32_FLOAT`
   - drawType=`draw`
   - 结论：当前进入世界前的首个真实阻塞点是 post-process blur pass，而不是 world opaque family。

2. `minecraft:pipeline/lightmap`
   - passLabel=`Update light`
   - colorFormat=`RGBA8_UNORM`
   - depthFormat=`NONE`
   - drawType=`draw`
   - 结论：在解掉 `blur/0` 后，world entry 已进入实际世界渲染，新的前置 blocker 是 fullscreen lightmap 更新 pass。

3. `minecraft:pipeline/sky`
   - passLabel=`Sky disc`
   - colorFormat=`RGBA8_UNORM`
   - depthFormat=`D32_FLOAT`
   - drawType=`draw`
   - 结论：在解掉 `lightmap` 后，当前首个世界内渲染 blocker 已推进到 sky family。

## 本轮已清除的前置阻塞

1. `minecraft:pipeline/mojang_logo`
   - 现象：在主菜单前阶段因 GUI pipeline 分类过窄而 fail-fast。
   - 处理：恢复非 world 路径的 GUI 顶点格式推断后解除。

2. `minecraft:pipeline/animate_sprite_interpolate`
   - 现象：资源动画阶段在 atlas 动画插值 pass 崩溃。
   - 处理：并入现有 animated sprite native blit handler 后解除。

3. `minecraft:blur/0`
   - 现象：进入世界前的 post-process blur pass 因无 vertex buffer 的 fullscreen path 未实现而崩溃。
   - 处理：为 `minecraft:blur/*` 增加 Java 侧最小 post-process copy 路径后解除。

4. `minecraft:pipeline/lightmap`
   - 现象：进入世界后，lightmap 更新 pass 因 fullscreen uniform-only path 未实现而崩溃。
   - 处理：按 `lightmap.fsh` 公式补上 Java 侧 lightmap 生成路径后解除。

## world pass census 结果

- 本轮仍然没有采集到 `[MetalExp][world-pass]` 日志。
- 原因：虽然客户端已成功进入 `New World` 并开始实际世界渲染，但当前首个 blocker 仍停在 `minecraft:pipeline/sky`，尚未进入已接线的 world opaque census 点。
- 含义：下一轮优先级已从 post-process / lightmap 前置路径，推进到 world 内的 sky family。
