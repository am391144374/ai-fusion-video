ALTER TABLE `afv_comfyui_workflow_version`
  ADD COLUMN `test_prompt_id` varchar(36) CHARACTER SET ascii COLLATE ascii_general_ci DEFAULT NULL COMMENT '当前或最近一次试运行的 ComfyUI 任务标识' AFTER `test_message`,
  ADD COLUMN `test_started_at` datetime DEFAULT NULL COMMENT '最近一次试运行开始时间' AFTER `test_prompt_id`,
  ADD COLUMN `test_duration_millis` bigint DEFAULT NULL COMMENT '最近一次试运行耗时（毫秒）' AFTER `test_started_at`,
  ADD COLUMN `test_outputs_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '最近一次试运行保存结果' AFTER `test_duration_millis`;
