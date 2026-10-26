# heiye-spring-ai-openrouter-starter
该模块基于 spring ai 为基本架构，让调用 OpenRouter 的类接上了 spring ai 的接口 ，尽可能让调用模型的方式接近于调用 spring ai 支持的模型模式，使 OpenRouter 提供的模型可以更好的衔接支持 spring ai 的项目


## 聊天选项

`OpenRouterChatModel.createRequest()` 使用 `ModelOptionsUtils.merge()` 将选项合并到请求。
`OpenRouterChatOptions` 的 JSON 字段与请求保持一致，推理参数位于 `reasoning` 对象中：

```java
OpenRouterChatOptions options = OpenRouterChatOptions.builder()
        .model("your-model")
        .maxTokens(2048)
        .effort("high") // 同时支持 HIGH
        .thinkMaxTokens(1024)
        .exclude(false)
        .enable(true)
        .build();
```

也可以通过 `.reasoning(new OpenRouterApi.Think(...))` 或 `setReasoning(...)` 设置整个推理配置。
推理参数的 builder 方法和 setter 继续可用，JSON 序列化只输出嵌套的 `reasoning`。`getEnable()` 已移除；读取开关时先判断 `getReasoning()` 非 null，再调用 `.enabled()`。
直接提供 JSON 选项时，应将原来的顶层推理字段迁移至 `reasoning`。

未配置的常规参数返回 `null`，不会隐式发送 `0` 或空停止序列。
合并采用非 null 值覆盖规则：运行时未提供 `reasoning` 时保留默认配置；提供时整体替换默认的
`reasoning`，不会逐项继承其中未设置的字段。`stream` 选项非 null 时覆盖请求构建方法传入的流式标志。

验证：使用 JDK 17 执行 `mvn test`。
