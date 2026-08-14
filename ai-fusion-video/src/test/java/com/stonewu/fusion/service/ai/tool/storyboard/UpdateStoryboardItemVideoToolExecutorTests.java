package com.stonewu.fusion.service.ai.tool.storyboard;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.stonewu.fusion.entity.storyboard.StoryboardItem;
import com.stonewu.fusion.service.ai.ToolExecutionContext;
import com.stonewu.fusion.service.ai.tool.ToolResourceAccessGuard;
import com.stonewu.fusion.service.storage.MediaStorageService;
import com.stonewu.fusion.service.storyboard.StoryboardService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UpdateStoryboardItemVideoToolExecutorTests {

    @Test
    void deletesPreviousFileAfterReplacingStoredVideo() {
        StoryboardService storyboardService = mock(StoryboardService.class);
        ToolResourceAccessGuard accessGuard = mock(ToolResourceAccessGuard.class);
        MediaStorageService mediaStorageService = mock(MediaStorageService.class);
        UpdateStoryboardItemVideoToolExecutor executor = new UpdateStoryboardItemVideoToolExecutor(
                storyboardService, accessGuard, mediaStorageService);
        StoryboardItem item = StoryboardItem.builder()
                .id(103L)
                .generatedVideoUrl("/media/videos/old.mp4")
                .videoPrompt("existing prompt")
                .build();
        when(accessGuard.requireStoryboardItem(103L, 7L)).thenReturn(item);
        when(mediaStorageService.deleteStoredFile("/media/videos/old.mp4")).thenReturn(true);

        String result = executor.execute(
                "{\"storyboardItemId\":103,\"videoUrl\":\"/media/videos/new.mp4\"}",
                ToolExecutionContext.builder().userId(7L).build());

        JSONObject response = JSONUtil.parseObj(result);
        assertThat(response.getStr("status")).isEqualTo("success");
        assertThat(response.getBool("previousVideoDeleted")).isTrue();
        assertThat(item.getGeneratedVideoUrl()).isEqualTo("/media/videos/new.mp4");
        assertThat(item.getVideoPrompt()).isEqualTo("existing prompt");
        InOrder order = inOrder(storyboardService, mediaStorageService);
        order.verify(storyboardService).updateItem(item);
        order.verify(mediaStorageService).deleteStoredFile("/media/videos/old.mp4");
    }

    @Test
    void doesNotDeleteFileWhenDatabaseUpdateFails() {
        StoryboardService storyboardService = mock(StoryboardService.class);
        ToolResourceAccessGuard accessGuard = mock(ToolResourceAccessGuard.class);
        MediaStorageService mediaStorageService = mock(MediaStorageService.class);
        UpdateStoryboardItemVideoToolExecutor executor = new UpdateStoryboardItemVideoToolExecutor(
                storyboardService, accessGuard, mediaStorageService);
        StoryboardItem item = StoryboardItem.builder()
                .id(103L)
                .generatedVideoUrl("/media/videos/old.mp4")
                .build();
        when(accessGuard.requireStoryboardItem(103L, 7L)).thenReturn(item);
        when(storyboardService.updateItem(item)).thenThrow(new RuntimeException("database error"));

        String result = executor.execute(
                "{\"storyboardItemId\":103,\"videoUrl\":\"/media/videos/new.mp4\"}",
                ToolExecutionContext.builder().userId(7L).build());

        assertThat(JSONUtil.parseObj(result).getStr("status")).isEqualTo("error");
        verify(mediaStorageService, never()).deleteStoredFile("/media/videos/old.mp4");
    }

    @Test
    void doesNotDeleteWhenVideoUrlIsUnchanged() {
        StoryboardService storyboardService = mock(StoryboardService.class);
        ToolResourceAccessGuard accessGuard = mock(ToolResourceAccessGuard.class);
        MediaStorageService mediaStorageService = mock(MediaStorageService.class);
        UpdateStoryboardItemVideoToolExecutor executor = new UpdateStoryboardItemVideoToolExecutor(
                storyboardService, accessGuard, mediaStorageService);
        StoryboardItem item = StoryboardItem.builder()
                .id(103L)
                .generatedVideoUrl("/media/videos/same.mp4")
                .build();
        when(accessGuard.requireStoryboardItem(103L, 7L)).thenReturn(item);

        executor.execute(
                "{\"storyboardItemId\":103,\"videoUrl\":\"/media/videos/same.mp4\"}",
                ToolExecutionContext.builder().userId(7L).build());

        verify(mediaStorageService, never()).deleteStoredFile("/media/videos/same.mp4");
    }
}
