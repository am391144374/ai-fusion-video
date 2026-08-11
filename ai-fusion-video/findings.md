# 发现与决策（Findings & Decisions）

## 需求（Requirements）

- 分镜视频提示词应遵循 MiniMax H3 Prompt Writing Skill。
- 按输入素材选择 T2VA、I2VA、FL2VA、L2VA 或 Ref2VA。

## 研究发现（Research Findings）

- 基础模式必须依次使用 `integrated_multimodal_description`、`overall_soundscape`、`non_diegetic_music`。
- 全参考模式必须依次使用 `subject_definitions`、`summary`、`retention_analysis`、`detailed_description`、`overall_soundscape`、`non_diegetic_music`。
- 重写段落使用英文；对白、歌词和画面可见文字保留原语言。
- 实际提示词由 `storyboard_video_executor` Agent 生成，规则文件为 `src/main/resources/prompts/agents/storyboard-video-executor.system.md`；生成后的提示词通过 `update_storyboard_item_video` 保存到 `StoryboardItem.videoPrompt`。
- 现有链路已能读取首帧、尾帧、参考图、时长和模型能力；因此可在同一 Agent 提示词中按素材组合选择 H3 模式，无需新增中间服务或表字段。
- 当前规则要求中文 2–5 句，与 H3 要求的英文结构化字段直接冲突，需以用户指定的 H3 规则替换该段。
- 视频能力校验允许只传尾帧；因此可支持 H3 的 L2VA，不必继续沿用“只有尾帧即丢弃”的旧规则。
- 模型可能配置首尾帧和多模态参考互斥；提示词选择与调用参数必须遵守 `get_generation_model_capabilities` 的返回，不能假设所有素材可同时传入。
- 本机 JDK 26 可兼容编译 Java 21 目标，已用于完成定向测试。

## 技术决策（Technical Decisions）

| 决策 | 理由 |
|------|------|
| 待定位后在实际视频提示词生成入口落规则 | 防止只修改分镜说明而不影响视频生成请求 |
| 参考素材实际传入时使用 Ref2VA，否则按首尾帧选择基础模式 | 避免为被能力裁剪的素材生成无效引用，并遵守 H3 的模式定义 |

## 遇到的问题（Issues Encountered）

| 问题 | 解决方式 |
|------|----------|
| 未找到已有的提示词资源专项测试 | 后续新增针对 H3 规则的资源约束测试 |
| Maven 默认 Java 运行时为 Java 8 | 使用本机 Java 17+ 运行 Maven；Spring Boot 3.5.14 的插件 class 文件版本为 61 |
| 项目编译目标为 Java 21 | JDK 17 已通过构建插件加载，但不能执行 `release 21` 编译；需要 JDK 21 |

## 资源（Resources）

- https://raw.githubusercontent.com/MiniMax-AI/MiniMax-H3/main/skills/h3-prompt-writing/SKILL.md
- https://raw.githubusercontent.com/MiniMax-AI/MiniMax-H3/main/skills/h3-prompt-writing/references/base-en.txt
- https://raw.githubusercontent.com/MiniMax-AI/MiniMax-H3/main/skills/h3-prompt-writing/references/ref-en.txt
