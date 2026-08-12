# Grok 视频代理支持开发计划

## 目标

让 OpenAI 视频协议兼容 CLIProxyAPI 的 xAI/Grok Video 模型，同时保持 Sora 现有行为不变。

## 实现范围

- 识别 `grok-imagine-video` 及其变体和 CLIProxyAPI 支持的模型前缀。
- Grok 提交请求使用 JSON，首帧使用 `image`，多参考图使用 `reference_images`。
- 所有图片输入转换为 Data URI，避免上游无法访问本地或内网地址。
- Grok 任务不请求 Sora 缩略图接口，视频内容仍沿用现有任务轮询与下载流程。
- 增加 Grok CLIProxyAPI 模型预设和协议测试。

## 验证计划

- 运行 `GrokCliProxyVideoProtocolAdapterTests`。
- 回归运行 `OpenAiAndAgnesVideoStrategyTests`。
