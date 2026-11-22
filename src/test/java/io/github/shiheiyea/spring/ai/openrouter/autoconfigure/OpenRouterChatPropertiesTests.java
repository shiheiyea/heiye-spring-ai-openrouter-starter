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

import org.junit.jupiter.api.Test;

import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.webclient.autoconfigure.WebClientAutoConfiguration;

import io.github.shiheiyea.spring.ai.openrouter.OpenRouterChatOptions;
import io.github.shiheiyea.spring.ai.openrouter.api.OpenRouterApi.Reasoning;

import static org.assertj.core.api.Assertions.assertThat;

class OpenRouterChatPropertiesTests {

    @Test
    void chatProperties() {
        new ApplicationContextRunner().withPropertyValues(
                "spring.ai.openrouter.base-url=TEST_BASE_URL",
                "spring.ai.openrouter.api-key=abc123",
                "spring.ai.openrouter.chat.model=MODEL_XYZ",
                "spring.ai.openrouter.chat.temperature=0.55",
                "spring.ai.openrouter.chat.max-tokens=64",
                "spring.ai.openrouter.chat.stop=STOP_1,STOP_2")
                .withConfiguration(AutoConfigurations.of(OpenRouterChatAutoConfiguration.class,
                        RestClientAutoConfiguration.class, SpringAiRetryAutoConfiguration.class,
                        ToolCallingAutoConfiguration.class, WebClientAutoConfiguration.class))
                .run(context -> {
                    OpenRouterChatProperties chatProperties = context.getBean(OpenRouterChatProperties.class);
                    OpenRouterConnectionProperties connectionProperties = context
                            .getBean(OpenRouterConnectionProperties.class);

                    assertThat(connectionProperties.getApiKey()).isEqualTo("abc123");
                    assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");

                    assertThat(chatProperties.getApiKey()).isNull();
                    assertThat(chatProperties.getBaseUrl()).isNull();

                    OpenRouterChatOptions options = chatProperties.toOptions();
                    assertThat(options.getModel()).isEqualTo("MODEL_XYZ");
                    assertThat(options.getTemperature()).isEqualTo(0.55);
                    assertThat(options.getMaxTokens()).isEqualTo(64);
                    assertThat(options.getStopSequences()).containsExactly("STOP_1", "STOP_2");
                });
    }

    @Test
    void chatPropertiesBindToolCallingStrict() {
        new ApplicationContextRunner().withPropertyValues("spring.ai.openrouter.api-key=abc123",
                "spring.ai.openrouter.chat.strict=true")
                .withConfiguration(AutoConfigurations.of(OpenRouterChatAutoConfiguration.class,
                        RestClientAutoConfiguration.class, SpringAiRetryAutoConfiguration.class,
                        ToolCallingAutoConfiguration.class, WebClientAutoConfiguration.class))
                .run(context -> {
                    OpenRouterChatProperties chatProperties = context.getBean(OpenRouterChatProperties.class);

                    assertThat(chatProperties.getStrict()).isTrue();
                    assertThat(chatProperties.toOptions().getStrict()).isTrue();
                });
    }

    @Test
    void chatPropertiesLeaveToolCallingStrictUnsetByDefault() {
        new ApplicationContextRunner().withPropertyValues("spring.ai.openrouter.api-key=abc123")
                .withConfiguration(AutoConfigurations.of(OpenRouterChatAutoConfiguration.class,
                        RestClientAutoConfiguration.class, SpringAiRetryAutoConfiguration.class,
                        ToolCallingAutoConfiguration.class, WebClientAutoConfiguration.class))
                .run(context -> assertThat(context.getBean(OpenRouterChatProperties.class).toOptions().getStrict())
                        .isNull());
    }

    @Test
    void chatPropertiesFallBackToDefaults() {
        new ApplicationContextRunner().withPropertyValues("spring.ai.openrouter.api-key=abc123")
                .withConfiguration(AutoConfigurations.of(OpenRouterChatAutoConfiguration.class,
                        RestClientAutoConfiguration.class, SpringAiRetryAutoConfiguration.class,
                        ToolCallingAutoConfiguration.class, WebClientAutoConfiguration.class))
                .run(context -> {
                    OpenRouterChatProperties chatProperties = context.getBean(OpenRouterChatProperties.class);

                    assertThat(chatProperties.isEnabled()).isTrue();
                    assertThat(chatProperties.getCompletionsPath())
                            .isEqualTo(OpenRouterChatProperties.DEFAULT_COMPLETIONS_PATH);

                    OpenRouterChatOptions options = chatProperties.toOptions();
                    // 采样温度没有内置默认值，未配置时不设置
                    assertThat(options.getTemperature()).isNull();
                });
    }

    @Test
    void chatModelIsNotCreatedWhenChatModelPropertyPointsElsewhere() {
        new ApplicationContextRunner()
                .withPropertyValues("spring.ai.model.chat=openai", "spring.ai.openrouter.api-key=abc123")
                .withConfiguration(AutoConfigurations.of(OpenRouterChatAutoConfiguration.class,
                        RestClientAutoConfiguration.class, SpringAiRetryAutoConfiguration.class,
                        ToolCallingAutoConfiguration.class, WebClientAutoConfiguration.class))
                .run(context -> assertThat(context).doesNotHaveBean("openRouterChatModel"));
    }

    @Test
    @SuppressWarnings("deprecation")
    void deprecatedOptionsBridgeStillBinds() {
        new ApplicationContextRunner()
                .withPropertyValues("spring.ai.openrouter.api-key=abc123",
                        "spring.ai.openrouter.chat.options.model=LEGACY_MODEL",
                        "spring.ai.openrouter.chat.options.temperature=0.3")
                .withConfiguration(AutoConfigurations.of(OpenRouterChatAutoConfiguration.class,
                        RestClientAutoConfiguration.class, SpringAiRetryAutoConfiguration.class,
                        ToolCallingAutoConfiguration.class, WebClientAutoConfiguration.class))
                .run(context -> {
                    OpenRouterChatProperties chatProperties = context.getBean(OpenRouterChatProperties.class);

                    OpenRouterChatProperties.Options legacyOptions = chatProperties.getOptions();
                    assertThat(legacyOptions.getModel()).isEqualTo("LEGACY_MODEL");
                    assertThat(legacyOptions.getTemperature()).isEqualTo(0.3);

                    OpenRouterChatOptions options = chatProperties.toOptions();
                    assertThat(options.getModel()).isEqualTo("LEGACY_MODEL");
                    assertThat(options.getTemperature()).isEqualTo(0.3);
                });
    }

    @Test
    @SuppressWarnings("deprecation")
    void deprecatedOptionsBridgeKeepsToolCallingStrict() {
        // strict 既有扁平键又有嵌套 setter，是唯一会走两条路的字段：
        // 若嵌套 setter 只写 chatOptions 而不回写扁平字段，toOptions() 会用外层的 null 把它冲掉
        new ApplicationContextRunner()
                .withPropertyValues("spring.ai.openrouter.api-key=abc123",
                        "spring.ai.openrouter.chat.options.strict=true")
                .withConfiguration(AutoConfigurations.of(OpenRouterChatAutoConfiguration.class,
                        RestClientAutoConfiguration.class, SpringAiRetryAutoConfiguration.class,
                        ToolCallingAutoConfiguration.class, WebClientAutoConfiguration.class))
                .run(context -> {
                    OpenRouterChatProperties chatProperties = context.getBean(OpenRouterChatProperties.class);

                    assertThat(chatProperties.getOptions().getStrict()).isTrue();
                    assertThat(chatProperties.getStrict()).isTrue();
                    assertThat(chatProperties.toOptions().getStrict()).isTrue();
                });
    }

    @Test
    @SuppressWarnings("deprecation")
    void structuredFieldsComeFromDeprecatedOptions() {
        new ApplicationContextRunner()
                .withPropertyValues("spring.ai.openrouter.api-key=abc123",
                        "spring.ai.openrouter.chat.options.reasoning.effort=high")
                .withConfiguration(AutoConfigurations.of(OpenRouterChatAutoConfiguration.class,
                        RestClientAutoConfiguration.class, SpringAiRetryAutoConfiguration.class,
                        ToolCallingAutoConfiguration.class, WebClientAutoConfiguration.class))
                .run(context -> {
                    OpenRouterChatProperties chatProperties = context.getBean(OpenRouterChatProperties.class);

                    assertThat(chatProperties.getOptions().getReasoning()).isNotNull();
                    assertThat(chatProperties.getOptions().getReasoning().effort())
                            .isEqualTo(Reasoning.Effort.HIGH);
                    assertThat(chatProperties.toOptions().getReasoning().effort()).isEqualTo(Reasoning.Effort.HIGH);
                });
    }

    @Test
    void completionsPathAndModelsAreBound() {
        new ApplicationContextRunner()
                .withPropertyValues("spring.ai.openrouter.api-key=abc123",
                        "spring.ai.openrouter.chat.completions-path=/api/v1/chat/completions",
                        "spring.ai.openrouter.chat.models=model-a,model-b")
                .withConfiguration(AutoConfigurations.of(OpenRouterChatAutoConfiguration.class,
                        RestClientAutoConfiguration.class, SpringAiRetryAutoConfiguration.class,
                        ToolCallingAutoConfiguration.class, WebClientAutoConfiguration.class))
                .run(context -> {
                    OpenRouterChatProperties chatProperties = context.getBean(OpenRouterChatProperties.class);

                    assertThat(chatProperties.getCompletionsPath()).isEqualTo("/api/v1/chat/completions");
                    assertThat(chatProperties.toOptions().getModels()).isEqualTo(List.of("model-a", "model-b"));
                });
    }

}
