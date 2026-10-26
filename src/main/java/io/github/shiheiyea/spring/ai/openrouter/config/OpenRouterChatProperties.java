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
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import io.github.shiheiyea.spring.ai.openrouter.OpenRouterChatOptions;
import io.github.shiheiyea.spring.ai.openrouter.api.OpenRouterApi.ChatModel;

/**
 * @author: heiye
 * @date: 2026/04/18 01:32
 * @version: v1.0.0
 * @description: OpenRouter 聊天配置， 继承 OpenRouterParentProperties
 *              用于在 yml 文件中配置相关参数
 */
// 声明此类绑定配置文件中以 spring.ai.openrouter.chat 为前缀的属性。例如 spring.ai.openrouter.chat.enabled=true 会映射到类的 enabled 字段
@ConfigurationProperties("spring.ai.openrouter.chat")
public class OpenRouterChatProperties extends OpenRouterParentProperties{
    public static final String CONFIG_PREFIX = "spring.ai.openrouter.chat";
    public static final String DEFAULT_CHAT_MODEL;
    private static final Double DEFAULT_TEMPERATURE;
    public static final String DEFAULT_COMPLETIONS_PATH = "/api/v1/chat/completions";
    private boolean enabled = true;
    private String completionsPath = "/api/v1/chat/completions";

    // 标记这是嵌套配置对象
    @NestedConfigurationProperty
    private final OpenRouterChatOptions options;

    public OpenRouterChatProperties() {
        this.options = OpenRouterChatOptions.builder()
                .model(DEFAULT_CHAT_MODEL)
                .temperature(DEFAULT_TEMPERATURE)
                .build();
    }

    static {
        DEFAULT_CHAT_MODEL = ChatModel.GOOGLE_GEMINI_3_FLASH_PREVIEW.getValue();
        DEFAULT_TEMPERATURE = (double)1.0F;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getCompletionsPath() {
        return completionsPath;
    }

    public void setCompletionsPath(String completionsPath) {
        this.completionsPath = completionsPath;
    }

    public OpenRouterChatOptions getOptions() {
        return options;
    }
}
