# 复用已有视频提示词完成总结

## 最终行为

- 目标镜头已有非空 `videoPrompt`：原样传给 `generate_video`，不再重新生成提示词。
- 视频生成成功：只更新 `videoUrl`，不传 `videoPrompt`，保护原提示词。
- 目标镜头提示词为空：继续执行原有素材裁剪、H3 模式选择和提示词生成。
- `promptOnly: true` 且已有提示词：不生成、不更新，直接跳过。

## 关键实现

- 执行 Agent 增加已有提示词优先分支，并禁止翻译、润色、拼接或覆盖。
- 调度 Agent 固化已有提示词优先原则。
- 资源约束测试覆盖复用、只更新视频地址及 promptOnly 跳过规则。

## 验证结果

- `git apply --check`：通过。
- `git diff --check`：通过。
