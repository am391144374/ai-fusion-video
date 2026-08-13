package com.stonewu.fusion.service.ai;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 分镜视频执行器提示词约束测试。
 */
class StoryboardVideoExecutorPromptTests {

    @Test
    void shouldRequireMiniMaxH3ModesAndOutputStructures() throws IOException {
        // 步骤一：读取运行时真正加载的分镜视频执行器提示词。
        String prompt;
        try (InputStream input = new ClassPathResource(
                "prompts/agents/storyboard-video-executor.system.md").getInputStream()) {
            prompt = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        // 步骤二：固定 H3 五种输入模式，防止后续只保留部分首尾帧场景。
        assertThat(prompt).contains("T2VA", "I2VA", "FL2VA", "L2VA", "Ref2VA");
        assertThat(prompt).contains("仅有尾帧：使用 **L2VA**");

        // 步骤三：固定基础模式和完整参考模式的字段名称与顺序。
        assertThat(prompt).containsSubsequence(
                "integrated_multimodal_description:",
                "overall_soundscape:",
                "non_diegetic_music:");
        assertThat(prompt).containsSubsequence(
                "subject_definitions:",
                "summary:",
                "retention_analysis:",
                "detailed_description:",
                "overall_soundscape:",
                "non_diegetic_music:");

        // 步骤四：固定“英文 H3 标识 + 中文正文”的输出约束。
        assertThat(prompt).contains(
                "固定 H3 标识保持英文",
                "所有字段正文、镜头描述、运镜、声音、配乐、参考关系说明和首尾帧对齐说明必须使用中文",
                "<d>[Chinese] 原始台词</d>");
        assertThat(prompt).contains(
                "目标视频在 0.00 秒处完整参考 <Picture 1>（来自 [Shot 1]）",
                "<Picture 1>（来自 [Shot 1]）对应目标视频 0.00 秒；<Picture 2>（来自 [Shot N]）",
                "<Picture 1>（来自 [Shot N]）对应目标视频 S.SS 秒");
        assertThat(prompt).doesNotContain(
                "`videoPrompt` 必须使用英文",
                "Cinematic, high-quality visual detail",
                "明确写出 `Cinematic`");

        // 步骤五：固定同场次前后镜头的连续性处理及冲突优先级。
        assertThat(prompt).contains(
                "目标镜头的前一项是上一镜头，后一项是下一镜头",
                "开场承接上一镜头",
                "结尾衔接下一镜头",
                "显式首尾帧 > 当前镜头剧本字段 > 相邻镜头上下文",
                "上一镜头和下一镜头不得作为额外 `[Shot N]` 写入当前视频时间线");
    }

    @Test
    void shouldReuseExistingVideoPromptWithoutOverwritingIt() throws IOException {
        String executorPrompt;
        try (InputStream input = new ClassPathResource(
                "prompts/agents/storyboard-video-executor.system.md").getInputStream()) {
            executorPrompt = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(executorPrompt).contains(
                "必须把已有 `videoPrompt` 原文作为最终提示词",
                "禁止重新编写、翻译、润色、拼接或覆盖",
                "update_storyboard_item_video(storyboardItemId, videoUrl)",
                "不得传入 `videoPrompt`");
        assertThat(executorPrompt).contains(
                "`promptOnly: true` 时若已有提示词",
                "也不调用 `update_storyboard_item_video`");
        assertThat(executorPrompt).contains(
                "只有 `videoPrompt` 为空或全为空白时，才执行后续提示词编写步骤",
                "只省略对应素材参数，禁止因此改写已有提示词",
                "非空则不生成、不更新，直接报告已跳过");
    }
}
