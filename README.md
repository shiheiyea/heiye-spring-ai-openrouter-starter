<div align="center">

# Heiye Spring AI OpenRouter Starter

**用 Spring AI 的 `ChatModel` 接口访问 OpenRouter**

一个 Spring Boot Starter：把 OpenRouter 的 Chat Completions 接口适配成 Spring AI 的聊天模型，
复用统一的消息、聊天选项、工具调用、结构化输出与可观测性机制。

![Java](https://img.shields.io/badge/Java-17-007396?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-6DB33F?logo=springboot&logoColor=white)
![Spring AI](https://img.shields.io/badge/Spring%20AI-2.0.1-6DB33F?logo=spring&logoColor=white)
![License](https://img.shields.io/badge/License-Apache%202.0-blue)

</div>

[项目定位](#项目定位) · [快速开始](#快速开始) · [配置](#配置) · [使用指南](#使用指南) · [能力清单](#能力清单) · [从 1.1.0 迁移](#从-110-迁移) · [构建与测试](#构建与测试) · [常见问题](#常见问题)

## 项目定位

本项目把 OpenRouter 适配为 Spring AI 的聊天模型，建模对象是 OpenRouter 协议的真实能力面。

- **标准契约**：实现 `ChatModel`，可注入 `ChatModel` 接口与其它模型实现互换。
- **两条链路**：非流式 `call(Prompt)` 与流式 `stream(Prompt)`；两者都有 Micrometer 观测，重试只覆盖非流式请求。
- **贴近协议**：OpenRouter 的采样参数、推理控制、provider 路由偏好、多模态输入都直接可用。
- **不重复造轮子**：工具执行、结构化输出转换交由 Spring AI 既有机制处理。

模型 ID 直接传字符串即可。某个模型是否可用、是否支持工具调用或推理，由 OpenRouter 和实际承接的提供商决定。
本项目不是 OpenRouter 全量 API SDK（不含 Embedding、图像生成、模型列表等其它接口），也不是 Spring AI 官方模块。
它取代的是 Spring AI 官方 starter 中「某一家的聊天模型实现」这一层，`spring.ai.model.chat` 是单选开关，同一应用只启用一个。

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>io.github.shiheiyea</groupId>
    <artifactId>heiye-spring-ai-model-openrouter-starter</artifactId>
    <version>2.0.1</version>
</dependency>
```

> 尚未发布到 Maven Central 时，先克隆 [本仓库](https://github.com/shiheiyea/heiye-spring-ai-openrouter-starter)
> 执行 `mvn clean install`，再在本地项目引用。

建议消费项目使用 Spring Boot 4.1.1，并导入 Spring AI BOM 2.0.1，避免不同 Spring AI 模块版本混用：

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>2.0.1</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

### 2. 配置密钥与模型

```yaml
spring:
  ai:
    openrouter:
      api-key: ${OPENROUTER_API_KEY}
      chat:
        model: ${OPENROUTER_MODEL}
        temperature: 0.7
        max-tokens: 2048
```

```powershell
$env:OPENROUTER_API_KEY = '你的 OpenRouter API Key'
$env:OPENROUTER_MODEL = '你的完整模型 ID'
```

不要把密钥提交到仓库。项目不会自动加载 `.env` 文件。

### 3. 注入并使用

```java
import io.github.shiheiyea.spring.ai.openrouter.OpenRouterChatModel;
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

    @GetMapping("/ai/chat")
    public String chat(@RequestParam(defaultValue = "用一句话介绍 Spring AI") String message) {
        return chatModel.call(message);
    }

    @GetMapping(value = "/ai/chat/stream", produces = "text/event-stream")
    public Flux<String> stream(@RequestParam(defaultValue = "用一句话介绍 Spring AI") String message) {
        return chatModel.stream(message);
    }
}
```

示例需要 Spring Web 依赖。返回 `Flux` 的接口在 Spring MVC 与 WebFlux 下都可直接使用。

## 配置

配置分两层：`spring.ai.openrouter.*` 是连接参数，`spring.ai.openrouter.chat.*` 是聊天参数。
聊天参数可以按需省略。除 `model` 有代码默认值外，未配置的项不写入请求体，由 OpenRouter 决定默认行为。

### 连接

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `spring.ai.model.chat` | 未配置时也启用 | 设为 `openrouter` 启用；设为 `none` 或其它值禁用 |
| `spring.ai.openrouter.api-key` | 无 | API Key，启用自动配置时必填 |
| `spring.ai.openrouter.base-url` | `https://openrouter.ai` | 服务地址 |
| `spring.ai.openrouter.chat.api-key` | 无 | 非空时覆盖上面的 API Key |
| `spring.ai.openrouter.chat.base-url` | 无 | 非空时覆盖上面的服务地址 |

默认请求地址为 `https://openrouter.ai/api/v1/chat/completions`。
若自定义 `base-url`，请注意它与 `completions-path` 是拼接关系，不要重复写 `/api/v1`。

### 聊天

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `spring.ai.openrouter.chat.completions-path` | `/api/v1/chat/completions` | 请求路径 |
| `spring.ai.openrouter.chat.model` | `google/gemini-3-flash-preview` | 代码默认值，建议显式指定 |
| `spring.ai.openrouter.chat.temperature` | 未设置 | 采样温度，不设置时由模型自身决定 |
| `spring.ai.openrouter.chat.max-tokens` | 未设置 | 序列化为 `max_completion_tokens` |
| `spring.ai.openrouter.chat.top-p` | 未设置 | 核采样 |
| `spring.ai.openrouter.chat.top-k` | 未设置 | top-k 采样 |
| `spring.ai.openrouter.chat.min-p` | 未设置 | 最小概率采样 |
| `spring.ai.openrouter.chat.top-a` | 未设置 | top-a 采样 |
| `spring.ai.openrouter.chat.frequency-penalty` | 未设置 | 频率惩罚 |
| `spring.ai.openrouter.chat.presence-penalty` | 未设置 | 存在惩罚 |
| `spring.ai.openrouter.chat.repetition-penalty` | 未设置 | OpenRouter 扩展的重复惩罚 |
| `spring.ai.openrouter.chat.seed` | 未设置 | 随机种子 |
| `spring.ai.openrouter.chat.stop` | 未设置 | 停止序列，最多 4 条 |
| `spring.ai.openrouter.chat.logprobs` | 未设置 | 是否返回 token 概率 |
| `spring.ai.openrouter.chat.top-logprobs` | 未设置 | 返回前 N 个候选概率 |
| `spring.ai.openrouter.chat.models` | 未设置 | 备用模型列表，按顺序降级 |
| `spring.ai.openrouter.chat.modalities` | 未设置 | 输出模态，如 `text`、`image`、`audio` |
| `spring.ai.openrouter.chat.service-tier` | 未设置 | 服务等级：`auto`、`default`、`flex`、`priority`、`scale`、`ultrafast`，`fast` 是 `priority` 的别名 |
| `spring.ai.openrouter.chat.user` | 未设置 | 终端用户标识，服务端哈希后转发 |
| `spring.ai.openrouter.chat.parallel-tool-calls` | 未设置 | 是否允许并行工具调用 |
| `spring.ai.openrouter.chat.strict` | 未设置 | 工具调用是否启用严格模式，序列化为 `tools[].function.strict` |
| `spring.ai.openrouter.chat.prompt-cache-key` | 未设置 | 提示缓存键 |
| `spring.ai.openrouter.chat.session-id` | 未设置 | 会话标识，最长 256 字符 |

还有一个 `spring.ai.openrouter.chat.stream` 属性未列入上表：它能被绑定，但实际是否流式由调用方式（`call` / `stream`）决定，配置它不会改变链路。

`stop` 超过 4 条时，Builder 会在构建阶段抛出 `IllegalArgumentException`，不会截断或忽略。

### 结构化字段

推理参数是嵌套结构，没有对应的扁平项，写在旧路径下：

```yaml
spring:
  ai:
    openrouter:
      chat:
        options:
          reasoning:
            effort: high            # max / xhigh / high / medium / low / minimal / none
            max-tokens: 2048        # 与 effort 择一
            summary: auto           # auto / concise / detailed
            mode: standard          # standard / pro
            context: current_turn   # auto / all_turns / current_turn
```

provider 路由偏好、工具选择、自定义请求字段同样是嵌套结构，建议直接用代码设置，避免 YAML 里手写复杂对象：

```java
var options = OpenRouterChatOptions.builder()
        .provider(new OpenRouterApi.ProviderPreferences(
                true,                                   // allowFallbacks
                OpenRouterApi.ProviderPreferences.DataCollection.DENY,
                null,                                   // enforceDistillableText
                null,                                   // ignore
                null,                                   // maxPrice
                List.of("anthropic", "google"),          // only
                null,                                   // options
                List.of("anthropic"),                    // order
                null,                                   // preferredMaxLatency
                null,                                   // preferredMinThroughput
                null,                                   // quantizations
                true,                                   // requireParameters
                null,                                   // sort
                null))                                  // zdr
        .extraBody(Map.of("transforms", List.of("middle-out")))
        .build();
```

这些字段与扁平项最终落到同一份 `OpenRouterChatOptions` 上。注意请求级选项是**整体替换**而不是逐字段合并：
一旦给 `call` / `stream` 传了 `Prompt` 选项，配置项里的值就不再参与这次请求。想在保留配置的基础上只改一两个字段，
用 `chatModel.getOptions().mutate()` 作为起点：

```java
var options = chatModel.getOptions().mutate()
        .temperature(0.2)
        .build();
```

### 覆盖自动配置

自动配置在容器中不存在 `OpenRouterChatModel` 时创建模型，声明同类型 Bean 即可整体替换。
需要完全关闭本模块时，设置 `spring.ai.model.chat=none`。

## 使用指南

以下示例按 Bean 的实际类型注入 `OpenRouterChatModel`。只需要标准能力时也可以声明为 `ChatModel`，两者的
`call` / `stream` 用法相同，但具体类才能访问 `getOptions()` 等 OpenRouter 专有方法。

逐请求的选项通过 `OpenRouterChatOptions` 传入：不传选项时使用配置项与代码默认值；传了选项则以传入的为准。
用 `ChatClient` 时框架会先取模型默认值，再合并请求级选项，两层配置可以叠加。

`ChatClient` 相关的示例需要额外引入 `spring-ai-client-chat`，本模块不传递该依赖。

### 非流式调用

```java
var options = OpenRouterChatOptions.builder()
        .temperature(0.3)
        .maxTokens(512)
        .build();

ChatResponse response = chatModel.call(new Prompt("什么是依赖注入？", options));
String text = response.getResult().getOutput().getText();
Usage usage = response.getMetadata().getUsage();
```

`OpenRouterChatOptions` 是不可变对象：`builder()` 创建、`mutate()` 派生修改、`combineWith(...)` 合并另一份选项。

### 流式调用

```java
chatModel.stream(new Prompt("解释一个适合使用动态规划的问题。", options))
        .doOnNext(response -> {
            String text = response.getResult().getOutput().getText();
            if (text != null) {
                System.out.print(text);
            }
        })
        .blockLast();
```

流中的正文是增量片段，应用需要自行拼接。`blockLast()` 仅适用于命令行等允许阻塞的场景，
在 Web 应用中应直接返回 `Flux`。

### 推理内容

只调思考强度时用 `reasoningEffort`，其余推理字段保持默认：

```java
var options = OpenRouterChatOptions.builder()
        .reasoningEffort(OpenRouterApi.Reasoning.Effort.HIGH)
        .build();
```

需要控制推理预算、或只想统计不想拿到推理文本时，用静态工厂：

```java
var budgeted = OpenRouterChatOptions.builder()
        .reasoning(OpenRouterApi.Reasoning.budget(2048))   // 只给推理 token 预算
        .build();

var hidden = OpenRouterChatOptions.builder()
        .reasoning(OpenRouterApi.Reasoning.excluded())     // 开启推理但不回传推理文本
        .build();
```

需要同时指定多个维度时才用完整构造器：

```java
var detailed = OpenRouterChatOptions.builder()
        .reasoning(new OpenRouterApi.Reasoning(
                OpenRouterApi.Reasoning.Effort.HIGH,   // effort
                2048,                                  // max_tokens
                false,                                 // exclude
                true,                                  // enabled
                OpenRouterApi.Reasoning.Summary.AUTO,  // summary
                OpenRouterApi.Reasoning.Mode.STANDARD, // mode
                OpenRouterApi.Reasoning.Context.CURRENT_TURN))
        .build();
```

开启推理后，推理文本与推理详情分别通过 `OpenRouterAssistantMessage` 暴露：

```java
chatModel.stream(new Prompt("解释一个适合使用动态规划的问题。", options))
        .doOnNext(response -> {
            if (response.getResult().getOutput() instanceof OpenRouterAssistantMessage message) {
                if (message.getReasoningContent() != null) {
                    System.out.print("[推理] " + message.getReasoningContent());
                }
            }
        })
        .blockLast();
```

`reasoningContent` 是便于阅读的推理文本；`reasoningDetails` 是协议层的推理详情，
部分模型要求在多轮对话中把它原样回传，否则会丢失推理签名。只要历史里的助手消息是本模块返回的
`OpenRouterAssistantMessage`，下一轮请求就会自动带上这两个字段，无需手工拼接：

```java
var history = List.of(
        new UserMessage("第 2 + 2 等于几？"),
        assistantMessage,  // 上一轮返回的 OpenRouterAssistantMessage，原样放回历史即可
        new UserMessage("再加上 3 呢？"));
chatModel.call(new Prompt(history, options));
```

两个使用注意：

- **各模型支持的档位不同。** `GET /api/v1/models` 的 `reasoning.supported_efforts` 给出每个模型的范围，
  例如 `google/gemini-4-argon` 只支持 `high` / `medium` / `low` 且推理不可关闭（传 `none` 返回 400），
  `openai/gpt-6-luna` 支持 `max` / `xhigh` / `high` / `medium` / `low` / `none`。传了范围外的档位
  通常不会报错，上游会映射到最接近的一档。
- **推理 token 计入 `maxTokens`。** 强度调高而 `maxTokens` 不变时，推理可能吃掉整个预算，
  表现为 `finish_reason=length` 且正文为空。调高强度时请同步调大 `maxTokens`，
  或用 `Reasoning.budget(...)` 单独给推理预算。

### 多模态输入

```java
import org.springframework.ai.content.Media;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.core.io.FileSystemResource;

var image = new Media(Media.Format.IMAGE_PNG, new FileSystemResource("chart.png"));
var userMessage = UserMessage.builder()
        .text("这张图里是什么？")
        .media(image)
        .build();
chatModel.call(new Prompt(userMessage, options));
```

`Media` 的构造器只接受 `MimeType` 加 `URI` 或 `Resource` 两种组合，不接受 `Path`。
传入 `Resource` 时，构造器会把内容读成 `byte[]` 存进 `Media`；本模块按 `Media#getData()` 的实际类型分发：

| `Media` 数据实际类型 | 图片 | 音频 |
| --- | --- | --- |
| `URI` 或 `String`（远程地址） | 直接作为 `image_url` 发送 | 不支持，只接受 `byte[]` |
| `byte[]`（含 `Resource` 读入的内容） | 编码为 `data:` URL 发送 | 编码为裸 Base64 发送（不带 `data:` 前缀） |

音频与图片的编码方式不同：OpenRouter 的 `input_audio.data` 只接受裸 Base64，
带上 `data:audio/wav;base64,` 前缀会被上游判定为解码失败。
音频的格式串取 MIME 子类型（如 `audio/wav` → `wav`），MIME 缺失或类型不匹配的内容会被丢弃并记录一条 INFO 日志。
非图片、非音频的 MIME 会退化为文本部件。视频与 `file` 内容部件不在建模范围内。

### 工具调用

注册工具的方式与 Spring AI 其它模型一致，通过 `toolCallbacks` 提供：

```java
import org.springframework.ai.support.ToolCallbacks;

var options = OpenRouterChatOptions.builder()
        .toolCallbacks(ToolCallbacks.from(new DemoTools()))
        .build();

var response = chatModel.call(new Prompt("请查询当前应用名称。", options));
```

**执行归属**：`ChatModel` 只负责发起请求并返回模型产出的工具调用，不执行工具——这是 Spring AI 2.0.1 的统一契约，
不是本模块的缺口。需要自动执行时有两种做法：

- 用 `ChatClient`：它默认自动注册 `ToolCallingAdvisor`，会驱动「请求 → 执行工具 → 再请求」的循环，直到模型不再请求工具。
- 直接用 `ChatModel`：自行读取响应中的工具调用，执行后用 `ToolResponseMessage` 把结果带回下一轮。

`ChatClient` 里注册工具用 `.tools(...)`（逐次）或 `.defaultTools(...)`（构建时），工具由 `ToolCallingAdvisor` 自动执行：

```java
ChatClient chatClient = ChatClient.builder(chatModel)
        .defaultTools(new DemoTools())
        .build();

String answer = chatClient.prompt("请查询当前应用名称。").call().content();
```

直接用 `ChatModel` 时手动驱动循环：

```java
import org.springframework.ai.model.tool.DefaultToolCallingManager;

var toolCallingManager = DefaultToolCallingManager.builder().build();
var prompt = new Prompt("旧金山天气如何？", options);
var response = chatModel.call(prompt);

while (response.hasToolCalls()) {
    var result = toolCallingManager.executeToolCalls(prompt, response);
    prompt = new Prompt(result.conversationHistory(), options);
    response = chatModel.call(prompt);
}

String text = response.getResult().getOutput().getText();
```

流式场景下，工具调用片段会先被缝合为完整调用再返回；需要聚合完整响应时可配合
`org.springframework.ai.chat.model.MessageAggregator#aggregate(Flux, Consumer)`。

### 结构化输出

OpenRouter 的 `response_format` 支持三种形式：

| 形式 | 行为 | 适用场景 |
| --- | --- | --- |
| `json_schema` | 按给定 JSON Schema 约束输出 | 上游支持受约束解码时，最可靠 |
| `json_object` | 只要求输出是 JSON 对象 | 上游不支持 schema，但支持 JSON 模式 |
| `text` | 不做限制 | 默认 |

推荐通过 `ChatClient` 的统一入口，让框架把目标类型转成 schema：

```java
ChatClient chatClient = ChatClient.builder(chatModel).build();

ArticleSummary result = chatClient.prompt("把这段说明整理成标题和三条要点")
        .options(OpenRouterChatOptions.builder().model("google/gemini-3-flash-preview"))
        .call()
        .entity(ArticleSummary.class, spec -> spec.useProviderStructuredOutput());
```

也可以直接给选项设置响应格式：

```java
// 只支持 JSON 模式的上游：退化为 json_object，schema 放进提示词
var jsonObjectOptions = OpenRouterChatOptions.builder()
        .responseFormat(OpenRouterChatOptions.ResponseFormat.builder()
                .type(OpenRouterApi.ResponseFormat.Type.JSON_OBJECT)
                .build())
        .build();

// 手写 schema
var schemaOptions = OpenRouterChatOptions.builder()
        .outputSchema("""
                {"type":"object","properties":{"title":{"type":"string"}},"required":["title"]}
                """)
        .build();
```

`outputSchema(...)` 会自动把响应格式切换为 `json_schema`，等价于手动构造 `ResponseFormat`。
schema 名称默认 `openrouter_response_schema`，可用 `name(...)` 覆盖。

直接用 `ChatModel` 时，需要自己把 JSON 反序列化成对象。`BeanOutputConverter` 可以同时生成 schema 和完成转换：

```java
import org.springframework.ai.converter.BeanOutputConverter;

var converter = new BeanOutputConverter<>(ArticleSummary.class);

var options = OpenRouterChatOptions.builder()
        .outputSchema(converter.getJsonSchema())
        .build();

ChatResponse response = chatModel.call(new Prompt("把这段说明整理成标题和三条要点", options));
ArticleSummary summary = converter.convert(response.getResult().getOutput().getText());
```

需要更精细控制响应格式时，用 `responseFormat(...)` 直接构造，其中 `strict` 默认为 `true`：

```java
var options = OpenRouterChatOptions.builder()
        .responseFormat(OpenRouterChatOptions.ResponseFormat.builder()
                .type(OpenRouterApi.ResponseFormat.Type.JSON_SCHEMA)
                .jsonSchema(converter.getJsonSchema())
                .name("article_summary")
                .strict(false)          // 关闭严格模式，部分上游会因此接受更宽松的 schema
                .build())
        .build();
```

`ResponseFormat.Builder#jsonSchema(String)` 会自动把类型切换成 `JSON_SCHEMA`，单独调用 `type(...)` 时则按传入值走。
只设置 `type(JSON_OBJECT)` 而模型仍返回非 JSON 文本时，本模块不做补救，需要应用自行校验。

#### 不同模型的支持情况

结构化输出是否真正生效，取决于**模型和实际承接该模型的提供商**，而不是本模块：

- 支持与否按 endpoint 判定。同一个模型可能由多个提供商承接，其中只有部分支持结构化输出，且支持情况会随时间变化。
  可在 [OpenRouter 模型列表](https://openrouter.ai/models?order=newest&supported_parameters=structured_outputs)按 `structured_outputs` 参数筛选。
- 即使上游支持，`strict` 的语义也不完全一致：部分提供商执行原生受约束解码，部分只是把你的 schema 转成自己的格式或当作强提示。
  因此 `strict: true` 是**请求严格**，不是**结果保证**，仍需对返回内容做校验。
- 部分上游完全不支持 `response_format`，请求被路由过去会直接报错，而不是静默忽略。

#### 使用注意事项

- **强制路由到支持的上游**：在 provider 偏好中设置 `require_parameters = true`，
  否则请求可能被分发给不支持该参数的 endpoint 并返回 400。
- **流式响应不消费结构化输出**：流式链路会照常发出 `response_format`，但 Spring AI 没有对应的流式消费机制，
  需要应用自行聚合文本再反序列化。
- **schema 版本**：`BeanOutputConverter` 产出的 schema 顶层带 `"$schema": "https://json-schema.org/draft/2020-12/schema"`，
  本项目原样透传；若某上游只接受更旧的草案版本，需要自行删键。
- **不要写进 `extraBody`**：`response_format` 已由强类型组件承载，`extraBody` 中的同名键会被剔除。
- **没有对应的扁平配置项**：结构化输出按请求设置，不通过 `application.yml` 配置。

#### 在 ChatClient 上全局启用

如果希望所有 `.entity(...)` 调用都走原生结构化输出，可以在构建 `ChatClient` 时打开一次：

```java
ChatClient chatClient = ChatClient.builder(chatModel)
        .defaultAdvisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
        .build();
```

这个参数本身不生成 schema，只是往请求上下文放一个开关标记；当调用真正用到 `.entity(...)` 时，
框架据此把 schema 写进 `OpenRouterChatOptions#outputSchema`，效果与逐调用 `useProviderStructuredOutput()` 相同。
不走 `.entity(...)` 的调用（如 `call().content()`）不受影响。

两点需要留意：

- 该开关是**单向**的：判定依据是上下文中是否存在该键，传入 `false` 一样算打开，且无法从已构建的 advisor 列表中移除。
  需要回到逐调用控制时，另建一个不挂载该参数的 `ChatClient`。
- 若应用里已经有自定义的工具 Advisor，注意工具 Advisor 在链中只能有一个，注册第二个会导致默认的 `ToolCallingAdvisor` 不再自动注册。

### 手动创建模型

需要自定义请求头（例如 OpenRouter 的应用归属头）或完全接管客户端时，可以手动构建：

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

`OpenRouterApi.Builder` 还支持 `baseUrl(String)`、`completionsPath(String)`、
`restClientBuilder(RestClient.Builder)`、`webClientBuilder(WebClient.Builder)`、
`responseErrorHandler(ResponseErrorHandler)`，以及逐条添加请求头的 `header(String, String)`。
`restClientBuilder` 与 `webClientBuilder` 用于接管 HTTP 客户端，例如换成自定义超时或代理。

### 观测与重试

两条链路都接入了 Micrometer observation，观测名为 `gen_ai.client.operation`，标签沿用 Spring AI 的聊天模型约定
（`gen_ai.*` 系列），可直接对接现有监控。自定义观测约定时，向容器提供 `ChatModelObservationConvention`。

重试由 Spring AI 的 `spring.ai.retry.*` 配置驱动：

```yaml
spring:
  ai:
    retry:
      max-attempts: 10
      backoff:
        initial-interval: 2s
        multiplier: 5
        max-interval: 3m
      on-client-errors: false
      on-http-codes: [429, 500, 502, 503, 504]
      exclude-on-http-codes: []
```

需要自定义时，向容器提供 `RetryTemplate`。注意**重试只覆盖非流式链路**：`call(Prompt)` 走 `RetryUtils.execute`，
`stream(Prompt)` 直接订阅 SSE，不经过重试模板，流式中断需要应用侧重连。

## 能力清单

| 维度 | 支持情况 |
| --- | --- |
| 构建坐标 | Java 17 · Spring Boot 4.1.1 · Spring AI BOM 2.0.1 |
| 选项契约 | 实现 `ToolCallingChatOptions` 与 `StructuredOutputChatOptions`，可参与 `ChatClient` 的统一选项机制 |
| 非流式 | `call(Prompt)`，走 `RestClient`，带重试 |
| 流式 | `stream(Prompt)`，走 `WebClient` + SSE，工具调用片段自动缝合 |
| 观测 | 两条链路均有 Micrometer observation 埋点 |
| 采样与请求控制 | temperature、top-p、top-k、min-p、top-a、各类惩罚、seed、stop、logprobs、备用模型、服务等级等 |
| 推理 | 7 档 effort，支持 max-tokens、summary、mode、context；推理详情无损往返 |
| provider 路由 | `ProviderPreferences` 全部 14 项（allow / ignore / only / order、价格上限、量化、数据策略、`require_parameters`、`zdr` 等） |
| 结构化输出 | `json_schema` / `json_object` / `text` 三种响应格式 |
| 工具调用 | 解析 Spring AI `ToolCallback` 为工具定义；模型层不执行工具，执行交由 `ToolCallingManager` 或 `ToolCallingAdvisor` |
| 多模态 | 图片与音频输入 |
| 响应元数据 | `usage` 全字段（含缓存读写的映射）、`reasoningDetails`、`system_fingerprint`、`created` |
| 额外请求字段 | `extraBody` 透传 OpenRouter 的扩展参数 |
| AOT | 注册 Jackson DTO 的 runtime hints，支持 GraalVM 原生镜像 |
| 不覆盖 | 视频与 `file` 内容部件；Embedding、图像生成、语音、模型列表等其它模型接口；会话历史的自动保存（需应用自行传入） |

## 从 1.1.0 迁移

2.0.1 是一次破坏性升级，对齐 Spring AI 2.0.1 的 `ChatModel` 契约。

1. **坐标与版本**：artifactId 不变（`heiye-spring-ai-model-openrouter-starter`），`<version>` 从 `1.1.0` 改为 `2.0.1`；
   同时 Spring Boot 从 3.5.7 升到 4.1.1、Spring AI 从 1.1.0 升到 2.0.1。
2. **包名迁移**：`io.github.shiheiyea.spring.ai.openrouter.config` → `...openrouter.autoconfigure`，
   涉及 `OpenRouterChatAutoConfiguration`、`OpenRouterChatProperties`、`OpenRouterConnectionProperties`、`OpenRouterParentProperties` 四个类，不保留旧包。
3. **配置扁平化**：`spring.ai.openrouter.chat.options.model` → `spring.ai.openrouter.chat.model`，其余标量项同理。
   旧写法仍生效，但已在配置元数据中标记为废弃。结构化字段（`reasoning` / `provider` / `tool-choice` / `extra-body`）没有扁平项，继续走旧写法。
   两种写法同时存在时落到同一份 `OpenRouterChatOptions`，后解析的值覆盖前者。
4. **`getDefaultOptions()` → `getOptions()`**：前者在 Spring AI 2.0.1 已废弃；`OpenRouterChatModel.Builder` 的 `defaultOptions(...)` 同步改名为 `options(...)`。
5. **`call(Prompt)` 可用**：1.1.0 返回 `null`，现在返回真实 `ChatResponse`。
6. **工具执行下沉**：模型层内部的自动执行逻辑已移除。若依赖旧的自动执行行为，请改用 `ChatClient`（自动注册 `ToolCallingAdvisor`）或手动驱动 `ToolCallingManager`。
7. **`prefix` 删除**：`OpenRouterAssistantMessage` 不再有 `prefix`，新增 `reasoningDetails`。
8. **`max_tokens` → `max_completion_tokens`**：请求字段名变化，`maxTokens(...)` 方法名不变。
9. **`reasoning` 强类型化**：`effort` 从 3 档扩展到 7 档，新增 `summary`、`mode`、`context`。原有 `low` / `medium` / `high` 取值继续可用。
   只调强度时不必再补一串 `null`，改用 `builder.reasoningEffort(Effort.HIGH)` 或 `Reasoning.effort(...)`。
10. **默认 temperature 调整**：1.1.0 的默认值是 `1.0`，2.0.0 改为 `0.7`；2.0.1 起不再内置默认值，未配置时不写入请求体。显式配置过的不受影响。

## 构建与测试

需要 JDK 17：

```shell
mvn clean verify
```

产物为 `target/heiye-spring-ai-model-openrouter-starter-2.0.1.jar`，是供其它应用依赖的普通 JAR，
不是可直接 `java -jar` 启动的 Spring Boot 应用。

`verify` 会编译、执行单元测试并打包。真实网络集成测试默认以 `@Disabled` 关闭，
需要手动启用并设置 `OPENROUTER_API_KEY` 环境变量。

## 常见问题

**启动时报 `OpenRouter API key must be set`**

本模块默认启用自动配置，但没有默认密钥。设置 `spring.ai.openrouter.api-key`（或用环境变量注入）；
若当前环境暂不使用聊天模型，可设 `spring.ai.model.chat=none` 关闭。

**模型 ID 该怎么填？为什么返回 400 或 404？**

模型 ID 是 OpenRouter 的完整标识（形如 `provider/model-name`），本模块不做校验。
请以 OpenRouter 模型列表为准；模型下线或当前账号无权限时，错误由上游返回。

**为什么请求被路由到了不支持我所需参数的提供商？**

OpenRouter 会在多个提供商之间路由。需要强制只使用支持特定参数的上游时，在 provider 偏好中设置 `require_parameters = true`。

**直接用 `ChatModel` 时，工具为什么没有被执行？**

这是 Spring AI 2.0.1 的契约：`ChatModel` 只发起请求并返回模型产出的工具调用，不执行工具。
`ChatClient` 会自动注册 `ToolCallingAdvisor` 完成执行，所以用 `ChatClient` 时不需要额外处理；
直接用 `ChatModel` 时需按[工具调用](#工具调用)中的方式自行驱动 `ToolCallingManager`。

**流式调用能直接拿到结构化对象吗？**

不能。流式链路只返回文本增量，需要应用自行聚合后再反序列化。需要结构化结果时，优先使用非流式调用配合 `.entity(...)`。

**推理内容在哪里读？**

在 `OpenRouterAssistantMessage` 上：`getReasoningContent()` 取推理文本，`getReasoningDetails()` 取协议层详情。
流式场景下两者都是增量片段。

**支持 Spring Boot 3.x 吗？**

不支持。2.0.1 依赖 Spring AI 2.0.1，构建与测试基线是 Spring Boot 4.1.1 与 JDK 17。

## 参与贡献

欢迎通过 [Issues](https://github.com/shiheiyea/heiye-spring-ai-openrouter-starter/issues) 提交问题与建议。
报告问题时请附 JDK / Spring Boot / Spring AI 版本、模型 ID、脱敏后的配置与错误信息；不要附带 API Key。

## 许可证

本项目采用 [Apache License 2.0](LICENSE)。部分实现参考并改编自 Spring AI 2.0.1，
来源与修改说明见 [NOTICE](NOTICE)。

## 参考资料

- [Spring AI 聊天模型文档](https://docs.springframework.org.cn/spring-ai/reference/api/chatmodel.html)
- [OpenRouter 聊天补全 API](https://openrouter.ai/docs/api-reference/overview)
- [OpenRouter 结构化输出](https://openrouter.ai/docs/guides/features/structured-outputs)
- [OpenRouter Provider 路由](https://openrouter.ai/docs/guides/routing/provider-selection)
