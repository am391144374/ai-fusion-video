package com.stonewu.fusion.service.ai.tool.storyboard;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.stonewu.fusion.entity.storyboard.StoryboardItem;
import com.stonewu.fusion.entity.storyboard.StoryboardScene;
import com.stonewu.fusion.service.ai.ToolExecutionContext;
import com.stonewu.fusion.service.ai.tool.ToolResourceAccessGuard;
import com.stonewu.fusion.service.asset.AssetService;
import com.stonewu.fusion.service.storyboard.StoryboardService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GetStoryboardSceneItemsToolExecutorTests {

    @Test
    void resolvesSceneFromItemWhenSceneIdIsNegativePlaceholder() {
        StoryboardService storyboardService = mock(StoryboardService.class);
        AssetService assetService = mock(AssetService.class);
        ToolResourceAccessGuard accessGuard = mock(ToolResourceAccessGuard.class);
        GetStoryboardSceneItemsToolExecutor executor = new GetStoryboardSceneItemsToolExecutor(
                storyboardService, assetService, accessGuard);
        StoryboardItem item = StoryboardItem.builder()
                .id(103L)
                .storyboardId(5L)
                .storyboardSceneId(12L)
                .shotNumber("1")
                .build();
        StoryboardScene scene = StoryboardScene.builder()
                .id(12L)
                .storyboardId(5L)
                .sceneHeading("室内 客厅 日")
                .build();
        when(accessGuard.requireStoryboardItem(103L, 7L)).thenReturn(item);
        when(accessGuard.requireStoryboardScene(12L, 7L)).thenReturn(scene);
        when(storyboardService.listItemsByScene(12L)).thenReturn(List.of(item));

        String result = executor.execute(
                "{\"storyboardSceneId\":-1,\"storyboardItemId\":103}",
                ToolExecutionContext.builder().userId(7L).build());

        JSONObject response = JSONUtil.parseObj(result);
        assertThat(response.getLong("storyboardSceneId")).isEqualTo(12L);
        assertThat(response.getInt("totalItems")).isOne();
        assertThat(response.getJSONArray("items").getJSONObject(0)
                .getBool("isCurrentTarget")).isTrue();
        verify(accessGuard, never()).requireStoryboardScene(-1L, 7L);
    }

    @Test
    void rejectsConflictingPositiveSceneAndItemIds() {
        StoryboardService storyboardService = mock(StoryboardService.class);
        ToolResourceAccessGuard accessGuard = mock(ToolResourceAccessGuard.class);
        GetStoryboardSceneItemsToolExecutor executor = new GetStoryboardSceneItemsToolExecutor(
                storyboardService, mock(AssetService.class), accessGuard);
        StoryboardItem item = StoryboardItem.builder()
                .id(103L)
                .storyboardId(5L)
                .storyboardSceneId(12L)
                .build();
        when(accessGuard.requireStoryboardItem(103L, 7L)).thenReturn(item);

        String result = executor.execute(
                "{\"storyboardSceneId\":99,\"storyboardItemId\":103}",
                ToolExecutionContext.builder().userId(7L).build());

        JSONObject response = JSONUtil.parseObj(result);
        assertThat(response.getStr("status")).isEqualTo("error");
        assertThat(response.getStr("message")).contains("所属场次不一致");
        verify(accessGuard, never()).requireStoryboardScene(99L, 7L);
    }

    @Test
    void rejectsRequestWithoutAnyPositiveIdentifier() {
        GetStoryboardSceneItemsToolExecutor executor = new GetStoryboardSceneItemsToolExecutor(
                mock(StoryboardService.class),
                mock(AssetService.class),
                mock(ToolResourceAccessGuard.class));

        String result = executor.execute(
                "{\"storyboardSceneId\":-1}",
                ToolExecutionContext.builder().userId(7L).build());

        JSONObject response = JSONUtil.parseObj(result);
        assertThat(response.getStr("status")).isEqualTo("error");
        assertThat(response.getStr("message")).contains("必须为正整数");
    }
}
