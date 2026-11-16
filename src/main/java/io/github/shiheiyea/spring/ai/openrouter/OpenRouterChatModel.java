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
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.support.UsageCalculator;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MimeType;

import io.github.shiheiyea.spring.ai.openrouter.api.OpenRouterApi;
import io.github.shiheiyea.spring.ai.openrouter.api.OpenRouterApi.ChatCompletion;
import io.github.shiheiyea.spring.ai.openrouter.api.OpenRouterApi.ChatCompletionChunk;
import io.github.shiheiyea.spring.ai.openrouter.api.OpenRouterApi.ChatCompletionFinishReason;
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

    /**
     * 兜底观测约定，没有显式设置时使用。
     */
    public static final ChatModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultChatModelObservationConvention();

    private final OpenRouterChatOptions options;

    /**
     * 重试模板。网络抖动与上游 5xx 由它负责重放，默认取框架提供的共享实例。
     */
    public final RetryTemplate retryTemplate;

    private final OpenRouterApi openRouterApi;

    private final ObservationRegistry observationRegistry;

    private final ToolCallingManager toolCallingManager;

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
                        logger.warn("No chat completion returned for prompt: " + prompt);
                        return new ChatResponse(List.of());
                    }

                    List<ChatCompletion.Choice> choices = chatCompletion.choices();
                    if (choices == null) {
                        logger.warn("No choices returned for prompt: " + prompt);
                        return new ChatResponse(List.of());
                    }

                    List<Generation> generations = choices.stream().map(choice -> {
                        Map<String, Object> metadata = Map.of("id", chatCompletion.id() != null ? chatCompletion.id() : "",
                                "role", choice.message() != null && choice.message().role() != null
                                        ? choice.message().role().name() : "",
                                "index", choice.index() != null ? choice.index() : 0, "finishReason",
                                choice.finishReason() != null ? choice.finishReason().name() : "");
                        return buildGeneration(choice, metadata);
                    }).toList();

                    Usage currentUsage = chatCompletion.usage() != null ? getDefaultUsage(chatCompletion.usage())
                            : new EmptyUsage();
                    Usage cumulativeUsage = UsageCalculator.getCumulativeUsage(currentUsage, previousChatResponse);

                    return new ChatResponse(generations, from(chatCompletion, cumulativeUsage));
                });

        observationContext.setResponse(response);
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

            Flux<ChatResponse> flux = this.openRouterApi.chatCompletionStream(request)
                    .map(chunk -> {
                        List<Generation> generations = chunk.choices().stream()
                                .map(this::buildGeneration)
                                .toList();
                        Usage currentUsage = chunk.usage() != null ? getDefaultUsage(chunk.usage())
                                : new EmptyUsage();
                        Usage cumulativeUsage = UsageCalculator.getCumulativeUsage(currentUsage,
                                previousChatResponse);
                        return new ChatResponse(generations, this.from(chunk, cumulativeUsage));
                    })
                    .onErrorMap(e -> e)
                    .doOnError(observation::error)
                    .doFinally(signal -> observation.stop())
                    .contextWrite(context -> context.put(ObservationThreadLocalAccessor.KEY, observation));

            return new MessageAggregator().aggregate(flux, observationContext::setResponse);
        });
    }

    private Generation buildGeneration(ChatCompletionChunk.ChunkChoice choice) {
        List<AssistantMessage.ToolCall> toolCalls = new ArrayList<>();
        String textContent = null;
        String reasoningContent = null;
        List<ReasoningDetail> reasoningDetails = null;

        ChatCompletionMessage delta = choice.delta();
        if (delta != null) {
            textContent = delta.content();
            reasoningContent = delta.reasoning();
            reasoningDetails = delta.reasoningDetails();
            if (delta.toolCalls() != null) {
                toolCalls = delta.toolCalls()
                        .stream()
                        .map(toolCall -> new AssistantMessage.ToolCall(toolCall.id(),
                                toolCall.type() != null ? toolCall.type() : "function",
                                toolCall.function() != null ? toolCall.function().name() : null,
                                toolCall.function() != null ? toolCall.function().arguments() : null))
                        .toList();
            }
        }

        String finishReason = null;
        if (choice.finishReason() != null) {
            finishReason = choice.finishReason().name();
        }
        else if (choice.nativeFinishReason() != null) {
            finishReason = choice.nativeFinishReason().name();
        }

        ChatGenerationMetadata metadata = ChatGenerationMetadata.builder().finishReason(finishReason).build();

        OpenRouterAssistantMessage message = OpenRouterAssistantMessage.builder()
                .content(textContent)
                .reasoningContent(reasoningContent)
                .reasoningDetails(reasoningDetails)
                .toolCalls(toolCalls)
                .build();

        return new Generation(message, metadata);
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
                        .map(toolCall -> new AssistantMessage.ToolCall(toolCall.id(),
                                toolCall.type() != null ? toolCall.type() : "function",
                                toolCall.function() != null ? toolCall.function().name() : null,
                                toolCall.function() != null ? toolCall.function().arguments() : null))
                        .toList();
            }
        }

        String finishReason = null;
        if (choice.finishReason() != null) {
            finishReason = choice.finishReason().name();
        }
        else if (choice.nativeFinishReason() != null) {
            finishReason = choice.nativeFinishReason().name();
        }

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

        for (Message message : prompt.getInstructions()) {
            if (message.getMessageType() == MessageType.USER || message.getMessageType() == MessageType.SYSTEM) {
                chatCompletionMessages.add(new ChatCompletionMessage(toRequestContent(message),
                        ChatCompletionMessage.Role.valueOf(message.getMessageType().name())));
            }
            else if (message.getMessageType() == MessageType.ASSISTANT) {
                AssistantMessage assistantMessage = (AssistantMessage) message;
                List<ToolCall> toolCalls = null;
                if (!CollectionUtils.isEmpty(assistantMessage.getToolCalls())) {
                    toolCalls = assistantMessage.getToolCalls()
                            .stream()
                            .map(toolCall -> new ToolCall(toolCall.id(), toolCall.type(),
                                    new ChatCompletionFunction(toolCall.name(), toolCall.arguments())))
                            .toList();
                }
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
            else if (message.getMessageType() == MessageType.TOOL) {
                ToolResponseMessage toolMessage = (ToolResponseMessage) message;
                toolMessage.getResponses().forEach(response -> Assert.isTrue(response.id() != null,
                        "ToolResponseMessage must have an id"));
                toolMessage.getResponses()
                        .forEach(response -> chatCompletionMessages
                                .add(new ChatCompletionMessage(response.responseData(), ChatCompletionMessage.Role.TOOL,
                                        response.name(), response.id(), null)));
            }
            else {
                throw new IllegalArgumentException("Unsupported message type: " + message.getMessageType());
            }
        }

        ChatCompletionRequest request = new ChatCompletionRequest(chatCompletionMessages, stream);

        OpenRouterChatOptions requestOptions = (OpenRouterChatOptions) prompt.getOptions();
        Assert.state(requestOptions != null, "requestOptions must not be null");

        // 运行期选项优先，缺省值来自模型持有的默认选项
        request = new ChatCompletionRequest(request.messages(), request.stream(),
                ModelOptionsUtils.mergeOption(requestOptions.getTools(), request.tools()),
                ModelOptionsUtils.mergeOption(request.responseFormat(), request.responseFormat()),
                requestOptions.getModel(), requestOptions.getMaxTokens(), requestOptions.getTemperature(),
                requestOptions.getTopP(), requestOptions.getTopK(), requestOptions.getFrequencyPenalty(),
                requestOptions.getPresencePenalty(), requestOptions.getRepetitionPenalty(), requestOptions.getSeed(),
                requestOptions.getMinP(), requestOptions.getTopA(), requestOptions.getStopSequences(),
                request.logitBias(), requestOptions.getTopLogprobs(), requestOptions.getLogprobs(),
                requestOptions.getModels(), requestOptions.getModalities(), requestOptions.getServiceTier(),
                requestOptions.getUser(), requestOptions.getParallelToolCalls(), requestOptions.getPromptCacheKey(),
                requestOptions.getSessionId(), requestOptions.getProvider(), requestOptions.getReasoning(),
                requestOptions.getToolChoice(), requestOptions.getExtraBody());

        List<ToolDefinition> toolDefinitions = this.toolCallingManager.resolveToolDefinitions(requestOptions);
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
                    logger.info("Could not process image media with data of type: "
                            + data.getClass().getSimpleName()
                            + ". Only java.net.URI, java.lang.String and byte[] are supported.");
                }
            }
            else if (mimeType.getType().equalsIgnoreCase("audio")) {
                Object data = media.getData();
                if (data instanceof byte[] bytes) {
                    String dataUrl = "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(bytes);
                    contentParts.add(new OpenRouterApi.AudioContent(
                            new OpenRouterApi.InputAudio(dataUrl, this.audioFormat(mimeType))));
                }
                else {
                    logger.info("Could not process media with data of type: " + data.getClass().getSimpleName()
                            + ". Only byte[] is supported for non-image media.");
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
        Long cacheReadInputTokens = usage.promptTokensDetails() != null
                && usage.promptTokensDetails().cachedTokens() != null
                        ? usage.promptTokensDetails().cachedTokens().longValue() : null;
        Long cacheWriteInputTokens = usage.promptTokensDetails() != null
                && usage.promptTokensDetails().cacheWriteTokens() != null
                        ? usage.promptTokensDetails().cacheWriteTokens().longValue() : null;
        return new DefaultUsage(usage.promptTokens(), usage.completionTokens(), usage.totalTokens(), usage,
                cacheReadInputTokens, cacheWriteInputTokens);
    }

    private ChatResponseMetadata from(ChatCompletionChunk result, Usage usage) {
        Assert.notNull(result, "OpenRouter ChatCompletionChunk must not be null");
        ChatResponseMetadata.Builder builder = ChatResponseMetadata.builder();
        if (result.id() != null) {
            builder.id(result.id());
        }
        if (result.model() != null) {
            builder.model(result.model());
        }
        if (result.created() != null) {
            builder.keyValue("created", result.created());
        }
        if (result.systemFingerprint() != null) {
            builder.keyValue("system-fingerprint", result.systemFingerprint());
        }
        builder.usage(usage);
        return builder.build();
    }

    private ChatResponseMetadata from(ChatCompletion result, Usage usage) {
        Assert.notNull(result, "OpenRouter ChatCompletion must not be null");
        ChatResponseMetadata.Builder builder = ChatResponseMetadata.builder();
        if (result.id() != null) {
            builder.id(result.id());
        }
        if (result.model() != null) {
            builder.model(result.model());
        }
        if (result.created() != null) {
            builder.keyValue("created", result.created());
        }
        if (result.systemFingerprint() != null) {
            builder.keyValue("system-fingerprint", result.systemFingerprint());
        }
        builder.usage(usage);
        return builder.build();
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
     */
    private Prompt buildRequestPrompt(Prompt prompt) {
        if (prompt.getOptions() == null) {
            return prompt.mutate().chatOptions(this.getOptions()).build();
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
