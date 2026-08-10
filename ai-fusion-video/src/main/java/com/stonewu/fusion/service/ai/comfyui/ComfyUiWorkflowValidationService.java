package com.stonewu.fusion.service.ai.comfyui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stonewu.fusion.common.BusinessException;
import com.stonewu.fusion.controller.ai.vo.comfyui.ComfyUiConnectionRespVO;
import com.stonewu.fusion.controller.ai.vo.comfyui.ComfyUiWorkflowValidationRespVO;
import com.stonewu.fusion.controller.ai.vo.comfyui.ComfyUiStoredOutputRespVO;
import com.stonewu.fusion.controller.ai.vo.comfyui.ComfyUiWorkflowTestRespVO;
import com.stonewu.fusion.entity.ai.ApiConfig;
import com.stonewu.fusion.entity.ai.ComfyUiWorkflow;
import com.stonewu.fusion.entity.ai.ComfyUiWorkflowVersion;
import com.stonewu.fusion.service.ai.ApiConfigService;
import com.stonewu.fusion.service.ai.comfyui.client.ComfyUiConnectionResult;
import com.stonewu.fusion.service.ai.comfyui.client.ComfyUiNativeClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Online validation against the exact target ComfyUI instance. */
@Service
@Slf4j
public class ComfyUiWorkflowValidationService {

    private final ApiConfigService apiConfigService;
    private final ComfyUiWorkflowService workflowService;
    private final ComfyUiWorkflowDocumentService documentService;
    private final ComfyUiNativeClient nativeClient;
    private final ComfyUiGenerationExecutor generationExecutor;
    private final ObjectMapper objectMapper;
    private final TaskExecutor workflowTestExecutor;

    public ComfyUiWorkflowValidationService(ApiConfigService apiConfigService,
                                            ComfyUiWorkflowService workflowService,
                                            ComfyUiWorkflowDocumentService documentService,
                                            ComfyUiNativeClient nativeClient,
                                            ComfyUiGenerationExecutor generationExecutor,
                                            ObjectMapper objectMapper,
                                            @Qualifier("comfyUiWorkflowTestExecutor") TaskExecutor workflowTestExecutor) {
        this.apiConfigService = apiConfigService;
        this.workflowService = workflowService;
        this.documentService = documentService;
        this.nativeClient = nativeClient;
        this.generationExecutor = generationExecutor;
        this.objectMapper = objectMapper;
        this.workflowTestExecutor = workflowTestExecutor;
    }

    public ComfyUiConnectionRespVO testConnection(Long apiConfigId) {
        ApiConfig apiConfig = requireComfyUiApiConfig(apiConfigId);
        ComfyUiConnectionResult result = nativeClient.testConnection(apiConfig);
        return ComfyUiConnectionRespVO.builder()
                .connected(result.connected())
                .jobsApiSupported(result.jobsApiSupported())
                .version(result.version())
                .systemStats(result.systemStats())
                .features(result.features())
                .build();
    }

    public ComfyUiWorkflowValidationRespVO validateVersion(Long versionId) {
        ComfyUiWorkflowVersion version = workflowService.requireVersion(versionId);
        ComfyUiWorkflow workflow = workflowService.requireWorkflow(version.getWorkflowId());
        ApiConfig apiConfig = requireComfyUiApiConfig(workflow.getApiConfigId());
        try {
            nativeClient.testConnection(apiConfig);
            ObjectNode apiWorkflow = documentService.parseApiWorkflow(version.getApiWorkflowJson());
            Set<String> missingClasses = new LinkedHashSet<>();
            List<String> invalidModelInputs = new ArrayList<>();
            Map<String, JsonNode> definitionCache = new HashMap<>();
            Iterator<Map.Entry<String, JsonNode>> nodes = apiWorkflow.fields();
            int checkedNodes = 0;
            while (nodes.hasNext()) {
                Map.Entry<String, JsonNode> entry = nodes.next();
                checkedNodes++;
                String nodeId = entry.getKey();
                JsonNode workflowNode = entry.getValue();
                String classType = workflowNode.path("class_type").asText();
                JsonNode definition = definitionCache.computeIfAbsent(classType,
                        ignored -> nativeClient.getNodeInfo(apiConfig, classType).path(classType));
                if (definition.isMissingNode() || !definition.isObject()) {
                    missingClasses.add(classType);
                    continue;
                }
                validateChoiceInputs(nodeId, workflowNode.path("inputs"), definition, invalidModelInputs);
            }
            boolean valid = missingClasses.isEmpty() && invalidModelInputs.isEmpty();
            String message = valid
                    ? "已在目标 ComfyUI 校验 " + checkedNodes + " 个节点"
                    : buildFailureMessage(missingClasses, invalidModelInputs);
            workflowService.recordValidation(versionId, valid, message);
            return ComfyUiWorkflowValidationRespVO.builder()
                    .valid(valid)
                    .checkedNodeCount(checkedNodes)
                    .missingNodeClasses(List.copyOf(missingClasses))
                    .invalidModelInputs(List.copyOf(invalidModelInputs))
                    .message(message)
                    .build();
        } catch (BusinessException error) {
            workflowService.recordValidation(versionId, false, error.getMessage());
            throw error;
        }
    }

    public ComfyUiWorkflowTestRespVO startTestVersion(Long versionId, Map<String, Object> inputs) {
        ComfyUiExecutionContext context = generationExecutor.resolveTestContext(versionId);
        ComfyUiPreparedSubmission submission = generationExecutor.prepare(
                context, "workflow-test-" + versionId, inputs);
        long startedAt = System.currentTimeMillis();

        // 步骤一：先记录运行中状态，防止同一版本被重复提交到 ComfyUI。
        workflowService.recordExecutionTestStarted(versionId, submission.promptId());
        try {
            // 步骤二：提交后立即返回 promptId，不在 HTTP 请求中等待生成结果。
            generationExecutor.submit(submission);
            workflowTestExecutor.execute(() -> finishTestVersion(
                    versionId, context, submission.promptId(), startedAt));
            return ComfyUiWorkflowTestRespVO.builder()
                    .running(true)
                    .passed(false)
                    .promptId(submission.promptId())
                    .durationMillis(0L)
                    .outputs(List.of())
                    .message("已提交 ComfyUI，正在后台试运行")
                    .build();
        } catch (RuntimeException error) {
            try {
                generationExecutor.cancel(context, submission.promptId());
            } catch (RuntimeException ignored) {
                // 原始提交异常优先返回。
            }
            workflowService.recordExecutionTest(
                    versionId, false, error.getMessage(), System.currentTimeMillis() - startedAt, null);
            throw error;
        }
    }

    public ComfyUiWorkflowTestRespVO getTestResult(Long versionId) {
        ComfyUiWorkflowVersion version = workflowService.requireVersion(versionId);
        boolean running = Integer.valueOf(ComfyUiWorkflowVersion.TEST_RUNNING)
                .equals(version.getTestStatus());
        List<ComfyUiStoredOutputRespVO> outputs = parseStoredOutputs(version.getTestOutputsJson());
        return ComfyUiWorkflowTestRespVO.builder()
                .running(running)
                .passed(Integer.valueOf(ComfyUiWorkflowVersion.TEST_PASSED)
                        .equals(version.getTestStatus()))
                .promptId(version.getTestPromptId())
                .durationMillis(version.getTestDurationMillis() == null ? 0L : version.getTestDurationMillis())
                .outputs(outputs)
                .message(version.getTestMessage() == null ? "尚未试运行" : version.getTestMessage())
                .build();
    }

    private void finishTestVersion(Long versionId,
                                   ComfyUiExecutionContext context,
                                   String promptId,
                                   long startedAt) {
        try {
            // 步骤三：后台定时查询 ComfyUI 任务，直到完成、失败或达到对应模型类型的最长等待时间。
            long timeout = context.workflow().getModelType() == 2
                    ? 25L * 60L * 1_000L : 60L * 60L * 1_000L;
            var job = generationExecutor.waitForJob(context, promptId, 2_000L, timeout);
            List<ComfyUiStoredOutput> stored = generationExecutor.storeOutputs(context, job);
            long duration = System.currentTimeMillis() - startedAt;
            String message = "试运行成功，已保存 " + stored.size() + " 个输出";
            workflowService.recordExecutionTest(
                    versionId, true, message, duration, writeStoredOutputs(stored));
        } catch (RuntimeException error) {
            try {
                generationExecutor.cancel(context, promptId);
            } catch (RuntimeException ignored) {
                // 原始执行异常优先记录。
            }
            long duration = System.currentTimeMillis() - startedAt;
            String message = error instanceof BusinessException
                    ? error.getMessage() : "ComfyUI 试运行执行异常";
            workflowService.recordExecutionTest(versionId, false, message, duration, null);
            log.error("[ComfyUI Workflow Test] 试运行失败: versionId={}, promptId={}",
                    versionId, promptId, error);
        }
    }

    private String writeStoredOutputs(List<ComfyUiStoredOutput> stored) {
        try {
            return objectMapper.writeValueAsString(stored);
        } catch (Exception error) {
            throw new BusinessException(500, "序列化 ComfyUI 试运行结果失败");
        }
    }

    private List<ComfyUiStoredOutputRespVO> parseStoredOutputs(String outputsJson) {
        if (outputsJson == null || outputsJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(outputsJson, new TypeReference<List<ComfyUiStoredOutput>>() {
            }).stream().map(output -> ComfyUiStoredOutputRespVO.builder()
                    .mediaType(output.mediaType())
                    .role(output.role())
                    .url(output.url())
                    .size(output.size())
                    .build()).toList();
        } catch (Exception error) {
            throw new BusinessException(500, "解析 ComfyUI 试运行结果失败");
        }
    }

    private void validateChoiceInputs(String nodeId,
                                      JsonNode workflowInputs,
                                      JsonNode definition,
                                      List<String> invalidInputs) {
        JsonNode definitions = definition.path("input");
        Iterator<Map.Entry<String, JsonNode>> inputs = workflowInputs.fields();
        while (inputs.hasNext()) {
            Map.Entry<String, JsonNode> input = inputs.next();
            if (!input.getValue().isTextual()) {
                continue;
            }
            JsonNode inputDefinition = definitions.path("required").get(input.getKey());
            if (inputDefinition == null) {
                inputDefinition = definitions.path("optional").get(input.getKey());
            }
            if (inputDefinition == null || !inputDefinition.isArray()
                    || inputDefinition.isEmpty() || !inputDefinition.get(0).isArray()) {
                continue;
            }
            boolean allowed = false;
            for (JsonNode choice : inputDefinition.get(0)) {
                if (choice.isTextual() && choice.asText().equals(input.getValue().asText())) {
                    allowed = true;
                    break;
                }
            }
            if (!allowed) {
                invalidInputs.add(nodeId + "." + input.getKey() + "=" + input.getValue().asText());
            }
        }
    }

    private String buildFailureMessage(Set<String> missingClasses, List<String> invalidInputs) {
        List<String> parts = new ArrayList<>();
        if (!missingClasses.isEmpty()) {
            parts.add("缺少节点: " + String.join(", ", missingClasses));
        }
        if (!invalidInputs.isEmpty()) {
            parts.add("目标实例不存在模型/选项: " + String.join(", ", invalidInputs));
        }
        return String.join("；", parts);
    }

    private ApiConfig requireComfyUiApiConfig(Long id) {
        ApiConfig apiConfig = id == null ? null : apiConfigService.getById(id);
        if (apiConfig == null) {
            throw new BusinessException(404, "API 配置不存在");
        }
        if (!ComfyUiWorkflowService.PLATFORM.equalsIgnoreCase(apiConfig.getPlatform())) {
            throw new BusinessException(400, "API 配置不是 ComfyUI 平台");
        }
        if (!Integer.valueOf(1).equals(apiConfig.getStatus())) {
            throw new BusinessException(400, "ComfyUI API 配置已禁用");
        }
        return apiConfig;
    }
}
