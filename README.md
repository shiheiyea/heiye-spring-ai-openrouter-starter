# Heiye Spring AI OpenRouter Starter

通过 Spring AI 的 `ChatModel` 接口访问 OpenRouter，在 Spring Boot 应用中使用统一的消息、聊天选项和工具调用机制。

基于 **Java 17 · Spring Boot 4.1.0 · Spring AI 2.0.0**，产物为普通依赖 JAR。

> `2.0.0` 是一次破坏性升级：对齐 Spring AI 2.0.0 的 `ChatModel` 契约（`getOptions()` 取代 `getDefaultOptions()`）、把配置拍平到 `spring.ai.openrouter.chat.*`、自动配置类从 `...openrouter.config` 搬到 `...openrouter.autoconfigure`、非流式 `call(Prompt)` 从返回 `null` 补成真实实现。迁移步骤见[从 1.1.0 迁移](#从-110-迁移)。

[项目定位](#项目定位) · [快速开始](#快速开始) · [配置说明](#配置说明) · [使用示例](#使用示例) · [能力清单](#能力清单) · [与 Spring AI 2.0.0 DeepSeek 模块对比](#与-spring-ai-200-deepseek-模块对比) · [从 1.1.0 迁移](#从-110-迁移) · [本地构建](#本地构建) · [常见问题](#常见问题)

## 项目定位

本项目把 OpenRouter 的 Chat Completions 接口适配为 Spring AI 的聊天模型，建模对象是 OpenRouter 协议的真实能力面，而不是 DeepSeek 能力集的子集。提供：

- Spring Boot 自动配置，连接参数与聊天参数分离，聊天配置支持扁平键与旧写法兼容。
- 非流式 `call(Prompt)` 与流式 `stream(Prompt)` 两条真实链路，都带 Micrometer observation 与重试。
- 请求体 43 个字段的完整落点：25 个高频字段强类型建模，12 个低频字段通过 `extraBody` 原样透传，4 个由模型层组装，1 个不暴露，1 个已删除。
- 响应侧 lossless 往返：`reasoning_details` 12 字段、`usage` 10 字段、`system_fingerprint` 与 `openrouter_metadata` 进 `nativeUsage`。
- 多模态输入：图片（URI / String / `byte[]`）与音频（`byte[]`），按 MIME 前缀分流。
- 工具定义解析与 Spring AI `ToolCallback` 支持，流式工具调用片段缝合。
- Jackson DTO 的 AOT runtime hints 注册。

模型 ID 直接传字符串即可。模型是否可用、是否支持工具与推理，由 OpenRouter 和实际提供商决定。本项目不是 OpenRouter 全量 API SDK，也不是 Spring AI 官方模块。

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>io.github.shiheiyea</groupId>
    <artifactId>heiye-spring-ai-model-openrouter-starter</artifactId>
    <version>2.0.0</version>
</dependency>
```

> 发布到 Maven Central 之前，请先克隆源码执行 `mvn clean install` 再在本地项目引用。

建议消费项目使用 Spring Boot 4.1.0 并导入 Spring AI BOM 2.0.0，避免不同 Spring AI 模块版本混用：

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>2.0.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

若要运行后面的 HTTP/SSE 示例，还需添加 `spring-boot-starter-webflux`。

### 2. 配置密钥和模型

```yaml
spring:
  ai:
    model:
      chat: openrouter
    openrouter:
      api-key: ${OPENROUTER_API_KEY}
      chat:
        model: ${OPENROUTER_MODEL}
        temperature: 0.7
        max-tokens: 2048
```

设置环境变量（PowerShell）：

```powershell
$env:OPENROUTER_API_KEY = '你的 OpenRouter API Key'
$env:OPENROUTER_MODEL = '你的完整模型 ID'
```

不要把密钥提交到仓库。项目不会自动加载 `.env` 文件。

### 3. 创建聊天接口

```java
package com.example.demo;

import io.github.shiheiyea.spring.ai.openrouter.OpenRouterChatModel;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
public class ChatController {

    private final OpenRouterChatModel chatModel;

    public ChatController(OpenRouterChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @GetMapping(value = "/ai/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@RequestParam(defaultValue = "用一句话介绍 Spring AI") String message) {
        return chatModel.stream(message);
    }

    @GetMapping("/ai/chat/sync")
    public String chatSync(@RequestParam(defaultValue = "用一句话介绍 Spring AI") String message) {
        return chatModel.call(message);
    }
}
```

## 配置说明

### 连接和自动配置

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `spring.ai.model.chat` | 未配置时也启用本模块 | 显式设为 `openrouter` 启用；设为 `none` 或其他值时不启用 |
| `spring.ai.openrouter.api-key` | 无 | API Key，启用自动配置时必须提供 |
| `spring.ai.openrouter.base-url` | `https://openrouter.ai` | 通用服务地址 |
| `spring.ai.openrouter.chat.api-key` | 无 | 非空时覆盖通用 API Key |
| `spring.ai.openrouter.chat.base-url` | 无 | 非空时覆盖通用服务地址 |
| `spring.ai.openrouter.chat.enabled` | `true` | 属性存在，自动配置未把它作为条件 |
| `spring.ai.openrouter.chat.completions-path` | `/api/v1/chat/completions` | 请求路径 |

默认 URL 与路径组合为 `https://openrouter.ai/api/v1/chat/completions`。使用默认路径时 base URL 应保持 `https://openrouter.ai`，避免重复添加 `/api/v1`。

自动配置在缺少 `OpenRouterChatModel` Bean 时创建模型；手动声明同类型 Bean 可覆盖它。

### 聊天选项（扁平键）

2.0.0 把选项拍平到 `spring.ai.openrouter.chat.*`，共 24 个键：`enabled`、`completions-path` 两个基础设施键，加 22 个选项键。

| 配置键 | 默认 | 说明 |
| --- | --- | --- |
| `model` | `google/gemini-3-flash-preview` | 代码默认值，建议显式指定 |
| `temperature` | 兜底 `0.7` | 采样温度，spec 描述区间 0–2 |
| `stream` | `null` | 通常保持未配置 |
| `max-tokens` | `null` | 序列化为 `max_completion_tokens` |
| `top-p` / `top-k` | `null` | 采样参数 |
| `frequency-penalty` / `presence-penalty` | `null` | 重复相关惩罚 |
| `repetition-penalty` | `null` | OpenRouter 扩展的重复惩罚 |
| `seed` | `null` | 随机种子 |
| `min-p` / `top-a` | `null` | 采样下限与 top-a |
| `stop` | `null` | 停止序列，**最多 4 条** |
| `logprobs` / `top-logprobs` | `null` | 概率返回 |
| `models` | `null` | provider 侧 fallback 模型列表，**最多 3 个** |
| `modalities` | `null` | `text` / `image` / `audio` |
| `service-tier` | `null` | `auto` / `default` / `fast` / `flex` / `priority` / `scale` / `ultrafast` |
| `user` | `null` | 终端用户标识，服务端会哈希后转发 |
| `parallel-tool-calls` | `null` | 是否允许并行工具调用 |
| `prompt-cache-key` | `null` | 提示缓存键 |
| `session-id` | `null` | 会话标识，最长 256 字符 |

结构字段（`reasoning`、`provider`、`tool-choice`、`extra-body`）只在旧写法 `spring.ai.openrouter.chat.options.*` 下提供，两者最终落到同一份选项上。

推理配置示例：

```yaml
spring:
  ai:
    openrouter:
      chat:
        reasoning:
          effort: high      # max / xhigh / high / medium / low / minimal / none
          max-tokens: 2048  # 与 effort 择一
          summary: auto     # auto / concise / detailed
          mode: standard    # standard / pro
          context: current_turn  # auto / all_turns / current_turn
```

### 请求体 43 字段落点

| 类别 | 数量 | 承载方式 |
| --- | --- | --- |
| A 高频强类型 | 25 | `OpenRouterChatOptions` 字段 + `ChatCompletionRequest` 组件 |
| B 低频透传 | 12 | `OpenRouterChatOptions.extraBody`，经 `@JsonAnyGetter` 展平到请求体顶层 |
| C 模型层组装 | 4 | `messages` / `stream` / `tools` / `response_format`（强类型 `ResponseFormat`，由 `responseFormat` / `outputSchema` 驱动） |
| D 不暴露 | 1 | `reasoning_effort`，与 `reasoning.effort` 互斥，只留后者 |
| E 已删除 | 1 | `max_tokens`，改用 `max_completion_tokens` |

`extraBody` 覆盖的 12 个字段：`cache_control`、`debug`、`deferred_tools`、`image_config`、`metadata`、`plugins`、`prediction`、`prompt_cache_options`、`route`、`stop_server_tools_when`、`stream_options`、`trace`。

```java
var options = OpenRouterChatOptions.builder()
        .extraBody(Map.of(
                "trace", Map.of("trace_id", "abc"),
                "metadata", Map.of("tenant", "demo")))
        .build();
```

## 使用示例

### 非流式调用

```java
var options = OpenRouterChatOptions.builder()
        .temperature(0.3)
        .maxTokens(512)
        .build();

ChatResponse response = chatModel.call(new Prompt("什么是依赖注入？", options));
String text = response.getResult().getOutput().getText();
var usage = response.getMetadata().getUsage();
```

### 流式调用与推理读取

```java
var options = OpenRouterChatOptions.builder()
        .reasoning(new OpenRouterApi.Reasoning(
                OpenRouterApi.Reasoning.Effort.HIGH, null, null, null, null, null, null))
        .build();

chatModel.stream(new Prompt("解释一个适合使用动态规划的问题。", options))
        .doOnNext(response -> {
            var output = response.getResult().getOutput();
            if (output.getText() != null) {
                System.out.print(output.getText());
            }
            if (output instanceof OpenRouterAssistantMessage message) {
                if (message.getReasoningContent() != null) {
                    System.out.print("[推理] " + message.getReasoningContent());
                }
                if (message.getReasoningDetails() != null) {
                    // 12 字段的推理详情，回传上游时需要原样带回
                    System.out.println(message.getReasoningDetails());
                }
            }
        })
        .blockLast();
```

流中的正文与推理都是增量，应用需要自行聚合。`blockLast()` 仅用于命令行等允许阻塞的场景。

### 推理详情往返

`OpenRouterAssistantMessage.reasoningDetails` 会在下一轮请求中作为 `reasoning_details` 原样发回上游。这是保留推理签名类模型的必要条件。

```java
var history = List.of(
        new UserMessage("第 2 + 2 等于几？"),
        assistantMessage,  // 含 reasoningDetails 的历史 assistant 消息
        new UserMessage("再加上 3 呢？")
);
chatModel.call(new Prompt(history, options));
```

### 多模态输入

```java
var image = new Media(Media.MimeType.IMAGE_PNG, Path.of("chart.png"));
var userMessage = new UserMessage("这张图里是什么？", List.of(image));

chatModel.call(new Prompt(List.of(userMessage), options));
```

图片支持 `URI`、`String` 与 `byte[]`；音频支持 `byte[]`，其余类型退化为文本部件。视频与 `file` 变体不建模。

### 注册工具并调用

```java
var options = OpenRouterChatOptions.builder()
        .toolCallbacks(ToolCallbacks.from(new DemoTools()))
        .build();

var response = chatModel.call(new Prompt("请调用工具查询应用名称。", options));
```

2.0.0 与 DeepSeek 模块一致：**模型层不再执行工具**。需要自动执行时用 `ToolCallingManager` 或 Spring AI 2.0.0 的 `ToolCallingAdvisor`，或手动读取 `getToolCalls()` 后自行执行并在下一轮传入 `ToolResponseMessage`。

### 结构化输出（`response_format`）

三种格式都能直接请求：`json_schema`（带结构约束）、`json_object`（只要求是 JSON 对象）、`text`（不限制）。
推荐走 Spring AI 的统一入口，用 `ChatClient` 的 `.entity(...)` 配合 `useProviderStructuredOutput()`：
框架会自动把目标类型转成 JSON Schema，通过 `StructuredOutputChatOptions#outputSchema` 写进 `response_format`。

```java
// 由 provider 侧按 schema 约束生成，客户端只做反序列化
var result = chatClient.prompt("把这段说明整理成标题和三条要点")
        .options(OpenRouterChatOptions.builder().model("google/gemini-3-flash-preview").build())
        .call()
        .entity(ArticleSummary.class, spec -> spec.useProviderStructuredOutput());
```

也可以直接给 Options 设响应格式：

```java
// 只支持 JSObject 的上游：退化成 json_object，schema 放进提示词
var jsonObjectOptions = OpenRouterChatOptions.builder()
        .responseFormat(OpenRouterChatOptions.ResponseFormat.builder()
                .type(OpenRouterApi.ResponseFormat.Type.JSON_OBJECT)
                .build())
        .build();

// 手写 schema（不经过 BeanOutputConverter）
var schemaOptions = OpenRouterChatOptions.builder()
        .outputSchema("""
                {"type":"object","properties":{"title":{"type":"string"}},"required":["title"]}
                """)
        .build();
```

三层保证：

1. `strict` 缺省 `true`，请求上游用原生受约束解码；需要关掉时用 `responseFormat(...).strict(false)`。
2. schema 字符串在**请求构建期**解析并校验，非法 JSON 直接抛 `IllegalArgumentException`，不会把坏请求发出去。
3. `json_schema.name` 缺省 `openrouter_response_schema`（OpenRouter 要求该字段必填，字符集 `a-zA-Z0-9_-`，长度 ≤ 64），可用 `name(...)` 覆盖。

六条限制：

- `strict: true` 是**请求严格**，不是**结果保证**。是否真的受约束取决于上游 provider 的实现，部分 provider 只把它当强提示。
- 流式响应会照常发出 `response_format`，但 Spring AI 的流式链路没有结构化 output 的消费机制，需要应用自行聚合文本再反序列化。
- `BeanOutputConverter` 产出的 schema 顶层带 `"$schema": "https://json-schema.org/draft/2020-12/schema"`，本项目原样透传；若某上游只接受 draft-07 等旧版本，需要使用者自行删键。
- 部分上游不支持 `response_format`。需要强制路由时配 `provider.require_parameters = true`，否则请求可能被分发给不支持的 endpoint 并返回 400。
- 没有扁平配置键：`spring.ai.openrouter.chat.*` 下不提供 `response-format` 这类属性，结构化输出只能按请求设置（或用本节末尾的 ChatClient 全局开关，那是 advisor 参数，不是配置属性）。
- 不要在 `extraBody` 里塞 `response_format`：该键已由强类型组件承载，`extraBodyProperties()` 会主动剔除同名键，塞进去也不会生效。

**ChatClient 侧全局启用（可选）**

除了逐请求写 `useProviderStructuredOutput()`，也可以在整个 `ChatClient` 上一次性打开原生结构化输出。
这是 Spring AI 官方文档里的「全局启用」，参数是 `AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT`：

```java
ChatClient chatClient = ChatClient.builder(chatModel)
        .defaultAdvisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
        .build();

// 之后不必再写 spec.useProviderStructuredOutput()
var result = chatClient.prompt("把这段说明整理成标题和三条要点")
        .call()
        .entity(ArticleSummary.class);
```

它本身不生成 schema，只是往请求上下文里放一个 `spring.ai.chat.client.structured.output.native=true` 标记；
当调用真的用到 `.entity(...)` 时，框架据此把 `BeanOutputConverter` 产出的 schema 写进 `StructuredOutputChatOptions#outputSchema`，
效果与逐调用 `useProviderStructuredOutput()` 相同。不走 `.entity(...)` 的普通调用（`call().content()`、`stream()` 等）完全不受影响——当时没有 schema 可写，标记被忽略。

三条使用须知：

1. **开了就关不掉**：判定用的是 `context().containsKey(...)`，不是取值布尔比较，`param(key, false)` 一样算打开；`advisors(Consumer)` 内部是 `putAll`，也无法移除已放入的键。唯一的逃生门是另建一个不挂这个 `ChatClient` 实例。
2. **别在模型路由不可控时默认打开**：打开后每个 `.entity(...)` 调用都会带上 `response_format`，一旦被路由到不支持该参数的 endpoint，OpenRouter 会直接报错而不是静默忽略。要求强制路由时配 `provider.require_parameters = true`。
3. **需要自己引入 `spring-ai-client-chat`**：`ChatClient` 与 `AdvisorParams` 都在那个 artifact 里。本模块只在 `ChatModel` 必要链路上取依赖，`spring-ai-client-chat` 经 `spring-ai-test` 以 test 作用域进来，不传递给使用方（官方 `spring-ai-starter-model-deepseek` 这类 starter 是 compile 引入的，本项目没有对应 starter）。

### 手动创建模型与自定义请求头

```java
var api = OpenRouterApi.builder()
        .apiKey(System.getenv("OPENROUTER_API_KEY"))
        .header("HTTP-Referer", "https://your-app.example")
        .header("X-OpenRouter-Title", "Your App")
        .build();

var chatModel = OpenRouterChatModel.builder()
        .openRouterApi(api)
        .options(OpenRouterChatOptions.builder()
                .model(System.getenv("OPENROUTER_MODEL"))
                .temperature(0.7)
                .build())
        .build();
```

`Builder` 上的选项方法名为 `options(...)`（2.0.0 对齐 Spring AI 2.0.0 的 `ChatModel.getOptions()` 命名），不再叫 `defaultOptions(...)`。

## 能力清单

| 维度 | 2.0.0 |
| --- | --- |
| 构建 | Java 17 · Spring Boot 4.1.0 · Spring AI BOM 2.0.0 · `revision` = 2.0.0 |
| 包结构 | 根包（Options / ChatModel / AssistantMessage）、`api`、`api.common`、`autoconfigure`、`aot` |
| Options 契约 | `implements ToolCallingChatOptions, StructuredOutputChatOptions`，泛型 Builder + `mutate()` + `clone()` + `combineWith()`，31 个不可变字段 |
| 非流式 | `chatCompletionEntity` 真实实现，走 `RestClient` + 重试 |
| 流式 | `WebClient` + SSE 解析 + 窗口缝合，`MessageAggregator` 聚合 |
| 观测 | `ChatModelObservationDocumentation` 埋点，两条链路都覆盖 |
| 重试 | `org.springframework.core.retry.RetryTemplate` + `RetryUtils.execute` 真实接线 |
| 配置 | 扁平键 24 项 + `@DeprecatedConfigurationProperty` 兼容桥 |
| 多模态 | image（URI / String / byte[]）+ audio（byte[]）+ else 文本兜底 |
| 响应 | `usage` 10 字段（含 cache 读写）、`reasoningDetails` 12 字段、`system_fingerprint`、`openrouter_metadata` |
| AOT | `OpenRouterRuntimeHints` 注册 Jackson DTO |
| 测试 | 8 个单元测试类（74 个用例）+ 3 个 `@Disabled` 真实网络 IT |

### 明确不做

- 视频输入与 `file` 内容部件：Spring AI 2.0.0 的 OpenAI 模块自身无 video 分支，不建模。
- 模型层工具执行：交由 `ToolCallingManager` / `ToolCallingAdvisor`。
- Embedding、图像生成、语音、模型列表查询等其它模型接口。
- Session 与记忆：不自动保存会话，需要应用传入历史。

## 与 Spring AI 2.0.0 DeepSeek 模块对比

对标源码：`models/spring-ai-deepseek` 与 `auto-configurations/models/spring-ai-autoconfigure-model-deepseek`（tag `v2.0.0`）。

| 维度 | 本项目 2.0.0 | DeepSeek 2.0.0 | 结论 |
| --- | --- | --- | --- |
| 非流式 `call()` | 真实实现 | 有 | ✅ 对齐 |
| 流式 | WebClient + 窗口缝合 | 同 | ✅ 对齐 |
| 观测 | 两条链路埋点 | 同 | ✅ 对齐 |
| 重试 | `RetryTemplate` 真实接线 | 同 | ✅ 对齐 |
| Options 契约 | `ToolCallingChatOptions` + `StructuredOutputChatOptions` + 泛型 Builder + mutate | 仅 `ToolCallingChatOptions` + 泛型 Builder + mutate | ➕ 本项目多接一个接口 |
| 配置扁平化 | 24 项扁平 + 兼容桥 | 13 项扁平（2.0.1 为 15 项） | ✅ 对齐机制，键更多 |
| 请求字段 | 43 字段全景（25 强类型 + 12 extraBody） | 14 组件 | ➕ 覆盖更广 |
| `reasoning_details` | 12 字段无损往返 | 2 字段（reasoningContent + prefix） | ➕ 本项目更完整 |
| `usage` | 10 字段 + cache 读写映射 | 4 字段，cache 未映射 | ➕ 本项目更完整 |
| 多模态 | image + audio + else | DeepSeek 无多模态 | ➕ 本项目特有 |
| 应用归属头 | `HTTP-Referer` / `X-OpenRouter-Title` / `X-OpenRouter-Categories` | 无 | ➕ 本项目特有 |
| 结构化输出 | `json_schema` / `json_object` / `text` 三变体，实现 `StructuredOutputChatOptions` | `text` / `json_object` 两变体，不实现 `StructuredOutputChatOptions` | ➕ 本项目支持 `json_schema` |
| 工具执行 | 模型层不执行，交 Advisor | 同 | ✅ 对齐 |
| 测试 | 8 个单元测试类（74 个用例）+ 3 个 `@Disabled` IT | 14 个测试文件（含多套 IT） | ⚠️ 结构对齐，数量少于官方 |
| CI | 无 | GitHub Actions | ⚠️ 本轮不新增 |

深度对比见 [docs/CAPABILITIES.md](docs/CAPABILITIES.md)。

## 从 1.1.0 迁移

1. **坐标版本**：`<version>` 从 `1.1.0` 改为 `2.0.0`，同时把 Spring Boot 升到 4.1.0、Spring AI BOM 升到 2.0.0。
2. **包名迁移**：`io.github.shiheiyea.spring.ai.openrouter.config` → `...openrouter.autoconfigure`，4 个类。不保留旧包。
3. **配置拍平**：`spring.ai.openrouter.chat.options.model` → `spring.ai.openrouter.chat.model`。旧写法仍生效但已标 `@Deprecated`，会在启动时给出告警。
4. **`getDefaultOptions()` → `getOptions()`**：`getDefaultOptions()` 在 Spring AI 2.0.0 已标 `@Deprecated(forRemoval = true)`。Builder 方法 `defaultOptions(...)` 同步改名为 `options(...)`。
5. **`call(Prompt)` 可用**：1.1.0 返回 `null`，现在返回真实 `ChatResponse`。
6. **工具执行下沉**：1.1.0 模型层内部的 `toolExecutionEligibilityPredicate` + `executeToolCalls` + `returnDirect` 递归全部移除。若依赖旧的自动执行行为，请改用 `ToolCallingAdvisor`。
7. **`prefix` 删除**：`OpenRouterAssistantMessage` 不再有 `prefix`；新增 `reasoningDetails`。
8. **`max_tokens` → `max_completion_tokens`**：字段名变了，`maxTokens(...)` 方法名不变。
9. **`reasoning` 强类型**：`effort` 从 3 值扩到 7 值，新增 `summary` / `mode` / `context`。旧配置的 `effort: low|medium|high` 仍可用。

## 本地构建

使用 JDK 17：

```shell
JAVA_HOME=E:\code\JDK\Java17 mvn clean verify
```

产物是 `target/heiye-spring-ai-model-openrouter-starter-2.0.0.jar`，供其他应用依赖，不是可直接 `java -jar` 启动的 Spring Boot 应用。`verify` 会编译、跑 74 个单元测试并打包，不包含真实 OpenRouter 请求。

真实网络测试默认关闭（`@Disabled`），手动执行时设置 `OPENROUTER_API_KEY` 环境变量。

## 常见问题

**为什么启动时提示 `OpenRouter API key must be set`？**

模块默认自动启用。设置 `OPENROUTER_API_KEY`，或在暂时不使用时配置 `spring.ai.model.chat=none`。

**为什么 `spring.ai.openrouter.chat.options.*` 还能用？**

保留了 `@DeprecatedConfigurationProperty` 兼容桥，但启动时会有弃用告警。新代码请使用扁平键；结构字段（`reasoning` / `provider` / `tool-choice` / `extra-body`）仍然只在 `options.*` 下提供。

**为什么工具没有被自动执行？**

2.0.0 对齐 DeepSeek 模块，模型层不执行工具。请用 `ToolCallingManager` 手动执行，或引入 `ToolCallingAdvisor`。

**为什么没有自动重试或模型指标？**

两条链路都已接入。重试由 `spring.ai.retry.*` 配置，观测由 `ObservationRegistry` 驱动。

**回传推理详情为什么重要？**

部分模型要求把 `reasoning_details` 原样带回，否则会丢签名。本模块在 `OpenRouterAssistantMessage.reasoningDetails` 里保留 12 个字段并在下一轮请求中原样发出。

## 参与贡献

欢迎通过 [Issues](https://github.com/shiheiyea/heiye-spring-ai-openrouter-starter/issues) 提交问题和建议。报告问题时请附 JDK / Spring Boot / Spring AI 版本、模型 ID、脱敏后的配置与错误信息；不要附带 API Key。

本项目采用 [Apache License 2.0](LICENSE)。部分实现参考并改编自 Spring AI 2.0.0，来源与修改说明见 [NOTICE](NOTICE)。

## 参考资料

- [Spring AI v2.0.0 DeepSeek 模块](https://github.com/spring-projects/spring-ai/tree/v2.0.0/models/spring-ai-deepseek)：范式核对基线。
- [Spring AI v2.0.0 DeepSeek 自动配置](https://github.com/spring-projects/spring-ai/tree/v2.0.0/auto-configurations/models/spring-ai-autoconfigure-model-deepseek)：自动配置与依赖组合基线。
- [OpenRouter OpenAPI 规范](https://openrouter.ai/openapi.json)：请求体 43 字段与响应 schema 的一手来源。
