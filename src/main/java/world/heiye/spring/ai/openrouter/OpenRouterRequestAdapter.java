package world.heiye.spring.ai.openrouter;


import world.heiye.spring.ai.openrouter.api.OpenRouterApi;

import java.util.Objects;

/**
 * @author: heiye
 * @date: 2025/11/15 上午2:51
 * @version: v1.0.0
 * @description: openrouter 请求参数转化器
 */
public class OpenRouterRequestAdapter {
    public static OpenRouterApi.ChatCompletionRequest adaptOptionsToRequest(
            OpenRouterChatOptions options,
            OpenRouterApi.ChatCompletionRequest baseRequest) {

        if (Objects.isNull(options)) {
            return baseRequest;
        }

        OpenRouterApi.Think think = buildThinkFromOptions(options);

        return new OpenRouterApi.ChatCompletionRequest(
                baseRequest.messages(),
                baseRequest.prompt(),
                options.getModel() != null ? options.getModel() : baseRequest.model(),
                baseRequest.responseFormat(),
                baseRequest.stop(),
                options.getStream() != null ? options.getStream() : baseRequest.stream(),
                baseRequest.maxTokens(),
                options.getTemperature() != null ? options.getTemperature() : baseRequest.temperature(),
                baseRequest.seed(),
                baseRequest.topP(),
                baseRequest.topK(),
                baseRequest.frequencyPenalty(),
                baseRequest.presencePenalty(),
                baseRequest.repetitionPenalty(),
                baseRequest.logitBias(),
                baseRequest.topLogprobs(),
                baseRequest.minP(),
                baseRequest.topA(),
                baseRequest.transforms(),
                baseRequest.models(),
                think != null ? think : baseRequest.reasoning(),
                baseRequest.tools(),
                baseRequest.toolChoice()
        );
    }

    /**
     * 构建 Think
     *
     * @param options
     * @return
     */
    private static OpenRouterApi.Think buildThinkFromOptions(OpenRouterChatOptions options) {
        if (Objects.isNull(options.getEffort()) &&
                Objects.isNull(options.getThinkMaxTokens()) &&
                Objects.isNull(options.getExclude()) &&
                Objects.isNull(options.getEnable())) {
            return null;
        }

        return new OpenRouterApi.Think(
                options.getEffort() != null ?
                        OpenRouterApi.Effort.valueOf(options.getEffort()) : null,
                options.getThinkMaxTokens(),
                options.getExclude(),
                options.getEnable()
        );
    }
}
