/*
 * Copyright 2023-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
 * Modified in 2026 by shiheiyea: adapted for OpenRouter, including package names,
 * protocol types, reasoning options and Spring Boot integration. See NOTICE.
 */

package io.github.shiheiyea.spring.ai.openrouter;

import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.chat.observation.ChatModelObservationDocumentation;
import org.springframework.ai.chat.observation.DefaultChatModelObservationConvention;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.support.UsageCalculator;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.util.JsonHelper;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MimeType;
import org.springframework.util.StringUtils;

import io.github.shiheiyea.spring.ai.openrouter.api.OpenRouterApi;
import io.github.shiheiyea.spring.ai.openrouter.api.OpenRouterApi.ChatCompletion;
import io.github.shiheiyea.spring.ai.openrouter.api.OpenRouterApi.ChatCompletionChunk;
import io.github.shiheiyea.spring.ai.openrouter.api.OpenRouterApi.ChatCompletionFunction;
import io.github.shiheiyea.spring.ai.openrouter.api.OpenRouterApi.ChatCompletionMessage;
import io.github.shiheiyea.spring.ai.openrouter.api.OpenRouterApi.ChatCompletionRequest;
import io.github.shiheiyea.spring.ai.openrouter.api.OpenRouterApi.FunctionTool;
import io.github.shiheiyea.spring.ai.openrouter.api.OpenRouterApi.ReasoningDetail;
import io.github.shiheiyea.spring.ai.openrouter.api.OpenRouterApi.ToolCall;
import io.github.shiheiyea.spring.ai.openrouter.api.common.OpenRouterConstants;

public class OpenRouterChatModel implements ChatModel {

    private static final ToolCallingManager DEFAULT_TOOL_CALLING_MANAGER = ToolCallingManager.builder().build();

    private static final Log logger = LogFactory.getLog(OpenRouterChatModel.class);

    private static final JsonHelper jsonHelper = new JsonHelper();

    /**
     * 兜底观测约定，没有显式设置时使用。
     */
    public static final ChatModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultChatModelObservationConvention();

    /**
     * 配置项
     */
    private final OpenRouterChatOptions options;

    /**
     * 重试模板。网络抖动与上游 5xx 由它负责重放，默认取框架提供的共享实例。
     */
    public final RetryTemplate retryTemplate;

    /**
     * OpenRouter API 客户端
     */
    private final OpenRouterApi openRouterApi;

    /**
     * 观测注册表
     */
    private final ObservationRegistry observationRegistry;

    /**
     * 工具调用管理器
     */
    private final ToolCallingManager toolCallingManager;

    /**
     * 观测约定
     */
    private ChatModelObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

    public OpenRouterChatModel(OpenRouterApi openRouterApi, OpenRouterChatOptions options,
            ToolCallingManager toolCallingManager, RetryTemplate retryTemplate,
            ObservationRegistry observationRegistry) {
        Assert.notNull(openRouterApi, "openRouterApi must not be null");
        Assert.notNull(options, "options must not be null");
        Assert.notNull(toolCallingManager, "toolCallingManager must not be null");
        Assert.notNull(retryTemplate, "retryTemplate must not be null");
        Assert.notNull(observationRegistry, "observationRegistry must not be null");
        this.openRouterApi = openRouterApi;
        this.options = options;
        this.toolCallingManager = toolCallingManager;
        this.retryTemplate = retryTemplate;
        this.observationRegistry = observationRegistry;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        Prompt requestPrompt = buildRequestPrompt(prompt);
        return this.internalCall(requestPrompt, null);
    }

    /**
     * 非流式调用，观测链路覆盖请求构造、重试与响应解析三段。
     */
    private ChatResponse internalCall(Prompt prompt, @Nullable ChatResponse previousChatResponse) {
        ChatCompletionRequest request = createRequest(prompt, false);

        ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
                .prompt(prompt)
                .provider(OpenRouterConstants.PROVIDER_NAME)
                .build();

        ChatResponse response = ChatModelObservationDocumentation.CHAT_MODEL_OPERATION
                .observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
                        this.observationRegistry)
                .observe(() -> {
                    ResponseEntity<ChatCompletion> completionEntity = RetryUtils.execute(this.retryTemplate,
                            () -> this.openRouterApi.chatCompletionEntity(request));

                    ChatCompletion chatCompletion = completionEntity.getBody();
                    if (chatCompletion == null) {
                        if (logger.isWarnEnabled()) {
                            logger.warn("No chat completion returned for prompt: " + prompt);
                        }
                        return new ChatResponse(List.of());
                    }

                    List<ChatCompletion.Choice> choices = chatCompletion.choices();
                    if (choices == null) {
                        if (logger.isWarnEnabled()) {
                            logger.warn("No choices returned for prompt: " + prompt);
                        }
                        return new ChatResponse(List.of());
                    }

                    List<Generation> generations = choices.stream().map(choice -> {
                        Map<String, Object> metadata = Map.of(
                                "id", chatCompletion.id() != null ? chatCompletion.id() : "",
                                "role", choice.message() != null && choice.message().role() != null ? choice.message().role().name() : "",
                                "index", choice.index() != null ? choice.index() : 0,
                                "finishReason", choice.finishReason() != null ? choice.finishReason().name() : "");
                        return buildGeneration(choice, metadata);
                    }).toList();

                    Usage currentUsage = chatCompletion.usage() != null ? getDefaultUsage(chatCompletion.usage())
                            : new EmptyUsage();
                    Usage cumulativeUsage = UsageCalculator.getCumulativeUsage(currentUsage, previousChatResponse);
                    ChatResponse chatResponse = new ChatResponse(generations, from(chatCompletion, cumulativeUsage));

                    // 必须写在 observe 的 lambda 内：span 停止时才读取 response 提取 usage 与 model 键值
                    observationContext.setResponse(chatResponse);

                    return chatResponse;
                });

        return response;
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        Prompt requestPrompt = buildRequestPrompt(prompt);
        return this.internalStream(requestPrompt, null);
    }

    /**
     * 流式调用。观测对象随数据流生命周期启停，聚合器在流结束后把完整响应写回观测上下文。
     */
    private Flux<ChatResponse> internalStream(Prompt prompt, @Nullable ChatResponse previousChatResponse) {
        return Flux.deferContextual(contextView -> {
            ChatCompletionRequest request = createRequest(prompt, true);

            // 先建流再建观测：建流是惰性的，不会发请求，但它的前置校验会同步抛异常；
            // 排在观测之前，可避免校验失败时留下已启动却不会被 stop 的观测。
            Flux<ChatCompletionChunk> completionChunks = this.openRouterApi.chatCompletionStream(request);

            // 流式响应只有首个分块带 role，同一 id 的后续分块共用它，因此按 id 记忆
            ConcurrentHashMap<String, String> roleMap = new ConcurrentHashMap<>();

            ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
                    .prompt(prompt)
                    .provider(OpenRouterConstants.PROVIDER_NAME)
                    .streaming(true)
                    .build();

            Observation observation = ChatModelObservationDocumentation.CHAT_MODEL_OPERATION.observation(
                    this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
                    this.observationRegistry);

            Observation parentObservation = contextView.getOrDefault(ObservationThreadLocalAccessor.KEY, null);
            observation.parentObservation(parentObservation);

            // 观测必须在订阅线程内 start，否则上下文传播链会断在这里
            try (Observation.Scope ignored = parentObservation != null ? parentObservation.openScope()
                    : Observation.Scope.NOOP) {
                observation.start();
            }

            // 分块先转成完整响应形状，再复用同一套 generation 构造，避免流式与非流式的元数据分叉
            Flux<ChatResponse> chatResponse = completionChunks.map(this::chunkToChatCompletion).map(chatCompletion -> {
                try {
                    String id = chatCompletion.id();

                    List<Generation> generations = chatCompletion.choices().stream().map(choice -> {
                        if (choice.message() != null && choice.message().role() != null) {
                            roleMap.putIfAbsent(id, choice.message().role().name());
                        }

                        Map<String, Object> metadata = Map.of(
                                "id", id != null ? id : "",
                                "role", roleMap.getOrDefault(id, ""),
                                "finishReason", choice.finishReason() != null ? choice.finishReason().name() : "");
                        return buildGeneration(choice, metadata);
                    }).toList();

                    Usage currentUsage = chatCompletion.usage() != null ? getDefaultUsage(chatCompletion.usage())
                            : new EmptyUsage();
                    Usage cumulativeUsage = UsageCalculator.getCumulativeUsage(currentUsage, previousChatResponse);

                    return new ChatResponse(generations, this.from(chatCompletion, cumulativeUsage));
                }
                catch (Exception ex) {
                    logger.error("Error processing chat completion", ex);
                    return new ChatResponse(List.of());
                }
            });

            Flux<ChatResponse> flux = chatResponse
                    .doOnError(observation::error)
                    .doFinally(signal -> observation.stop())
                    .contextWrite(context -> context.put(ObservationThreadLocalAccessor.KEY, observation));

            return new MessageAggregator().aggregate(flux, observationContext::setResponse);
        });
    }

    /**
     * 把流式分块转成与非流式同形的响应对象，usage 留给调用处单独处理。
     */
    private ChatCompletion chunkToChatCompletion(ChatCompletionChunk chunk) {
        List<ChatCompletion.Choice> choices = chunk.choices()
                .stream()
                .map(chunkChoice -> new ChatCompletion.Choice(chunkChoice.finishReason(),
                        chunkChoice.nativeFinishReason(), chunkChoice.index(), chunkChoice.delta(),
                        chunkChoice.logprobs()))
                .toList();

        return new ChatCompletion(chunk.id(), choices, chunk.created(), chunk.model(), chunk.serviceTier(),
                chunk.systemFingerprint(), chunk.object(), chunk.usage());
    }

    private Generation buildGeneration(ChatCompletion.Choice choice, Map<String, Object> metadata) {
        List<AssistantMessage.ToolCall> toolCalls = new ArrayList<>();
        String textContent = null;
        String reasoningContent = null;
        List<ReasoningDetail> reasoningDetails = null;

        ChatCompletionMessage message = choice.message();
        if (message != null) {
            textContent = message.content();
            reasoningContent = message.reasoning();
            reasoningDetails = message.reasoningDetails();
            if (message.toolCalls() != null) {
                toolCalls = message.toolCalls()
                        .stream()
                        .map(toolCall -> new AssistantMessage.ToolCall(
                                toolCall.id(), "function",
                                toolCall.function() != null ? toolCall.function().name() : null,
                                toolCall.function() != null ? toolCall.function().arguments() : null))
                        .toList();
            }
        }

        // 聚合网关下部分 provider 只回 native_finish_reason
        String finishReason = choice.finishReason() != null ? choice.finishReason().name()
                : choice.nativeFinishReason() != null ? choice.nativeFinishReason().name() : "";

        ChatGenerationMetadata generationMetadata = ChatGenerationMetadata.builder().finishReason(finishReason).build();

        OpenRouterAssistantMessage assistantMessage = OpenRouterAssistantMessage.builder()
                .content(textContent)
                .reasoningContent(reasoningContent)
                .reasoningDetails(reasoningDetails)
                .properties(metadata)
                .toolCalls(toolCalls)
                .build();

        return new Generation(assistantMessage, generationMetadata);
    }

    /**
     * 组装请求消息。
     *
     * 用户消息带媒体时改走内容部件数组，图片与音频按 MIME 前缀分流，其余类型退回文本部件；
     * 助手消息要把推理文本与推理详情原样带回，否则多轮推理会失去上下文。
     */
    ChatCompletionRequest createRequest(Prompt prompt, boolean stream) {
        List<ChatCompletionMessage> chatCompletionMessages = new ArrayList<>();

        // 遍历消息列表，构建 ChatCompletionMessage
        for (Message message : prompt.getInstructions()) {
            // 用户消息或系统消息
            if (message.getMessageType() == MessageType.USER || message.getMessageType() == MessageType.SYSTEM) {
                chatCompletionMessages.add(new ChatCompletionMessage(toRequestContent(message),
                        ChatCompletionMessage.Role.valueOf(message.getMessageType().name())));
            }
            // AI 消息
            else if (message.getMessageType() == MessageType.ASSISTANT) {
                AssistantMessage assistantMessage = (AssistantMessage) message;
                List<ToolCall> toolCalls = null;
                if (!CollectionUtils.isEmpty(assistantMessage.getToolCalls())) {
                    toolCalls = assistantMessage.getToolCalls()
                            .stream()
                            .map(toolCall -> new ToolCall(toolCall.id(), toolCall.type(), new ChatCompletionFunction(toolCall.name(), toolCall.arguments())))
                            .toList();
                }
                // 思考内容
                String reasoningContent = null;
                List<ReasoningDetail> reasoningDetails = null;
                if (assistantMessage instanceof OpenRouterAssistantMessage openRouterAssistantMessage) {
                    reasoningContent = openRouterAssistantMessage.getReasoningContent();
                    reasoningDetails = openRouterAssistantMessage.getReasoningDetails();
                }
                chatCompletionMessages.add(new ChatCompletionMessage(assistantMessage.getText(),
                        ChatCompletionMessage.Role.ASSISTANT, null, null, toolCalls, reasoningContent,
                        reasoningDetails, null, null));
            }
            // 工具消息
            else if (message.getMessageType() == MessageType.TOOL) {
                // 工具响应消息
                ToolResponseMessage toolMessage = (ToolResponseMessage) message;
                toolMessage.getResponses().forEach(response -> Assert.isTrue(response.id() != null, "ToolResponseMessage must have an id"));

                // 添加工具响应消息
                toolMessage.getResponses().forEach(response -> chatCompletionMessages.add(new ChatCompletionMessage(response.responseData(), ChatCompletionMessage.Role.TOOL, response.name(), response.id(), null)));
            }
            else {
                throw new IllegalArgumentException("Unsupported message type: " + message.getMessageType());
            }
        }

        ChatCompletionRequest request = new ChatCompletionRequest(chatCompletionMessages, stream);

        OpenRouterChatOptions requestOptions = (OpenRouterChatOptions) prompt.getOptions();
        Assert.state(requestOptions != null, "requestOptions must not be null");

        // 响应格式在请求构建期做一次转换，非法 schema 在这里就失败
        OpenRouterApi.ResponseFormat responseFormat = this.toResponseFormat(requestOptions);

        // 运行期选项优先，缺省值来自模型持有的默认选项
        request = new ChatCompletionRequest(request.messages(), request.stream(),
                requestOptions.getTools(), responseFormat,
                requestOptions.getModel(), requestOptions.getMaxTokens(), requestOptions.getTemperature(),
                requestOptions.getTopP(), requestOptions.getTopK(), requestOptions.getFrequencyPenalty(),
                requestOptions.getPresencePenalty(), requestOptions.getRepetitionPenalty(), requestOptions.getSeed(),
                requestOptions.getMinP(), requestOptions.getTopA(), requestOptions.getStopSequences(),
                requestOptions.getLogitBias(), requestOptions.getTopLogprobs(), requestOptions.getLogprobs(),
                requestOptions.getModels(), requestOptions.getModalities(), requestOptions.getServiceTier(),
                requestOptions.getUser(), requestOptions.getParallelToolCalls(), requestOptions.getPromptCacheKey(),
                requestOptions.getSessionId(), requestOptions.getProvider(), requestOptions.getReasoning(),
                requestOptions.getToolChoice(), requestOptions.getExtraBody());

        List<ToolDefinition> toolDefinitions = this.toolCallingManager.resolveToolDefinitions(requestOptions);
        // 添加工具定义
        if (!CollectionUtils.isEmpty(toolDefinitions)) {
            request = new ChatCompletionRequest(request.messages(), request.stream(),
                    this.getFunctionTools(toolDefinitions), request.responseFormat(), request.model(),
                    request.maxTokens(), request.temperature(), request.topP(), request.topK(),
                    request.frequencyPenalty(), request.presencePenalty(), request.repetitionPenalty(),
                    request.seed(), request.minP(), request.topA(), request.stop(), request.logitBias(),
                    request.topLogprobs(), request.logprobs(), request.models(), request.modalities(),
                    request.serviceTier(), request.user(), request.parallelToolCalls(), request.promptCacheKey(),
                    request.sessionId(), request.provider(), request.reasoning(), request.toolChoice(),
                    request.extraBody());
        }

        return request;
    }

    /**
     * 把 Options 侧的响应格式转成请求体的强类型形态。
     *
     * 三种类型分别对应纯文本、无结构 JSON 和带 schema 的 JSON。带 schema 时在这里解析字符串，
     * 同时补齐两个可以省略的字段：结构名缺省取 {@code openrouter_response_schema}，严格模式缺省开启。
     * 解析失败直接抛参数异常，不做静默降级。
     */
    private OpenRouterApi.@Nullable ResponseFormat toResponseFormat(OpenRouterChatOptions options) {
        OpenRouterChatOptions.ResponseFormat format = options.getResponseFormat();
        if (format == null) {
            return null;
        }
        if (format.getType() == OpenRouterApi.ResponseFormat.Type.TEXT) {
            return OpenRouterApi.ResponseFormat.text();
        }
        if (format.getType() == OpenRouterApi.ResponseFormat.Type.JSON_OBJECT) {
            return OpenRouterApi.ResponseFormat.jsonObject();
        }

        String jsonSchema = format.getJsonSchema();
        if (!StringUtils.hasText(jsonSchema)) {
            throw new IllegalArgumentException(
                    "JSON schema must be set when response format type is JSON_SCHEMA");
        }

        Map<String, Object> schema;
        try {
            schema = jsonHelper.fromJsonToMap(jsonSchema);
        }
        catch (RuntimeException ex) {
            throw new IllegalArgumentException("Failed to parse JSON schema: " + jsonSchema, ex);
        }
        // 解析成空 Map 但原文不是空对象，说明上游给了 "null" 这类合法 JSON 却不是 schema
        if (schema.isEmpty() && !"{}".equals(jsonSchema.trim())) {
            throw new IllegalArgumentException("Failed to parse JSON schema: " + jsonSchema);
        }

        String name = format.getName() != null ? format.getName() : OpenRouterApi.JSON_SCHEMA_NAME;
        Boolean strict = format.getStrict() != null ? format.getStrict() : Boolean.TRUE;
        OpenRouterApi.ResponseFormat.JsonSchema schemaHolder = new OpenRouterApi.ResponseFormat.JsonSchema(name,
                null, new HashMap<>(schema), strict);
        return new OpenRouterApi.ResponseFormat(OpenRouterApi.ResponseFormat.Type.JSON_SCHEMA, schemaHolder);
    }

    /**
     * 把用户或系统消息转成请求 content。
     *
     * 纯文本消息直接给字符串；带媒体时组装内容部件数组，部件顺序保持原来的媒体顺序。
     */
    private Object toRequestContent(Message message) {
        if (!(message instanceof UserMessage userMessage) || CollectionUtils.isEmpty(userMessage.getMedia())) {
            return message.getText();
        }

        List<Object> contentParts = new ArrayList<>();
        if (message.getText() != null && !message.getText().isEmpty()) {
            contentParts.add(new OpenRouterApi.TextContent(message.getText()));
        }
        for (Media media : userMessage.getMedia()) {
            MimeType mimeType = media.getMimeType();
            if (mimeType == null) {
                continue;
            }
            if (mimeType.getType().equalsIgnoreCase("image")) {
                Object data = media.getData();
                if (data instanceof URI || data instanceof String) {
                    contentParts.add(new OpenRouterApi.ImageContent(new OpenRouterApi.ImageUrl(data.toString())));
                }
                else if (data instanceof byte[] bytes) {
                    String dataUrl = "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(bytes);
                    contentParts.add(new OpenRouterApi.ImageContent(new OpenRouterApi.ImageUrl(dataUrl)));
                }
                else {
                    if (logger.isInfoEnabled()) {
                        logger.info("Could not process image media with data of type: "
                                + (data != null ? data.getClass().getSimpleName() : "null")
                                + ". Only java.net.URI, java.lang.String and byte[] are supported.");
                    }
                }
            }
            else if (mimeType.getType().equalsIgnoreCase("audio")) {
                Object data = media.getData();
                if (data instanceof byte[] bytes) {
                    // 音频与图片不同：input_audio.data 只接受裸 base64，
                    // 带上 "data:audio/wav;base64," 前缀会被上游判定为解码失败。
                    contentParts.add(new OpenRouterApi.AudioContent(
                            new OpenRouterApi.InputAudio(Base64.getEncoder().encodeToString(bytes),
                                    this.audioFormat(mimeType))));
                }
                else {
                    if (logger.isInfoEnabled()) {
                        logger.info("Could not process media with data of type: "
                                + (data != null ? data.getClass().getSimpleName() : "null")
                                + ". Only byte[] is supported for non-image media.");
                    }
                }
            }
            else {
                contentParts.add(new OpenRouterApi.TextContent(media.toString()));
            }
        }

        return contentParts;
    }

    /**
     * 音频格式取 MIME 子类型，OpenRouter 只认扩展名形式的格式串。
     */
    private String audioFormat(MimeType mimeType) {
        return mimeType.getSubtype() != null ? mimeType.getSubtype().toLowerCase() : "wav";
    }

    private List<FunctionTool> getFunctionTools(List<ToolDefinition> toolDefinitions) {
        return toolDefinitions.stream()
                .map(toolDefinition -> new FunctionTool(new FunctionTool.Function(toolDefinition.name(),
                        toolDefinition.description(), toolDefinition.inputSchema())))
                .toList();
    }

    /**
     * 用量换算。缓存读写的 token 数只在上游给出明细时才有值。
     */
    private DefaultUsage getDefaultUsage(OpenRouterApi.Usage usage) {
        // 缓存读取的 token 数
        Long cacheReadInputTokens = usage.promptTokensDetails() != null
                && usage.promptTokensDetails().cachedTokens() != null
                ? usage.promptTokensDetails().cachedTokens().longValue() : null;

        // 缓存写入的 token 数
        Long cacheWriteInputTokens = usage.promptTokensDetails() != null
                && usage.promptTokensDetails().cacheWriteTokens() != null
                ? usage.promptTokensDetails().cacheWriteTokens().longValue() : null;

        // 总的 token 数
        return new DefaultUsage(usage.promptTokens(), usage.completionTokens(), usage.totalTokens(), usage,
                cacheReadInputTokens, cacheWriteInputTokens);
    }

    private ChatResponseMetadata from(ChatCompletion result, Usage usage) {
        Assert.notNull(result, "OpenRouter ChatCompletion must not be null");

        // 缺字段补默认值，不用 if 跳过，避免 id/model 落到 null
        return ChatResponseMetadata.builder()
                .id(result.id() != null ? result.id() : "")
                .usage(usage)
                .model(result.model() != null ? result.model() : "")
                .keyValue("created", result.created() != null ? result.created() : 0L)
                .keyValue("system-fingerprint", result.systemFingerprint() != null ? result.systemFingerprint() : "")
                .build();
    }

    @Override
    public OpenRouterChatOptions getOptions() {
        return this.options;
    }

    public void setObservationConvention(ChatModelObservationConvention observationConvention) {
        Assert.notNull(observationConvention, "observationConvention cannot be null");
        this.observationConvention = observationConvention;
    }

    /**
     * 调用方没带选项时补上模型默认选项，带上选项则原样放行。
     *
     * <p>这里刻意不用 {@code prompt.mutate()}：它内部的 {@code instructionsCopy()} 会把
     * {@link OpenRouterAssistantMessage} 重建成普通 {@code AssistantMessage}，历史里的
     * {@code reasoningContent} 与 {@code reasoningDetails} 会在补选项这一步被悄悄丢掉。</p>
     */
    private Prompt buildRequestPrompt(Prompt prompt) {
        if (prompt.getOptions() == null) {
            return new Prompt(prompt.getInstructions(), this.getOptions());
        }
        return prompt;
    }

    @Override
    public String toString() {
        return "OpenRouterChatModel [options=" + this.options + "]";
    }

    public static final class Builder {

        private @Nullable OpenRouterApi openRouterApi;

        private OpenRouterChatOptions options = OpenRouterChatOptions.builder().build();

        private @Nullable ToolCallingManager toolCallingManager;

        private RetryTemplate retryTemplate = RetryUtils.DEFAULT_RETRY_TEMPLATE;

        private ObservationRegistry observationRegistry = ObservationRegistry.NOOP;

        private Builder() {
        }

        public Builder openRouterApi(OpenRouterApi openRouterApi) {
            this.openRouterApi = openRouterApi;
            return this;
        }

        public Builder options(OpenRouterChatOptions options) {
            this.options = options;
            return this;
        }

        public Builder toolCallingManager(ToolCallingManager toolCallingManager) {
            this.toolCallingManager = toolCallingManager;
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
            Assert.state(this.openRouterApi != null, "OpenRouterApi must not be null");
            ToolCallingManager manager = this.toolCallingManager != null ? this.toolCallingManager
                    : DEFAULT_TOOL_CALLING_MANAGER;
            return new OpenRouterChatModel(this.openRouterApi, this.options, manager, this.retryTemplate,
                    this.observationRegistry);
        }

    }

}
