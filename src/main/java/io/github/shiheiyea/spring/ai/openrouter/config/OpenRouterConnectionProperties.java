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

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author: heiye
 * @date: 2026/04/17 22:31
 * @version: v1.0.0
 * @description: OpenRouter 链接配置类, 该配置作为兜底配置，优先级低于 OpenRouterChatProperties
 */
@ConfigurationProperties(OpenRouterConnectionProperties.CONFIG_PREFIX)
public class OpenRouterConnectionProperties extends OpenRouterParentProperties{
    public static final String CONFIG_PREFIX = "spring.ai.openrouter";
    public static final String DEFAULT_BASE_URL = "https://openrouter.ai";

    public OpenRouterConnectionProperties() {
        super.setBaseUrl(DEFAULT_BASE_URL);
    }
}
