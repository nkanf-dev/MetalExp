# 2026-04-12 黑屏与 world terrain native 主链修复计划

## 目标

- 消除当前进入世界后的全黑主画面。
- 修正已确认错误的纹理上传参数转发，避免继续污染 atlas / 纹理上传链。
- 让 native world terrain 渲染路径至少稳定输出非黑画面，并在此基础上继续压低帧耗。

## 本轮问题收敛

1. `ByteBuffer` 版本的 `writeToTexture(...)` 参数转发仍然存在语义错位。
2. 现有 `[MetalExp][world-inputs]` atlas 采样日志读取的是 Java 侧 CPU 镜像，不能直接证明 native atlas 为空。
3. 当前更高概率的主阻塞是 `native/src/metalexp_world_pipeline.m` 与 Mojang `terrain.vsh/fsh` 语义仍未完全对齐，导致实际 world draw 输出为黑。

## 实施步骤

1. 修正 `MetalCommandEncoderBackend.writeToTexture(GpuTexture, ByteBuffer, ...)` 到 `MetalTexture.writeRegion(...)` 的参数映射。
2. 在 `MetalRenderPassBackend` 增加一次性 world draw 输入诊断，直接记录真实 world terrain draw 的关键 uniform / 顶点解释结果，避免继续依赖失真日志。
3. 对齐 `metalexp_world_pipeline.m` 与 Mojang `terrain.vsh/fsh`：
   - 核对顶点格式解释
   - 核对 lightmap 采样
   - 核对 fog / chunk visibility / 纹理采样语义
   - 必要时补齐 alpha cutout / translucent 状态
4. 增加或更新 `MetalDeviceBackendTest` 回归测试：
   - 锁住 `ByteBuffer writeToTexture` 参数转发
   - 锁住 world terrain native draw 至少能产出非黑像素
5. 运行：
   - `./gradlew test --tests dev.nkanf.metalexp.client.backend.MetalDeviceBackendTest`
   - `./gradlew build`
   - `./gradlew runClient --args='--quickPlaySingleplayer "New World"'`

## 验收标准

- 世界主画面不再全黑。
- 日志中 world terrain native path 继续命中，不出现新的 silent fallback。
- `MetalDeviceBackendTest` 与 `./gradlew build` 通过。
- 性能方面先确认从“黑屏但 GPU 拉满且极卡”推进到“能正常输出世界画面”；若仍达不到高帧率，再进入下一轮专门的 GPU/CPU profile 与批处理优化。
