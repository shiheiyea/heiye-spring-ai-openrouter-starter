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

package io.github.shiheiyea.spring.ai.openrouter.config;


import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.model.tool.DefaultToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import io.github.shiheiyea.spring.ai.openrouter.OpenRouterChatModel;
import io.github.shiheiyea.spring.ai.openrouter.api.OpenRouterApi;

import java.util.Objects;

/**
 * @author: heiye
 * @date: 2026/04/17 21:31
 * @version: v1.0.0
 * @description: OpenRouter 自动配置类
 */
@AutoConfiguration(
        after = {
                // 必须在这些配置之后加载，确保 Bean 已创建
                RestClientAutoConfiguration.class,
                WebClientAutoConfiguration.class,
                SpringAiRetryAutoConfiguration.class,
                ToolCallingAutoConfiguration.class
        }
)
// 	条件注解：只有当 classpath 中存在 OpenRouterApi 类时，此配置才会生效（避免类未找到错误）
@ConditionalOnClass({OpenRouterApi.class})
// 启用配置属性绑定，将 application.yml 中的配置映射到 OpenRouterConnectionProperties 和 OpenRouterChatProperties 对象
@EnableConfigurationProperties({OpenRouterConnectionProperties.class, OpenRouterChatProperties.class})
// 当 spring.ai.model.chat = openrouter 时启用；
// matchIfMissing = true 表示如果该属性不存在，默认也启用
@ConditionalOnProperty(
        name = {"spring.ai.model.chat"},
        havingValue = "openrouter",
        matchIfMissing = true
)
public class OpenRouterChatAutoConfiguration {
    @Bean
    // 只有当容器中不存在 OpenRouterChatModel Bean 时才创建
    @ConditionalOnMissingBean
    public OpenRouterChatModel openRouterChatModel(OpenRouterConnectionProperties commonProperties,
                                                   OpenRouterChatProperties chatProperties,
                                                   // 创建 RestClient 和 WebClient 的构建者
                                                   // ObjectProvider 是 Spring 的延迟获取机制，避免 Bean 不存在时抛出异常
                                                   ObjectProvider<RestClient.Builder> restClientBuilderProvider,
                                                   ObjectProvider<WebClient.Builder> webClientBuilderProvider,
                                                   // 创建工具调用管理器
                                                   ToolCallingManager toolCallingManager,
                                                   // 创建重试策略
                                                   RetryTemplate retryTemplate,
                                                   // 创建响应错误处理器
                                                   ResponseErrorHandler responseErrorHandler,
                                                   // 创建 ObservationRegistry
                                                   ObjectProvider<ObservationRegistry> observationRegistry,
                                                   // 创建 ChatModel 观测约定
                                                   ObjectProvider<ChatModelObservationConvention> observationConvention,
                                                   // 创建工具执行资格判断器
                                                   ObjectProvider<ToolExecutionEligibilityPredicate> openRouterToolExecutionEligibilityPredicate) {
        // 先创建底层的 OpenRouterApi 客户端
        OpenRouterApi openRouterApi = openRouterApi(chatProperties, commonProperties,
                // 如果有相关的 Bean 就拿，没有就执行备用方案
                restClientBuilderProvider.getIfAvailable(RestClient::builder),
                webClientBuilderProvider.getIfAvailable(WebClient::builder),
                responseErrorHandler);

        // 使用 Builder 模式构建 ChatModel
        OpenRouterChatModel chatModel = OpenRouterChatModel.builder()
                // 设置 API 客户端
                .openRouterApi(openRouterApi)
                // 默认参数（温度、模型名等）
                .defaultOptions(chatProperties.getOptions())
                // 函数调用管理器
                .toolCallingManager(toolCallingManager)
                // 工具执行资格判断器
                .toolExecutionEligibilityPredicate(openRouterToolExecutionEligibilityPredicate.getIfUnique(DefaultToolExecutionEligibilityPredicate::new))
                // 重试策略
                .retryTemplate(retryTemplate)
                // 监控观测注册表, 依旧用户没配置，则使用默认的 NOOP
                .observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
                .build();

        // 断言
        Objects.requireNonNull(chatModel);

        // 设置 ChatModel 观测约定
        observationConvention.ifAvailable(chatModel::setObservationConvention);

        return chatModel;
    }

    public OpenRouterApi openRouterApi(OpenRouterChatProperties chatProperties, OpenRouterConnectionProperties commonProperties, RestClient.Builder restClientBuilder, WebClient.Builder webClientBuilder, ResponseErrorHandler responseErrorHandler) {
        // 解析 Base URL：优先使用 chatProperties，否则用 commonProperties
        String resolvedBaseUrl = StringUtils.hasText(chatProperties.getBaseUrl()) ? chatProperties.getBaseUrl() : commonProperties.getBaseUrl();
        // 断言
        Assert.hasText(resolvedBaseUrl, "OpenRouter base URL must be set");
        // 配置 OpenRouter 的 ApiKey
        String resolvedApiKey = StringUtils.hasText(chatProperties.getApiKey()) ? chatProperties.getApiKey() : commonProperties.getApiKey();
        // 断言
        Assert.hasText(resolvedApiKey, "OpenRouter API key must be set");

        return OpenRouterApi.builder()
                .baseUrl(resolvedBaseUrl)
                .apiKey(new SimpleApiKey(resolvedApiKey))
                // 配置 completionsPath
                .completionsPath(chatProperties.getCompletionsPath())
                // 配置相关请求类
                .webClientBuilder(webClientBuilder)
                .restClientBuilder(restClientBuilder)
                .responseErrorHandler(responseErrorHandler)
                .build();
    }
}
