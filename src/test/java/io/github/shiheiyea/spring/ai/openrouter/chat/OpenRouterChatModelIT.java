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

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import io.github.shiheiyea.spring.ai.openrouter.OpenRouterAssistantMessage;
import io.github.shiheiyea.spring.ai.openrouter.OpenRouterChatModel;
import io.github.shiheiyea.spring.ai.openrouter.OpenRouterChatOptions;
import io.github.shiheiyea.spring.ai.openrouter.OpenRouterTestConfiguration;
import io.github.shiheiyea.spring.ai.openrouter.api.OpenRouterApi;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实调用的集成测试，需要 OPENROUTER_API_KEY 环境变量，默认关闭。
 */
@Disabled("Requires a real OPENROUTER_API_KEY and network access")
@SpringBootTest(classes = OpenRouterTestConfiguration.class)
class OpenRouterChatModelIT {

    @Autowired
    OpenRouterChatModel chatModel;

    @Test
    void callReturnsGeneration() {
        OpenRouterChatOptions options = OpenRouterChatOptions.builder()
                .model("google/gemini-3-flash-preview")
                .temperature(0.7)
                .maxTokens(2048)
                .build();

        ChatResponse response = this.chatModel.call(new Prompt("Why does a raven look like a desk?", options));

        assertThat(response).isNotNull();
        assertThat(response.getResult()).isNotNull();
        assertThat(response.getResult().getOutput().getText()).isNotEmpty();
        assertThat(response.getMetadata().getUsage().getTotalTokens()).isPositive();
    }

    @Test
    void streamReturnsChunks() {
        OpenRouterChatOptions options = OpenRouterChatOptions.builder()
                .model("google/gemini-3-flash-preview")
                .temperature(0.7)
                .build();

        Flux<ChatResponse> responses = this.chatModel.stream(new Prompt("Say hello in five words.", options));

        List<ChatResponse> collected = responses.collectList().block();

        assertThat(collected).isNotEmpty();
        assertThat(collected.get(collected.size() - 1).getResult().getOutput().getText()).isNotEmpty();
    }

    @Test
    void reasoningDetailsSurviveTheRoundTrip() {
        OpenRouterApi.Reasoning reasoning = new OpenRouterApi.Reasoning(OpenRouterApi.Reasoning.Effort.LOW, null, null,
                null, null, null, null);
        OpenRouterChatOptions options = OpenRouterChatOptions.builder()
                .model("google/gemini-3-flash-preview")
                .reasoning(reasoning)
                .build();

        ChatResponse response = this.chatModel.call(new Prompt("What is 2 + 2? Answer with the number.", options));

        AssistantMessage output = response.getResult().getOutput();
        assertThat(output).isInstanceOf(OpenRouterAssistantMessage.class);
    }
}
