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

package io.github.shiheiyea.spring.ai.openrouter.api.common;

public final class OpenRouterConstants {

    public static final String DEFAULT_BASE_URL = "https://openrouter.ai";

    public static final String DEFAULT_COMPLETIONS_PATH = "/api/v1/chat/completions";

    /**
     * 观测链路上报的 provider 名称，与 {@code AiProvider} 的取值风格保持一致（小写单词）。
     */
    public static final String PROVIDER_NAME = "openrouter";

    /**
     * 应用归属头，OpenRouter 用它把用量归到具体应用上做排行榜与统计。
     */
    public static final String APP_REFERER_HEADER = "HTTP-Referer";

    public static final String APP_TITLE_HEADER = "X-OpenRouter-Title";

    public static final String APP_CATEGORIES_HEADER = "X-OpenRouter-Categories";

    private OpenRouterConstants() {
    }

}
