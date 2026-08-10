package com.stonewu.fusion.entity.ai;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.stonewu.fusion.common.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;

/** Immutable executable version of a ComfyUI workflow. */
@TableName("afv_comfyui_workflow_version")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComfyUiWorkflowVersion extends BaseEntity {

    public static final int VALIDATION_PENDING = 0;
    public static final int VALIDATION_VALID = 1;
    public static final int VALIDATION_INVALID = 2;
    public static final int TEST_PENDING = 0;
    public static final int TEST_PASSED = 1;
    public static final int TEST_FAILED = 2;
    public static final int TEST_RUNNING = 3;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workflowId;

    private Integer versionNo;

    private String uiWorkflowJson;

    private String apiWorkflowJson;

    private String inputBindingsJson;

    private String outputBindingsJson;

    private String requiredNodesJson;

    private String workflowHash;

    @Builder.Default
    private Integer validationStatus = VALIDATION_PENDING;

    private String validationMessage;

    @Builder.Default
    private Integer testStatus = TEST_PENDING;

    private String testMessage;

    /** 当前试运行对应的 ComfyUI prompt 标识。 */
    private String testPromptId;

    /** 当前或最近一次试运行开始时间。 */
    private LocalDateTime testStartedAt;

    /** 最近一次试运行耗时（毫秒）。 */
    private Long testDurationMillis;

    /** 最近一次试运行保存结果的 JSON。 */
    private String testOutputsJson;

    private LocalDateTime lastTestTime;

    @Builder.Default
    private Boolean published = false;
}
