package com.stonewu.fusion.service.generation.video.strategy;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.stonewu.fusion.entity.ai.AiModel;
import com.stonewu.fusion.entity.ai.ApiConfig;
import com.stonewu.fusion.entity.generation.VideoTask;
import com.stonewu.fusion.service.generation.video.strategy.openai.OpenAiVideoProtocolAdapter;
import com.stonewu.fusion.service.generation.video.strategy.support.OpenAiCompatibleVideoProtocolContext;
import com.stonewu.fusion.service.generation.video.strategy.support.OpenAiCompatibleVideoProtocolSupport;
import com.stonewu.fusion.service.storage.StorageConfigService;
import com.stonewu.fusion.service.system.PresetArtStyleResourceResolver;
import okhttp3.RequestBody;
import okio.Buffer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class GrokCliProxyVideoProtocolAdapterTests {

    private final OpenAiCompatibleVideoProtocolSupport support = new OpenAiCompatibleVideoProtocolSupport(
            mock(StorageConfigService.class),
            new PresetArtStyleResourceResolver()
    );
    private final OpenAiVideoProtocolAdapter adapter = new OpenAiVideoProtocolAdapter(support);

    @Test
    void grokFirstFrameUsesJsonImageAndExcludesReferenceImages() throws IOException {
        String firstFrame = dataUri("image/png", "first-frame");
        VideoTask task = VideoTask.builder()
                .prompt("animate the first frame")
                .duration(10)
                .resolution("1280x720")
                .firstFrameImageUrl(firstFrame)
                .referenceImageUrls(JSONUtil.toJsonStr(List.of(
                        dataUri("image/jpeg", "reference-one"),
                        dataUri("image/webp", "reference-two")
                )))
                .build();

        RequestBody requestBody = adapter.buildSubmitBody(context("grok-imagine-video", task));
        JSONObject body = readJson(requestBody);

        assertThat(requestBody.contentType().toString()).isEqualTo("application/json");
        assertThat(body.getStr("model")).isEqualTo("grok-imagine-video");
        assertThat(body.getStr("prompt")).isEqualTo("animate the first frame");
        assertThat(body.getInt("seconds")).isEqualTo(10);
        assertThat(body.getStr("size")).isEqualTo("1280x720");
        assertThat(body.getJSONObject("image").getStr("url")).isEqualTo(firstFrame);
        assertThat(body.containsKey("reference_images")).isFalse();
        assertThat(adapter.resolveCoverContentUrl(context("grok-imagine-video", task), "video_123", null))
                .isNull();
    }

    @Test
    void grokMultipleReferencesUseJsonReferenceImages() throws IOException {
        String firstReference = dataUri("image/jpeg", "reference-one");
        String secondReference = dataUri("image/webp", "reference-two");
        VideoTask task = VideoTask.builder()
                .prompt("use both references")
                .referenceImageUrls(JSONUtil.toJsonStr(List.of(firstReference, secondReference)))
                .build();

        JSONObject body = readJson(adapter.buildSubmitBody(context("grok-imagine-video-hd", task)));

        assertThat(body.containsKey("image")).isFalse();
        assertThat(body.getJSONArray("reference_images")).hasSize(2);
        assertThat(body.getJSONArray("reference_images").getJSONObject(0).getStr("url"))
                .isEqualTo(firstReference);
        assertThat(body.getJSONArray("reference_images").getJSONObject(1).getStr("url"))
                .isEqualTo(secondReference);
    }

    @Test
    void recognizesCliProxyGrokModelPrefixesWithoutChangingSora() throws IOException {
        VideoTask task = VideoTask.builder().prompt("test").build();

        for (String modelCode : List.of(
                "grok-imagine-video",
                "grok-imagine-video-preview",
                "xai/grok-imagine-video",
                "x-ai/grok-imagine-video",
                "grok/grok-imagine-video"
        )) {
            assertThat(support.isGrokVideoModel(model(modelCode))).as(modelCode).isTrue();
            assertThat(adapter.buildSubmitBody(context(modelCode, task)).contentType().toString())
                    .as(modelCode)
                    .isEqualTo("application/json");
        }

        RequestBody soraBody = adapter.buildSubmitBody(context("sora-2", task));
        assertThat(support.isGrokVideoModel(model("sora-2"))).isFalse();
        assertThat(soraBody.contentType().toString()).startsWith("multipart/form-data");
    }

    private OpenAiCompatibleVideoProtocolContext context(String modelCode, VideoTask task) {
        return new OpenAiCompatibleVideoProtocolContext(
                model(modelCode),
                ApiConfig.builder().apiUrl("http://localhost:8317").build(),
                task,
                JSONUtil.createObj(),
                null
        );
    }

    private AiModel model(String modelCode) {
        return AiModel.builder().code(modelCode).build();
    }

    private JSONObject readJson(RequestBody body) throws IOException {
        Buffer buffer = new Buffer();
        body.writeTo(buffer);
        return JSONUtil.parseObj(buffer.readString(StandardCharsets.UTF_8));
    }

    private String dataUri(String mimeType, String value) {
        return "data:" + mimeType + ";base64,"
                + Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
