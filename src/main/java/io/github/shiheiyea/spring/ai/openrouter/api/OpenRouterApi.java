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

package io.github.shiheiyea.spring.ai.openrouter.api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.ai.model.ApiKey;
import org.springframework.ai.model.ChatModelDescription;
import org.springframework.ai.model.NoopApiKey;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.util.JsonHelper;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import io.github.shiheiyea.spring.ai.openrouter.api.common.OpenRouterConstants;

public class OpenRouterApi {

    private static final JsonHelper jsonHelper = new JsonHelper();

    public static final ChatModel DEFAULT_CHAT_MODEL = ChatModel.GOOGLE_GEMINI_3_FLASH_PREVIEW;

    /**
     * json_schema 变体的缺省结构名。OpenRouter 把 name 列为必填，字符集限 a-zA-Z0-9_- 且不超过 64 字符。
     */
    public static final String JSON_SCHEMA_NAME = "openrouter_response_schema";

    private static final Predicate<String> SSE_DONE_PREDICATE = "[DONE]"::equals;

    private final String completionsPath;

    private final ResponseErrorHandler responseErrorHandler;

    private final RestClient restClient;

    private final WebClient webClient;

    private final OpenRouterStreamFunctionCallingHelper chunkMerger = new OpenRouterStreamFunctionCallingHelper();

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 创建调用 OpenRouter 的接口对象。
     *
     * 两个 Builder 会先被克隆再设置 baseUrl 与默认请求头，避免污染调用方传入的实例。
     */
    public OpenRouterApi(ApiKey apiKey, String baseUrl, MultiValueMap<String, String> headers, String completionsPath,
            ResponseErrorHandler responseErrorHandler, RestClient.Builder restClientBuilder,
            WebClient.Builder webClientBuilder) {
        Assert.notNull(apiKey, "ApiKey must not be null");
        Assert.hasText(baseUrl, "Base URL must not be null");
        Assert.notNull(headers, "Headers must not be null");
        Assert.hasText(completionsPath, "Completions Path must not be empty");
        Assert.notNull(responseErrorHandler, "ResponseErrorHandler must not be null");
        Assert.notNull(restClientBuilder, "RestClient.Builder must not be null");
        Assert.notNull(webClientBuilder, "WebClient.Builder must not be null");
        this.completionsPath = completionsPath;
        this.responseErrorHandler = responseErrorHandler;

        // 统一注入鉴权、内容类型与自定义请求头
        Consumer<HttpHeaders> finalHeaders = (httpHeaders) -> {
            if (!(apiKey instanceof NoopApiKey)) {
                httpHeaders.setBearerAuth(apiKey.getValue());
            }
            httpHeaders.setContentType(MediaType.APPLICATION_JSON);
            httpHeaders.addAll(HttpHeaders.readOnlyHttpHeaders(headers));
        };

        this.restClient = restClientBuilder.clone()
                .baseUrl(baseUrl)
                .defaultHeaders(finalHeaders)
                .defaultStatusHandler(responseErrorHandler)
                .build();

        this.webClient = webClientBuilder.clone().baseUrl(baseUrl).defaultHeaders(finalHeaders).build();
    }

    /**
     * 使用已构建好的客户端创建接口对象，供需要自行控制 RestClient / WebClient 的场景使用。
     */
    public OpenRouterApi(String completionsPath, ResponseErrorHandler responseErrorHandler, RestClient restClient,
            WebClient webClient) {
        Assert.hasText(completionsPath, "Completions Path must not be empty");
        Assert.notNull(responseErrorHandler, "ResponseErrorHandler must not be null");
        Assert.notNull(restClient, "RestClient must not be null");
        Assert.notNull(webClient, "WebClient must not be null");
        this.completionsPath = completionsPath;
        this.responseErrorHandler = responseErrorHandler;
        this.restClient = restClient;
        this.webClient = webClient;
    }

    public String getCompletionsPath() {
        return this.completionsPath;
    }

    public ResponseErrorHandler getResponseErrorHandler() {
        return this.responseErrorHandler;
    }

    /**
     * 非流式调用，返回一次性完整响应。
     *
     * 请求体必须显式把 stream 设为 false，否则说明调用方拿错了通路。
     */
    public ResponseEntity<ChatCompletion> chatCompletionEntity(ChatCompletionRequest chatRequest) {
        Assert.notNull(chatRequest, "The request body can not be null.");
        Assert.isTrue(Boolean.FALSE.equals(chatRequest.stream()), "Request must set the stream property to false.");
        return this.restClient.post()
                .uri(this.getEndpoint())
                .body(chatRequest)
                .retrieve()
                .toEntity(ChatCompletion.class);
    }

    public Flux<ChatCompletionChunk> chatCompletionStream(ChatCompletionRequest chatRequest) {
        return this.chatCompletionStream(chatRequest, new HttpHeaders());
    }

    /**
     * 流式调用，返回逐块响应的数据流。
     *
     * 上游按 SSE 逐行输出，遇到 {@code [DONE]} 结束；工具调用期间同一轮次的参数被拆散。
     * 多个分块里，需要先累积成一个窗口再缝合，否则调用方会拿到半截 JSON。
     */
    public Flux<ChatCompletionChunk> chatCompletionStream(ChatCompletionRequest chatRequest,
            HttpHeaders additionalHttpHeader) {
        Assert.notNull(chatRequest, "The request body can not be null.");
        Assert.isTrue(Boolean.TRUE.equals(chatRequest.stream()), "Request must set the stream property to true.");

        // 记录当前是否处于工具调用的参数累积阶段。
        AtomicBoolean isInsideTool = new AtomicBoolean(false);

        return this.webClient.post()
                .uri(this.getEndpoint())
                .headers(headers -> headers.addAll(additionalHttpHeader))
                .body(Mono.just(chatRequest), ChatCompletionRequest.class)
                .retrieve()
                .bodyToFlux(String.class)
                .takeUntil(SSE_DONE_PREDICATE)
                .filter(SSE_DONE_PREDICATE.negate())
                .mapNotNull(content -> jsonHelper.fromJson(content, ChatCompletionChunk.class))
                .map(chunk -> {
                    if (this.chunkMerger.isStreamingToolFunctionCall(chunk)) {
                        isInsideTool.set(true);
                    }
                    return chunk;
                })
                // 工具调用开始后不再切分窗口，直到该次调用收到结束标记。
                .windowUntil(chunk -> {
                    if (isInsideTool.get() && this.chunkMerger.isStreamingToolFunctionCallFinish(chunk)) {
                        isInsideTool.set(false);
                        return true;
                    }
                    return !isInsideTool.get();
                })
                .concatMapIterable(window -> {
                    // 不传种子块：merge 的 previous 为 null 时直接返回 current，由它承担种子语义
                    Mono<ChatCompletionChunk> monoChunk = window.reduce(this.chunkMerger::merge);
                    return List.of(monoChunk);
                })
                .flatMap(mono -> mono);
    }

    /**
     * OpenRouter 只有一条聊天补全端点，路径在构建期确定，不做运行期重定向。
     */
    private String getEndpoint() {
        return this.completionsPath;
    }

    public static enum ChatModel implements ChatModelDescription {

        GOOGLE_GEMINI_3_FLASH_PREVIEW("google/gemini-3-flash-preview"),

        GOOGLE_GEMINI_3_1_PRO_PREVIEW("google/gemini-3.1-pro-preview");

        public final String value;

        ChatModel(String value) {
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

        private String baseUrl = OpenRouterConstants.DEFAULT_BASE_URL;

        private ApiKey apiKey;

        private MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();

        private String completionsPath = OpenRouterConstants.DEFAULT_COMPLETIONS_PATH;

        private RestClient.Builder restClientBuilder = RestClient.builder();

        private WebClient.Builder webClientBuilder = WebClient.builder();

        private ResponseErrorHandler responseErrorHandler = RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER;

        public Builder baseUrl(String baseUrl) {
            Assert.hasText(baseUrl, "Base URL must not be empty");
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder apiKey(ApiKey apiKey) {
            Assert.notNull(apiKey, "apiKey cannot be null");
            this.apiKey = apiKey;
            return this;
        }

        public Builder apiKey(String simpleApiKey) {
            Assert.hasText(simpleApiKey, "simpleApiKey cannot be empty");
            this.apiKey = new SimpleApiKey(simpleApiKey);
            return this;
        }

        public Builder headers(MultiValueMap<String, String> headers) {
            Assert.notNull(headers, "Headers must not be null");
            this.headers = headers;
            return this;
        }

        /**
         * 追加单个请求头，多用于 OpenRouter 的应用归属头。
         */
        public Builder header(String name, String value) {
            Assert.hasText(name, "Header name must not be empty");
            this.headers.add(name, value);
            return this;
        }

        public Builder completionsPath(String completionsPath) {
            Assert.hasText(completionsPath, "Completions Path must not be empty");
            this.completionsPath = completionsPath;
            return this;
        }

        public Builder restClientBuilder(RestClient.Builder restClientBuilder) {
            Assert.notNull(restClientBuilder, "RestClient.Builder must not be null");
            this.restClientBuilder = restClientBuilder;
            return this;
        }

        public Builder webClientBuilder(WebClient.Builder webClientBuilder) {
            Assert.notNull(webClientBuilder, "WebClient.Builder must not be null");
            this.webClientBuilder = webClientBuilder;
            return this;
        }

        public Builder responseErrorHandler(ResponseErrorHandler responseErrorHandler) {
            Assert.notNull(responseErrorHandler, "ResponseErrorHandler must not be null");
            this.responseErrorHandler = responseErrorHandler;
            return this;
        }

        public OpenRouterApi build() {
            Assert.notNull(this.apiKey, "apiKey must be set");
            return new OpenRouterApi(this.apiKey, this.baseUrl, this.headers, this.completionsPath,
                    this.responseErrorHandler, this.restClientBuilder, this.webClientBuilder);
        }

    }

    /**
     * 一次聊天补全请求。
     *
     * 请求体字段分三类：模型层组装 {@code messages} / {@code stream} / {@code tools} /
     * {@code response_format}，5 个高频强类型字段，以及由 {@link #extraBody} 承载的
     * 12 个低频字段。{@code extraBody} 通过 {@code @JsonAnyGetter} 展平到请求体顶层，
     * 所以序列化结果里不会出现 {@code extra_body} 这个包裹层。
     *
     * {@code response_format} 由强类型 {@link ResponseFormat} 承载，取值来自 Options 侧的
     * {@code StructuredOutputChatOptions#getOutputSchema()} 或用户直接设置的响应格式。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ChatCompletionRequest(@JsonProperty("messages") List<ChatCompletionMessage> messages,

            @JsonProperty("stream") Boolean stream,

            @JsonProperty("tools") List<FunctionTool> tools,

            @JsonProperty("response_format") ResponseFormat responseFormat,

            @JsonProperty("model") String model,

            @JsonProperty("max_completion_tokens") Integer maxTokens,

            @JsonProperty("temperature") Double temperature,

            @JsonProperty("top_p") Double topP,

            @JsonProperty("top_k") Integer topK,

            @JsonProperty("frequency_penalty") Double frequencyPenalty,

            @JsonProperty("presence_penalty") Double presencePenalty,

            @JsonProperty("repetition_penalty") Double repetitionPenalty,

            @JsonProperty("seed") Integer seed,

            @JsonProperty("min_p") Double minP,

            @JsonProperty("top_a") Double topA,

            @JsonProperty("stop") List<String> stop,

            @JsonProperty("logit_bias") Map<String, Number> logitBias,

            @JsonProperty("top_logprobs") Integer topLogprobs,

            @JsonProperty("logprobs") Boolean logprobs,

            @JsonProperty("models") List<String> models,

            @JsonProperty("modalities") List<String> modalities,

            @JsonProperty("service_tier") String serviceTier,

            @JsonProperty("user") String user,

            @JsonProperty("parallel_tool_calls") Boolean parallelToolCalls,

            @JsonProperty("prompt_cache_key") String promptCacheKey,

            @JsonProperty("session_id") String sessionId,

            @JsonProperty("provider") ProviderPreferences provider,

            @JsonProperty("reasoning") Reasoning reasoning,

            @JsonProperty("tool_choice") Object toolChoice,

            @JsonIgnore Map<String, Object> extraBody) {

        public ChatCompletionRequest(List<ChatCompletionMessage> messages, Boolean stream) {
            this(messages, stream, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                    null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        }

        public ChatCompletionRequest(List<ChatCompletionMessage> messages, String model, Double temperature) {
            this(messages, null, null, null, model, null, temperature, null, null, null, null, null, null, null, null,
                    null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        }

        /**
         * 把低频字段展平到请求体顶层，这是 OpenRouter 侧接收 extraBody 字段的落地点。
         *
         * {@code response_format} 已由强类型组件承载，透传同名键会产生重复字段，这里直接剔除。
         */
        @JsonAnyGetter
        public Map<String, Object> extraBodyProperties() {
            if (this.extraBody == null || this.extraBody.isEmpty()) {
                return Map.of();
            }
            if (!this.extraBody.containsKey("response_format")) {
                return this.extraBody;
            }
            Map<String, Object> properties = new HashMap<>(this.extraBody);
            properties.remove("response_format");
            return properties;
        }

        public static class ToolChoiceBuilder {

            public static final String AUTO = "auto";

            public static final String NONE = "none";

            public static final String REQUIRED = "required";

            /**
             * 指定调用某个具名函数，对应 OpenRouter 的 {@code ChatNamedToolChoice}。
             */
            public static Object FUNCTION(String functionName) {
                return Map.of("type", "function", "function", Map.of("name", functionName));
            }

        }

    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ResponseFormat(@JsonProperty("type") Type type,

            @JsonProperty("json_schema") JsonSchema jsonSchema) {

        /**
         * 纯文本输出，语义上等价于不限制格式。
         */
        public static ResponseFormat text() {
            return new ResponseFormat(Type.TEXT, null);
        }

        /**
         * JSON 对象输出，不约束具体结构，适合上游不支持 json_schema 的场景。
         */
        public static ResponseFormat jsonObject() {
            return new ResponseFormat(Type.JSON_OBJECT, null);
        }

        /**
         * 用 JSON Schema 字符串描述结构，缺省补齐结构名并开启严格模式。
         */
        public static ResponseFormat jsonSchema(String jsonSchema) {
            return jsonSchema(jsonHelper.fromJsonToMap(jsonSchema));
        }

        /**
         * 用 Java 类型生成 JSON Schema，内部走框架的结构生成器。
         */
        public static ResponseFormat jsonSchema(Class<?> type) {
            return jsonSchema(JsonSchemaGenerator.generateForType(type));
        }

        public static ResponseFormat jsonSchema(Map<String, Object> jsonSchema) {
            JsonSchema schema = new JsonSchema(JSON_SCHEMA_NAME, null, new HashMap<>(jsonSchema), Boolean.TRUE);
            return new ResponseFormat(Type.JSON_SCHEMA, schema);
        }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        public record JsonSchema(@JsonProperty("name") String name,

                @JsonProperty("description") String description,

                @JsonProperty("schema") Map<String, Object> schema,

                @JsonProperty("strict") Boolean strict) {

        }

        public enum Type {

            @JsonProperty("text")
            TEXT,

            @JsonProperty("json_object")
            JSON_OBJECT,

            @JsonProperty("json_schema")
            JSON_SCHEMA

        }

    }

    /**
     * 路由偏好。OpenRouter 会把请求分发给上百家上游，这一组字段决定挑哪一家。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ProviderPreferences(@JsonProperty("allow_fallbacks") Boolean allowFallbacks,

            @JsonProperty("data_collection") DataCollection dataCollection,

            @JsonProperty("enforce_distillable_text") Boolean enforceDistillableText,

            @JsonProperty("ignore") List<String> ignore,

            @JsonProperty("max_price") MaxPrice maxPrice,

            @JsonProperty("only") List<String> only,

            @JsonProperty("options") Map<String, Map<String, Object>> options,

            @JsonProperty("order") List<String> order,

            @JsonProperty("preferred_max_latency") Object preferredMaxLatency,

            @JsonProperty("preferred_min_throughput") Object preferredMinThroughput,

            @JsonProperty("quantizations") List<Quantization> quantizations,

            @JsonProperty("require_parameters") Boolean requireParameters,

            @JsonProperty("sort") Object sort,

            @JsonProperty("zdr") Boolean zdr) {

        public enum DataCollection {

            @JsonProperty("deny")
            DENY,

            @JsonProperty("allow")
            ALLOW

        }

        public enum Quantization {

            @JsonProperty("int4")
            INT4,

            @JsonProperty("int8")
            INT8,

            @JsonProperty("fp4")
            FP4,

            @JsonProperty("mxfp4")
            MXFP4,

            @JsonProperty("nvfp4")
            NVFP4,

            @JsonProperty("fp6")
            FP6,

            @JsonProperty("fp8")
            FP8,

            @JsonProperty("mxfp8")
            MXFP8,

            @JsonProperty("fp16")
            FP16,

            @JsonProperty("bf16")
            BF16,

            @JsonProperty("fp32")
            FP32,

            @JsonProperty("unknown")
            UNKNOWN;

            /**
             * 规范对该枚举标注了允许未知取值，认不出的量化方式按 null 处理而不是抛错。
             */
            @JsonCreator
            public static Quantization fromString(@Nullable String value) {
                if (value == null) {
                    return null;
                }
                for (Quantization quantization : values()) {
                    if (quantization.name().equalsIgnoreCase(value)) {
                        return quantization;
                    }
                }
                return null;
            }

        }

        /**
         * 价格上限，单位是每百万 token 的美元。规范里五个子项都声明为字符串，这里照抄不做数值化。
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public record MaxPrice(@JsonProperty("prompt") String prompt, @JsonProperty("completion") String completion,
                @JsonProperty("request") String request, @JsonProperty("image") String image,
                @JsonProperty("audio") String audio) {
        }

        public enum Sort {

            @JsonProperty("price")
            PRICE,

            @JsonProperty("throughput")
            THROUGHPUT,

            @JsonProperty("latency")
            LATENCY,

            @JsonProperty("exacto")
            EXACTO

        }

        /**
         * 带分区的排序配置，分区为 model 时回退模型仍留在回退位置，为 none 时所有端点一起排序。
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public record SortConfig(@JsonProperty("by") Sort by, @JsonProperty("partition") Partition partition) {

            public enum Partition {

                @JsonProperty("model")
                MODEL,

                @JsonProperty("none")
                NONE

            }

        }

        /**
         * 延迟百分位上限，秒。
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public record PercentileLatencyCutoffs(@JsonProperty("p50") Double p50, @JsonProperty("p75") Double p75,
                @JsonProperty("p90") Double p90, @JsonProperty("p99") Double p99) {
        }

        /**
         * 吞吐百分位下限，token 每秒。
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public record PercentileThroughputCutoffs(@JsonProperty("p50") Double p50, @JsonProperty("p75") Double p75,
                @JsonProperty("p90") Double p90, @JsonProperty("p99") Double p99) {
        }

    }

    /**
     * 推理配置。与顶层 {@code reasoning_effort} 互斥，本模块只保留这一处入口。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Reasoning(@JsonProperty("effort") Effort effort,

            @JsonProperty("max_tokens") Integer maxTokens,

            @JsonProperty("exclude") Boolean exclude,

            @JsonProperty("enabled") Boolean enabled,

            @JsonProperty("summary") Summary summary,

            @JsonProperty("mode") Mode mode,

            @JsonProperty("context") Context context) {

        public enum Effort {

            @JsonProperty("max")
            MAX,

            @JsonProperty("xhigh")
            XHIGH,

            @JsonProperty("high")
            HIGH,

            @JsonProperty("medium")
            MEDIUM,

            @JsonProperty("low")
            LOW,

            @JsonProperty("minimal")
            MINIMAL,

            @JsonProperty("none")
            NONE;

            /**
             * 规范允许上游扩展取值，认不出时按 null 处理。
             */
            @JsonCreator
            public static Effort fromString(@Nullable String value) {
                if (value == null) {
                    return null;
                }
                for (Effort effort : values()) {
                    if (effort.name().equalsIgnoreCase(value)) {
                        return effort;
                    }
                }
                return null;
            }

        }

        public enum Summary {

            @JsonProperty("auto")
            AUTO,

            @JsonProperty("concise")
            CONCISE,

            @JsonProperty("detailed")
            DETAILED

        }

        public enum Mode {

            @JsonProperty("standard")
            STANDARD,

            @JsonProperty("pro")
            PRO

        }

        public enum Context {

            @JsonProperty("auto")
            AUTO,

            @JsonProperty("all_turns")
            ALL_TURNS,

            @JsonProperty("current_turn")
            CURRENT_TURN

        }

    }

    public static class FunctionTool {

        @JsonProperty("type")
        private Type type = Type.FUNCTION;

        @JsonProperty("function")
        private Function function;

        public FunctionTool() {
        }

        public FunctionTool(Type type, Function function) {
            this.type = type;
            this.function = function;
        }

        public FunctionTool(Function function) {
            this(Type.FUNCTION, function);
        }

        public Type getType() {
            return this.type;
        }

        public void setType(Type type) {
            this.type = type;
        }

        public Function getFunction() {
            return this.function;
        }

        public void setFunction(Function function) {
            this.function = function;
        }

        public enum Type {

            @JsonProperty("function")
            FUNCTION

        }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class Function {

            @JsonProperty("name")
            private String name;

            @JsonProperty("description")
            private String description;

            @JsonProperty("parameters")
            private Map<String, Object> parameters;

            @JsonProperty("strict")
            private Boolean strict;

            @JsonIgnore
            private String jsonSchema;

            public Function() {
            }

            public Function(String name, String description, Map<String, Object> parameters) {
                this.name = name;
                this.description = description;
                this.parameters = parameters;
            }

            /**
             * 用 JSON Schema 字符串描述参数，模型层解析工具定义时走这条路径。
             */
            public Function(String name, String description, String jsonSchema) {
                this.name = name;
                this.description = description;
                this.jsonSchema = jsonSchema;
                this.parameters = jsonHelper.fromJsonToMap(jsonSchema);
            }

            public String getName() {
                return this.name;
            }

            public void setName(String name) {
                this.name = name;
            }

            public String getDescription() {
                return this.description;
            }

            public void setDescription(String description) {
                this.description = description;
            }

            public Map<String, Object> getParameters() {
                return this.parameters;
            }

            public void setParameters(Map<String, Object> parameters) {
                this.parameters = parameters;
            }

            public Boolean getStrict() {
                return this.strict;
            }

            public void setStrict(Boolean strict) {
                this.strict = strict;
            }

            public String getJsonSchema() {
                return this.jsonSchema;
            }

            /**
             * 设置 JSON Schema 字符串的同时同步解析出的参数表，避免两个字段各说各话。
             */
            public void setJsonSchema(String jsonSchema) {
                this.jsonSchema = jsonSchema;
                if (jsonSchema != null) {
                    this.parameters = jsonHelper.fromJsonToMap(jsonSchema);
                }
            }

        }

    }

    /**
     * 一条聊天消息，请求与响应共用。
     *
     * 请求，{@code content} 可能是纯字符串，也可能是多模态内容部件数组；响应侧还额外带回
     * 推理文本、推理详情与生成的图片、音频。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChatCompletionMessage(@JsonProperty("content") Object rawContent,

            @JsonProperty("role") Role role,

            @JsonProperty("name") String name,

            @JsonProperty("tool_call_id") String toolCallId,

            @JsonProperty("tool_calls") @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY) List<ToolCall> toolCalls,

            @JsonProperty("reasoning") String reasoning,

            @JsonProperty("reasoning_details") List<ReasoningDetail> reasoningDetails,

            @JsonProperty("images") List<ChatAssistantImage> images,

            @JsonProperty("audio") ChatAudioOutput audio) {

        public ChatCompletionMessage(Object content, Role role) {
            this(content, role, null, null, null, null, null, null, null);
        }

        public ChatCompletionMessage(Object content, Role role, String name, String toolCallId,
                List<ToolCall> toolCalls) {
            this(content, role, name, toolCallId, toolCalls, null, null, null, null);
        }

        /**
         * 取纯文本内容。多模态请求的 content 是内容部件数组，此处不做拼接。
         */
        public String content() {
            if (this.rawContent == null) {
                return null;
            }
            if (this.rawContent instanceof String text) {
                return text;
            }
            throw new IllegalStateException("The content is not a string!");
        }

        public enum Role {

            @JsonProperty("system")
            SYSTEM,

            @JsonProperty("user")
            USER,

            @JsonProperty("assistant")
            ASSISTANT,

            @JsonProperty("tool")
            TOOL

        }

    }

    /**
     * 请求侧内容部件，一条部件的 type 决定哪个字段有效。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TextContent(@JsonProperty("type") MessageType type, @JsonProperty("text") String text) {

        public TextContent(String text) {
            this(MessageType.TEXT, text);
        }

    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ImageContent(@JsonProperty("type") MessageType type,
            @JsonProperty("image_url") ImageUrl imageUrl) {

        public ImageContent(ImageUrl imageUrl) {
            this(MessageType.IMAGE_URL, imageUrl);
        }

    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AudioContent(@JsonProperty("type") MessageType type,
            @JsonProperty("input_audio") InputAudio inputAudio) {

        public AudioContent(InputAudio inputAudio) {
            this(MessageType.INPUT_AUDIO, inputAudio);
        }

    }

    /**
     * 图片引用，detail 可取 auto / low / high / original，其中 original 是 OpenRouter 扩展值。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ImageUrl(@JsonProperty("url") String url, @JsonProperty("detail") String detail) {

        public ImageUrl(String url) {
            this(url, null);
        }

    }

    /**
     * Base64 编码的音频数据与格式，格式取值随上游 provider 而异。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record InputAudio(@JsonProperty("data") String data, @JsonProperty("format") String format) {
    }

    public enum MessageType {

        @JsonProperty("text")
        TEXT,

        @JsonProperty("image_url")
        IMAGE_URL,

        @JsonProperty("input_audio")
        INPUT_AUDIO

    }

    /**
     * 生成完成的图片，url 可能是远程地址也可能是 data URL。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ChatAssistantImage(@JsonProperty("image_url") ImageUrl imageUrl) {
    }

    /**
     * 生成的音频，data 是 Base64 内容，transcript 是配套文字稿。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ChatAudioOutput(@JsonProperty("data") String data, @JsonProperty("expires_at") Long expiresAt,
            @JsonProperty("id") String id, @JsonProperty("transcript") String transcript) {
    }

    /**
     * 推理详情。四种变体共用一个结构，由 type 区分有效字段，回传上游时需要原样带回。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ReasoningDetail(@JsonProperty("type") ReasoningDetailType type,

            @JsonProperty("id") String id,

            @JsonProperty("index") Integer index,

            @JsonProperty("format") String format,

            @JsonProperty("text") String text,

            @JsonProperty("summary") String summary,

            @JsonProperty("data") String data,

            @JsonProperty("signature") String signature,

            @JsonProperty("arguments") String arguments,

            @JsonProperty("result") String result,

            @JsonProperty("tool_call_id") String toolCallId,

            @JsonProperty("tool_name") String toolName) {

        public enum ReasoningDetailType {

            @JsonProperty("reasoning.text")
            TEXT,

            @JsonProperty("reasoning.summary")
            SUMMARY,

            @JsonProperty("reasoning.encrypted")
            ENCRYPTED,

            @JsonProperty("reasoning.server_tool_call")
            SERVER_TOOL_CALL;

            /**
             * 规范允许上游新增推理详情类型，认不出时按 null 处理而不是拒绝整条响应。
             */
            @JsonCreator
            public static ReasoningDetailType fromString(@Nullable String value) {
                if (value == null) {
                    return null;
                }
                for (ReasoningDetailType type : values()) {
                    if (type.getValue().equalsIgnoreCase(value)) {
                        return type;
                    }
                }
                return null;
            }

            public String getValue() {
                return switch (this) {
                    case TEXT -> "reasoning.text";
                    case SUMMARY -> "reasoning.summary";
                    case ENCRYPTED -> "reasoning.encrypted";
                    case SERVER_TOOL_CALL -> "reasoning.server_tool_call";
                };
            }

        }

    }

    /**
     * 流式分块。上游出错时会在流里回一条带 error 的分块，而不是抛，HTTP 错误。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChatCompletionChunk(@JsonProperty("id") String id,

            @JsonProperty("choices") List<ChunkChoice> choices,

            @JsonProperty("created") Long created,

            @JsonProperty("model") String model,

            @JsonProperty("service_tier") String serviceTier,

            @JsonProperty("system_fingerprint") String systemFingerprint,

            @JsonProperty("object") String object,

            @JsonProperty("usage") Usage usage,

            @JsonProperty("error") ChatCompletionError error) {

        public ChatCompletionChunk(String id, List<ChunkChoice> choices, Long created, String model,
                String serviceTier, String systemFingerprint, String object, Usage usage) {
            this(id, choices, created, model, serviceTier, systemFingerprint, object, usage, null);
        }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        public record ChunkChoice(@JsonProperty("finish_reason") ChatCompletionFinishReason finishReason,

                @JsonProperty("native_finish_reason") ChatCompletionFinishReason nativeFinishReason,

                @JsonProperty("index") Integer index,

                @JsonProperty("delta") ChatCompletionMessage delta,

                @JsonProperty("logprobs") LogProbs logprobs) {
        }

    }

    /**
     * 流内错误。metadata 里的 error_type 是 OpenRouter 归一化后的错误类型。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ChatCompletionError(@JsonProperty("code") Integer code, @JsonProperty("message") String message,
            @JsonProperty("metadata") ErrorMetadata metadata) {

        @JsonInclude(JsonInclude.Include.NON_NULL)
        public record ErrorMetadata(@JsonProperty("error_type") String errorType,
                @JsonProperty("provider_code") String providerCode) {
        }

    }

    /**
     * 非流式响应体。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChatCompletion(@JsonProperty("id") String id,

            @JsonProperty("choices") List<Choice> choices,

            @JsonProperty("created") Long created,

            @JsonProperty("model") String model,

            @JsonProperty("service_tier") String serviceTier,

            @JsonProperty("system_fingerprint") String systemFingerprint,

            @JsonProperty("object") String object,

            @JsonProperty("usage") Usage usage) {

        @JsonInclude(JsonInclude.Include.NON_NULL)
        public record Choice(@JsonProperty("finish_reason") ChatCompletionFinishReason finishReason,

                @JsonProperty("native_finish_reason") ChatCompletionFinishReason nativeFinishReason,

                @JsonProperty("index") Integer index,

                @JsonProperty("message") ChatCompletionMessage message,

                @JsonProperty("logprobs") LogProbs logprobs) {
        }

    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LogProbs(@JsonProperty("content") List<Content> content,
            @JsonProperty("refusal") List<Content> refusal) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Content(@JsonProperty("token") String token, @JsonProperty("logprob") Float logprob,
            @JsonProperty("bytes") List<Integer> probBytes,
            @JsonProperty("top_logprobs") List<TopLogProbs> topLogprobs) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TopLogProbs(@JsonProperty("token") String token, @JsonProperty("logprob") Float logprob,
            @JsonProperty("bytes") List<Integer> probBytes) {
    }

    /**
     * 用量统计。OpenRouter 在通用 token 计数之外还给出成本、缓存与 BYOK 信息。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Usage(@JsonProperty("completion_tokens") Integer completionTokens,

            @JsonProperty("completion_tokens_details") CompletionTokensDetails completionTokensDetails,

            @JsonProperty("cost") Double cost,

            @JsonProperty("cost_details") CostDetails costDetails,

            @JsonProperty("deferred_tools_details") DeferredToolsDetails deferredToolsDetails,

            @JsonProperty("is_byok") Boolean isByok,

            @JsonProperty("prompt_tokens") Integer promptTokens,

            @JsonProperty("prompt_tokens_details") PromptTokensDetails promptTokensDetails,

            @JsonProperty("server_tool_use_details") ServerToolUseDetails serverToolUseDetails,

            @JsonProperty("total_tokens") Integer totalTokens) {

        public Usage(Integer completionTokens, Integer promptTokens, Integer totalTokens) {
            this(completionTokens, null, null, null, null, null, promptTokens, null, null, totalTokens);
        }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record CompletionTokensDetails(@JsonProperty("accepted_prediction_tokens") Integer acceptedPredictionTokens,
                @JsonProperty("audio_tokens") Integer audioTokens,
                @JsonProperty("reasoning_tokens") Integer reasoningTokens,
                @JsonProperty("rejected_prediction_tokens") Integer rejectedPredictionTokens) {
        }

        /**
         * 上游推理成本明细，单位美元。命名用 prompt / completions，与 responses 通路的
         * input / output 是两套写法，不可互换。
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record CostDetails(@JsonProperty("server_tool_cost") Double serverToolCost,
                @JsonProperty("upstream_inference_completions_cost") Double upstreamInferenceCompletionsCost,
                @JsonProperty("upstream_inference_cost") Double upstreamInferenceCost,
                @JsonProperty("upstream_inference_prompt_cost") Double upstreamInferencePromptCost) {
        }

        /**
         * 延迟工具一整轮的总量。原始 token 字段只反映最后一次调用，成本字段覆盖全部调用。
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record DeferredToolsDetails(@JsonProperty("completion_tokens") Integer completionTokens,
                @JsonProperty("prompt_tokens") Integer promptTokens,
                @JsonProperty("total_tokens") Integer totalTokens,
                @JsonProperty("turns") Integer turns) {
        }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record PromptTokensDetails(@JsonProperty("audio_tokens") Integer audioTokens,
                @JsonProperty("cache_write_tokens") Integer cacheWriteTokens,
                @JsonProperty("cached_tokens") Integer cachedTokens,
                @JsonProperty("video_tokens") Integer videoTokens) {
        }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record ServerToolUseDetails(@JsonProperty("tool_calls_executed") Integer toolCallsExecuted,
                @JsonProperty("tool_calls_requested") Integer toolCallsRequested,
                @JsonProperty("web_search_requests") Integer webSearchRequests) {
        }

    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChatCompletionFunction(@JsonProperty("name") String name,
            @JsonProperty("arguments") String arguments) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ToolCall(@JsonProperty("index") Integer index, @JsonProperty("id") String id,
            @JsonProperty("type") String type, @JsonProperty("function") ChatCompletionFunction function) {

        public ToolCall(String id, String type, ChatCompletionFunction function) {
            this(null, id, type, function);
        }

    }

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

        /**
         * 规范允许上游返回未列出的结束原因，认不出时按 null 处理。
         */
        @JsonCreator
        public static ChatCompletionFinishReason fromString(@Nullable String value) {
            if (value == null) {
                return null;
            }
            for (ChatCompletionFinishReason reason : values()) {
                if (reason.name().equalsIgnoreCase(value)) {
                    return reason;
                }
            }
            return null;
        }

    }

}
