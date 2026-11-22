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

import org.springframework.util.CollectionUtils;

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

        ChunkChoice previousChoice0 = CollectionUtils.isEmpty(previous.choices()) ? null : previous.choices().get(0);
        ChunkChoice currentChoice0 = CollectionUtils.isEmpty(current.choices()) ? null : current.choices().get(0);

        ChunkChoice choice = currentChoice0 != null ? this.merge(previousChoice0, currentChoice0) : null;
        List<ChunkChoice> choices = choice == null ? List.of() : List.of(choice);

        return new ChatCompletionChunk(current.id() != null ? current.id() : previous.id(), choices,
                current.created() != null ? current.created() : previous.created(),
                current.model() != null ? current.model() : previous.model(),
                current.serviceTier() != null ? current.serviceTier() : previous.serviceTier(),
                current.systemFingerprint() != null ? current.systemFingerprint() : previous.systemFingerprint(),
                current.object() != null ? current.object() : previous.object(),
                current.usage() != null ? current.usage() : previous.usage(),
                current.error() != null ? current.error() : previous.error());
    }

    private ChunkChoice merge(@Nullable ChunkChoice previous, ChunkChoice current) {
        if (previous == null) {
            return current;
        }

        return new ChunkChoice(current.finishReason() != null ? current.finishReason() : previous.finishReason(),
                current.nativeFinishReason() != null ? current.nativeFinishReason() : previous.nativeFinishReason(),
                current.index() != null ? current.index() : previous.index(),
                this.merge(previous.delta(), current.delta()),
                current.logprobs() != null ? current.logprobs() : previous.logprobs());
    }

    /**
     * 合并增量消息。
     *
     * 文本内容按顺序拼接；工具调用按 {@code index} 归并，同一序号的分片累积成一次调用。
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

        List<ToolCall> currentToolCalls = current.toolCalls();
        List<ToolCall> toolCalls = (currentToolCalls != null && !currentToolCalls.isEmpty())
                ? this.mergeToolCalls(previous.toolCalls(), currentToolCalls) : previous.toolCalls();

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
     * 按序号归并工具调用。
     *
     * 并行工具调用时多个调用会交错下发，续写分片只带序号、不带 id，按 id 是否出现来判断新增
     * 会把第二个及以后的调用错误地并到前一个上，因此以序号为准。
     */
    private List<ToolCall> mergeToolCalls(@Nullable List<ToolCall> previous, List<ToolCall> current) {
        List<ToolCall> merged = previous != null ? new ArrayList<>(previous) : new ArrayList<>();
        for (ToolCall currentToolCall : current) {
            int existing = this.toolCallIndexOf(merged, currentToolCall);
            if (existing < 0) {
                merged.add(currentToolCall);
            }
            else {
                merged.set(existing, this.merge(merged.get(existing), currentToolCall));
            }
        }
        return merged;
    }

    private int toolCallIndexOf(List<ToolCall> toolCalls, ToolCall target) {
        // 序号是并行调用的唯一标识；缺序号时退回按 id 匹配
        if (target.index() != null) {
            for (int i = 0; i < toolCalls.size(); i++) {
                if (target.index().equals(toolCalls.get(i).index())) {
                    return i;
                }
            }
            return -1;
        }
        if (target.id() != null) {
            for (int i = 0; i < toolCalls.size(); i++) {
                ToolCall candidate = toolCalls.get(i);
                if (candidate.index() == null && target.id().equals(candidate.id())) {
                    return i;
                }
            }
            return -1;
        }
        // 既无序号也无 id 的分片是上一个调用的参数续写
        return toolCalls.isEmpty() ? -1 : toolCalls.size() - 1;
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
