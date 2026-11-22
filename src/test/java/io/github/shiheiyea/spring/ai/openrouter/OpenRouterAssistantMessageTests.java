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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
import org.springframework.ai.content.Media;

import io.github.shiheiyea.spring.ai.openrouter.api.OpenRouterApi.ReasoningDetail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

class OpenRouterAssistantMessageTests {

    @Test
    void testConstructorWithContentOnly() {
        String content = "Hello, world!";
        OpenRouterAssistantMessage message = OpenRouterAssistantMessage.builder().content(content).build();

        assertThat(message.getText()).isEqualTo(content);
        assertThat(message.getReasoningContent()).isNull();
        assertThat(message.getReasoningDetails()).isNull();
    }

    @Test
    void testConstructorWithContentAndReasoningContent() {
        String content = "Hello, world!";
        String reasoningContent = "This is my reasoning";
        OpenRouterAssistantMessage message = OpenRouterAssistantMessage.builder()
                .content(content)
                .reasoningContent(reasoningContent)
                .build();

        assertThat(message.getText()).isEqualTo(content);
        assertThat(message.getReasoningContent()).isEqualTo(reasoningContent);
        assertThat(message.getReasoningDetails()).isNull();
    }

    @Test
    void testConstructorWithContentAndProperties() {
        String content = "Hello, world!";
        Map<String, Object> properties = new HashMap<>();
        properties.put("key1", "value1");
        properties.put("key2", 123);

        OpenRouterAssistantMessage message = OpenRouterAssistantMessage.builder()
                .content(content)
                .properties(properties)
                .build();

        assertThat(message.getText()).isEqualTo(content);
        assertThat(message.getMetadata()).containsAllEntriesOf(properties);
        assertThat(message.getReasoningContent()).isNull();
    }

    @Test
    void testConstructorWithContentPropertiesAndToolCalls() {
        String content = "Hello, world!";
        Map<String, Object> properties = new HashMap<>();
        properties.put("key1", "value1");

        List<ToolCall> toolCalls = List.of(new ToolCall("1", "function", "myFunction", "{}"));

        OpenRouterAssistantMessage message = OpenRouterAssistantMessage.builder()
                .content(content)
                .properties(properties)
                .toolCalls(toolCalls)
                .build();

        assertThat(message.getText()).isEqualTo(content);
        assertThat(message.getMetadata()).containsAllEntriesOf(properties);
        assertThat(message.getToolCalls()).isEqualTo(toolCalls);
        assertThat(message.getReasoningContent()).isNull();
    }

    @Test
    void testConstructorWithAllParameters() {
        String content = "Hello, world!";
        String reasoningContent = "This is my reasoning";
        List<ReasoningDetail> reasoningDetails = List.of(new ReasoningDetail(
                ReasoningDetail.ReasoningDetailType.TEXT, "id-1", 0, null, "reasoning text", null, null, null, null,
                null, null, null));
        Map<String, Object> properties = new HashMap<>();
        properties.put("key1", "value1");
        List<ToolCall> toolCalls = List.of(new ToolCall("1", "function", "myFunction", "{}"));

        OpenRouterAssistantMessage message = OpenRouterAssistantMessage.builder()
                .content(content)
                .reasoningContent(reasoningContent)
                .reasoningDetails(reasoningDetails)
                .properties(properties)
                .toolCalls(toolCalls)
                .build();

        assertThat(message.getText()).isEqualTo(content);
        assertThat(message.getReasoningContent()).isEqualTo(reasoningContent);
        assertThat(message.getReasoningDetails()).isEqualTo(reasoningDetails);
        assertThat(message.getMetadata()).containsAllEntriesOf(properties);
        assertThat(message.getToolCalls()).isEqualTo(toolCalls);
    }

    @Test
    void testSettersAndGetters() {
        OpenRouterAssistantMessage message = OpenRouterAssistantMessage.builder().build();

        String reasoningContent = "New reasoning content";
        List<ReasoningDetail> reasoningDetails = List.of(new ReasoningDetail(
                ReasoningDetail.ReasoningDetailType.SUMMARY, "id-1", 0, null, null, "summary", null, null, null, null,
                null, null));

        message.setReasoningContent(reasoningContent);
        message.setReasoningDetails(reasoningDetails);

        assertThat(message.getReasoningContent()).isEqualTo(reasoningContent);
        assertThat(message.getReasoningDetails()).isEqualTo(reasoningDetails);
    }

    @Test
    void testEqualsAndHashCode() {
        OpenRouterAssistantMessage message1 = OpenRouterAssistantMessage.builder()
                .content("content")
                .reasoningContent("reasoning")
                .build();
        OpenRouterAssistantMessage message2 = OpenRouterAssistantMessage.builder()
                .content("content")
                .reasoningContent("reasoning")
                .build();

        assertThat(message1).isEqualTo(message2);
        assertThat(message1.hashCode()).isEqualTo(message2.hashCode());

        OpenRouterAssistantMessage message3 = OpenRouterAssistantMessage.builder()
                .content("content")
                .reasoningContent("different reasoning")
                .build();

        assertThat(message1).isNotEqualTo(message3);
    }

    @Test
    void testToString() {
        OpenRouterAssistantMessage message = OpenRouterAssistantMessage.builder()
                .content("content")
                .reasoningContent("reasoning")
                .build();

        assertThatNoException().isThrownBy(message::toString);
        assertThat(message.toString()).contains("content", "reasoning");
    }

    @Test
    void testBuilderComplete() {
        Map<String, Object> properties = Map.of("key", "value");
        List<ToolCall> toolCalls = List.of(new ToolCall("1", "function", "testFunction", "{}"));
        List<Media> media = List.of();

        OpenRouterAssistantMessage.Builder builder = new OpenRouterAssistantMessage.Builder();
        OpenRouterAssistantMessage message = builder.content("content")
                .reasoningContent("reasoning")
                .properties(properties)
                .toolCalls(toolCalls)
                .media(media)
                .build();

        assertThat(message.getText()).isEqualTo("content");
        assertThat(message.getReasoningContent()).isEqualTo("reasoning");
        assertThat(message.getMetadata()).containsAllEntriesOf(properties);
        assertThat(message.getToolCalls()).isEqualTo(toolCalls);
        assertThat(message.getMedia()).isEqualTo(media);
    }

    @Test
    void mutateReplaysReasoningFields() {
        Map<String, Object> properties = Map.of("key", "value");
        List<ToolCall> toolCalls = List.of(new ToolCall("1", "function", "testFunction", "{}"));
        List<ReasoningDetail> reasoningDetails = List.of(new ReasoningDetail(
                ReasoningDetail.ReasoningDetailType.SUMMARY, "id-1", 0, null, null, "summary", null, null, null, null,
                null, null));

        OpenRouterAssistantMessage original = OpenRouterAssistantMessage.builder()
                .content("content")
                .reasoningContent("reasoning")
                .reasoningDetails(reasoningDetails)
                .properties(properties)
                .toolCalls(toolCalls)
                .build();

        OpenRouterAssistantMessage mutated = original.mutate().build();

        assertThat(mutated.getText()).isEqualTo("content");
        assertThat(mutated.getReasoningContent()).isEqualTo("reasoning");
        assertThat(mutated.getReasoningDetails()).isEqualTo(reasoningDetails);
        assertThat(mutated.getMetadata()).containsAllEntriesOf(properties);
        assertThat(mutated.getToolCalls()).isEqualTo(toolCalls);
    }

    @Test
    void copyKeepsSubclassAndReasoningFields() {
        OpenRouterAssistantMessage original = OpenRouterAssistantMessage.builder()
                .content("content")
                .reasoningContent("reasoning")
                .build();

        AssistantMessage copied = original.copy();

        // 复制后仍是子类，推理字段不会被基类复制逻辑丢掉
        assertThat(copied).isInstanceOf(OpenRouterAssistantMessage.class);
        assertThat(((OpenRouterAssistantMessage) copied).getReasoningContent()).isEqualTo("reasoning");
    }

}
