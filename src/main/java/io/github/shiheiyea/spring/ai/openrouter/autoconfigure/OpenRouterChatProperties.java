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

package io.github.shiheiyea.spring.ai.openrouter.autoconfigure;

import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.DeprecatedConfigurationProperty;

import io.github.shiheiyea.spring.ai.openrouter.OpenRouterChatOptions;
import io.github.shiheiyea.spring.ai.openrouter.api.OpenRouterApi;
import io.github.shiheiyea.spring.ai.openrouter.api.common.OpenRouterConstants;

@ConfigurationProperties(OpenRouterChatProperties.CONFIG_PREFIX)
public class OpenRouterChatProperties extends OpenRouterParentProperties {

    public static final String CONFIG_PREFIX = "spring.ai.openrouter.chat";

    public static final String DEFAULT_COMPLETIONS_PATH = OpenRouterConstants.DEFAULT_COMPLETIONS_PATH;

    /**
     * 是否启用 OpenRouter 聊天客户端。
     */
    private boolean enabled = true;

    private String completionsPath = DEFAULT_COMPLETIONS_PATH;

    private @Nullable String model;

    private @Nullable Double temperature;

    private @Nullable Boolean stream;

    private @Nullable Integer maxTokens;

    private @Nullable Double frequencyPenalty;

    private @Nullable Double presencePenalty;

    private @Nullable Double topP;

    private @Nullable Integer topK;

    private @Nullable Integer seed;

    private @Nullable Double minP;

    private @Nullable Double topA;

    private @Nullable Double repetitionPenalty;

    private @Nullable List<String> stop;

    private @Nullable Integer topLogprobs;

    private @Nullable Boolean logprobs;

    private @Nullable List<String> models;

    private @Nullable List<String> modalities;

    private @Nullable String serviceTier;

    private @Nullable String user;

    private @Nullable Boolean parallelToolCalls;

    private @Nullable String promptCacheKey;

    private @Nullable String sessionId;

    private @Nullable Boolean strict;

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getCompletionsPath() {
        return this.completionsPath;
    }

    public void setCompletionsPath(String completionsPath) {
        this.completionsPath = completionsPath;
    }

    public @Nullable String getModel() {
        return this.model;
    }

    public void setModel(@Nullable String model) {
        this.model = model;
    }

    public @Nullable Double getTemperature() {
        return this.temperature;
    }

    public void setTemperature(@Nullable Double temperature) {
        this.temperature = temperature;
    }

    public @Nullable Boolean getStream() {
        return this.stream;
    }

    public void setStream(@Nullable Boolean stream) {
        this.stream = stream;
    }

    public @Nullable Integer getMaxTokens() {
        return this.maxTokens;
    }

    public void setMaxTokens(@Nullable Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    public @Nullable Double getFrequencyPenalty() {
        return this.frequencyPenalty;
    }

    public void setFrequencyPenalty(@Nullable Double frequencyPenalty) {
        this.frequencyPenalty = frequencyPenalty;
    }

    public @Nullable Double getPresencePenalty() {
        return this.presencePenalty;
    }

    public void setPresencePenalty(@Nullable Double presencePenalty) {
        this.presencePenalty = presencePenalty;
    }

    public @Nullable Double getTopP() {
        return this.topP;
    }

    public void setTopP(@Nullable Double topP) {
        this.topP = topP;
    }

    public @Nullable Integer getTopK() {
        return this.topK;
    }

    public void setTopK(@Nullable Integer topK) {
        this.topK = topK;
    }

    public @Nullable Integer getSeed() {
        return this.seed;
    }

    public void setSeed(@Nullable Integer seed) {
        this.seed = seed;
    }

    public @Nullable Double getMinP() {
        return this.minP;
    }

    public void setMinP(@Nullable Double minP) {
        this.minP = minP;
    }

    public @Nullable Double getTopA() {
        return this.topA;
    }

    public void setTopA(@Nullable Double topA) {
        this.topA = topA;
    }

    public @Nullable Double getRepetitionPenalty() {
        return this.repetitionPenalty;
    }

    public void setRepetitionPenalty(@Nullable Double repetitionPenalty) {
        this.repetitionPenalty = repetitionPenalty;
    }

    public @Nullable List<String> getStop() {
        return this.stop;
    }

    public void setStop(@Nullable List<String> stop) {
        this.stop = stop;
    }

    public @Nullable Integer getTopLogprobs() {
        return this.topLogprobs;
    }

    public void setTopLogprobs(@Nullable Integer topLogprobs) {
        this.topLogprobs = topLogprobs;
    }

    public @Nullable Boolean getLogprobs() {
        return this.logprobs;
    }

    public void setLogprobs(@Nullable Boolean logprobs) {
        this.logprobs = logprobs;
    }

    public @Nullable List<String> getModels() {
        return this.models;
    }

    public void setModels(@Nullable List<String> models) {
        this.models = models;
    }

    public @Nullable List<String> getModalities() {
        return this.modalities;
    }

    public void setModalities(@Nullable List<String> modalities) {
        this.modalities = modalities;
    }

    public @Nullable String getServiceTier() {
        return this.serviceTier;
    }

    public void setServiceTier(@Nullable String serviceTier) {
        this.serviceTier = serviceTier;
    }

    public @Nullable String getUser() {
        return this.user;
    }

    public void setUser(@Nullable String user) {
        this.user = user;
    }

    public @Nullable Boolean getParallelToolCalls() {
        return this.parallelToolCalls;
    }

    public void setParallelToolCalls(@Nullable Boolean parallelToolCalls) {
        this.parallelToolCalls = parallelToolCalls;
    }

    public @Nullable String getPromptCacheKey() {
        return this.promptCacheKey;
    }

    public void setPromptCacheKey(@Nullable String promptCacheKey) {
        this.promptCacheKey = promptCacheKey;
    }

    public @Nullable String getSessionId() {
        return this.sessionId;
    }

    public void setSessionId(@Nullable String sessionId) {
        this.sessionId = sessionId;
    }

    public @Nullable Boolean getStrict() {
        return this.strict;
    }

    public void setStrict(@Nullable Boolean strict) {
        this.strict = strict;
    }

    /**
     * 把配置组装成运行期选项。
     *
     * 标量与结构字段最终都落在同一份选项上：标量既可写扁平键也可写
     * {@code spring.ai.openrouter.chat.options.*}，结构字段只在后者里提供。
     */
    public OpenRouterChatOptions toOptions() {
        return currentOptions().mutate()
                .model(this.model)
                .temperature(this.temperature)
                .stream(this.stream)
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
                .topLogprobs(this.topLogprobs)
                .logprobs(this.logprobs)
                .models(this.models)
                .modalities(this.modalities)
                .serviceTier(this.serviceTier)
                .user(this.user)
                .parallelToolCalls(this.parallelToolCalls)
                .promptCacheKey(this.promptCacheKey)
                .sessionId(this.sessionId)
                .strict(this.strict)
                .build();
    }

    private OpenRouterChatOptions currentOptions() {
        return (this.chatOptions != null) ? this.chatOptions : OpenRouterChatOptions.builder().build();
    }

    private void updateOptions(java.util.function.UnaryOperator<OpenRouterChatOptions.Builder> customizer) {
        this.chatOptions = customizer.apply(currentOptions().mutate()).build();
    }

    /**
     * 结构字段（推理、路由偏好、工具选择、extraBody）的存储位置，扁平键没有对应项。
     */
    private OpenRouterChatOptions chatOptions = OpenRouterChatOptions.builder().build();

    /**
     * 兼容 1.1.0 的 {@code spring.ai.openrouter.chat.options.*} 写法，新写法请改用扁平键。
     */
    @DeprecatedConfigurationProperty(replacement = CONFIG_PREFIX)
    @Deprecated(since = "2.0.0", forRemoval = true)
    public Options getOptions() {
        return this.options;
    }

    public void setOptions(Options options) {
        this.options = options;
    }

    private Options options = new Options();

    public class Options {

        @DeprecatedConfigurationProperty(replacement = CONFIG_PREFIX + ".model")
        @Deprecated(since = "2.0.0", forRemoval = true)
        public @Nullable String getModel() {
            return OpenRouterChatProperties.this.getModel();
        }

        public void setModel(@Nullable String model) {
            OpenRouterChatProperties.this.setModel(model);
        }

        @DeprecatedConfigurationProperty(replacement = CONFIG_PREFIX + ".temperature")
        @Deprecated(since = "2.0.0", forRemoval = true)
        public @Nullable Double getTemperature() {
            return OpenRouterChatProperties.this.getTemperature();
        }

        public void setTemperature(@Nullable Double temperature) {
            OpenRouterChatProperties.this.setTemperature(temperature);
        }

        @DeprecatedConfigurationProperty(replacement = CONFIG_PREFIX + ".stream")
        @Deprecated(since = "2.0.0", forRemoval = true)
        public @Nullable Boolean getStream() {
            return OpenRouterChatProperties.this.getStream();
        }

        public void setStream(@Nullable Boolean stream) {
            OpenRouterChatProperties.this.setStream(stream);
        }

        @DeprecatedConfigurationProperty(replacement = CONFIG_PREFIX + ".reasoning")
        @Deprecated(since = "2.0.0", forRemoval = true)
        public OpenRouterApi.@Nullable Reasoning getReasoning() {
            return OpenRouterChatProperties.this.currentOptions().getReasoning();
        }

        public void setReasoning(OpenRouterApi.@Nullable Reasoning reasoning) {
            OpenRouterChatProperties.this.updateOptions(builder -> builder.reasoning(reasoning));
        }

        @DeprecatedConfigurationProperty(replacement = CONFIG_PREFIX + ".max-tokens")
        @Deprecated(since = "2.0.0", forRemoval = true)
        public @Nullable Integer getMaxTokens() {
            return OpenRouterChatProperties.this.getMaxTokens();
        }

        public void setMaxTokens(@Nullable Integer maxTokens) {
            OpenRouterChatProperties.this.setMaxTokens(maxTokens);
        }

        @DeprecatedConfigurationProperty(replacement = CONFIG_PREFIX + ".frequency-penalty")
        @Deprecated(since = "2.0.0", forRemoval = true)
        public @Nullable Double getFrequencyPenalty() {
            return OpenRouterChatProperties.this.getFrequencyPenalty();
        }

        public void setFrequencyPenalty(@Nullable Double frequencyPenalty) {
            OpenRouterChatProperties.this.setFrequencyPenalty(frequencyPenalty);
        }

        @DeprecatedConfigurationProperty(replacement = CONFIG_PREFIX + ".presence-penalty")
        @Deprecated(since = "2.0.0", forRemoval = true)
        public @Nullable Double getPresencePenalty() {
            return OpenRouterChatProperties.this.getPresencePenalty();
        }

        public void setPresencePenalty(@Nullable Double presencePenalty) {
            OpenRouterChatProperties.this.setPresencePenalty(presencePenalty);
        }

        @DeprecatedConfigurationProperty(replacement = CONFIG_PREFIX + ".top-p")
        @Deprecated(since = "2.0.0", forRemoval = true)
        public @Nullable Double getTopP() {
            return OpenRouterChatProperties.this.getTopP();
        }

        public void setTopP(@Nullable Double topP) {
            OpenRouterChatProperties.this.setTopP(topP);
        }

        @DeprecatedConfigurationProperty(replacement = CONFIG_PREFIX + ".top-k")
        @Deprecated(since = "2.0.0", forRemoval = true)
        public @Nullable Integer getTopK() {
            return OpenRouterChatProperties.this.getTopK();
        }

        public void setTopK(@Nullable Integer topK) {
            OpenRouterChatProperties.this.setTopK(topK);
        }

        @DeprecatedConfigurationProperty(replacement = CONFIG_PREFIX + ".seed")
        @Deprecated(since = "2.0.0", forRemoval = true)
        public @Nullable Integer getSeed() {
            return OpenRouterChatProperties.this.getSeed();
        }

        public void setSeed(@Nullable Integer seed) {
            OpenRouterChatProperties.this.setSeed(seed);
        }

        @DeprecatedConfigurationProperty(replacement = CONFIG_PREFIX + ".min-p")
        @Deprecated(since = "2.0.0", forRemoval = true)
        public @Nullable Double getMinP() {
            return OpenRouterChatProperties.this.getMinP();
        }

        public void setMinP(@Nullable Double minP) {
            OpenRouterChatProperties.this.setMinP(minP);
        }

        @DeprecatedConfigurationProperty(replacement = CONFIG_PREFIX + ".top-a")
        @Deprecated(since = "2.0.0", forRemoval = true)
        public @Nullable Double getTopA() {
            return OpenRouterChatProperties.this.getTopA();
        }

        public void setTopA(@Nullable Double topA) {
            OpenRouterChatProperties.this.setTopA(topA);
        }

        @DeprecatedConfigurationProperty(replacement = CONFIG_PREFIX + ".repetition-penalty")
        @Deprecated(since = "2.0.0", forRemoval = true)
        public @Nullable Double getRepetitionPenalty() {
            return OpenRouterChatProperties.this.getRepetitionPenalty();
        }

        public void setRepetitionPenalty(@Nullable Double repetitionPenalty) {
            OpenRouterChatProperties.this.setRepetitionPenalty(repetitionPenalty);
        }

        @DeprecatedConfigurationProperty(replacement = CONFIG_PREFIX + ".stop")
        @Deprecated(since = "2.0.0", forRemoval = true)
        public @Nullable List<String> getStop() {
            return OpenRouterChatProperties.this.getStop();
        }

        public void setStop(@Nullable List<String> stop) {
            OpenRouterChatProperties.this.setStop(stop);
        }

        @DeprecatedConfigurationProperty(replacement = CONFIG_PREFIX + ".top-logprobs")
        @Deprecated(since = "2.0.0", forRemoval = true)
        public @Nullable Integer getTopLogprobs() {
            return OpenRouterChatProperties.this.getTopLogprobs();
        }

        public void setTopLogprobs(@Nullable Integer topLogprobs) {
            OpenRouterChatProperties.this.setTopLogprobs(topLogprobs);
        }

        @DeprecatedConfigurationProperty(replacement = CONFIG_PREFIX + ".logprobs")
        @Deprecated(since = "2.0.0", forRemoval = true)
        public @Nullable Boolean getLogprobs() {
            return OpenRouterChatProperties.this.getLogprobs();
        }

        public void setLogprobs(@Nullable Boolean logprobs) {
            OpenRouterChatProperties.this.setLogprobs(logprobs);
        }

        @DeprecatedConfigurationProperty(replacement = CONFIG_PREFIX + ".models")
        @Deprecated(since = "2.0.0", forRemoval = true)
        public @Nullable List<String> getModels() {
            return OpenRouterChatProperties.this.getModels();
        }

        public void setModels(@Nullable List<String> models) {
            OpenRouterChatProperties.this.setModels(models);
        }

        @DeprecatedConfigurationProperty(replacement = CONFIG_PREFIX + ".modalities")
        @Deprecated(since = "2.0.0", forRemoval = true)
        public @Nullable List<String> getModalities() {
            return OpenRouterChatProperties.this.getModalities();
        }

        public void setModalities(@Nullable List<String> modalities) {
            OpenRouterChatProperties.this.setModalities(modalities);
        }

        @DeprecatedConfigurationProperty(replacement = CONFIG_PREFIX + ".service-tier")
        @Deprecated(since = "2.0.0", forRemoval = true)
        public @Nullable String getServiceTier() {
            return OpenRouterChatProperties.this.getServiceTier();
        }

        public void setServiceTier(@Nullable String serviceTier) {
            OpenRouterChatProperties.this.setServiceTier(serviceTier);
        }

        @DeprecatedConfigurationProperty(replacement = CONFIG_PREFIX + ".user")
        @Deprecated(since = "2.0.0", forRemoval = true)
        public @Nullable String getUser() {
            return OpenRouterChatProperties.this.getUser();
        }

        public void setUser(@Nullable String user) {
            OpenRouterChatProperties.this.setUser(user);
        }

        @DeprecatedConfigurationProperty(replacement = CONFIG_PREFIX + ".parallel-tool-calls")
        @Deprecated(since = "2.0.0", forRemoval = true)
        public @Nullable Boolean getParallelToolCalls() {
            return OpenRouterChatProperties.this.getParallelToolCalls();
        }

        public void setParallelToolCalls(@Nullable Boolean parallelToolCalls) {
            OpenRouterChatProperties.this.setParallelToolCalls(parallelToolCalls);
        }

        @DeprecatedConfigurationProperty(replacement = CONFIG_PREFIX + ".prompt-cache-key")
        @Deprecated(since = "2.0.0", forRemoval = true)
        public @Nullable String getPromptCacheKey() {
            return OpenRouterChatProperties.this.getPromptCacheKey();
        }

        public void setPromptCacheKey(@Nullable String promptCacheKey) {
            OpenRouterChatProperties.this.setPromptCacheKey(promptCacheKey);
        }

        @DeprecatedConfigurationProperty(replacement = CONFIG_PREFIX + ".session-id")
        @Deprecated(since = "2.0.0", forRemoval = true)
        public @Nullable String getSessionId() {
            return OpenRouterChatProperties.this.getSessionId();
        }

        public void setSessionId(@Nullable String sessionId) {
            OpenRouterChatProperties.this.setSessionId(sessionId);
        }

        @DeprecatedConfigurationProperty(replacement = CONFIG_PREFIX + ".provider")
        @Deprecated(since = "2.0.0", forRemoval = true)
        public OpenRouterApi.@Nullable ProviderPreferences getProvider() {
            return OpenRouterChatProperties.this.currentOptions().getProvider();
        }

        public void setProvider(OpenRouterApi.@Nullable ProviderPreferences provider) {
            OpenRouterChatProperties.this.updateOptions(builder -> builder.provider(provider));
        }

        @DeprecatedConfigurationProperty(replacement = CONFIG_PREFIX + ".tool-choice")
        @Deprecated(since = "2.0.0", forRemoval = true)
        public @Nullable Object getToolChoice() {
            return OpenRouterChatProperties.this.currentOptions().getToolChoice();
        }

        public void setToolChoice(@Nullable Object toolChoice) {
            OpenRouterChatProperties.this.updateOptions(builder -> builder.toolChoice(toolChoice));
        }

        @DeprecatedConfigurationProperty(replacement = CONFIG_PREFIX + ".extra-body")
        @Deprecated(since = "2.0.0", forRemoval = true)
        public @Nullable Map<String, Object> getExtraBody() {
            return OpenRouterChatProperties.this.currentOptions().getExtraBody();
        }

        public void setExtraBody(@Nullable Map<String, Object> extraBody) {
            OpenRouterChatProperties.this.updateOptions(builder -> builder.extraBody(extraBody));
        }

        @DeprecatedConfigurationProperty(replacement = CONFIG_PREFIX + ".strict")
        @Deprecated(since = "2.0.0", forRemoval = true)
        public @Nullable Boolean getStrict() {
            return OpenRouterChatProperties.this.getStrict();
        }

        public void setStrict(@Nullable Boolean strict) {
            OpenRouterChatProperties.this.setStrict(strict);
        }

    }

}
