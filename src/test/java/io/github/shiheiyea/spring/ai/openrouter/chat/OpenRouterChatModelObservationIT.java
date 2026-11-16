/*
 * Copyright 2023-present the original author or authors.
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

package io.github.shiheiyea.spring.ai.openrouter.chat;

import java.util.List;

import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.observation.DefaultChatModelObservationConvention;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.observation.conventions.AiOperationType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.core.retry.RetryTemplate;

import io.github.shiheiyea.spring.ai.openrouter.OpenRouterChatModel;
import io.github.shiheiyea.spring.ai.openrouter.OpenRouterChatOptions;
import io.github.shiheiyea.spring.ai.openrouter.api.OpenRouterApi;
import io.github.shiheiyea.spring.ai.openrouter.api.common.OpenRouterConstants;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.ai.chat.observation.ChatModelObservationDocumentation.HighCardinalityKeyNames;
import static org.springframework.ai.chat.observation.ChatModelObservationDocumentation.LowCardinalityKeyNames;

/**
 * Observation 埋点的集成测试，需要 OPENROUTER_API_KEY 环境变量，默认关闭。
 */
@Disabled("Requires a real OPENROUTER_API_KEY and network access")
@SpringBootTest(classes = OpenRouterChatModelObservationIT.Config.class)
class OpenRouterChatModelObservationIT {

    private static final String MODEL = "google/gemini-3-flash-preview";

    @Autowired
    TestObservationRegistry observationRegistry;

    @Autowired
    OpenRouterChatModel chatModel;

    @BeforeEach
    void beforeEach() {
        this.observationRegistry.clear();
    }

    @Test
    void observationForChatOperation() {
        OpenRouterChatOptions options = OpenRouterChatOptions.builder()
                .model(MODEL)
                .maxTokens(2048)
                .temperature(0.7)
                .build();

        ChatResponse chatResponse = this.chatModel.call(new Prompt("Why does a raven look like a desk?", options));

        assertThat(chatResponse.getResult().getOutput().getText()).isNotEmpty();
        validate(false);
    }

    @Test
    void observationForStreamingChatOperation() {
        OpenRouterChatOptions options = OpenRouterChatOptions.builder()
                .model(MODEL)
                .maxTokens(2048)
                .temperature(0.7)
                .build();

        List<ChatResponse> responses = this.chatModel
                .stream(new Prompt("Why does a raven look like a desk?", options))
                .collectList()
                .block();

        assertThat(responses).isNotEmpty();
        validate(true);
    }

    private void validate(boolean streaming) {
        var observationAssert = TestObservationRegistryAssert.assertThat(this.observationRegistry)
                .doesNotHaveAnyRemainingCurrentObservation()
                .hasObservationWithNameEqualTo(DefaultChatModelObservationConvention.DEFAULT_NAME)
                .that()
                .hasContextualNameEqualTo("chat " + MODEL)
                .hasLowCardinalityKeyValue(LowCardinalityKeyNames.AI_OPERATION_TYPE.asString(),
                        AiOperationType.CHAT.value())
                .hasLowCardinalityKeyValue(LowCardinalityKeyNames.AI_PROVIDER.asString(),
                        OpenRouterConstants.PROVIDER_NAME)
                .hasLowCardinalityKeyValue(LowCardinalityKeyNames.REQUEST_MODEL.asString(), MODEL)
                .hasBeenStarted()
                .hasBeenStopped();

        if (streaming) {
            observationAssert.hasHighCardinalityKeyValue(HighCardinalityKeyNames.REQUEST_STREAM.asString(), "true");
        }
        else {
            observationAssert
                    .doesNotHaveHighCardinalityKeyValueWithKey(HighCardinalityKeyNames.REQUEST_STREAM.asString());
        }
    }

    @SpringBootConfiguration
    static class Config {

        @Bean
        TestObservationRegistry observationRegistry() {
            return TestObservationRegistry.create();
        }

        @Bean
        OpenRouterApi openRouterApi() {
            return OpenRouterApi.builder().apiKey(System.getenv("OPENROUTER_API_KEY")).build();
        }

        @Bean
        OpenRouterChatModel openRouterChatModel(OpenRouterApi openRouterApi,
                TestObservationRegistry observationRegistry) {
            return new OpenRouterChatModel(openRouterApi, OpenRouterChatOptions.builder().build(),
                    ToolCallingManager.builder().build(), new RetryTemplate(), observationRegistry);
        }
    }
}
