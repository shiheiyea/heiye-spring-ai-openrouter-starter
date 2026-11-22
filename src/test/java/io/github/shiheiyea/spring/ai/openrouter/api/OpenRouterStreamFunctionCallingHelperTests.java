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

package io.github.shiheiyea.spring.ai.openrouter.api;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.shiheiyea.spring.ai.openrouter.api.OpenRouterApi.ChatCompletionChunk;
import io.github.shiheiyea.spring.ai.openrouter.api.OpenRouterApi.ChatCompletionChunk.ChunkChoice;
import io.github.shiheiyea.spring.ai.openrouter.api.OpenRouterApi.ChatCompletionFinishReason;
import io.github.shiheiyea.spring.ai.openrouter.api.OpenRouterApi.ChatCompletionFunction;
import io.github.shiheiyea.spring.ai.openrouter.api.OpenRouterApi.ChatCompletionMessage;
import io.github.shiheiyea.spring.ai.openrouter.api.OpenRouterApi.ChatCompletionMessage.Role;
import io.github.shiheiyea.spring.ai.openrouter.api.OpenRouterApi.ReasoningDetail;
import io.github.shiheiyea.spring.ai.openrouter.api.OpenRouterApi.ToolCall;

import static org.assertj.core.api.Assertions.assertThat;

class OpenRouterStreamFunctionCallingHelperTests {

    private OpenRouterStreamFunctionCallingHelper helper;

    @BeforeEach
    void setUp() {
        this.helper = new OpenRouterStreamFunctionCallingHelper();
    }

    @Test
    void mergeWhenPreviousIsNullShouldReturnCurrent() {
        ChatCompletionChunk current = chunk("id1", List.of());

        ChatCompletionChunk result = this.helper.merge(null, current);

        assertThat(result).isEqualTo(current);
    }

    @Test
    void mergeShouldMergeBasicFieldsFromCurrentAndPrevious() {
        ChatCompletionChunk previous = new ChatCompletionChunk("id1", List.of(), 123L, "model1", null, null, null,
                null);
        ChatCompletionChunk current = new ChatCompletionChunk("id2", List.of(), null, null, null, null, null, null);

        ChatCompletionChunk result = this.helper.merge(previous, current);

        assertThat(result.id()).isEqualTo("id2");
        assertThat(result.created()).isEqualTo(123L);
        assertThat(result.model()).isEqualTo("model1");
    }

    @Test
    void mergeShouldMergeMessagesContent() {
        ChatCompletionMessage previousMsg = new ChatCompletionMessage("Hello ", Role.ASSISTANT);
        ChatCompletionMessage currentMsg = new ChatCompletionMessage("World!", Role.ASSISTANT);

        ChatCompletionChunk previous = chunkWithDelta("id", previousMsg);
        ChatCompletionChunk current = chunkWithDelta("id", currentMsg);

        ChatCompletionChunk result = this.helper.merge(previous, current);

        assertThat(result.choices()).hasSize(1);
        assertThat(result.choices().get(0).delta().content()).isEqualTo("Hello World!");
    }

    @Test
    void mergeShouldMergeReasoningContent() {
        ChatCompletionChunk previous = chunkWithDelta("id", message("first ", "step one"));
        ChatCompletionChunk current = chunkWithDelta("id", message("second", " is done"));

        ChatCompletionChunk result = this.helper.merge(previous, current);

        ChatCompletionMessage delta = result.choices().get(0).delta();
        assertThat(delta.content()).isEqualTo("first second");
        assertThat(delta.reasoning()).isEqualTo("step one is done");
    }

    @Test
    void mergeShouldMergeReasoningDetailsByIndexAndType() {
        ReasoningDetail first = reasoningDetail(ReasoningDetail.ReasoningDetailType.TEXT, 0, "step ", null);
        ReasoningDetail second = reasoningDetail(ReasoningDetail.ReasoningDetailType.TEXT, 0, "two", null);
        ReasoningDetail summary = reasoningDetail(ReasoningDetail.ReasoningDetailType.SUMMARY, 0, null,
                "short summary");

        ChatCompletionChunk previous = chunkWithDelta("id", messageWithReasoningDetails(List.of(first)));
        ChatCompletionChunk current = chunkWithDelta("id", messageWithReasoningDetails(List.of(second)));
        ChatCompletionChunk result = this.helper.merge(previous, current);

        List<ReasoningDetail> merged = result.choices().get(0).delta().reasoningDetails();
        assertThat(merged).hasSize(1);
        assertThat(merged.get(0).text()).isEqualTo("step two");

        result = this.helper.merge(chunkWithDelta("id", messageWithReasoningDetails(List.of(first, summary))),
                chunkWithDelta("id", messageWithReasoningDetails(List.of(second))));

        assertThat(result.choices().get(0).delta().reasoningDetails()).hasSize(2);
    }

    @Test
    void mergeShouldAccumulateToolCallArguments() {
        ToolCall firstCall = new ToolCall(0, "call-1", "function",
                new ChatCompletionFunction("getWeather", "{\"city\":"));
        ToolCall secondCall = new ToolCall(null, null, null, new ChatCompletionFunction(null, "\"Paris\"}"));

        ChatCompletionChunk previous = chunkWithDelta("id", messageWithToolCalls(List.of(firstCall)));
        ChatCompletionChunk current = chunkWithDelta("id", messageWithToolCalls(List.of(secondCall)));

        ChatCompletionChunk result = this.helper.merge(previous, current);

        List<ToolCall> toolCalls = result.choices().get(0).delta().toolCalls();
        assertThat(toolCalls).hasSize(1);
        assertThat(toolCalls.get(0).function().name()).isEqualTo("getWeather");
        assertThat(toolCalls.get(0).function().arguments()).isEqualTo("{\"city\":\"Paris\"}");
    }

    @Test
    void mergeShouldKeepParallelToolCallsSeparateByIndex() {
        // 两个并行调用的首片同时下发，各自带序号与 id
        ToolCall firstCallStart = new ToolCall(0, "call-1", "function",
                new ChatCompletionFunction("getWeather", "{\"city\":"));
        ToolCall secondCallStart = new ToolCall(1, "call-2", "function",
                new ChatCompletionFunction("getTime", "{\"zone\":"));

        ChatCompletionChunk previous = chunkWithDelta("id", messageWithToolCalls(List.of(firstCallStart)));
        ChatCompletionChunk current = chunkWithDelta("id", messageWithToolCalls(List.of(secondCallStart)));

        ChatCompletionChunk result = this.helper.merge(previous, current);

        List<ToolCall> toolCalls = result.choices().get(0).delta().toolCalls();
        assertThat(toolCalls).hasSize(2);
        assertThat(toolCalls.get(0).index()).isZero();
        assertThat(toolCalls.get(0).function().name()).isEqualTo("getWeather");
        assertThat(toolCalls.get(1).index()).isEqualTo(1);
        assertThat(toolCalls.get(1).function().name()).isEqualTo("getTime");
    }

    @Test
    void mergeShouldAppendArgumentsToTheRightParallelCall() {
        ToolCall firstCall = new ToolCall(0, "call-1", "function",
                new ChatCompletionFunction("getWeather", "{\"city\":"));
        ToolCall secondCall = new ToolCall(1, "call-2", "function",
                new ChatCompletionFunction("getTime", "{\"zone\":"));

        ChatCompletionChunk start = chunkWithDelta("id", messageWithToolCalls(List.of(firstCall, secondCall)));

        // 续片只带序号，不带 id
        ToolCall firstCallDelta = new ToolCall(0, null, null,
                new ChatCompletionFunction(null, "\"Paris\"}"));
        ToolCall secondCallDelta = new ToolCall(1, null, null,
                new ChatCompletionFunction(null, "\"UTC\"}"));

        ChatCompletionChunk result = this.helper.merge(start,
                chunkWithDelta("id", messageWithToolCalls(List.of(firstCallDelta))));
        result = this.helper.merge(result,
                chunkWithDelta("id", messageWithToolCalls(List.of(secondCallDelta))));

        List<ToolCall> toolCalls = result.choices().get(0).delta().toolCalls();
        assertThat(toolCalls).hasSize(2);
        assertThat(toolCalls.get(0).function().name()).isEqualTo("getWeather");
        assertThat(toolCalls.get(0).function().arguments()).isEqualTo("{\"city\":\"Paris\"}");
        assertThat(toolCalls.get(1).function().name()).isEqualTo("getTime");
        assertThat(toolCalls.get(1).function().arguments()).isEqualTo("{\"zone\":\"UTC\"}");
    }

    @Test
    void mergeShouldPreferCurrentFinishReason() {
        ChatCompletionChunk previous = chunkWithFinishReason("id", ChatCompletionFinishReason.STOP);
        ChatCompletionChunk current = chunkWithFinishReason("id", ChatCompletionFinishReason.LENGTH);

        ChatCompletionChunk result = this.helper.merge(previous, current);

        assertThat(result.choices().get(0).finishReason()).isEqualTo(ChatCompletionFinishReason.LENGTH);
    }

    @Test
    void isStreamingToolFunctionCallShouldDetectToolCalls() {
        ChatCompletionChunk withToolCall = chunkWithDelta("id",
                messageWithToolCalls(List.of(new ToolCall(0, "call-1", "function",
                        new ChatCompletionFunction("getWeather", "{}")))));

        assertThat(this.helper.isStreamingToolFunctionCall(withToolCall)).isTrue();
        assertThat(this.helper.isStreamingToolFunctionCall(chunkWithDelta("id", message("text", null)))).isFalse();
    }

    @Test
    void isStreamingToolFunctionCallFinishShouldDetectToolCallFinishReasons() {
        assertThat(this.helper
                .isStreamingToolFunctionCallFinish(chunkWithFinishReason("id", ChatCompletionFinishReason.TOOL_CALLS)))
                .isTrue();
        assertThat(this.helper
                .isStreamingToolFunctionCallFinish(chunkWithFinishReason("id", ChatCompletionFinishReason.TOOL_CALL)))
                .isTrue();
        assertThat(this.helper
                .isStreamingToolFunctionCallFinish(chunkWithFinishReason("id", ChatCompletionFinishReason.STOP)))
                .isFalse();
    }

    @Test
    void mergeShouldTolerateChunksWithoutChoices() {
        ChatCompletionChunk previous = chunk("id1", List.of());

        ChatCompletionChunk result = this.helper.merge(previous, chunk("id2", List.of()));

        assertThat(result.id()).isEqualTo("id2");
        assertThat(result.choices()).isEmpty();
    }

    @Test
    void mergeShouldSkipPreviousWithoutChoices() {
        ChatCompletionChunk previous = chunk("id1", List.of());

        ChatCompletionChunk result = this.helper.merge(previous, chunkWithFinishReason("id2", ChatCompletionFinishReason.STOP));

        assertThat(result.choices()).hasSize(1);
        assertThat(result.choices().get(0).finishReason()).isEqualTo(ChatCompletionFinishReason.STOP);
    }
    private ChatCompletionChunk chunk(String id, List<ChunkChoice> choices) {
        return new ChatCompletionChunk(id, choices, null, null, null, null, null, null);
    }

    private ChatCompletionChunk chunkWithDelta(String id, ChatCompletionMessage delta) {
        return chunk(id, List.of(new ChunkChoice(null, null, 0, delta, null)));
    }

    private ChatCompletionChunk chunkWithFinishReason(String id, ChatCompletionFinishReason finishReason) {
        return chunk(id, List.of(new ChunkChoice(finishReason, null, 0, message("", null), null)));
    }

    private ChatCompletionMessage message(String content, String reasoning) {
        return new ChatCompletionMessage(content, Role.ASSISTANT, null, null, null, reasoning, null, null, null);
    }

    private ChatCompletionMessage messageWithToolCalls(List<ToolCall> toolCalls) {
        return new ChatCompletionMessage(null, Role.ASSISTANT, null, null, toolCalls, null, null, null, null);
    }

    private ChatCompletionMessage messageWithReasoningDetails(List<ReasoningDetail> reasoningDetails) {
        return new ChatCompletionMessage(null, Role.ASSISTANT, null, null, null, null, reasoningDetails, null, null);
    }

    private ReasoningDetail reasoningDetail(ReasoningDetail.ReasoningDetailType type, Integer index, String text,
            String summary) {
        return new ReasoningDetail(type, "id-" + type.getValue(), index, null, text, summary, null, null, null, null,
                null, null);
    }

}
