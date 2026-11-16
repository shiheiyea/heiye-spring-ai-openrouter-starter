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
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.content.Media;

import io.github.shiheiyea.spring.ai.openrouter.api.OpenRouterApi.ReasoningDetail;

public class OpenRouterAssistantMessage extends AssistantMessage {

    private @Nullable String reasoningContent;

    private @Nullable List<ReasoningDetail> reasoningDetails;

    public OpenRouterAssistantMessage(@Nullable String content) {
        super(content);
    }

    /**
     * 创建携带推理信息的助手消息。
     */
    protected OpenRouterAssistantMessage(@Nullable String content, @Nullable String reasoningContent,
            @Nullable List<ReasoningDetail> reasoningDetails, Map<String, Object> properties,
            List<ToolCall> toolCalls, List<Media> media) {
        super(content, properties, toolCalls, media);
        this.reasoningContent = reasoningContent;
        this.reasoningDetails = reasoningDetails;
    }

    public @Nullable String getReasoningContent() {
        return this.reasoningContent;
    }

    public void setReasoningContent(@Nullable String reasoningContent) {
        this.reasoningContent = reasoningContent;
    }

    public @Nullable List<ReasoningDetail> getReasoningDetails() {
        return this.reasoningDetails;
    }

    public void setReasoningDetails(@Nullable List<ReasoningDetail> reasoningDetails) {
        this.reasoningDetails = reasoningDetails;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof OpenRouterAssistantMessage that)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        return Objects.equals(this.reasoningContent, that.reasoningContent)
                && Objects.equals(this.reasoningDetails, that.reasoningDetails);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), this.reasoningContent, this.reasoningDetails);
    }

    @Override
    public String toString() {
        return "OpenRouterAssistantMessage [content=" + this.getText() + ", reasoningContent="
                + this.reasoningContent + ", reasoningDetails=" + this.reasoningDetails + ", metadata="
                + this.getMetadata() + ", toolCalls=" + this.getToolCalls() + ", media=" + this.getMedia() + "]";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends AbstractBuilder<Builder> {

    }

    public static class AbstractBuilder<B extends AbstractBuilder<B>> extends AssistantMessage.Builder<B> {

        protected @Nullable String reasoningContent;

        protected @Nullable List<ReasoningDetail> reasoningDetails;

        public B reasoningContent(@Nullable String reasoningContent) {
            this.reasoningContent = reasoningContent;
            return self();
        }

        public B reasoningDetails(@Nullable List<ReasoningDetail> reasoningDetails) {
            this.reasoningDetails = reasoningDetails;
            return self();
        }

        @Override
        public OpenRouterAssistantMessage build() {
            return new OpenRouterAssistantMessage(this.content, this.reasoningContent, this.reasoningDetails,
                    this.properties, this.toolCalls, this.media);
        }

    }

}
