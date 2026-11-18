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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;
import org.springframework.ai.model.tool.StructuredOutputChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.github.shiheiyea.spring.ai.openrouter.api.OpenRouterApi;
import io.github.shiheiyea.spring.ai.openrouter.api.OpenRouterApi.ResponseFormat.Type;

/**
 * OpenRouter 聊天选项。
 *
 * 与官方模块的差异在于字段规模：OpenRouter 的请求体有 43 个键，这里把其中高频的
 * 25 个做成强类型字段，其余 12 个低频字段通过 {@code extraBody} 原样透传。
 * 避免为一个罕用字段改一次选项类。
 *
 * {@code responseFormat} 是结构化输出的 Options 侧载体，对应请求体的 {@code response_format}，
 * 不计入上面那 43 个键。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OpenRouterChatOptions implements ToolCallingChatOptions, StructuredOutputChatOptions {

    private final String model;

    private final Double temperature;

    private final Boolean stream;

    private final OpenRouterApi.Reasoning reasoning;

    private final Integer maxTokens;

    private final Double frequencyPenalty;

    private final Double presencePenalty;

    private final Double topP;

    private final Integer topK;

    private final Integer seed;

    private final Double minP;

    private final Double topA;

    private final Double repetitionPenalty;

    private final List<String> stop;

    private final Map<String, Number> logitBias;

    private final Integer topLogprobs;

    private final Boolean logprobs;

    private final List<String> models;

    private final List<String> modalities;

    private final String serviceTier;

    private final String user;

    private final Boolean parallelToolCalls;

    private final String promptCacheKey;

    private final String sessionId;

    private final OpenRouterApi.ProviderPreferences provider;

    private final List<OpenRouterApi.FunctionTool> tools;

    private final Object toolChoice;

    private final Map<String, Object> extraBody;

    private final List<ToolCallback> toolCallbacks;

    private final Map<String, Object> toolContext;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private final ResponseFormat responseFormat;

    protected OpenRouterChatOptions(@Nullable String model, @Nullable Double temperature, @Nullable Boolean stream,
            OpenRouterApi.@Nullable Reasoning reasoning, @Nullable Integer maxTokens,
            @Nullable Double frequencyPenalty, @Nullable Double presencePenalty, @Nullable Double topP,
            @Nullable Integer topK, @Nullable Integer seed, @Nullable Double minP, @Nullable Double topA,
            @Nullable Double repetitionPenalty, @Nullable List<String> stop,
            @Nullable Map<String, Number> logitBias, @Nullable Integer topLogprobs,
            @Nullable Boolean logprobs, @Nullable List<String> models, @Nullable List<String> modalities,
            @Nullable String serviceTier, @Nullable String user, @Nullable Boolean parallelToolCalls,
            @Nullable String promptCacheKey, @Nullable String sessionId,
            OpenRouterApi.@Nullable ProviderPreferences provider, @Nullable List<OpenRouterApi.FunctionTool> tools,
            @Nullable Object toolChoice, @Nullable Map<String, Object> extraBody,
            @Nullable List<ToolCallback> toolCallbacks, @Nullable Map<String, Object> toolContext,
            @Nullable ResponseFormat responseFormat) {
        // OpenRouter 没有默认模型，缺省时沿用接口层声明的模型，保证请求始终带 model
        this.model = model != null ? model : OpenRouterApi.DEFAULT_CHAT_MODEL.getValue();
        this.temperature = temperature != null ? temperature : 0.7;
        this.stream = stream;
        this.reasoning = reasoning;
        this.maxTokens = maxTokens;
        this.frequencyPenalty = frequencyPenalty;
        this.presencePenalty = presencePenalty;
        this.topP = topP;
        this.topK = topK;
        this.seed = seed;
        this.minP = minP;
        this.topA = topA;
        this.repetitionPenalty = repetitionPenalty;
        this.stop = stop != null ? List.copyOf(stop) : null;
        this.logitBias = logitBias != null ? Map.copyOf(logitBias) : null;
        this.topLogprobs = topLogprobs;
        this.logprobs = logprobs;
        this.models = models != null ? List.copyOf(models) : null;
        this.modalities = modalities != null ? List.copyOf(modalities) : null;
        this.serviceTier = serviceTier;
        this.user = user;
        this.parallelToolCalls = parallelToolCalls;
        this.promptCacheKey = promptCacheKey;
        this.sessionId = sessionId;
        this.provider = provider;
        this.tools = tools != null ? List.copyOf(tools) : null;
        this.toolChoice = toolChoice;
        this.extraBody = extraBody != null ? Map.copyOf(extraBody) : null;
        this.toolCallbacks = toolCallbacks != null ? List.copyOf(toolCallbacks) : null;
        this.toolContext = toolContext != null ? Map.copyOf(toolContext) : null;
        this.responseFormat = responseFormat;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ResponseFormat {

        private Type type = Type.TEXT;

        private String jsonSchema;

        private String name;

        private Boolean strict;

        public ResponseFormat() {
        }

        public ResponseFormat(Type type, String jsonSchema, String name, Boolean strict) {
            this.type = type;
            this.jsonSchema = jsonSchema;
            this.name = name;
            this.strict = strict;
        }

        public Type getType() {
            return this.type;
        }

        public void setType(Type type) {
            this.type = type;
        }

        public String getJsonSchema() {
            return this.jsonSchema;
        }

        public void setJsonSchema(String jsonSchema) {
            this.jsonSchema = jsonSchema;
        }

        public String getName() {
            return this.name;
        }

        public void setName(String name) {
            this.name = name;
        }

        /**
         * 严格模式开关，缺省由请求构建期补成开启。
         *
         * 与工具调用的 strict 是两个独立字段，这里只作用于 {@code response_format.json_schema}。
         */
        public Boolean getStrict() {
            return this.strict;
        }

        public void setStrict(Boolean strict) {
            this.strict = strict;
        }

        @Override
        public boolean equals(@Nullable Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ResponseFormat that = (ResponseFormat) o;
            return this.type == that.type && Objects.equals(this.jsonSchema, that.jsonSchema)
                    && Objects.equals(this.name, that.name) && Objects.equals(this.strict, that.strict);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.type, this.jsonSchema, this.name, this.strict);
        }

        @Override
        public String toString() {
            return "ResponseFormat [type=" + this.type + ", jsonSchema=" + this.jsonSchema + ", name=" + this.name
                    + ", strict=" + this.strict + "]";
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {

            private final ResponseFormat responseFormat = new ResponseFormat();

            public Builder type(Type type) {
                this.responseFormat.setType(type);
                return this;
            }

            /**
             * 设置 schema 字符串，同时把类型切到 json_schema，省去一次显式调用。
             */
            public Builder jsonSchema(String jsonSchema) {
                this.responseFormat.setJsonSchema(jsonSchema);
                this.responseFormat.setType(Type.JSON_SCHEMA);
                return this;
            }

            public Builder name(String name) {
                this.responseFormat.setName(name);
                return this;
            }

            public Builder strict(Boolean strict) {
                this.responseFormat.setStrict(strict);
                return this;
            }

            public ResponseFormat build() {
                return this.responseFormat;
            }

        }

    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String getModel() {
        return this.model;
    }

    @Override
    public @Nullable Double getTemperature() {
        return this.temperature;
    }

    public @Nullable Boolean getStream() {
        return this.stream;
    }

    public OpenRouterApi.@Nullable Reasoning getReasoning() {
        return this.reasoning;
    }

    @Override
    public @Nullable Integer getMaxTokens() {
        return this.maxTokens;
    }

    @Override
    public @Nullable Double getFrequencyPenalty() {
        return this.frequencyPenalty;
    }

    @Override
    public @Nullable Double getPresencePenalty() {
        return this.presencePenalty;
    }

    @Override
    public @Nullable Double getTopP() {
        return this.topP;
    }

    @Override
    public @Nullable Integer getTopK() {
        return this.topK;
    }

    public @Nullable Integer getSeed() {
        return this.seed;
    }

    public @Nullable Double getMinP() {
        return this.minP;
    }

    public @Nullable Double getTopA() {
        return this.topA;
    }

    public @Nullable Double getRepetitionPenalty() {
        return this.repetitionPenalty;
    }

    @Override
    public @Nullable List<String> getStopSequences() {
        return this.stop;
    }

    public @Nullable Map<String, Number> getLogitBias() {
        return this.logitBias;
    }

    public @Nullable Integer getTopLogprobs() {
        return this.topLogprobs;
    }

    public @Nullable Boolean getLogprobs() {
        return this.logprobs;
    }

    public @Nullable List<String> getModels() {
        return this.models;
    }

    public @Nullable List<String> getModalities() {
        return this.modalities;
    }

    public @Nullable String getServiceTier() {
        return this.serviceTier;
    }

    public @Nullable String getUser() {
        return this.user;
    }

    public @Nullable Boolean getParallelToolCalls() {
        return this.parallelToolCalls;
    }

    public @Nullable String getPromptCacheKey() {
        return this.promptCacheKey;
    }

    public @Nullable String getSessionId() {
        return this.sessionId;
    }

    public OpenRouterApi.@Nullable ProviderPreferences getProvider() {
        return this.provider;
    }

    public @Nullable List<OpenRouterApi.FunctionTool> getTools() {
        return this.tools;
    }

    public @Nullable Object getToolChoice() {
        return this.toolChoice;
    }

    public @Nullable Map<String, Object> getExtraBody() {
        return this.extraBody;
    }

    public @Nullable ResponseFormat getResponseFormat() {
        return this.responseFormat;
    }

    /**
     * 结构化输出的 schema 字符串，由 {@code responseFormat} 派生，不单独存一份。
     */
    @Override
    public @Nullable String getOutputSchema() {
        return this.responseFormat != null ? this.responseFormat.getJsonSchema() : null;
    }

    @Override
    @JsonIgnore
    public @Nullable List<ToolCallback> getToolCallbacks() {
        return this.toolCallbacks;
    }

    @Override
    @JsonIgnore
    public @Nullable Map<String, Object> getToolContext() {
        return this.toolContext;
    }

    @Override
    public Builder mutate() {
        return OpenRouterChatOptions.builder()
                // ChatOptions
                .model(this.model)
                .temperature(this.temperature)
                .stream(this.stream)
                .reasoning(this.reasoning)
                .maxTokens(this.maxTokens)
                .frequencyPenalty(this.frequencyPenalty)
                .presencePenalty(this.presencePenalty)
                .topP(this.topP)
                .topK(this.topK)
                .seed(this.seed)
                .minP(this.minP)
                .topA(this.topA)
                .repetitionPenalty(this.repetitionPenalty)
                .stop(this.stop)
                .logitBias(this.logitBias)
                .topLogprobs(this.topLogprobs)
                .logprobs(this.logprobs)
                .models(this.models)
                .modalities(this.modalities)
                .serviceTier(this.serviceTier)
                .user(this.user)
                .parallelToolCalls(this.parallelToolCalls)
                .promptCacheKey(this.promptCacheKey)
                .sessionId(this.sessionId)
                .provider(this.provider)
                // ToolCallingChatOptions
                .tools(this.tools)
                .toolChoice(this.toolChoice)
                .extraBody(this.extraBody)
                .toolCallbacks(this.toolCallbacks)
                .toolContext(this.toolContext)
                // StructuredOutputChatOptions
                .responseFormat(this.responseFormat);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.model, this.temperature, this.stream, this.reasoning, this.maxTokens,
                this.frequencyPenalty, this.presencePenalty, this.topP, this.topK, this.seed, this.minP, this.topA,
                this.repetitionPenalty, this.stop, this.logitBias, this.topLogprobs, this.logprobs, this.models,
                this.modalities, this.serviceTier, this.user, this.parallelToolCalls, this.promptCacheKey,
                this.sessionId, this.provider, this.tools, this.toolChoice, this.extraBody, this.toolCallbacks,
                this.toolContext, this.responseFormat);
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        OpenRouterChatOptions that = (OpenRouterChatOptions) o;
        return Objects.equals(this.model, that.model) && Objects.equals(this.temperature, that.temperature)
                && Objects.equals(this.stream, that.stream) && Objects.equals(this.reasoning, that.reasoning)
                && Objects.equals(this.maxTokens, that.maxTokens)
                && Objects.equals(this.frequencyPenalty, that.frequencyPenalty)
                && Objects.equals(this.presencePenalty, that.presencePenalty)
                && Objects.equals(this.topP, that.topP) && Objects.equals(this.topK, that.topK)
                && Objects.equals(this.seed, that.seed) && Objects.equals(this.minP, that.minP)
                && Objects.equals(this.topA, that.topA)
                && Objects.equals(this.repetitionPenalty, that.repetitionPenalty)
                && Objects.equals(this.stop, that.stop) && Objects.equals(this.logitBias, that.logitBias)
                && Objects.equals(this.topLogprobs, that.topLogprobs)
                && Objects.equals(this.logprobs, that.logprobs) && Objects.equals(this.models, that.models)
                && Objects.equals(this.modalities, that.modalities)
                && Objects.equals(this.serviceTier, that.serviceTier) && Objects.equals(this.user, that.user)
                && Objects.equals(this.parallelToolCalls, that.parallelToolCalls)
                && Objects.equals(this.promptCacheKey, that.promptCacheKey)
                && Objects.equals(this.sessionId, that.sessionId) && Objects.equals(this.provider, that.provider)
                && Objects.equals(this.tools, that.tools) && Objects.equals(this.toolChoice, that.toolChoice)
                && Objects.equals(this.extraBody, that.extraBody)
                && Objects.equals(this.toolCallbacks, that.toolCallbacks)
                && Objects.equals(this.toolContext, that.toolContext)
                && Objects.equals(this.responseFormat, that.responseFormat);
    }

    @Override
    public String toString() {
        return "OpenRouterChatOptions [model=" + this.model + ", temperature=" + this.temperature + ", stream="
                + this.stream + ", reasoning=" + this.reasoning + ", maxTokens=" + this.maxTokens
                + ", frequencyPenalty=" + this.frequencyPenalty + ", presencePenalty=" + this.presencePenalty
                + ", topP=" + this.topP + ", topK=" + this.topK + ", seed=" + this.seed + ", minP=" + this.minP
                + ", topA=" + this.topA + ", repetitionPenalty=" + this.repetitionPenalty + ", stop=" + this.stop
                + ", logitBias=" + this.logitBias + ", topLogprobs=" + this.topLogprobs + ", logprobs="
                + this.logprobs + ", models=" + this.models + ", modalities=" + this.modalities + ", serviceTier="
                + this.serviceTier + ", user=" + this.user
                + ", parallelToolCalls=" + this.parallelToolCalls + ", promptCacheKey=" + this.promptCacheKey
                + ", sessionId=" + this.sessionId + ", provider=" + this.provider + ", tools=" + this.tools
                + ", toolChoice=" + this.toolChoice + ", extraBody=" + this.extraBody + ", toolCallbacks="
                + this.toolCallbacks + ", toolContext=" + this.toolContext + ", responseFormat="
                + this.responseFormat + "]";
    }

    public static class Builder extends AbstractBuilder<Builder> {

    }

    protected abstract static class AbstractBuilder<B extends AbstractBuilder<B>>
            extends DefaultToolCallingChatOptions.Builder<B> implements StructuredOutputChatOptions.Builder<B> {

        protected @Nullable String model;

        protected @Nullable Double temperature;

        protected @Nullable Boolean stream;

        protected OpenRouterApi.@Nullable Reasoning reasoning;

        protected @Nullable Integer maxTokens;

        protected @Nullable Double frequencyPenalty;

        protected @Nullable Double presencePenalty;

        protected @Nullable Double topP;

        protected @Nullable Integer topK;

        protected @Nullable Integer seed;

        protected @Nullable Double minP;

        protected @Nullable Double topA;

        protected @Nullable Double repetitionPenalty;

        protected @Nullable List<String> stop;

        protected @Nullable Map<String, Number> logitBias;

        protected @Nullable Integer topLogprobs;

        protected @Nullable Boolean logprobs;

        protected @Nullable List<String> models;

        protected @Nullable List<String> modalities;

        protected @Nullable String serviceTier;

        protected @Nullable String user;

        protected @Nullable Boolean parallelToolCalls;

        protected @Nullable String promptCacheKey;

        protected @Nullable String sessionId;

        protected OpenRouterApi.@Nullable ProviderPreferences provider;

        protected @Nullable List<OpenRouterApi.FunctionTool> tools;

        protected @Nullable Object toolChoice;

        protected @Nullable Map<String, Object> extraBody;

        protected @Nullable ResponseFormat responseFormat;

        @Override
        public B clone() {
            B copy = super.clone();
            copy.model = this.model;
            copy.temperature = this.temperature;
            copy.stream = this.stream;
            copy.reasoning = this.reasoning;
            copy.maxTokens = this.maxTokens;
            copy.frequencyPenalty = this.frequencyPenalty;
            copy.presencePenalty = this.presencePenalty;
            copy.topP = this.topP;
            copy.topK = this.topK;
            copy.seed = this.seed;
            copy.minP = this.minP;
            copy.topA = this.topA;
            copy.repetitionPenalty = this.repetitionPenalty;
            copy.stop = this.stop;
            copy.logitBias = this.logitBias;
            copy.topLogprobs = this.topLogprobs;
            copy.logprobs = this.logprobs;
            copy.models = this.models;
            copy.modalities = this.modalities;
            copy.serviceTier = this.serviceTier;
            copy.user = this.user;
            copy.parallelToolCalls = this.parallelToolCalls;
            copy.promptCacheKey = this.promptCacheKey;
            copy.sessionId = this.sessionId;
            copy.provider = this.provider;
            copy.tools = this.tools;
            copy.toolChoice = this.toolChoice;
            copy.extraBody = this.extraBody;
            copy.responseFormat = this.responseFormat;
            return copy;
        }

        @Override
        public B model(@Nullable String model) {
            this.model = model;
            return self();
        }

        public B model(OpenRouterApi.@Nullable ChatModel model) {
            this.model = (model != null) ? model.getValue() : null;
            return self();
        }

        public B temperature(@Nullable Double temperature) {
            this.temperature = temperature;
            return self();
        }

        public B stream(@Nullable Boolean stream) {
            this.stream = stream;
            return self();
        }

        public B reasoning(OpenRouterApi.@Nullable Reasoning reasoning) {
            this.reasoning = reasoning;
            return self();
        }

        @Override
        public B maxTokens(@Nullable Integer maxTokens) {
            this.maxTokens = maxTokens;
            return self();
        }

        @Override
        public B frequencyPenalty(@Nullable Double frequencyPenalty) {
            this.frequencyPenalty = frequencyPenalty;
            return self();
        }

        @Override
        public B presencePenalty(@Nullable Double presencePenalty) {
            this.presencePenalty = presencePenalty;
            return self();
        }

        @Override
        public B topP(@Nullable Double topP) {
            this.topP = topP;
            return self();
        }

        @Override
        public B topK(@Nullable Integer topK) {
            this.topK = topK;
            return self();
        }

        public B seed(@Nullable Integer seed) {
            this.seed = seed;
            return self();
        }

        public B minP(@Nullable Double minP) {
            this.minP = minP;
            return self();
        }

        public B topA(@Nullable Double topA) {
            this.topA = topA;
            return self();
        }

        public B repetitionPenalty(@Nullable Double repetitionPenalty) {
            this.repetitionPenalty = repetitionPenalty;
            return self();
        }

        @Override
        public B stopSequences(@Nullable List<String> stop) {
            // 规范里数组分支声明了 maxItems: 4，描述也写 up to 4
            Assert.isTrue(stop == null || stop.size() <= 4, "stop must contain at most 4 sequences");
            this.stop = stop;
            return self();
        }

        public B stop(@Nullable List<String> stop) {
            return stopSequences(stop);
        }

        /**
         * token 级偏置，键是 token id 的字符串形式，值域 -100 到 100。
         */
        public B logitBias(@Nullable Map<String, Number> logitBias) {
            this.logitBias = logitBias;
            return self();
        }

        public B topLogprobs(@Nullable Integer topLogprobs) {
            this.topLogprobs = topLogprobs;
            return self();
        }

        public B logprobs(@Nullable Boolean logprobs) {
            this.logprobs = logprobs;
            return self();
        }

        public B models(@Nullable List<String> models) {
            // 规范里 ChatModelNames 声明了 maxItems: 3
            Assert.isTrue(models == null || models.size() <= 3, "models must contain at most 3 entries");
            this.models = models;
            return self();
        }

        public B modalities(@Nullable List<String> modalities) {
            this.modalities = modalities;
            return self();
        }

        public B serviceTier(@Nullable String serviceTier) {
            this.serviceTier = serviceTier;
            return self();
        }

        public B user(@Nullable String user) {
            this.user = user;
            return self();
        }

        public B parallelToolCalls(@Nullable Boolean parallelToolCalls) {
            this.parallelToolCalls = parallelToolCalls;
            return self();
        }

        public B promptCacheKey(@Nullable String promptCacheKey) {
            this.promptCacheKey = promptCacheKey;
            return self();
        }

        public B sessionId(@Nullable String sessionId) {
            this.sessionId = sessionId;
            return self();
        }

        public B provider(OpenRouterApi.@Nullable ProviderPreferences provider) {
            this.provider = provider;
            return self();
        }

        public B tools(@Nullable List<OpenRouterApi.FunctionTool> tools) {
            this.tools = tools;
            return self();
        }

        public B toolChoice(@Nullable Object toolChoice) {
            this.toolChoice = toolChoice;
            return self();
        }

        /**
         * 低频字段原样透传到请求体顶层，不做类型建模。
         */
        public B extraBody(@Nullable Map<String, Object> extraBody) {
            this.extraBody = extraBody;
            return self();
        }

        /**
         * 直接给出完整的响应格式，三种类型（text / json_object / json_schema）都能表达。
         */
        public B responseFormat(@Nullable ResponseFormat responseFormat) {
            this.responseFormat = responseFormat;
            return self();
        }

        /**
         * 只给 schema 字符串时，自动补成 {@code json_schema} 类型的响应格式。
         *
         * 传 null 或空白表示清空响应格式，回到不限制的状态。
         */
        @Override
        public B outputSchema(@Nullable String outputSchema) {
            if (!StringUtils.hasText(outputSchema)) {
                this.responseFormat = null;
            }
            else {
                this.responseFormat = ResponseFormat.builder()
                    .type(Type.JSON_SCHEMA)
                    .jsonSchema(outputSchema)
                    .build();
            }
            return self();
        }

        @Override
        public B combineWith(ChatOptions.Builder<?> other) {
            super.combineWith(other);
            if (other instanceof AbstractBuilder<?> that) {
                this.model = that.model != null ? that.model : this.model;
                this.temperature = that.temperature != null ? that.temperature : this.temperature;
                this.stream = that.stream != null ? that.stream : this.stream;
                this.reasoning = that.reasoning != null ? that.reasoning : this.reasoning;
                this.maxTokens = that.maxTokens != null ? that.maxTokens : this.maxTokens;
                this.frequencyPenalty = that.frequencyPenalty != null ? that.frequencyPenalty : this.frequencyPenalty;
                this.presencePenalty = that.presencePenalty != null ? that.presencePenalty : this.presencePenalty;
                this.topP = that.topP != null ? that.topP : this.topP;
                this.topK = that.topK != null ? that.topK : this.topK;
                this.seed = that.seed != null ? that.seed : this.seed;
                this.minP = that.minP != null ? that.minP : this.minP;
                this.topA = that.topA != null ? that.topA : this.topA;
                this.repetitionPenalty = that.repetitionPenalty != null ? that.repetitionPenalty
                        : this.repetitionPenalty;
                this.stop = that.stop != null ? that.stop : this.stop;
                this.logitBias = that.logitBias != null ? that.logitBias : this.logitBias;
                this.topLogprobs = that.topLogprobs != null ? that.topLogprobs : this.topLogprobs;
                this.logprobs = that.logprobs != null ? that.logprobs : this.logprobs;
                this.models = that.models != null ? that.models : this.models;
                this.modalities = that.modalities != null ? that.modalities : this.modalities;
                this.serviceTier = that.serviceTier != null ? that.serviceTier : this.serviceTier;
                this.user = that.user != null ? that.user : this.user;
                this.parallelToolCalls = that.parallelToolCalls != null ? that.parallelToolCalls
                        : this.parallelToolCalls;
                this.promptCacheKey = that.promptCacheKey != null ? that.promptCacheKey : this.promptCacheKey;
                this.sessionId = that.sessionId != null ? that.sessionId : this.sessionId;
                this.provider = that.provider != null ? that.provider : this.provider;
                this.toolChoice = that.toolChoice != null ? that.toolChoice : this.toolChoice;
                this.responseFormat = that.responseFormat != null ? that.responseFormat : this.responseFormat;
                if (that.tools != null) {
                    if (this.tools == null) {
                        this.tools = new ArrayList<>(that.tools);
                    }
                    else {
                        List<OpenRouterApi.FunctionTool> merged = new ArrayList<>(this.tools);
                        merged.addAll(that.tools);
                        this.tools = merged;
                    }
                }
                if (that.extraBody != null) {
                    if (this.extraBody == null) {
                        this.extraBody = new HashMap<>(that.extraBody);
                    }
                    else {
                        Map<String, Object> merged = new HashMap<>(this.extraBody);
                        merged.putAll(that.extraBody);
                        this.extraBody = merged;
                    }
                }
            }
            return self();
        }

        @Override
        public OpenRouterChatOptions build() {
            return new OpenRouterChatOptions(this.model, this.temperature, this.stream, this.reasoning, this.maxTokens,
                    this.frequencyPenalty, this.presencePenalty, this.topP, this.topK, this.seed, this.minP, this.topA,
                    this.repetitionPenalty, this.stop, this.logitBias, this.topLogprobs, this.logprobs, this.models,
                    this.modalities, this.serviceTier, this.user, this.parallelToolCalls, this.promptCacheKey,
                    this.sessionId, this.provider, this.tools, this.toolChoice, this.extraBody, this.toolCallbacks,
                    this.toolContext, this.responseFormat);
        }

    }

}
