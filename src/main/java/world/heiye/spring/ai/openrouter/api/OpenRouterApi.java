package world.heiye.spring.ai.openrouter.api;

import com.fasterxml.jackson.annotation.*;
import org.springframework.ai.model.*;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * @author: heiye
 * @date: 2025/11/11 下午10:06
 * @version: v1.0.0
 * @description: openRouter api 设置
 */
public class OpenRouterApi {
    /**
     * 默认模型
     */
    public static final ChatModel DEFAULT_CHAT_MODEL;
    /**
     * webClient 结束标志
     */
    private static final Predicate<String> SSE_DONE_PREDICATE;
    /**
     * 通用地址
     */
    private final String completionsPath;
    /**
     * webClient 错误监听
     */
    private final ResponseErrorHandler responseErrorHandler;

    private final RestClient restClient;

    /**
     * 构建 webClient
     */
    private final WebClient webClient;

    /**
     * 默认的流式工具调用处理
     */
    private OpenRouterStreamFunctionCallingHelper chunkMerger = new OpenRouterStreamFunctionCallingHelper();

    /**
     * 构建 Builder
     *
     * @return
     */
    public static Builder builder() {
        return new Builder();
    }

    public OpenRouterApi(ApiKey apiKey, String baseUrl, MultiValueMap<String, String> headers, String completionsPath, ResponseErrorHandler responseErrorHandler, RestClient.Builder restClientBuilder, WebClient.Builder webClientBuilder) {
        // 断言
        Assert.hasText(completionsPath, "Completions Path must not be null");
        Assert.notNull(headers, "Headers must not be null");
        this.completionsPath = completionsPath;
        this.responseErrorHandler = responseErrorHandler;

        // 创建请求头
        Consumer<HttpHeaders> finalHeaders = (h) -> {
            if (!(apiKey instanceof NoopApiKey)) {
                h.setBearerAuth(apiKey.getValue());
            }

            h.setContentType(MediaType.APPLICATION_JSON);
            h.addAll(headers);
        };


        this.restClient = restClientBuilder.baseUrl(baseUrl)
                .defaultHeaders(finalHeaders)
                .defaultStatusHandler(responseErrorHandler)
                .build();

        this.webClient = webClientBuilder.baseUrl(baseUrl)
                .defaultHeaders(finalHeaders)
                .build();
    }

    public Flux<ChatCompletionChunk> chatCompletionStream(ChatCompletionRequest chatRequest) {
        return this.chatCompletionStream(chatRequest, new LinkedMultiValueMap());
    }

    /**
     * 调用 OpenRouter api
     *
     * @param chatRequest
     * @param additionalHttpHeader
     * @return
     */
    public Flux<ChatCompletionChunk> chatCompletionStream(ChatCompletionRequest chatRequest, MultiValueMap<String, String> additionalHttpHeader) {
        // 创建原子布尔值，用于记录是否需要工具调用
        AtomicBoolean isInsideTool = new AtomicBoolean(false);

        return this.webClient.post()
                // 请求地址
                .uri(this.getEndpoint(chatRequest))
                // 设置请求头
                .headers(headers -> headers.addAll(additionalHttpHeader))
                // 设置请求体
                .body(Mono.just(chatRequest), ChatCompletionRequest.class)
                // 获取响应
                .retrieve()
                // 获取响应体
                .bodyToFlux(String.class)
                // 到结束标记停止
                .takeUntil(SSE_DONE_PREDICATE)
                // 过滤掉结束标记
                .filter(SSE_DONE_PREDICATE.negate())
                // 转换为对象
                .map(content -> ModelOptionsUtils.jsonToObject(content, ChatCompletionChunk.class))
                .map(chunk -> {
                    // 判断是否需要工具调用
                    if (this.chunkMerger.isStreamingToolFunctionCall(chunk)) {
                        isInsideTool.set(true);
                    }

                    return chunk;
                })
                // 窗口处理
                .windowUntil(chunk -> {
                    // 判断工具调用是否已经全部传输完成
                    // 如果需要处理，判断为两个，上面判断了需要工具调用 isInsideTool 为 true
                    // isStreamingToolFunctionCallFinish 会判断 chunk 中是否含有工具调用的字符串
                    // 只有当 AI 发出“结束标志”（finish_reason: tool_calls）时，这个窗口才会关闭。
                    // 在这之前，windowUntil 不会关闭窗口会将 chunk 缓存起来
                    // 也就是说，只要 ai 有调用工具的倾向，windowUntil 就是用来存储 ai 调用的完整请求数据的池子
                    if (isInsideTool.get() && this.chunkMerger.isStreamingToolFunctionCallFinish(chunk)) {
                        isInsideTool.set(false);
                        return true;
                    } else {
                        return !isInsideTool.get();
                    }
                })
                // 在这里对 windowUntil 池子里的数据进行缝合
                .concatMapIterable(window -> {
                    Mono<ChatCompletionChunk> monoChunk = window.reduce(new ChatCompletionChunk(null, null, null, null, null, null, null), (previous, current) -> {
                        return this.chunkMerger.merge(previous, current);
                    });
                    return List.of(monoChunk);
                })
                .flatMap(mono -> mono);
    }

    /**
     * 获取请求地址
     *
     * @param request
     * @return
     */
    private String getEndpoint(ChatCompletionRequest request) {
        // 这里可以根据调用的模型参数中是否带有前缀，重定向其他地址
        // 当前不需要删了
        return this.completionsPath;
    }

    static {
        DEFAULT_CHAT_MODEL = ChatModel.GOOGLE_GEMINI_3_FLASH_PREVIEW;
        SSE_DONE_PREDICATE = "[DONE]"::equals;
    }

    public static enum ChatModel implements ChatModelDescription {
        GOOGLE_GEMINI_3_FLASH_PREVIEW("google/gemini-3-flash-preview"),
        GOOGLE_GEMINI_3_1_PRO_PREVIEW("google/gemini-3.1-pro-preview");

        public final String value;

        private ChatModel(String value) {
            this.value = value;
        }

        public String getValue() {
            return this.value;
        }

        @Override
        public String getName() {
            return this.value;
        }
    }

    public static class Builder {
        private String baseUrl = "https://openrouter.ai";
        private ApiKey apiKey;
        private MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        private String completionsPath = "/api/v1/chat/completions";
        private RestClient.Builder restClientBuilder = RestClient.builder();
        private WebClient.Builder webClientBuilder = WebClient.builder();
        private ResponseErrorHandler responseErrorHandler;

        public Builder() {
            this.responseErrorHandler = RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER;
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder apiKey(ApiKey apiKey) {
            Assert.notNull(apiKey, "apiKey cannot be null");
            this.apiKey = apiKey;
            return this;
        }

        public Builder apiKey(String simpleApiKey) {
            Assert.notNull(simpleApiKey, "simpleApiKey cannot be null");
            this.apiKey = new SimpleApiKey(simpleApiKey);
            return this;
        }

        public Builder headers(MultiValueMap<String, String> headers) {
            this.headers = headers;
            return this;
        }

        public Builder completionsPath(String completionsPath) {
            this.completionsPath = completionsPath;
            return this;
        }

        public Builder restClientBuilder(RestClient.Builder restClientBuilder) {
            this.restClientBuilder = restClientBuilder;
            return this;
        }

        public Builder webClientBuilder(WebClient.Builder webClientBuilder) {
            this.webClientBuilder = webClientBuilder;
            return this;
        }

        public Builder responseErrorHandler(ResponseErrorHandler responseErrorHandler) {
            this.responseErrorHandler = responseErrorHandler;
            return this;
        }

        public OpenRouterApi build() {

            if (this.apiKey == null) {
                new Throwable("OpenRouterApi.Builder.build() called with null apiKey - 调用栈：").printStackTrace();
            }
            Assert.notNull(this.apiKey, "apiKey must be set");
            return new OpenRouterApi(this.apiKey, this.baseUrl, this.headers, this.completionsPath, this.responseErrorHandler, this.restClientBuilder, this.webClientBuilder);
        }
    }

    /**
     * OpenRouterRequest 请求记录类
     *
     * @param messages
     * @param prompt
     * @param model
     * @param responseFormat
     * @param stop
     * @param stream
     * @param maxTokens
     * @param temperature
     * @param seed
     * @param topP
     * @param topK
     * @param frequencyPenalty
     * @param presencePenalty
     * @param repetitionPenalty
     * @param logitBias
     * @param topLogprobs
     * @param minP
     * @param topA
     * @param transforms
     * @param models
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ChatCompletionRequest(
            // 聊天消息
            @JsonProperty("messages")
            List<ChatCompletionMessage> messages,

            // 提示词
            @JsonProperty("prompt")
            String prompt,

            // 模型名称
            @JsonProperty("model")
            String model,

            // 结构化输出响应格式
            @JsonProperty("response_format")
            Object responseFormat,

            // 如果模型遇到停止数组中指定的任何标记，则立即停止生成。
            @JsonProperty("stop")
            List<String> stop,

            // 是否流式返回结果
            @JsonProperty("stream")
            Boolean stream,

            // 这设置了模型可以生成的标记数量的上限。
            // 它不会产生超过此限制的数量。
            // 最大值是上下文长度减去提示长度。
            // Range: [1, context_length)
            @JsonProperty("max_tokens")
            Integer maxTokens,

            // 此设置会影响模型响应的多样性。较低的值会导致更可预测和典型的响应，
            // 而较高的值则鼓励更多样化和不太常见的响应。
            // 在 0 时，模型始终对给定输入给出相同的响应。
            // Range: [0, 2]
            @JsonProperty("temperature")
            Double temperature,

            // 如果指定，推理将确定性地采样，
            // 以便具有相同种子和参数的重复请求应返回相同的结果。
            // 某些模型不能保证确定性。
            // Integer only
            @JsonProperty("seed")
            Integer seed,

            // 此设置将模型的选择限制为可能标记的百分比：只有概率加起来为 P 的顶级标记。
            // 较低的值使模型的响应更可预测，而默认设置允许全方位的标记选择。
            // 把它想象成一个动态的 Top-K。
            // Range: (0, 1]
            @JsonProperty("top_p")
            Double topP,

            // 这限制了模型在每个步骤中对标记的选择，使其从较小的集合中进行选择。
            // 值为 1 表示模型将始终选择最有可能的下一个标记，从而产生可预测的结果。
            // 默认情况下，此设置处于禁用状态，使模型考虑所有选择。
            // Range: [1, Infinity) Not available for OpenAI models
            @JsonProperty("top_k")
            Integer topK,

            // 此设置旨在根据标记在输入中出现的频率来控制标记的重复。
            // 它尝试降低使用那些在输入中出现较多的标记，与它们出现的频率成正比。
            // 令牌惩罚随出现次数而增加。负值将鼓励 token 重用。
            // Range: [-2, 2]
            @JsonProperty("frequency_penalty")
            Double frequencyPenalty,

            // 调整模型重复输入中已使用的特定标记的频率。
            // 较高的值使这种重复的可能性降低，而负值则相反。
            // 令牌惩罚不会随出现次数而变化。负值将鼓励代币重用。
            // Range: [-2, 2]
            @JsonProperty("presence_penalty")
            Double presencePenalty,

            // 有助于减少输入中标记的重复。较高的值使模型不太可能重复标记，
            // 但过高的值会使输出的连贯性降低（通常使用缺少小单词的连续句子）。
            // token 惩罚根据原始 token 的概率进行缩放。
            // Range: (0, 2]
            @JsonProperty("repetition_penalty")
            Double repetitionPenalty,

            // 接受一个 JSON 对象，该对象将令牌（由分词器中的令牌 ID 指定）映射到 -100 到 100 之间的关联偏差值。
            // 从数学上讲，偏差在采样之前被添加到模型生成的 logit 中。
            // 确切的效果因模型而异，但值介于 -1 和 1 之间应降低或增加选择的可能性;
            // 像 -100 或 100 这样的值应该会导致相关令牌被禁止或独占选择。
            // Range: [0, 1]
            @JsonProperty("logit_bias")
            Map<Integer, Integer> logitBias,

            // 一个介于 0 和 20 之间的整数，指定在每个标记位置最有可能返回的标记数，每个标记都有关联的对数概率。
            // 如果使用此参数，则必须将 logprobs 设置为 true。
            // Integer only
            @JsonProperty("top_logprobs")
            Integer topLogprobs,

            // 表示相对于最可能令牌的概率，考虑令牌的最小概率。
            // 该值根据最可能令牌的置信度而变化。如果您的 Min-P 设置为 0.1，
            // 则意味着它只允许至少是最佳选项的 1/10 的 token 。
            // Range: [0, 1]
            @JsonProperty("min_p")
            Double minP,

            // 根据最有可能的代币的概率，仅考虑具有“足够高”概率的顶级代币。可以把它想象成一个动态的 Top-P。
            // 较低的 Top-A 值会根据最高概率标记集中选择，但范围更窄。
            // 较高的 Top-A 值不一定会影响输出的创造力，而是根据最大概率细化过滤过程。
            // Range: [0, 1]
            @JsonProperty("top_a")
            Double topA,

            // 这对于不需要完美回忆的情况很有用。
            // 转换的工作原理是从提示中间删除或截断消息，直到提示适合模型的上下文窗口。
            @JsonProperty("transforms")
            List<String> transforms,

            // 允许您在主模型的提供程序关闭、速率受限或由于内容审核而拒绝回复时自动尝试其他模型。
            @JsonProperty("models")
            List<String> models,

            // 深度思考对象字段
            @JsonProperty("reasoning")
            Think reasoning,

            @JsonProperty("tools")
            List<FunctionTool> tools,

            @JsonProperty("tool_choice")
            Object toolChoice) {
        public ChatCompletionRequest(List<ChatCompletionMessage> messages, String model, Boolean stream, Double temperature) {
            this(messages, null, model, null, null, stream, null, temperature, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        }

        public ChatCompletionRequest(List<ChatCompletionMessage> messages, Boolean stream) {
            this(messages, null, null, null, null, stream, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        }

        public ChatCompletionRequest(@JsonProperty("messages") List<ChatCompletionMessage> messages, @JsonProperty("prompt") String prompt, @JsonProperty("model") String model, @JsonProperty("response_format") Object responseFormat, @JsonProperty("stop") List<String> stop, @JsonProperty("stream") Boolean stream, @JsonProperty("max_tokens") Integer maxTokens, @JsonProperty("temperature") Double temperature, @JsonProperty("seed") Integer seed, @JsonProperty("top_p") Double topP, @JsonProperty("top_k") Integer topK, @JsonProperty("frequency_penalty") Double frequencyPenalty, @JsonProperty("presence_penalty") Double presencePenalty, @JsonProperty("repetition_penalty") Double repetitionPenalty, @JsonProperty("logit_bias") Map<Integer, Integer> logitBias, @JsonProperty("top_logprobs") Integer topLogprobs, @JsonProperty("min_p") Double minP, @JsonProperty("top_a") Double topA, @JsonProperty("transforms") List<String> transforms, @JsonProperty("models") List<String> models, @JsonProperty("reasoning") Think reasoning, @JsonProperty("tools") List<FunctionTool> tools, @JsonProperty("tool_choice") Object toolChoice) {
            this.messages = messages;
            this.prompt = prompt;
            this.model = model;
            this.responseFormat = responseFormat;
            this.stop = stop;
            this.stream = stream;
            this.maxTokens = maxTokens;
            this.temperature = temperature;
            this.seed = seed;
            this.topP = topP;
            this.topK = topK;
            this.frequencyPenalty = frequencyPenalty;
            this.presencePenalty = presencePenalty;
            this.repetitionPenalty = repetitionPenalty;
            this.logitBias = logitBias;
            this.topLogprobs = topLogprobs;
            this.minP = minP;
            this.topA = topA;
            this.transforms = transforms;
            this.models = models;
            this.reasoning = reasoning;
            this.tools = tools;
            this.toolChoice = toolChoice;
        }

        public static class ToolChoiceBuilder {
            public static final String AUTO = "auto";
            public static final String NONE = "none";

            public ToolChoiceBuilder() {
            }

            public static Object FUNCTION(String functionName) {
                return Map.of("type", "function", "function", Map.of("name", functionName));
            }
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Think(
            // 分配多少 token 用于推理
            @JsonProperty("effort") Effort effort,
            // 推理的 token 数量
            @JsonProperty("max_tokens") Integer maxTokens,
            // 是否排除推理结果
            @JsonProperty("exclude") Boolean exclude,
            // 是否启用推理
            @JsonProperty("enabled") Boolean enabled) {

        public Think(@JsonProperty("effort") Effort effort, Integer maxTokens, Boolean exclude, Boolean enabled) {
            this.effort = effort;
            this.maxTokens = maxTokens;
            this.exclude = exclude;
            this.enabled = enabled;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FunctionTool {
        @JsonProperty("type")
        private Type type;
        @JsonProperty("function")
        private Function function;

        public FunctionTool() {
            this.type = Type.FUNCTION;
        }

        public FunctionTool(Type type, Function function) {
            this.type = Type.FUNCTION;
            this.type = type;
            this.function = function;
        }

        public FunctionTool(Function function) {
            this(Type.FUNCTION, function);
        }

        public Type getType() {
            return this.type;
        }

        public Function getFunction() {
            return this.function;
        }

        public void setType(Type type) {
            this.type = type;
        }

        public void setFunction(Function function) {
            this.function = function;
        }

        public static enum Type {
            @JsonProperty("function")
            FUNCTION;

            private Type() {
            }
        }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class Function {
            // 函数名称
            @JsonProperty("name")
            private String name;
            // 函数描述
            @JsonProperty("description")
            private String description;
            // 函数参数
            @JsonProperty("parameters")
            private Map<String, Object> parameters;
            // 在 DeepSeekApi 中，还有 strict 字段。但 openrouter 没有该参数
            @JsonIgnore
            private String jsonSchema;

            private Function() {
            }

            public Function(String name, String description, Map<String, Object> parameters) {
                this.description = description;
                this.name = name;
                this.parameters = parameters;
            }

            public Function(String name, String description, String jsonSchema) {
                this(description, name, ModelOptionsUtils.jsonToMap(jsonSchema));
            }

            public String getDescription() {
                return this.description;
            }

            public String getName() {
                return this.name;
            }

            public Map<String, Object> getParameters() {
                return this.parameters;
            }

            public void setDescription(String description) {
                this.description = description;
            }

            public void setName(String name) {
                this.name = name;
            }

            public void setParameters(Map<String, Object> parameters) {
                this.parameters = parameters;
            }

            public void setJsonSchema(String jsonSchema) {
                this.jsonSchema = jsonSchema;
                if (jsonSchema != null) {
                    this.parameters = ModelOptionsUtils.jsonToMap(jsonSchema);
                }

            }
        }
    }

    /**
     * 调用 openrount 返回响应数据
     */
    public record ChatCompletionChunk(
            @JsonProperty("id") String id,
            // AI 响应数据
            @JsonProperty("choices") List<Choice> choices,
            @JsonProperty("created") Long created,
            @JsonProperty("model") String model,

            @JsonProperty("object") String object,
            // 当提供商支持时才返回

            @JsonProperty("system_fingerprint") String systemFingerprint,
            // 对于非流式处理，始终返回使用情况数据。
            // 流式传输时，您将在末尾获得一个使用对象，
            // 并附有一个空的选择数组
            @JsonProperty("usage") Usage usage
    ) {
        /**
         * 调用 openrount 主要响应数据
         */
        public record Choice(

                @JsonProperty("index") Long index,

                // 模型生成的delta。存储 AI 的消息和推理过程
                @JsonProperty("delta") ChatCompletionMessage delta,

                // 模型生成文本的完成原因。
                @JsonProperty("finish_reason") ChatCompletionFinishReason finishReason,

                // 模型生成文本的完成原因。
                // 有些模型不会提供 openrount 中 finishReason 里存在的字段，而是会用别的字段
                @JsonProperty("native_finish_reason") ChatCompletionFinishReason nativeFinishReason
        ) {
        }
    }

    /**
     * 记录 token 消耗
     */
    public record Usage(
            //  如果有图片和工具，则包含在内
            @JsonProperty("prompt_tokens") Integer promptTokens,
            // 生成的令牌
            @JsonProperty("completion_tokens") Integer completionTokens,
            // 上述两个字段的和
            @JsonProperty("total_tokens") Integer totalTokens
    ) {
    }

    /**
     * 聊天消息
     *
     * @param rawContent
     * @param role
     * @param name
     * @param toolCallId
     * @param toolCalls
     * @param reasoning
     * @param reasoningDetails
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(
            ignoreUnknown = true
    )
    public static record ChatCompletionMessage(Object rawContent, Role role, String name, String toolCallId,
                                               List<ToolCall> toolCalls, String reasoning,
                                               List<ReasoningDetail> reasoningDetails) {
        public ChatCompletionMessage(Object content, Role role) {
            this(content, role, null, null, null, null, null);
        }

        public ChatCompletionMessage(Object content, Role role, String name, String toolCallId, List<ToolCall> toolCalls) {
            this(content, role, name, toolCallId, toolCalls, null, null);
        }

        public ChatCompletionMessage(@JsonProperty("content") Object rawContent, @JsonProperty("role") Role role, @JsonProperty("name") String name, @JsonProperty("tool_call_id") String toolCallId, @JsonProperty("tool_calls") @JsonFormat(with = {JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY}) List<ToolCall> toolCalls, @JsonProperty("reasoning") String reasoning, @JsonProperty("reasoning_details") List<ReasoningDetail> reasoningDetails) {
            this.rawContent = rawContent;
            this.role = role;
            this.name = name;
            this.toolCallId = toolCallId;
            this.toolCalls = toolCalls;
            this.reasoning = reasoning;
            this.reasoningDetails = reasoningDetails;
        }

        public String content() {
            if (this.rawContent == null) {
                return null;
            } else {
                Object var2 = this.rawContent;
                if (var2 instanceof String) {
                    String text = (String) var2;
                    return text;
                } else {
                    throw new IllegalStateException("The content is not a string!");
                }
            }
        }

        // 聊天内容
        @JsonProperty("content")
        public Object rawContent() {
            return this.rawContent;
        }

        // 角色
        @JsonProperty("role")
        public Role role() {
            return this.role;
        }

        @JsonProperty("name")
        public String name() {
            return this.name;
        }

        // 工具调用 id
        @JsonProperty("tool_call_id")
        public String toolCallId() {
            return this.toolCallId;
        }

        // 工具调用
        @JsonProperty("tool_calls")
        @JsonFormat(
                with = {JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY}
        )
        public List<ToolCall> toolCalls() {
            return this.toolCalls;
        }

        // 深度思考推理过程
        @JsonProperty("reasoning")
        public String reasoning() {
            return this.reasoning;
        }

        // 推理过程详情
        @JsonProperty("reasoning_details")
        public List<ReasoningDetail> reasoningDetails() {
            return this.reasoningDetails;
        }

        public static enum Role {
            // 系统角色
            @JsonProperty("system")
            SYSTEM,
            // 用户角色
            @JsonProperty("user")
            USER,
            // 助手角色
            @JsonProperty("assistant")
            ASSISTANT,
            // 工具角色
            @JsonProperty("tool")
            TOOL;

            private Role() {
            }
        }

        /**
         * 推理过程详情
         */
        public record ReasoningDetail(
                // 推理过程详情类型
                @JsonProperty("type") String type,
                // 推理过程详情内容
                @JsonProperty("text") String text,
                // 推理过程详情格式
                @JsonProperty("format") String format,
                // 推理过程详情索引
                @JsonProperty("index") Long index
        ) {
        }

        // 请求函数对象
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonIgnoreProperties(
                ignoreUnknown = true
        )
        public static record ChatCompletionFunction(String name, String arguments) {
            public ChatCompletionFunction(@JsonProperty("name") String name, @JsonProperty("arguments") String arguments) {
                this.name = name;
                this.arguments = arguments;
            }

            @JsonProperty("name")
            public String name() {
                return this.name;
            }

            @JsonProperty("arguments")
            public String arguments() {
                return this.arguments;
            }
        }

        // 工具调用
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonIgnoreProperties(
                ignoreUnknown = true
        )
        public static record ToolCall(Integer index, String id, String type, ChatCompletionFunction function) {
            public ToolCall(String id, String type, ChatCompletionFunction function) {
                this((Integer) null, id, type, function);
            }

            public ToolCall(@JsonProperty("index") Integer index, @JsonProperty("id") String id, @JsonProperty("type") String type, @JsonProperty("function") ChatCompletionFunction function) {
                this.index = index;
                this.id = id;
                this.type = type;
                this.function = function;
            }

            // 工具调用索引
            @JsonProperty("index")
            public Integer index() {
                return this.index;
            }

            // 工具调用 id
            @JsonProperty("id")
            public String id() {
                return this.id;
            }

            // 工具调用类型
            @JsonProperty("type")
            public String type() {
                return this.type;
            }

            // 工具调用函数
            @JsonProperty("function")
            public ChatCompletionFunction function() {
                return this.function;
            }
        }

        // 文本内容
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static record TextContent(
                @JsonProperty("type")
                MessageType type,
                @JsonProperty("text")
                String text
        ) {
        }

        // 图片内容
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static record ImageContent(

                @JsonProperty("type")
                MessageType type,
                @JsonProperty("image_url")
                ImageUrl imageUrl
        ) {
            // 图片地址记录类
            public record ImageUrl(
                    // 图片地址 或 base64
                    @JsonProperty("url")
                    String url,
                    // 该参数默认 auto
                    @JsonProperty("detail")
                    String detail
            ) {
            }
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(
            ignoreUnknown = true
    )
    public static record ChatCompletion(String id, List<Choice> choices, Long created, String model, String systemFingerprint, String object, Usage usage) {
        public ChatCompletion(@JsonProperty("id") String id, @JsonProperty("choices") List<Choice> choices, @JsonProperty("created") Long created, @JsonProperty("model") String model, @JsonProperty("system_fingerprint") String systemFingerprint, @JsonProperty("object") String object, @JsonProperty("usage") Usage usage) {
            this.id = id;
            this.choices = choices;
            this.created = created;
            this.model = model;
            this.systemFingerprint = systemFingerprint;
            this.object = object;
            this.usage = usage;
        }

        @JsonProperty("id")
        public String id() {
            return this.id;
        }

        @JsonProperty("choices")
        public List<Choice> choices() {
            return this.choices;
        }

        @JsonProperty("created")
        public Long created() {
            return this.created;
        }

        @JsonProperty("model")
        public String model() {
            return this.model;
        }

        @JsonProperty("system_fingerprint")
        public String systemFingerprint() {
            return this.systemFingerprint;
        }

        @JsonProperty("object")
        public String object() {
            return this.object;
        }

        @JsonProperty("usage")
        public Usage usage() {
            return this.usage;
        }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonIgnoreProperties(
                ignoreUnknown = true
        )
        public static record Choice(ChatCompletionFinishReason finishReason, ChatCompletionFinishReason nativeFinishReason, Long index, ChatCompletionMessage message, LogProbs logprobs) {
            public Choice(@JsonProperty("finish_reason") ChatCompletionFinishReason finishReason, @JsonProperty("native_finish_reason") ChatCompletionFinishReason nativeFinishReason, @JsonProperty("index") Long index, @JsonProperty("message") ChatCompletionMessage message, @JsonProperty("logprobs") LogProbs logprobs) {
                this.finishReason = finishReason;
                this.nativeFinishReason = nativeFinishReason;
                this.index = index;
                this.message = message;
                this.logprobs = logprobs;
            }

            @JsonProperty("finish_reason")
            public ChatCompletionFinishReason finishReason() {
                return this.finishReason;
            }

            @JsonProperty("native_finish_reason")
            public ChatCompletionFinishReason nativeFinishReason() {
                return this.nativeFinishReason;
            }

            @JsonProperty("index")
            public Long index() {
                return this.index;
            }

            @JsonProperty("message")
            public ChatCompletionMessage message() {
                return this.message;
            }

            @JsonProperty("logprobs")
            public LogProbs logprobs() {
                return this.logprobs;
            }
        }
    }

    // 这 LogProbs 不知道什么东西，我选择保留下
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(
            ignoreUnknown = true
    )
    public static record LogProbs(List<Content> content, List<Content> refusal) {
        public LogProbs(@JsonProperty("content") List<Content> content, @JsonProperty("refusal") List<Content> refusal) {
            this.content = content;
            this.refusal = refusal;
        }

        @JsonProperty("content")
        public List<Content> content() {
            return this.content;
        }

        @JsonProperty("refusal")
        public List<Content> refusal() {
            return this.refusal;
        }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonIgnoreProperties(
                ignoreUnknown = true
        )
        public static record Content(String token, Float logprob, List<Integer> probBytes, List<TopLogProbs> topLogprobs) {
            public Content(@JsonProperty("token") String token, @JsonProperty("logprob") Float logprob, @JsonProperty("bytes") List<Integer> probBytes, @JsonProperty("top_logprobs") List<TopLogProbs> topLogprobs) {
                this.token = token;
                this.logprob = logprob;
                this.probBytes = probBytes;
                this.topLogprobs = topLogprobs;
            }

            @JsonProperty("token")
            public String token() {
                return this.token;
            }

            @JsonProperty("logprob")
            public Float logprob() {
                return this.logprob;
            }

            @JsonProperty("bytes")
            public List<Integer> probBytes() {
                return this.probBytes;
            }

            @JsonProperty("top_logprobs")
            public List<TopLogProbs> topLogprobs() {
                return this.topLogprobs;
            }

            @JsonInclude(JsonInclude.Include.NON_NULL)
            @JsonIgnoreProperties(
                    ignoreUnknown = true
            )
            public static record TopLogProbs(String token, Float logprob, List<Integer> probBytes) {
                public TopLogProbs(@JsonProperty("token") String token, @JsonProperty("logprob") Float logprob, @JsonProperty("bytes") List<Integer> probBytes) {
                    this.token = token;
                    this.logprob = logprob;
                    this.probBytes = probBytes;
                }

                @JsonProperty("token")
                public String token() {
                    return this.token;
                }

                @JsonProperty("logprob")
                public Float logprob() {
                    return this.logprob;
                }

                @JsonProperty("bytes")
                public List<Integer> probBytes() {
                    return this.probBytes;
                }
            }
        }
    }

    // 消息类型枚举类
    public enum MessageType {
        @JsonProperty("text")
        TEXT,
        @JsonProperty("image_url")
        IMAGE_URL
    }

    /**
     * 聊天完成原因枚举类
     */
    public enum ChatCompletionFinishReason {
        @JsonProperty("stop")
        STOP,
        @JsonProperty("length")
        LENGTH,
        @JsonProperty("content_filter")
        CONTENT_FILTER,
        @JsonProperty("tool_calls")
        TOOL_CALLS,
        @JsonProperty("tool_call")
        TOOL_CALL,
        @JsonProperty("error")
        ERROR;

        private ChatCompletionFinishReason() {
        }

        // 添加一个容错的方法
        @JsonCreator
        public static ChatCompletionFinishReason fromString(String value) {
            if (value == null) {
                return null;
            }

            // 统一转换为小写进行比较
            String normalized = value.toLowerCase();
            for (ChatCompletionFinishReason reason : values()) {
                if (reason.name().toLowerCase().equals(normalized)) {
                    return reason;
                }
            }
            // 如果都不匹配，返回 null 或默认值
            return null;
        }
    }

    /**
     * 创建深度思考参数类
     */
    public enum Effort {
        @JsonProperty("high")
        HIGH,
        @JsonProperty("medium")
        MEDIUM,
        @JsonProperty("low")
        LOW,
        ;

        private Effort() {
        }

        // 添加一个容错的方法
        @JsonCreator
        public static Effort fromString(String value) {
            if (value == null) {
                return null;
            }

            // 统一转换为小写进行比较
            String normalized = value.toLowerCase();
            for (Effort thinkEffort : values()) {
                if (thinkEffort.name().toLowerCase().equals(normalized)) {
                    return thinkEffort;
                }
            }
            // 如果都不匹配，返回 null 或默认值
            return null;
        }
    }
}
