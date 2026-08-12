# 分镜场次 ID 解析修复进度

- [x] 定位 `storyboardSceneId=-1` 被直接查询的问题。
- [x] 将非正数 ID 归一为未提供。
- [x] 使用有效 `storyboardItemId` 解析真实场次。
- [x] 增加场次与条目归属冲突校验。
- [x] 增加三个回归测试场景。
- [x] 完成差异格式检查。
- [ ] 执行 Maven 测试（当前环境未安装 Java/Maven）。
