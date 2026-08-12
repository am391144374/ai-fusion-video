# 视频生成 Skill 加载校验修复完成总结

## 最终范围

- 修复生成视频时 `load_skill_through_path` 的 `skill_id` 枚举为空导致的参数校验失败。
- 用户主动选择的 Skill 继续由业务层校验并完整注入系统提示词。
- 共享 Harness Agent 不再注册按用户工作区动态发现的 Skill 加载器。
- 仅在应用仓库中实际发现 Skill 时挂载并启用静态仓库 Skill。

## 关键实现

- `AgentScopeSkillRegistry` 新增 `hasSkills()`，避免把空仓库当作有效 Skill 来源。
- `AgentScopeHarnessFactory` 禁用默认工作区 Skill 与动态 Skill 加载，只对非空仓库启用静态 Skill 支持。
- 回归测试覆盖空仓库检测，以及远程工作区存在时模型工具列表仍为空的场景。

## 验证结果

- `git apply --check`：通过。
- `git diff --check`：通过。
- Maven 针对性测试：当前环境未安装 Java/Maven，未执行。

## 遗留事项
