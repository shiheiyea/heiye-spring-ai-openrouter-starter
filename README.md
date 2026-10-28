# Heiye Spring AI OpenRouter Starter

通过 Spring AI 的 `ChatModel` 接口访问 OpenRouter，在 Spring Boot 应用中使用统一的消息、聊天选项和工具调用机制。

基于 **Java 17 · Spring Boot 3.5.7 · Spring AI 1.1.0**，产物为普通依赖 JAR。

> 当前是以流式聊天为主的初步实现：请使用 `stream(...)`。同步 `call(Prompt)` 尚未实现，不能直接照搬 Spring AI 的同步调用示例。版本号 `1.1.0` 是当前 POM 中的项目版本，不表示已经发布到 Maven Central。

[快速开始](#快速开始) · [配置说明](#配置说明) · [使用示例](#使用示例) · [能力边界与未实现清单](#能力边界与未实现清单) · [本地构建](#本地构建) · [常见问题](#常见问题)

## 项目定位

本项目将 OpenRouter 的 Chat Completions 流式接口适配为 Spring AI 的聊天模型，提供：

- Spring Boot 自动配置，以及通用连接参数和聊天专用参数。
- `Flux<ChatResponse>` 流式响应、文本增量和推理文本读取。
- Spring AI `ToolCallback` 工具定义、流式工具调用片段合并、工具执行和后续模型请求。
- 默认聊天选项与单次请求选项合并。
- 基础响应元数据：模型、响应 ID、结束原因和 Token 用量（取决于服务端响应）。
- Jackson DTO 的 AOT runtime hints 注册；Native Image 尚未验证。

模型 ID 可以直接传字符串，无需等待项目新增枚举。模型是否可用，以及是否支持工具、推理、采样参数，由 OpenRouter 和实际提供商决定。本项目不是 OpenRouter 全量 API SDK，也不是 Spring AI 官方模块。

## 快速开始

### 1. 添加依赖

当前坐标：

```xml
<dependency>
    <groupId>io.github.shiheiyea</groupId>
    <artifactId>heiye-spring-ai-model-openrouter-starter</artifactId>
    <version>1.1.0</version>
</dependency>
```

当前尚未发布到 Maven Central。发布后可直接使用上述坐标，无需添加额外仓库或下载认证；发布前请先克隆源码并执行 `mvn clean install`，再在本机项目引用。

建议消费项目使用 Spring Boot 3.5.7，并导入 Spring AI BOM 1.1.0，避免不同 Spring AI 模块版本混用：

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>1.1.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

若要运行后面的 HTTP/SSE 示例，还需添加：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
```

### 2. 配置密钥和模型

在消费项目的 `application.yml` 中配置：

```yaml
spring:
  ai:
    model:
      chat: openrouter
    openrouter:
      api-key: ${OPENROUTER_API_KEY}
      base-url: https://openrouter.ai
      chat:
        completions-path: /api/v1/chat/completions
        options:
          model: ${OPENROUTER_MODEL}
          temperature: 0.7
          max-tokens: 2048
```

设置环境变量。将 `OPENROUTER_MODEL` 替换为你在 OpenRouter 上可以使用的完整模型 ID（通常为 `提供商/模型名`）。例如 PowerShell：

```powershell
$env:OPENROUTER_API_KEY = '你的 OpenRouter API Key'
$env:OPENROUTER_MODEL = '你的完整模型 ID'
```

不要把密钥提交到仓库。这里只使用环境变量占位符，项目不会自动加载 `.env` 文件。

### 3. 创建流式接口

将以下 Controller 放到消费项目启动类的组件扫描范围内：

```java
package com.example.demo;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import io.github.shiheiyea.spring.ai.openrouter.OpenRouterChatModel;

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
}
```

启动消费项目后访问：

```shell
curl -N --get --data-urlencode "message=用一句话介绍 Spring AI" http://localhost:8080/ai/chat
```

这是返回文本增量的 SSE 示例。若需要区分推理、正文、工具调用和用量，请使用下文的 `stream(Prompt)` 并在应用层设计事件格式。

## 配置说明

### 连接和自动配置

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `spring.ai.model.chat` | 未配置时也启用本模块 | 显式设为 `openrouter` 启用；设为 `none` 或其他值时不启用本模块 |
| `spring.ai.openrouter.api-key` | 无 | API Key，启用自动配置时必须提供 |
| `spring.ai.openrouter.base-url` | `https://openrouter.ai` | 通用服务地址 |
| `spring.ai.openrouter.chat.api-key` | 无 | 非空时覆盖通用 API Key |
| `spring.ai.openrouter.chat.base-url` | 无 | 非空时覆盖通用服务地址 |
| `spring.ai.openrouter.chat.completions-path` | `/api/v1/chat/completions` | 请求路径 |
| `spring.ai.openrouter.chat.enabled` | `true` | **当前不生效**：存在属性，但自动配置未使用它作为条件 |

默认 URL 和路径组合为 `https://openrouter.ai/api/v1/chat/completions`。使用默认路径时，base URL 应保持为 `https://openrouter.ai`，避免重复添加 `/api/v1`。

自动配置在缺少 `OpenRouterChatModel` Bean 时创建模型。手动声明同类型 Bean 可覆盖它。`OpenRouterApi` 是自动配置内部创建的对象，并未单独注册成 Bean。同时接入其他模型 starter 时，建议显式配置 `spring.ai.model.chat`，并根据实际 Bean 使用限定注入。

### 聊天选项

以下均位于 `spring.ai.openrouter.chat.options` 下：

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `model` | `google/gemini-3-flash-preview` | 代码中的默认值；建议显式选择可用模型 |
| `temperature` | `1.0` | 采样温度；手动使用 `OpenRouterChatModel.builder()` 时默认是 `0.7` |
| `max-tokens` | `null` | 最大生成 Token 数 |
| `top-p` / `top-k` | `null` | 采样参数 |
| `frequency-penalty` / `presence-penalty` | `null` | 重复相关惩罚参数 |
| `stop` | `null` | 停止序列列表 |
| `stream` | `null` | 通常保持未配置；设置后会覆盖 `stream(...)` 创建请求时的流式标记 |
| `effort` | `null` | 推理强度：`low`、`medium`、`high`，也接受大写 |
| `think-max-tokens` | `null` | 推理 Token 预算，序列化到 `reasoning.max_tokens` |
| `exclude` | `null` | 是否排除返回的推理内容，序列化到 `reasoning.exclude` |
| `enable` | `null` | 推理开关，序列化到 `reasoning.enabled` |

未设置的常规选项保持 `null`，不会自动发送 `0` 或空停止列表。参数支持范围和组合约束取决于所选模型；推理强度与 Token 预算建议按提供商要求择一配置。

例如，在 `options` 下追加以下配置以请求推理：

```yaml
effort: high
enable: true
exclude: false
```

上述是 JavaBean 配置属性名称。实际发送的 JSON 使用嵌套结构：

```json
{
  "reasoning": {
    "effort": "high",
    "enabled": true,
    "exclude": false
  }
}
```

运行时非 `null` 选项优先于模型默认选项。`reasoning` **整体替换**：单次请求提供任何推理配置后，不会逐字段继承默认 `reasoning` 中的其余设置。工具名称、回调和上下文使用 Spring AI 的工具选项合并规则。

## 使用示例

以下片段中的 `chatModel` 均指注入的 `OpenRouterChatModel`。

### 单次请求选项与多轮消息

```java
import java.util.List;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import io.github.shiheiyea.spring.ai.openrouter.OpenRouterChatOptions;

var options = OpenRouterChatOptions.builder()
        .temperature(0.3)
        .maxTokens(512)
        .build();

var prompt = new Prompt(List.of(
        new SystemMessage("你是一位 Java 开发助手，请使用中文。"),
        new UserMessage("什么是依赖注入？"),
        new AssistantMessage("依赖注入由容器提供对象需要的依赖。"),
        new UserMessage("给出一个构造器注入的例子。")
), options);

Flux<ChatResponse> responses = chatModel.stream(prompt);
```

本模块不自动保存会话。下一轮需要由应用传入消息历史，或另行配置 Spring AI 的记忆机制。历史消息中的推理信息目前不会完整回传，见能力边界。

### 读取正文、推理和用量

```java
import org.springframework.ai.chat.prompt.Prompt;
import io.github.shiheiyea.spring.ai.openrouter.OpenRouterAssistantMessage;
import io.github.shiheiyea.spring.ai.openrouter.OpenRouterChatOptions;

var options = OpenRouterChatOptions.builder()
        .effort("high")
        .enable(true)
        .exclude(false)
        .build();

chatModel.stream(new Prompt("解释一个适合使用动态规划的问题。", options))
        .doOnNext(response -> {
            for (var generation : response.getResults()) {
                var output = generation.getOutput();
                if (output.getText() != null) {
                    System.out.print(output.getText());
                }
                if (output instanceof OpenRouterAssistantMessage message
                        && message.getReasoningContent() != null) {
                    System.out.print("[推理] " + message.getReasoningContent());
                }
            }
            // 用量可能仅在末尾 chunk 中出现，中间 chunk 不代表最终用量。
            System.out.println(response.getMetadata().getUsage());
        })
        .blockLast();
```

`blockLast()` 仅用于命令行等允许阻塞的场景；WebFlux 接口直接返回 Flux。此示例会产生真实请求和相应费用。流中的正文和推理是增量，应用需要自行聚合；读取推理开关应先判断 `options.getReasoning()` 是否为空，再调用 `.enabled()`。

### 注册工具并调用

```java
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.annotation.Tool;
import io.github.shiheiyea.spring.ai.openrouter.OpenRouterChatOptions;

public class DemoTools {
    @Tool(description = "返回应用的名称")
    public String applicationName() {
        return "OpenRouter Demo";
    }
}

// 在业务方法中执行：
var options = OpenRouterChatOptions.builder()
        .toolCallbacks(ToolCallbacks.from(new DemoTools()))
        .internalToolExecutionEnabled(true)
        .build();

var responses = chatModel.stream(new Prompt("请调用工具查询应用名称。", options));
// 将 responses 返回给 WebFlux，或在命令行订阅/阻塞消费；创建 Flux 本身不会发送请求。
```

当前工具流片段合并仅适合顺序工具调用，不支持多个工具调用参数交错返回。模型须支持工具调用。内部执行开启时，模块通过 `ToolCallingManager` 执行回调，再携带工具结果继续流式请求。工具可能被调用多次；应用应自行控制副作用、权限和执行次数。

若使用 `.internalToolExecutionEnabled(false)`，应用需读取响应中的 `getToolCalls()`、执行工具，并在后续 Prompt 中传入相应 `ToolResponseMessage`。仅传 `.tools(...)` 是声明远端工具协议，不会自动注册本地可执行回调。

### 手动创建模型与自定义请求头

在不使用自动配置的场景，或需要自定义 HTTP 请求头时：

```java
import org.springframework.util.LinkedMultiValueMap;
import io.github.shiheiyea.spring.ai.openrouter.OpenRouterChatModel;
import io.github.shiheiyea.spring.ai.openrouter.OpenRouterChatOptions;
import io.github.shiheiyea.spring.ai.openrouter.api.OpenRouterApi;

var headers = new LinkedMultiValueMap<String, String>();
headers.add("HTTP-Referer", "https://your-app.example");
headers.add("X-Title", "Your App");

var api = OpenRouterApi.builder()
        .apiKey(System.getenv("OPENROUTER_API_KEY"))
        .headers(headers)
        .build();

var chatModel = OpenRouterChatModel.builder()
        .openRouterApi(api)
        .defaultOptions(OpenRouterChatOptions.builder()
                .model(System.getenv("OPENROUTER_MODEL"))
                .temperature(0.7)
                .build())
        .build();
```

自动配置属性目前没有 `headers` 映射。若在 Spring Boot 中使用此方式，可把模型声明为 `@Bean`；需要按名称解析工具时，应将应用的 `ToolCallingManager` 传给模型 builder。

## 能力边界与未实现清单

本节以 [Spring AI **v1.1.0** 的 DeepSeek 模块源码](https://github.com/spring-projects/spring-ai/tree/v1.1.0/models/spring-ai-deepseek) 为检查基线，只列出当前实现缺口，不做模型效果或产品优劣比较。下列“未实现”是本仓库的适配状态，不等于 OpenRouter 服务端不支持。

### 对照基线确认的缺口

| 未实现或未完成项 | 当前状态与使用影响 |
| --- | --- |
| 同步聊天调用 | `OpenRouterChatModel.call(Prompt)` 直接返回 `null`。`call(String)`、`call(Message...)` 和依赖同步调用的 `ChatClient.call()` 也不可用；低层 API 仅有流式发送方法 |
| 默认选项查询 | `getDefaultOptions()` 委托接口默认实现，没有像基线一样返回已配置选项的副本；调用者不能靠它获取实际模型默认配置 |
| JSON 输出格式选项 | `OpenRouterChatOptions` 未暴露 `responseFormat`，无法通过常规聊天选项请求 `response_format`；低层请求 DTO 中存在该字段不等于模型层已经支持 |
| Logprobs 请求选项 | 缺少 `logprobs`、`topLogprobs` 聊天选项；流式 choice 也没有相应概率字段。低层非流式响应 DTO 的声明不构成完整调用能力 |
| Assistant prefix 续写 | 消息 builder 虽接受 `prefix`，但没有保存或发送它；不能使用基线中的 assistant prefix 续写能力 |
| Micrometer 观测链路 | 接收了 `ObservationRegistry` 和 convention，但实际流式方法未创建、传播或结束模型 observation；仅配置监控 Bean 不会获得完整模型观测 |
| 同步重试执行 | 保存了 `RetryTemplate`，但请求方法没有使用它执行调用。不要假定 `spring.ai.retry.*` 会为当前请求带来自动重试；基线的同步重试路径尚未实现 |

### 本模块额外需要了解的范围限制

- **多模态未接通**：虽然低层 DTO 定义了文本和图片结构，`createRequest()` 实际只读取消息文本，没有将 Spring AI `Media` 转为请求内容；没有完整的图片、音频、视频输入/输出支持。
- **推理历史不完整**：响应仅提取 `delta.reasoning` 为 `reasoningContent`，没有向上暴露完整 `reasoning_details`；历史 assistant 消息中的推理字段也没有回传。需要保留推理签名或详情的模型流程不应视为已支持。
- **扩展参数不完整**：`seed`、`transforms`、`models` 等字段即使存在于低层请求 DTO，也未作为 `OpenRouterChatOptions` 的常规配置开放；provider 路由、插件等不能仅凭服务端文档认定本模块已适配。
- **用量不保证完整**：当前只映射 prompt/completion/total Token 计数，没有完整的成本、缓存和推理用量明细。当前对 choices 和 delta 缺少空值防护；服务端若返回缺失这些字段的流事件，可能触发空指针异常并终止 Flux。
- **其他模型接口未实现**：不提供 Embedding、图像生成、语音、模型列表查询等完整适配；记忆、向量库、RAG 和业务 Advisor 需另外集成。
- **运行保障待验证**：自动重试、断流恢复、Native Image 和完整的 ChatClient/Advisor 组合尚未完成系统验证。

## 本地构建

使用 JDK 17，执行：

```shell
mvn clean verify
mvn clean install
```

也可以使用仓库中的 Maven Wrapper：Windows 执行 `mvnw.cmd clean verify`，Linux/macOS 执行 `./mvnw clean verify`。

默认产物是 `target/heiye-spring-ai-model-openrouter-starter-1.1.0.jar`，供其他应用依赖，不是可直接 `java -jar` 启动的 Spring Boot 应用。`verify` 会执行自动配置启动冒烟测试并验证编译、打包；不包含真实 OpenRouter 请求。

## 常见问题

**为什么启动时提示 `OpenRouter API key must be set`？**

模块默认自动启用。设置 `OPENROUTER_API_KEY`，或在暂时不使用时配置 `spring.ai.model.chat=none`。确认聊天专用配置没有覆盖你希望使用的连接配置。

**为什么 `call()` 返回空或抛异常？**

同步路径尚未实现，请改用 `stream()`。在命令行确实需要等待完成时，可消费并聚合 Flux，但这不等于原生同步 API 已经实现。

**为什么服务端返回 401、404 或参数错误？**

检查 API Key、实际拼接的 endpoint、完整模型 ID、模型访问权限和参数兼容性。不要把默认模型枚举视为模型长期可用的保证。

**为什么没看到推理内容？**

模型需要支持并返回推理文本，还需避免配置 `exclude=true`。`stream(String)` 只输出正文；需要通过 `stream(Prompt)` 读取 `OpenRouterAssistantMessage`。

**为什么没有自动重试或模型指标？**

当前只保留了重试和观测相关依赖、配置入口，实际请求链路尚未接入，详见未实现清单。

**为什么 Maven 找不到依赖？**

先确认版本已发布到 Maven Central，并检查 Maven 镜像同步情况。尚未发布时可先在本地执行 `mvn clean install`。

## 参与贡献

欢迎通过 [Issues](https://github.com/shiheiyea/heiye-spring-ai-openrouter-starter/issues) 提交问题和建议，或提交 Pull Request。报告调用问题时请附 JDK/Spring Boot/Spring AI 版本、模型 ID、脱敏后的配置与错误信息；不要附带 API Key。

优先补齐同步调用、观测、重试和测试，再扩展协议能力。本项目采用 [Apache License 2.0](LICENSE)。部分实现参考并改编自 Spring AI，相关来源和修改说明见 [NOTICE](NOTICE)。

## 参考资料

- [Spring AI v1.1.0 DeepSeek 模块](https://github.com/spring-projects/spring-ai/tree/v1.1.0/models/spring-ai-deepseek)：能力边界的源码核对基线。
