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

import io.github.shiheiyea.spring.ai.openrouter.OpenRouterChatModel;
import io.github.shiheiyea.spring.ai.openrouter.api.OpenRouterApi;

import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.webclient.autoconfigure.WebClientAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import io.micrometer.observation.ObservationRegistry;

@AutoConfiguration(after = { RestClientAutoConfiguration.class, WebClientAutoConfiguration.class,
        SpringAiRetryAutoConfiguration.class, ToolCallingAutoConfiguration.class })
@ConditionalOnClass({ OpenRouterApi.class })
@EnableConfigurationProperties({ OpenRouterConnectionProperties.class, OpenRouterChatProperties.class })
@ConditionalOnProperty(name = SpringAIModelProperties.CHAT_MODEL, havingValue = "openrouter", matchIfMissing = true)
public class OpenRouterChatAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public OpenRouterChatModel openRouterChatModel(OpenRouterConnectionProperties connectionProperties,
            OpenRouterChatProperties chatProperties, ObjectProvider<RestClient.Builder> restClientBuilderProvider,
            ObjectProvider<WebClient.Builder> webClientBuilderProvider, ToolCallingManager toolCallingManager,
            ObjectProvider<RetryTemplate> retryTemplate, ObjectProvider<ResponseErrorHandler> responseErrorHandler,
            ObjectProvider<ObservationRegistry> observationRegistry,
            ObjectProvider<ChatModelObservationConvention> observationConvention) {
        OpenRouterApi openRouterApi = openRouterApi(chatProperties, connectionProperties,
                restClientBuilderProvider.getIfAvailable(RestClient::builder),
                webClientBuilderProvider.getIfAvailable(WebClient::builder), responseErrorHandler);
        OpenRouterChatModel chatModel = OpenRouterChatModel.builder()
                .openRouterApi(openRouterApi)
                .options(chatProperties.toOptions())
                .toolCallingManager(toolCallingManager)
                .retryTemplate(retryTemplate.getIfUnique(() -> RetryUtils.DEFAULT_RETRY_TEMPLATE))
                .observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
                .build();
        observationConvention.ifAvailable(chatModel::setObservationConvention);
        return chatModel;
    }

    private OpenRouterApi openRouterApi(OpenRouterChatProperties chatProperties,
            OpenRouterConnectionProperties connectionProperties, RestClient.Builder restClientBuilder,
            WebClient.Builder webClientBuilder, ObjectProvider<ResponseErrorHandler> responseErrorHandler) {
        String resolvedBaseUrl = StringUtils.hasText(chatProperties.getBaseUrl()) ? chatProperties.getBaseUrl()
                : connectionProperties.getBaseUrl();
        Assert.hasText(resolvedBaseUrl, "OpenRouter base URL must be set");
        String resolvedApiKey = StringUtils.hasText(chatProperties.getApiKey()) ? chatProperties.getApiKey()
                : connectionProperties.getApiKey();
        Assert.hasText(resolvedApiKey, "OpenRouter API key must be set");
        return OpenRouterApi.builder()
                .baseUrl(resolvedBaseUrl)
                .apiKey(new SimpleApiKey(resolvedApiKey))
                .completionsPath(chatProperties.getCompletionsPath())
                .restClientBuilder(restClientBuilder)
                .webClientBuilder(webClientBuilder)
                .responseErrorHandler(responseErrorHandler.getIfAvailable(
                        () -> RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER))
                .build();
    }

}
