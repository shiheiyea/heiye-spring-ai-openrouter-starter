package world.heiye.spring.ai.openrouter;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.content.Media;

import java.util.List;
import java.util.Map;

/**
 * @author: heiye
 * @date: 2025/11/12 下午9:12
 * @version: v1.0.0
 * @description:
 */
public class OpenRouterAssistantMessage extends AssistantMessage {
    /**
     * 推理内容
     */
    private String reasoningContent;

    public OpenRouterAssistantMessage(String content) {
        super(content);
    }

    protected OpenRouterAssistantMessage(String content, String reasoningContent, Boolean prefix, Map<String, Object> properties, List<ToolCall> toolCalls, List<Media> media) {
        super(content, properties, toolCalls, media);
        this.reasoningContent = reasoningContent;
    }

    public String getReasoningContent() {
        return this.reasoningContent;
    }

    public static final class Builder {
        private String content;
        private Map<String, Object> properties = Map.of();
        private List<ToolCall> toolCalls = List.of();
        private List<Media> media = List.of();
        private Boolean prefix;
        private String reasoningContent;

        public Builder() {
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder properties(Map<String, Object> properties) {
            this.properties = properties;
            return this;
        }

        public Builder toolCalls(List<ToolCall> toolCalls) {
            this.toolCalls = toolCalls;
            return this;
        }

        public Builder media(List<Media> media) {
            this.media = media;
            return this;
        }

        public Builder prefix(Boolean prefix) {
            this.prefix = prefix;
            return this;
        }

        public Builder reasoningContent(String reasoningContent) {
            this.reasoningContent = reasoningContent;
            return this;
        }

        public OpenRouterAssistantMessage build() {
            return new OpenRouterAssistantMessage(this.content, this.reasoningContent, this.prefix, this.properties, this.toolCalls, this.media);
        }
    }
}
