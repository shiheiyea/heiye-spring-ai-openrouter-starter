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

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.ResponseEntity;

import io.github.shiheiyea.spring.ai.openrouter.api.OpenRouterApi;
import io.github.shiheiyea.spring.ai.openrouter.api.OpenRouterApi.ChatCompletion;
import io.github.shiheiyea.spring.ai.openrouter.api.OpenRouterApi.ChatCompletionFinishReason;
import io.github.shiheiyea.spring.ai.openrouter.api.OpenRouterApi.ChatCompletionRequest;
import io.github.shiheiyea.spring.ai.openrouter.api.OpenRouterApi.Reasoning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenRouterChatModelTests {

    private OpenRouterChatModel model() {
        return OpenRouterChatModel.builder().openRouterApi(mock(OpenRouterApi.class)).build();
    }

    @Test
    void transfersReasoningAndExplicitToolOptionsToRequest() {
        var options = OpenRouterChatOptions.builder()
                .model("test-model")
                .reasoning(new Reasoning(Reasoning.Effort.HIGH, 1024, false, true, null, null, null))
                .tools(List.of(new OpenRouterApi.FunctionTool(
                        new OpenRouterApi.FunctionTool.Function("test", "description", Map.of()))))
                .toolChoice("auto")
                .build();
        ChatCompletionRequest request = model().createRequest(new Prompt("hello", options), true);

        assertThat(request.model()).isEqualTo("test-model");
        assertThat(request.stream()).isTrue();
        assertThat(request.messages()).hasSize(1);
        assertThat(request.reasoning()).isEqualTo(
                new Reasoning(Reasoning.Effort.HIGH, 1024, false, true, null, null, null));
        assertThat(request.tools()).hasSize(1);
        assertThat(request.toolChoice()).isEqualTo("auto");
    }

    @Test
    void copiesOptionsWithoutInventingZeroDefaults() {
        var defaults = OpenRouterChatOptions.builder()
                .model("default")
                .maxTokens(512)
                .topP(0.8)
                .topK(20)
                .frequencyPenalty(0.1)
                .presencePenalty(0.2)
                .stop(List.of("END"))
                .stream(false)
                .reasoning(new Reasoning(Reasoning.Effort.LOW, null, null, null, null, null, null))
                .build();
        var copy = defaults.mutate().build();
        assertThat(copy).isEqualTo(defaults);
        assertThat(copy.getStopSequences()).containsExactly("END");

        ChatCompletionRequest request = model().createRequest(new Prompt("hello", defaults), false);
        assertThat(request.maxTokens()).isEqualTo(512);
        assertThat(request.topP()).isEqualTo(0.8);
        assertThat(request.topK()).isEqualTo(20);
        assertThat(request.frequencyPenalty()).isEqualTo(0.1);
        assertThat(request.presencePenalty()).isEqualTo(0.2);
        assertThat(request.stop()).containsExactly("END");
        assertThat(request.reasoning().effort()).isEqualTo(Reasoning.Effort.LOW);
        // 未设置的字段保持缺省，不被补成 0
        assertThat(request.seed()).isNull();
        assertThat(request.topLogprobs()).isNull();
        assertThat(request.temperature()).isNull();
    }

    @Test
    void runtimeReasoningReplacesDefaultReasoningAsAWhole() {
        var runtime = OpenRouterChatOptions.builder()
                .reasoning(new Reasoning(null, null, null, false, null, null, null))
                .build();
        ChatCompletionRequest request = model().createRequest(new Prompt("hello", runtime), false);
        assertThat(request.reasoning()).isEqualTo(new Reasoning(null, null, null, false, null, null, null));
    }

    @Test
    void createRequestCarriesLogitBias() {
        var options = OpenRouterChatOptions.builder().logitBias(Map.of("50256", -100)).build();
        ChatCompletionRequest request = model().createRequest(new Prompt("hello", options), false);

        assertThat(request.logitBias()).containsEntry("50256", -100);
    }

    @Test
    void callToleratesChoiceWithoutMessageAndFinishReason() {
        OpenRouterApi api = mock(OpenRouterApi.class);
        ChatCompletion completion = new ChatCompletion("id-1",
                List.of(new ChatCompletion.Choice(null, null, 0, null, null)), 1L, "test-model", null, null,
                "chat.completion", null);
        when(api.chatCompletionEntity(any())).thenReturn(ResponseEntity.ok(completion));

        OpenRouterChatModel model = OpenRouterChatModel.builder().openRouterApi(api).build();
        ChatResponse response = model.call(new Prompt("hello"));

        assertThat(response.getResults()).hasSize(1);
        // 缺失 finish_reason 时退空串，不给 null
        assertThat(response.getResult().getMetadata().getFinishReason()).isEmpty();
        assertThat(response.getResult().getOutput().getToolCalls()).isEmpty();
        ChatResponseMetadata metadata = response.getMetadata();
        assertThat(metadata.getId()).isEqualTo("id-1");
        assertThat(metadata.getModel()).isEqualTo("test-model");
        assertThat(metadata.getUsage().getPromptTokens()).isZero();
    }

    @Test
    void callFallsBackToNativeFinishReason() {
        OpenRouterApi api = mock(OpenRouterApi.class);
        var message = new OpenRouterApi.ChatCompletionMessage("done",
                OpenRouterApi.ChatCompletionMessage.Role.ASSISTANT);
        ChatCompletion completion = new ChatCompletion("id-2",
                List.of(new ChatCompletion.Choice(null, ChatCompletionFinishReason.STOP, 0, message, null)), 2L,
                "test-model", null, null, "chat.completion", null);
        when(api.chatCompletionEntity(any())).thenReturn(ResponseEntity.ok(completion));

        OpenRouterChatModel model = OpenRouterChatModel.builder().openRouterApi(api).build();
        ChatResponse response = model.call(new Prompt("hello"));

        assertThat(response.getResult().getMetadata().getFinishReason()).isEqualTo("STOP");
        assertThat(response.getResult().getOutput().getText()).isEqualTo("done");
    }

}
