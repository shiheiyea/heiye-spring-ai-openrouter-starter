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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import io.github.shiheiyea.spring.ai.openrouter.api.OpenRouterApi.ChatCompletionChunk;
import io.github.shiheiyea.spring.ai.openrouter.api.OpenRouterApi.ChatCompletionChunk.ChunkChoice;
import io.github.shiheiyea.spring.ai.openrouter.api.OpenRouterApi.ChatCompletionFinishReason;
import io.github.shiheiyea.spring.ai.openrouter.api.OpenRouterApi.ChatCompletionFunction;
import io.github.shiheiyea.spring.ai.openrouter.api.OpenRouterApi.ChatCompletionMessage;
import io.github.shiheiyea.spring.ai.openrouter.api.OpenRouterApi.ReasoningDetail;
import io.github.shiheiyea.spring.ai.openrouter.api.OpenRouterApi.ToolCall;

public class OpenRouterStreamFunctionCallingHelper {

    /**
     * 把当前分块合并到已累积的分块上。
     *
     * 除文本与工具调用参数需要拼接外，其余字段都是「当前有值则覆盖、否则沿用旧值」。
     */
    public ChatCompletionChunk merge(@Nullable ChatCompletionChunk previous, ChatCompletionChunk current) {
        if (previous == null) {
            return current;
        }

        List<ChunkChoice> choices = !current.choices().isEmpty()
                ? List.of(this.merge(previous.choices().get(0), current.choices().get(0))) : List.of();

        return new ChatCompletionChunk(current.id() != null ? current.id() : previous.id(), choices,
                current.created() != null ? current.created() : previous.created(),
                current.model() != null ? current.model() : previous.model(),
                current.serviceTier() != null ? current.serviceTier() : previous.serviceTier(),
                current.systemFingerprint() != null ? current.systemFingerprint() : previous.systemFingerprint(),
                current.object() != null ? current.object() : previous.object(),
                current.usage() != null ? current.usage() : previous.usage(),
                current.error() != null ? current.error() : previous.error());
    }

    private ChunkChoice merge(ChunkChoice previous, ChunkChoice current) {
        return new ChunkChoice(current.finishReason() != null ? current.finishReason() : previous.finishReason(),
                current.nativeFinishReason() != null ? current.nativeFinishReason() : previous.nativeFinishReason(),
                current.index() != null ? current.index() : previous.index(),
                this.merge(previous.delta(), current.delta()),
                current.logprobs() != null ? current.logprobs() : previous.logprobs());
    }

    /**
     * 合并增量消息。
     *
     * 文本内容按顺序拼接；工具调用按 id 判定是新增还是续写，续写时把参数拼接到最后一个调用上。
     */
    private ChatCompletionMessage merge(@Nullable ChatCompletionMessage previous, ChatCompletionMessage current) {
        if (previous == null) {
            return current;
        }

        Object previousRawContent = previous.rawContent();
        String content;
        if (current.rawContent() instanceof String currentText) {
            content = (previousRawContent instanceof String previousText) ? previousText + currentText : currentText;
        }
        else {
            content = (previousRawContent instanceof String previousString) ? previousString : null;
        }

        List<ToolCall> toolCalls;
        List<ToolCall> currentToolCalls = current.toolCalls();
        if (currentToolCalls != null && !currentToolCalls.isEmpty()) {
            List<ToolCall> previousToolCalls = previous.toolCalls() != null ? new ArrayList<>(previous.toolCalls())
                    : new ArrayList<>();
            for (ToolCall currentToolCall : currentToolCalls) {
                // 带 id 的是新调用的开头，不带 id 的是上一个调用的参数续写
                if (currentToolCall.id() != null) {
                    previousToolCalls.add(currentToolCall);
                }
                else if (!previousToolCalls.isEmpty()) {
                    int lastIndex = previousToolCalls.size() - 1;
                    previousToolCalls.set(lastIndex,
                            this.merge(previousToolCalls.get(lastIndex), currentToolCall));
                }
            }
            toolCalls = previousToolCalls;
        }
        else {
            toolCalls = previous.toolCalls();
        }

        return new ChatCompletionMessage(content,
                current.role() != null ? current.role() : previous.role(),
                current.name() != null ? current.name() : previous.name(),
                current.toolCallId() != null ? current.toolCallId() : previous.toolCallId(), toolCalls,
                this.concat(previous.reasoning(), current.reasoning()),
                this.mergeReasoningDetails(previous.reasoningDetails(), current.reasoningDetails()),
                current.images() != null ? current.images() : previous.images(),
                current.audio() != null ? current.audio() : previous.audio());
    }

    /**
     * 推理详情按 index 归并，同一条详情的内容也可能跨块拆开。
     */
    private @Nullable List<ReasoningDetail> mergeReasoningDetails(@Nullable List<ReasoningDetail> previous,
            @Nullable List<ReasoningDetail> current) {
        if (current == null || current.isEmpty()) {
            return previous;
        }
        if (previous == null || previous.isEmpty()) {
            return current;
        }
        List<ReasoningDetail> merged = new ArrayList<>(previous);
        for (ReasoningDetail detail : current) {
            int existing = this.indexOf(merged, detail);
            if (existing < 0) {
                merged.add(detail);
            }
            else {
                merged.set(existing, this.merge(merged.get(existing), detail));
            }
        }
        return merged;
    }

    private int indexOf(List<ReasoningDetail> details, ReasoningDetail target) {
        for (int i = 0; i < details.size(); i++) {
            if (Objects.equals(details.get(i).index(), target.index())
                    && details.get(i).type() == target.type()) {
                return i;
            }
        }
        return -1;
    }

    private ReasoningDetail merge(ReasoningDetail previous, ReasoningDetail current) {
        return new ReasoningDetail(current.type() != null ? current.type() : previous.type(),
                current.id() != null ? current.id() : previous.id(),
                current.index() != null ? current.index() : previous.index(),
                current.format() != null ? current.format() : previous.format(),
                this.concat(previous.text(), current.text()), this.concat(previous.summary(), current.summary()),
                this.concat(previous.data(), current.data()),
                current.signature() != null ? current.signature() : previous.signature(),
                this.concat(previous.arguments(), current.arguments()), this.concat(previous.result(), current.result()),
                current.toolCallId() != null ? current.toolCallId() : previous.toolCallId(),
                current.toolName() != null ? current.toolName() : previous.toolName());
    }

    private @Nullable String concat(@Nullable String previous, @Nullable String current) {
        if (current == null) {
            return previous;
        }
        return previous == null ? current : previous + current;
    }

    private ToolCall merge(ToolCall previous, ToolCall current) {
        return new ToolCall(current.index() != null ? current.index() : previous.index(),
                current.id() != null ? current.id() : previous.id(),
                current.type() != null ? current.type() : previous.type(),
                this.merge(previous.function(), current.function()));
    }

    /**
     * 函数名只在首块出现，参数则按块陆续送达，因此参数要拼接。
     */
    private ChatCompletionFunction merge(@Nullable ChatCompletionFunction previous, ChatCompletionFunction current) {
        if (previous == null) {
            return current;
        }
        StringBuilder arguments = new StringBuilder();
        if (previous.arguments() != null) {
            arguments.append(previous.arguments());
        }
        if (current.arguments() != null) {
            arguments.append(current.arguments());
        }
        return new ChatCompletionFunction(current.name() != null ? current.name() : previous.name(),
                arguments.toString());
    }

    public boolean isStreamingToolFunctionCall(@Nullable ChatCompletionChunk chunk) {
        if (chunk == null || chunk.choices() == null || chunk.choices().isEmpty()) {
            return false;
        }
        ChatCompletionMessage delta = chunk.choices().get(0).delta();
        return delta != null && delta.toolCalls() != null && !delta.toolCalls().isEmpty();
    }

    public boolean isStreamingToolFunctionCallFinish(@Nullable ChatCompletionChunk chunk) {
        if (chunk == null || chunk.choices() == null || chunk.choices().isEmpty()) {
            return false;
        }
        ChatCompletionFinishReason finishReason = chunk.choices().get(0).finishReason();
        return finishReason == ChatCompletionFinishReason.TOOL_CALLS
                || finishReason == ChatCompletionFinishReason.TOOL_CALL;
    }

}
