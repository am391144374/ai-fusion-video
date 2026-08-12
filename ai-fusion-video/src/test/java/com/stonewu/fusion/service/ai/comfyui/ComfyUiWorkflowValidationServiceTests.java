package com.stonewu.fusion.service.ai.comfyui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stonewu.fusion.controller.ai.vo.comfyui.ComfyUiWorkflowValidationRespVO;
import com.stonewu.fusion.entity.ai.ApiConfig;
import com.stonewu.fusion.entity.ai.ComfyUiWorkflow;
import com.stonewu.fusion.entity.ai.ComfyUiWorkflowVersion;
import com.stonewu.fusion.service.ai.ApiConfigService;
import com.stonewu.fusion.service.ai.comfyui.client.ComfyUiNativeClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComfyUiWorkflowValidationServiceTests {

    @Mock
    private ApiConfigService apiConfigService;
    @Mock
    private ComfyUiWorkflowService workflowService;
    @Mock
    private ComfyUiNativeClient nativeClient;

    private ObjectMapper objectMapper;
    private ComfyUiWorkflowValidationService validationService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        ComfyUiWorkflowDocumentService documentService =
                new ComfyUiWorkflowDocumentService(objectMapper);
        validationService = new ComfyUiWorkflowValidationService(
                apiConfigService,
                workflowService,
                documentService,
                nativeClient,
                mock(ComfyUiGenerationExecutor.class),
                objectMapper,
                Runnable::run);
    }

    @Test
    void validateVersionSkipsPlaceholderOfDynamicUploadedImage() throws Exception {
        prepareVersion("""
                {
                  "referenceImages": [
                    {"nodeId":"137","inputName":"image","valueType":"uploaded_image","index":0}
                  ]
                }
                """);

        ComfyUiWorkflowValidationRespVO result = validationService.validateVersion(21L);

        assertThat(result.isValid()).isTrue();
        assertThat(result.getInvalidModelInputs()).isEmpty();
        verify(workflowService).recordValidation(21L, true, "已在目标 ComfyUI 校验 1 个节点");
    }

    @Test
    void validateVersionStillRejectsUnboundMissingFixedImage() throws Exception {
        prepareVersion("{}");

        ComfyUiWorkflowValidationRespVO result = validationService.validateVersion(21L);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getInvalidModelInputs())
                .containsExactly("137.image=pasted/image.png");
        verify(workflowService).recordValidation(
                21L, false, "目标实例不存在模型/选项: 137.image=pasted/image.png");
    }

    private void prepareVersion(String inputBindingsJson) throws Exception {
        String apiWorkflowJson = """
                {
                  "137": {
                    "inputs": {"image":"pasted/image.png"},
                    "class_type":"LoadImage",
                    "_meta":{"title":"加载图像"}
                  }
                }
                """;
        ComfyUiWorkflowVersion version = ComfyUiWorkflowVersion.builder()
                .id(21L)
                .workflowId(12L)
                .apiWorkflowJson(apiWorkflowJson)
                .inputBindingsJson(inputBindingsJson)
                .build();
        ComfyUiWorkflow workflow = ComfyUiWorkflow.builder()
                .id(12L)
                .apiConfigId(7L)
                .modelType(3)
                .build();
        ApiConfig apiConfig = ApiConfig.builder()
                .id(7L)
                .platform("comfyui")
                .status(1)
                .build();

        when(workflowService.requireVersion(21L)).thenReturn(version);
        when(workflowService.requireWorkflow(12L)).thenReturn(workflow);
        when(apiConfigService.getById(7L)).thenReturn(apiConfig);
        when(nativeClient.getNodeInfo(apiConfig, "LoadImage"))
                .thenReturn(objectMapper.readTree("""
                        {
                          "LoadImage": {
                            "input": {
                              "required": {
                                "image": [["existing.png"]]
                              }
                            }
                          }
                        }
                        """));
    }
}
