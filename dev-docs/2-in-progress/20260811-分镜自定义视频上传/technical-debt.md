# 技术债务

当前无新增技术债务。

本次在通用上传接口中加入 `video/mp4` 类型白名单，前端同时限制为 MP4。文件类型仍依赖浏览器和 HTTP 上传提供的 MIME 类型；若未来需要抵御伪造媒体类型，应在存储层加入文件内容检测并统一治理。

当前验证环境缺少 Java 21，无法执行后端定向单元测试；这不是功能实现的兼容方案，需在具备 Java 21 的环境中补跑 `FileUploadControllerTests` 和 `VideoComposeServiceTests`。
