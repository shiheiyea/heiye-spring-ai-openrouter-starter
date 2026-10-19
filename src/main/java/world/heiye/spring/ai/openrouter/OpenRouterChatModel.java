package world.heiye.spring.ai.openrouter;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.metadata.*;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.chat.observation.DefaultChatModelObservationConvention;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.tool.*;
import org.springframework.ai.model.tool.internal.ToolCallReactiveContextHolder;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.support.UsageCalculator;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import world.heiye.spring.ai.openrouter.api.OpenRouterApi;

import java.util.Collection;
import java.util.List;

/**
 * @author: heiye
 * @date: 2025/11/11 下午10:01
 * @version: v1.0.0
 * @description: OpenRouterChatModel
 */
public class OpenRouterChatModel implements ChatModel {
    /**
     * 工具调用管理器
     */
    private static final ToolCallingManager DEFAULT_TOOL_CALLING_MANAGER = ToolCallingManager.builder().build();
    /**
     * ChatModel观测器用于定义如何记录和命名 AI 调用的监控指标与追踪数据。
     */
    private static final ChatModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultChatModelObservationConvention();
    /**
     * 存放默认参数
     */
    private final OpenRouterChatOptions defaultOptions;
    /**
     * 重试模板
     */
    public final RetryTemplate retryTemplate;
    /**
     * OpenRouter Api
     */
    private final OpenRouterApi openRouterApi;
    /**
     * 观测器
     */
    private final ObservationRegistry observationRegistry;
    /**
     * 工具调用管理器
     */
    private final ToolCallingManager toolCallingManager;
    /**
     * 工具执行资格判断
     */
    private final ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate;
    /**
     * 观测器
     */
    private ChatModelObservationConvention observationConvention;

    // 构造函数
    public OpenRouterChatModel(OpenRouterApi openRouterApi,
                               OpenRouterChatOptions defaultOptions,
                               ToolCallingManager toolCallingManager,
                               RetryTemplate retryTemplate,
                               ObservationRegistry observationRegistry) {
        this(openRouterApi,
                defaultOptions,
                toolCallingManager,
                retryTemplate,
                observationRegistry,
                new DefaultToolExecutionEligibilityPredicate());
    }

    public OpenRouterChatModel(OpenRouterApi openRouterApi,
                             OpenRouterChatOptions defaultOptions,
                             ToolCallingManager toolCallingManager,
                             RetryTemplate retryTemplate,
                             ObservationRegistry observationRegistry,
                             ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate) {
        // 创建 ChatModelObservationConvention 一个管理工具调用
        this.observationConvention = DEFAULT_OBSERVATION_CONVENTION;
        // 断言
        Assert.notNull(openRouterApi, "openRouterApi cannot be null");
        Assert.notNull(defaultOptions, "defaultOptions cannot be null");
        Assert.notNull(toolCallingManager, "toolCallingManager cannot be null");
        Assert.notNull(retryTemplate, "retryTemplate cannot be null");
        Assert.notNull(observationRegistry, "observationRegistry cannot be null");
        Assert.notNull(toolExecutionEligibilityPredicate, "toolExecutionEligibilityPredicate cannot be null");

        this.openRouterApi = openRouterApi;
        this.defaultOptions = defaultOptions;
        this.toolCallingManager = toolCallingManager;
        this.retryTemplate = retryTemplate;
        this.observationRegistry = observationRegistry;
        this.toolExecutionEligibilityPredicate = toolExecutionEligibilityPredicate;
    }

    /**
     * 在 Spring AI 中，这个方法是把 Prompt 转为对应调用 Api 的 Prompt, 并把工具参数赋好值
     * 如由 ChatOptions 接口类型转化为 DeepSeek 的 ChatOptions (DeepSeekChatOptions) 类
     *
     * @param prompt
     * @return
     */
    public Prompt buildRequestPrompt(Prompt prompt) {
        OpenRouterChatOptions runtimeOptions = null;

        // 先创建对应 Api 的 ChatOptions 类似于 DeepSeekChatOptions，这里用 OpenRouterChatOptions 代替
        // 判断 Prompt 里的 ChatOptions 是否为空
        // 不为空，判断里面的 ChatOptions 类型，如果是 ToolCallingChatOptions 类型，
        //      也就是调用工具，把里面的 ToolCallingChatOptions 工具类参数则 bean 拷贝为 OpenRouterChatOptions 工参数，
        //      否则拷贝为普通的聊天参数 (OpenRouterChatOptions)
        if (prompt.getOptions() != null) {
            // 获取到 ChatOptions
            ChatOptions options = prompt.getOptions();
            // 判断工具调用
            if (options instanceof ToolCallingChatOptions) {
                // 进行类型转换
                ToolCallingChatOptions toolCallingChatOptions = (ToolCallingChatOptions) options;
                runtimeOptions = (OpenRouterChatOptions) ModelOptionsUtils.copyToTarget(toolCallingChatOptions, ToolCallingChatOptions.class, OpenRouterChatOptions.class);
            } else {
                runtimeOptions = (OpenRouterChatOptions) ModelOptionsUtils.copyToTarget(prompt.getOptions(), ChatOptions.class, OpenRouterChatOptions.class);
            }
        }

        // 这里 bean 拷贝直接把默认值赋值 ChatOptions 里，转为 OpenRouterChatOptions 类型获得 requestOptions 参数
        OpenRouterChatOptions requestOptions = (OpenRouterChatOptions) ModelOptionsUtils.merge(runtimeOptions, this.defaultOptions, OpenRouterChatOptions.class);

        // 之后就是判断 requestOptions 是否空来塞调用工具的参数
        // 空就给你塞默认值
        // 没空就给你塞原本 ChatOptions 的工具参数
        if (runtimeOptions != null) {
            requestOptions.setInternalToolExecutionEnabled((Boolean) ModelOptionsUtils.mergeOption(runtimeOptions.getInternalToolExecutionEnabled(), this.defaultOptions.getInternalToolExecutionEnabled()));
            requestOptions.setToolNames(ToolCallingChatOptions.mergeToolNames(runtimeOptions.getToolNames(), this.defaultOptions.getToolNames()));
            requestOptions.setToolCallbacks(ToolCallingChatOptions.mergeToolCallbacks(runtimeOptions.getToolCallbacks(), this.defaultOptions.getToolCallbacks()));
            requestOptions.setToolContext(ToolCallingChatOptions.mergeToolContext(runtimeOptions.getToolContext(), this.defaultOptions.getToolContext()));
        } else {
            requestOptions.setInternalToolExecutionEnabled(this.defaultOptions.getInternalToolExecutionEnabled());
            requestOptions.setToolNames(this.defaultOptions.getToolNames());
            requestOptions.setToolCallbacks(this.defaultOptions.getToolCallbacks());
            requestOptions.setToolContext(this.defaultOptions.getToolContext());
        }

        // 验证工具回调
        ToolCallingChatOptions.validateToolCallbacks(requestOptions.getToolCallbacks());
        return new Prompt(prompt.getInstructions(), requestOptions);
    }

    /**
     * 流式处理
     *
     * @param prompt
     * @return
     */
    public Flux<ChatResponse> internalStream(Prompt prompt, ChatResponse previousChatResponse) {
        return Flux.deferContextual((contextView) -> {
            // 创建请求体
            OpenRouterApi.ChatCompletionRequest request = this.createRequest(prompt, true);

            // 发送请求获取到流
            Flux<OpenRouterApi.ChatCompletionChunk> completionChunks = this.openRouterApi.chatCompletionStream(request);

            // Spring AI 会在这里创建观测链路

            // 处理值转化为 OpenRouterChatResponse 类型
            Flux<ChatResponse> chatResponseFlux = completionChunks
                    // 顺序处理
                    .switchMap(chunk -> Mono.just(chunk).map(chunk2 -> {
                        // 获取到 OpenRouter 生成数据
                        List<Generation> generations = chunk2.choices().stream()
                                .map(this::buildGeneration)
                                .toList();

                        // 获取到该块的 token 使用数据
                        OpenRouterApi.Usage usage = chunk2.usage();
                        Usage currentUsage = usage != null ? this.getDefaultUsage(usage) : new EmptyUsage();

                        Usage cumulativeUsage = UsageCalculator.getCumulativeUsage(currentUsage, previousChatResponse);
                        // 进行反参
                        return new ChatResponse(generations, this.from(chunk2, cumulativeUsage));
                    }));
            // 获取到 OpenRouter 响应数据尝试调用工具
            Flux<ChatResponse> chatResponse = chatResponseFlux.flatMap(response -> {
                // 判断是否需要调用工具
                return this.toolExecutionEligibilityPredicate.isToolExecutionRequired(prompt.getOptions(), response) ? Flux.deferContextual(ctx -> {
                    ToolExecutionResult toolExecutionResult;

                    try {
                        // 设置工具调用上下文
                        ToolCallReactiveContextHolder.setContext(ctx);
                        // 执行工具调用
                        toolExecutionResult = this.toolCallingManager.executeToolCalls(prompt, response);
                    } finally {
                        // 清空工具调用上下文
                        ToolCallReactiveContextHolder.clearContext();
                    }

                    // 判断是否返回直接结果
                    return toolExecutionResult.returnDirect() ?
                            // 返回直接结果
                            Flux.just(ChatResponse.builder()
                                    .from(response)
                                    // 在这里添加上工具调用结果
                                    .generations(ToolExecutionResult.buildGenerations(toolExecutionResult))
                                    .build()) :
                            // 递归调用
                            this.internalStream(
                                    new Prompt(toolExecutionResult.conversationHistory(), prompt.getOptions()),
                                    response);
                    // 分离线程调用工具
                }).subscribeOn(Schedulers.boundedElastic()) :
                        // 不需要调用工具直接返回结果
                        Flux.just(response);
            });

            // Spring AI 在最后会进行一次聚合，对 token 用量、文本元数据等聚合
            // 在这里 OpenRouter 会在最后得 chunk 中会返回最后的用量数据
            // 文本元数据什么的都给一个上下文对象拿走了，还没输出一点都没处理
            // 文本元数据，我会在 advisor 聚合写入数据库
            return chatResponse;

        });
    }

    /**
     * 构建 OpenRouter 生成
     *
     * @param choice
     * @return
     */
    public Generation buildGeneration(OpenRouterApi.ChatCompletionChunk.Choice choice) {
        // 获取工具调用 如果工具调用为空则创建一个空数组
        List<AssistantMessage.ToolCall> toolCalls = choice.delta().toolCalls() == null ?
                List.of() :
                // 如果不为空，那么对工具调用数组转化为 AssistantMessage.ToolCall 数组
                choice.delta()
                        .toolCalls()
                        .stream()
                        .map((toolCall) ->
                                new AssistantMessage
                                        .ToolCall(toolCall.id(), "function", toolCall.function().name(), toolCall.function().arguments()))
                        .toList();

        // 创建生成元数据
        String finishReason = choice.finishReason() != null ?
                choice.finishReason().name() :
                // 当 finishReason 不存在时，使用 nativeFinishReason，如果没有就是没有了
                choice.nativeFinishReason() != null ? choice.nativeFinishReason().name()
                        : null;

        // 创建 Builder
        ChatGenerationMetadata.Builder generationMetadataBuilder = ChatGenerationMetadata.builder().finishReason(finishReason);

        // 获取推理内容
        String reasoningContent = choice.delta().reasoning();

        // 获取 choice 的文本内容
        String content = choice.delta().content();

        OpenRouterAssistantMessage.Builder builder = new OpenRouterAssistantMessage.Builder();
        // 构建 AI 助手消息
        OpenRouterAssistantMessage openRouterAssistantMessage = builder.content(content)
                .reasoningContent(reasoningContent)
                .toolCalls(toolCalls)
                .build();
        // 反参
        return new Generation(openRouterAssistantMessage, generationMetadataBuilder.build());
    }

    /**
     * 创建  OpenRouter 请求体
     *
     * @param prompt
     * @param stream
     * @return
     */
    OpenRouterApi.ChatCompletionRequest createRequest(Prompt prompt, Boolean stream) {
        // 获取聊天消息
        List<Message> messages = prompt.getInstructions();
        List<OpenRouterApi.ChatCompletionMessage> chatCompletionMessages = messages.stream().map((message) -> {
            // 在 spring ai 中 他会判断 消息类型，如果不是用户消息和系统消息
            if (message.getMessageType() != MessageType.USER && message.getMessageType() != MessageType.SYSTEM) {
                // 判断是否是助手消息
                if (message.getMessageType() == MessageType.ASSISTANT) {
                    // 如果是助手消息: 转化为 助手消息 (AssistantMessage)
                    AssistantMessage assistantMessage = (AssistantMessage) message;
                    //创建工具调用变量列表
                    List<OpenRouterApi.ChatCompletionMessage.ToolCall> toolCalls = null;
                    // 如果这个助手消息调用了工具，获取该列表
                    if (!CollectionUtils.isEmpty(assistantMessage.getToolCalls())) {
                        // 将这个工具调用变量添加到工具调用列表中
                        toolCalls = assistantMessage.getToolCalls().stream().map((toolCall) -> {
                                    // 创建工具调用的函数
                                    OpenRouterApi.ChatCompletionMessage.ChatCompletionFunction function = new OpenRouterApi.ChatCompletionMessage.ChatCompletionFunction(toolCall.name(), toolCall.arguments());
                                    // 构建工具调用参数
                                    return new OpenRouterApi.ChatCompletionMessage.ToolCall(toolCall.id(), toolCall.type(), function);
                                })
                                .toList();
                    }
                    // 如果在后期需要支持图片等媒体调用的话
                    // 在这里添加判断，参考：spring ai 的 openAi 的代码

                    // 如果这个助手消息没调用工具，那么就返回 AssistantMessage
                    return List.of(new OpenRouterApi.ChatCompletionMessage(
                            // 这里需要创建文本内容
                            assistantMessage.getText(),
                            OpenRouterApi.ChatCompletionMessage.Role.ASSISTANT,
                            null,
                            null,
                            toolCalls,
                            null,
                            null
                    ));
                }
                // 如果是工具消息: 转化为工具消息 (ToolResponseMessage)
                else if (message.getMessageType() == MessageType.TOOL) {
                    // 这里使用 spring ai 通用的工具消息
                    ToolResponseMessage toolMessage = (ToolResponseMessage) message;
                    // spring ai 在这里进行了遍历 toolMessage 进行断言判断，判断你这个 Response 是否为空
                    // 我们这里就不弄了，直接遍历构建传输的工具消息
                    return toolMessage.getResponses().stream().map((tr) ->
                            new OpenRouterApi.ChatCompletionMessage(
                                    // 直接填写字符串就好
                                    tr.responseData(),
                                    // 填写工具消息
                                    OpenRouterApi.ChatCompletionMessage.Role.TOOL,
                                    // 填写工具名称
                                    tr.name(),
                                    // 填写工具调用 id
                                    tr.id(),
                                    null)
                    ).toList();
                }
            }
            return List.of(new OpenRouterApi.ChatCompletionMessage(
                    message.getText(),
                    OpenRouterApi.ChatCompletionMessage.Role.valueOf(message.getMessageType().name()))
            );
        }).flatMap(Collection::stream).toList();

        // 创建请求
        OpenRouterApi.ChatCompletionRequest request = new OpenRouterApi.ChatCompletionRequest(chatCompletionMessages, stream);
        // 获取 OpenRouter 聊天选项
        OpenRouterChatOptions requestOptions = (OpenRouterChatOptions) prompt.getOptions();
        // 进行参数转移
        request = OpenRouterRequestAdapter.adaptOptionsToRequest(requestOptions, request);

        // 这里会用请求配置构建工具调用方法信息数组
        // 这个 resolveToolDefinitions 会找寻 options 中传入的方法是否存在
        // 和检验传入的 toolName 是否也存在与系统中
        List<ToolDefinition> toolDefinitions = this.toolCallingManager.resolveToolDefinitions(requestOptions);
        if (!CollectionUtils.isEmpty(toolDefinitions)) {
            // 将构建工具方法信息和请求信息进行合并
            request = (OpenRouterApi.ChatCompletionRequest) ModelOptionsUtils.merge(
                    OpenRouterChatOptions.builder()
                            .tools(this.getFunctionTools(toolDefinitions))
                            .build(),
                    request,
                    OpenRouterApi.ChatCompletionRequest.class);
        }
        return request;
    }


    /**
     * 将 ToolDefinition 类型转化为 OpenRouterApi 的 FunctionTool，用于发送请求给 ai
     *
     * @param toolDefinitions
     * @return
     */
    private List<OpenRouterApi.FunctionTool> getFunctionTools(List<ToolDefinition> toolDefinitions) {
        return toolDefinitions.stream().map((toolDefinition) -> {
            OpenRouterApi.FunctionTool.Function function = new OpenRouterApi.FunctionTool.Function(toolDefinition.description(), toolDefinition.name(), toolDefinition.inputSchema());
            return new OpenRouterApi.FunctionTool(function);
        }).toList();
    }

    /**
     * 获取传输的Usage
     *
     * @param usage
     * @return
     */
    private DefaultUsage getDefaultUsage(OpenRouterApi.Usage usage) {
        return new DefaultUsage(usage.promptTokens(), usage.completionTokens(), usage.totalTokens(), usage);
    }

    /**
     * 构建 ChatResponseMetadata
     *
     * @param result
     * @param usage
     * @return
     */
    private ChatResponseMetadata from(OpenRouterApi.ChatCompletionChunk result, Usage usage) {
        ChatResponseMetadata.Builder builder = ChatResponseMetadata.builder()
                // 构建 id
                .id(result.id() != null ? result.id() : "")
                // 构建 usage
                .usage(usage)
                // 构建 model
                .model(result.model() != null ? result.model() : "")
                // 构建 metadata
                .keyValue("created", result.created() != null ? result.created() : 0L)
                .keyValue("system-fingerprint", result.systemFingerprint() != null ? result.systemFingerprint() : "");

        return builder.build();
    }

    /**
     * 设置 ObservationConvention
     *
     * @param observationConvention
     */
    public void setObservationConvention(ChatModelObservationConvention observationConvention) {
        this.observationConvention = observationConvention;
    }

    @Override
    public String call(String message) {
        return ChatModel.super.call(message);
    }

    @Override
    public String call(Message... messages) {
        return ChatModel.super.call(messages);
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        return null;
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return ChatModel.super.getDefaultOptions();
    }

    @Override
    public Flux<String> stream(String message) {
        return ChatModel.super.stream(message);
    }

    @Override
    public Flux<String> stream(Message... messages) {
        return ChatModel.super.stream(messages);
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        // 构建请求体提示词
        Prompt requestPrompt = this.buildRequestPrompt(prompt);
        // 调用 internalStream 方法，获取流式数据
        return this.internalStream(requestPrompt, null);
    }


    // 构建 Builder 对象
    public static Builder builder() {
        return new Builder();
    }

    // 设计模式：建造者
    public static final class Builder {
        private OpenRouterApi openRouterApi;
        private OpenRouterChatOptions defaultOptions;
        private ToolCallingManager toolCallingManager;
        private ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate;
        private RetryTemplate retryTemplate;
        private ObservationRegistry observationRegistry;

        // 将必要参数进行构建
        private Builder() {
            // 构建默认参数，为后面流式调用时做兜底
            this.defaultOptions = OpenRouterChatOptions.builder()
                    .model(OpenRouterApi.DEFAULT_CHAT_MODEL)
                    .temperature(0.7)
                    .build();
            // 构建默认工具执行资格判断
            this.toolExecutionEligibilityPredicate = new DefaultToolExecutionEligibilityPredicate();
            // 构建默认重试模板
            this.retryTemplate = RetryUtils.DEFAULT_RETRY_TEMPLATE;
            // 构建默认观测注册表
            this.observationRegistry = ObservationRegistry.NOOP;
        }

        public Builder openRouterApi(OpenRouterApi openRouterApi) {
            this.openRouterApi = openRouterApi;
            return this;
        }

        public Builder defaultOptions(OpenRouterChatOptions defaultOptions) {
            this.defaultOptions = defaultOptions;
            return this;
        }

        public Builder toolCallingManager(ToolCallingManager toolCallingManager) {
            this.toolCallingManager = toolCallingManager;
            return this;
        }

        public Builder toolExecutionEligibilityPredicate(ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate) {
            this.toolExecutionEligibilityPredicate = toolExecutionEligibilityPredicate;
            return this;
        }

        public Builder retryTemplate(RetryTemplate retryTemplate) {
            this.retryTemplate = retryTemplate;
            return this;
        }

        public Builder observationRegistry(ObservationRegistry observationRegistry) {
            this.observationRegistry = observationRegistry;
            return this;
        }

        public OpenRouterChatModel build() {
            return this.toolCallingManager != null ?
                    new OpenRouterChatModel(
                            this.openRouterApi,
                            this.defaultOptions,
                            this.toolCallingManager,
                            this.retryTemplate,
                            this.observationRegistry,
                            this.toolExecutionEligibilityPredicate) : new OpenRouterChatModel(this.openRouterApi, this.defaultOptions, OpenRouterChatModel.DEFAULT_TOOL_CALLING_MANAGER, this.retryTemplate, this.observationRegistry, this.toolExecutionEligibilityPredicate);
        }
    }
}
