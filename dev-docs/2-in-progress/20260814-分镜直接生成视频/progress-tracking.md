# 分镜直接生成视频进度

## 当前状态

- [x] 确认交互：批量固定串行，失败暂停。
- [x] 确认参数：统一模型，每条使用自身提示词与时长，参考图自行选择。
- [x] 完成前端实现。
- [x] 完成定向验证。

## 变更文件

- `ai-fusion-video-web/app/(dashboard)/projects/[id]/storyboards/_components/video-gen-dialog.tsx`
- `ai-fusion-video-web/app/(dashboard)/projects/[id]/storyboards/_components/direct-video-generation-dialog.tsx`
- `ai-fusion-video-web/app/(dashboard)/projects/[id]/storyboards/_components/storyboard-ref-panel.tsx`

## 验证结果

- TypeScript `tsc --noEmit`：通过。
- 定向 ESLint：无错误；保留既有 `video-gen-dialog.tsx` 的 `<img>` 性能提示。
- 独立代码审阅：已修复离开页面后可能继续提交后续任务、失败暂停与手动停止状态混淆的问题。
- 完整 `pnpm lint`：因本地依赖同步检测到被策略忽略的 `msw`、`sharp`、`unrs-resolver` 构建脚本而未执行，和本次改动无关。
