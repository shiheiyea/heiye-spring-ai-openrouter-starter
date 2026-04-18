package world.heiye.spring.ai.openrouter.api;

import world.heiye.spring.ai.openrouter.api.OpenRouterApi.ChatCompletionChunk;
import world.heiye.spring.ai.openrouter.api.OpenRouterApi.ChatCompletionMessage.Role;
import world.heiye.spring.ai.openrouter.api.OpenRouterApi.ChatCompletionMessage.ToolCall;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;


/**
 * @author: heiye
 * @date: 2025/11/12 下午9:12
 * @version: v1.0.0
 * @description:
 *      解释：流式工具调用助手
 *      作用：将流式返回的多个 Chunk 碎片合并成一个完整的工具调用请求
 */
public class OpenRouterStreamFunctionCallingHelper {

    /**
     * 核心方法：合并两个 Chunk
     * 原理：保留元数据（ID, Model），但对 choices 里的内容进行增量合并
     */
    public ChatCompletionChunk merge(ChatCompletionChunk previous, ChatCompletionChunk current) {
        if (previous == null) {
            return current;
        }

        String id = current.id() != null ? current.id() : previous.id();
        Long created = current.created() != null ? current.created() : previous.created();
        String model = current.model() != null ? current.model() : previous.model();
        String object = current.object() != null ? current.object() : previous.object();
        OpenRouterApi.Usage usage = current.usage() != null ? current.usage() : previous.usage();

        // 合并 Choice 里的 delta 内容
        ChatCompletionChunk.Choice previousChoice = CollectionUtils.isEmpty(previous.choices()) ? null : previous.choices().get(0);
        ChatCompletionChunk.Choice currentChoice = CollectionUtils.isEmpty(current.choices()) ? null : current.choices().get(0);

        ChatCompletionChunk.Choice mergedChoice = this.merge(previousChoice, currentChoice);
        List<ChatCompletionChunk.Choice> chunkChoices = mergedChoice == null ? List.of() : List.of(mergedChoice);

        return new ChatCompletionChunk(id, chunkChoices, created, model, object, previous.systemFingerprint(), usage);
    }

    /**
     * 合并 Choice 碎片
     */
    private ChatCompletionChunk.Choice merge(ChatCompletionChunk.Choice previous, ChatCompletionChunk.Choice current) {
        if (previous == null) {
            return current;
        }

        // FinishReason 只有在最后一段才会出现
        OpenRouterApi.ChatCompletionFinishReason finishReason = current.finishReason() != null ? current.finishReason() : previous.finishReason();
        Long index = current.index() != null ? current.index() : previous.index();

        // 核心：合并 Delta 消息（包含 content 和 tool_calls）
        OpenRouterApi.ChatCompletionMessage mergedDelta = this.merge(previous.delta(), current.delta());

        return new ChatCompletionChunk.Choice(index, mergedDelta, finishReason, current.nativeFinishReason());
    }

    /**
     * 合并消息体（Delta）
     * 原理：
     * 1. Content（文本）进行字符串累加。
     * 2. ToolCalls（工具）进行增量合并。
     */
    private OpenRouterApi.ChatCompletionMessage merge(OpenRouterApi.ChatCompletionMessage previous, OpenRouterApi.ChatCompletionMessage current) {
        // 1. 合并文本内容
        String content = current.content() != null
                ? (previous.content() != null ? previous.content() + current.content() : current.content())
                : (previous.content() != null ? previous.content() : "");

        OpenRouterApi.ChatCompletionMessage.Role role = current.role() != null ? current.role() : previous.role();
        role = (role != null) ? role : Role.ASSISTANT;

        String toolCallId = current.toolCallId() != null ? current.toolCallId() : previous.toolCallId();

        // 2. 合并工具调用
        List<ToolCall> toolCalls = new ArrayList<>();
        ToolCall lastPreviousToolCall = null;

        if (!CollectionUtils.isEmpty(previous.toolCalls())) {
            // 取出上一个已有的工具
            lastPreviousToolCall = previous.toolCalls().get(previous.toolCalls().size() - 1);
            if (previous.toolCalls().size() > 1) {
                toolCalls.addAll(previous.toolCalls().subList(0, previous.toolCalls().size() - 1));
            }
        }

        if (!CollectionUtils.isEmpty(current.toolCalls())) {
            ToolCall currentToolCall = current.toolCalls().iterator().next();
            // 如果当前 chunk 带有 ID，说明是一个新的工具调用的开始
            if (StringUtils.hasText(currentToolCall.id())) {
                if (lastPreviousToolCall != null) {
                    toolCalls.add(lastPreviousToolCall);
                }
                toolCalls.add(currentToolCall);
            } else {
                // 如果没有 ID，说明是在填充上一个工具的 arguments（参数字符串）
                toolCalls.add(this.merge(lastPreviousToolCall, currentToolCall));
            }
        } else if (lastPreviousToolCall != null) {
            toolCalls.add(lastPreviousToolCall);
        }

        return new OpenRouterApi.ChatCompletionMessage(content, role, content, toolCallId, toolCalls);
    }

    /**
     * 合并具体的 ToolCall 碎片
     */
    private ToolCall merge(ToolCall previous, ToolCall current) {
        if (previous == null) {
            return current;
        }

        String id = StringUtils.hasText(current.id()) ? current.id() : previous.id();
        String type = current.type() != null ? current.type() : previous.type();

        // 合并 Function 里的 arguments 字符串
        OpenRouterApi.ChatCompletionMessage.ChatCompletionFunction function = this.merge(previous.function(), current.function());

        return new ToolCall(current.index() != null ? current.index() : previous.index(), id, type, function);
    }

    /**
     * 合并 Function 参数碎片
     */
    private OpenRouterApi.ChatCompletionMessage.ChatCompletionFunction merge(
            OpenRouterApi.ChatCompletionMessage.ChatCompletionFunction previous,
            OpenRouterApi.ChatCompletionMessage.ChatCompletionFunction current) {
        if (previous == null) {
            return current;
        }

        String name = StringUtils.hasText(current.name()) ? current.name() : previous.name();

        // 关键：将当前收到的参数碎片累加到 StringBuilder 后面
        StringBuilder arguments = new StringBuilder();
        if (previous.arguments() != null) {
            arguments.append(previous.arguments());
        }
        if (current.arguments() != null) {
            arguments.append(current.arguments());
        }

        return new OpenRouterApi.ChatCompletionMessage.ChatCompletionFunction(name, arguments.toString());
    }

    /**
     * 判断当前 Chunk 是否包含工具调用意图
     */
    public boolean isStreamingToolFunctionCall(ChatCompletionChunk chunk) {
        // 当前 Chunk 为空或者没有 choice 时，返回 false
        if (chunk == null || CollectionUtils.isEmpty(chunk.choices())) {
            return false;
        }
        // 获取第一个 choice
        ChatCompletionChunk.Choice choice = chunk.choices().get(0);
        // 如果choice 的 delta 不为空，并且 delta 的 tool_calls 不为空，则返回 true
        return choice.delta() != null && !CollectionUtils.isEmpty(choice.delta().toolCalls());
    }

    /**
     * 判断工具调用是否已经全部传输完成（通过 finish_reason 判断）
     */
    public boolean isStreamingToolFunctionCallFinish(ChatCompletionChunk chunk) {
        // 当前 Chunk 为空或者没有 choice 时，返回 false
        if (chunk == null || CollectionUtils.isEmpty(chunk.choices())) {
            return false;
        }
        // 获取第一个 choice
        ChatCompletionChunk.Choice choice = chunk.choices().get(0);
        // 注意：OpenRouter 可能返回 TOOL_CALLS 或 tool_calls，取决于具体模型
        return OpenRouterApi.ChatCompletionFinishReason.TOOL_CALLS.equals(choice.finishReason()) ||
                OpenRouterApi.ChatCompletionFinishReason.TOOL_CALL.equals(choice.finishReason());
    }
}