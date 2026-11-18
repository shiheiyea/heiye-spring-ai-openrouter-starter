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

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.util.JsonHelper;

import io.github.shiheiyea.spring.ai.openrouter.api.OpenRouterApi;
import io.github.shiheiyea.spring.ai.openrouter.api.OpenRouterApi.ChatCompletionMessage;
import io.github.shiheiyea.spring.ai.openrouter.api.OpenRouterApi.ChatCompletionRequest;
import io.github.shiheiyea.spring.ai.openrouter.api.OpenRouterApi.Reasoning;
import io.github.shiheiyea.spring.ai.openrouter.api.OpenRouterApi.ReasoningDetail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenRouterChatCompletionRequestTests {

    private final OpenRouterChatModel client = OpenRouterChatModel.builder()
            .openRouterApi(OpenRouterApi.builder().apiKey("TEST").build())
            .build();

    @Test
    void createRequestWithChatOptions() {
        Prompt prompt = new Prompt("Test message content",
                OpenRouterChatOptions.builder().model("DEFAULT_MODEL").temperature(66.6).build());

        ChatCompletionRequest request = this.client.createRequest(prompt, false);

        assertThat(request.messages()).hasSize(1);
        assertThat(request.stream()).isFalse();
        assertThat(request.model()).isEqualTo("DEFAULT_MODEL");
        assertThat(request.temperature()).isEqualTo(66.6D);

        request = this.client.createRequest(new Prompt("Test message content",
                OpenRouterChatOptions.builder().model("PROMPT_MODEL").temperature(99.9D).build()), true);

        assertThat(request.messages()).hasSize(1);
        assertThat(request.stream()).isTrue();
        assertThat(request.model()).isEqualTo("PROMPT_MODEL");
        assertThat(request.temperature()).isEqualTo(99.9D);
    }

    @Test
    void createRequestWithDefaultOptions() {
        ChatCompletionRequest request = this.client.createRequest(
                new Prompt("Test message content", OpenRouterChatOptions.builder().build()), false);

        assertThat(request.messages()).hasSize(1);
        assertThat(request.model()).isEqualTo(OpenRouterApi.DEFAULT_CHAT_MODEL.getValue());
        assertThat(request.temperature()).isEqualTo(0.7D);
    }

    @Test
    void createRequestWithMaxCompletionTokens() {
        Prompt prompt = new Prompt("Test message content", OpenRouterChatOptions.builder().maxTokens(64).build());

        ChatCompletionRequest request = this.client.createRequest(prompt, false);

        assertThat(request.maxTokens()).isEqualTo(64);
    }

    @Test
    void createRequestWithReasoningContent() {
        OpenRouterAssistantMessage assistantMessage = OpenRouterAssistantMessage.builder()
                .content("The answer is 42")
                .reasoningContent("Let me think about this step by step...")
                .build();

        Prompt prompt = new Prompt(List.of(assistantMessage),
                OpenRouterChatOptions.builder().model("PROMPT_MODEL").build());

        ChatCompletionRequest request = this.client.createRequest(prompt, false);

        assertThat(request.messages()).hasSize(1);
        ChatCompletionMessage message = request.messages().get(0);
        assertThat(message.role()).isEqualTo(ChatCompletionMessage.Role.ASSISTANT);
        assertThat(message.content()).isEqualTo("The answer is 42");
        assertThat(message.reasoning()).isEqualTo("Let me think about this step by step...");
    }

    @Test
    void createRequestWithNullReasoningContent() {
        OpenRouterAssistantMessage assistantMessage = OpenRouterAssistantMessage.builder()
                .content("The answer is 42")
                .build();

        Prompt prompt = new Prompt(List.of(assistantMessage),
                OpenRouterChatOptions.builder().model("PROMPT_MODEL").build());

        ChatCompletionRequest request = this.client.createRequest(prompt, false);

        assertThat(request.messages()).hasSize(1);
        assertThat(request.messages().get(0).reasoning()).isNull();
    }

    @Test
    void createRequestWithReasoningDetails() {
        List<ReasoningDetail> reasoningDetails = List.of(
                new ReasoningDetail(ReasoningDetail.ReasoningDetailType.TEXT, "reasoning-1", 0, null, "step one", null,
                        null, "sig", null, null, null, null),
                new ReasoningDetail(ReasoningDetail.ReasoningDetailType.SUMMARY, "reasoning-2", 0, null, null,
                        "summary text", null, null, null, null, null, null));

        OpenRouterAssistantMessage assistantMessage = OpenRouterAssistantMessage.builder()
                .content("The answer is 42")
                .reasoningDetails(reasoningDetails)
                .build();

        Prompt prompt = new Prompt(List.of(assistantMessage),
                OpenRouterChatOptions.builder().model("PROMPT_MODEL").build());

        ChatCompletionRequest request = this.client.createRequest(prompt, false);

        assertThat(request.messages().get(0).reasoningDetails()).hasSize(2);
        assertThat(request.messages().get(0).reasoningDetails().get(0).type())
                .isEqualTo(ReasoningDetail.ReasoningDetailType.TEXT);
        assertThat(request.messages().get(0).reasoningDetails().get(1).summary()).isEqualTo("summary text");
    }

    @Test
    void createRequestWithSystemMessage() {
        Prompt prompt = new Prompt(List.of(new SystemMessage("system prompt"), new UserMessage("user prompt")),
                OpenRouterChatOptions.builder().build());

        ChatCompletionRequest request = this.client.createRequest(prompt, false);

        assertThat(request.messages()).hasSize(2);
        assertThat(request.messages().get(0).role()).isEqualTo(ChatCompletionMessage.Role.SYSTEM);
        assertThat(request.messages().get(0).content()).isEqualTo("system prompt");
        assertThat(request.messages().get(1).role()).isEqualTo(ChatCompletionMessage.Role.USER);
    }

    @Test
    void createRequestWithExtraBody() {
        Prompt prompt = new Prompt("Test message content",
                OpenRouterChatOptions.builder()
                        .extraBody(Map.of("debug", Map.of("echo_upstream_body", true)))
                        .build());

        ChatCompletionRequest request = this.client.createRequest(prompt, false);

        assertThat(request.extraBody()).containsKey("debug");
        assertThat(request.extraBodyProperties()).containsKey("debug");
    }

    @Test
    void createRequestWithReasoningEffort() {
        Prompt prompt = new Prompt("Test message content", OpenRouterChatOptions.builder()
                .reasoning(new Reasoning(Reasoning.Effort.HIGH, null, null, null, null, null, null))
                .build());

        ChatCompletionRequest request = this.client.createRequest(prompt, false);

        assertThat(request.reasoning()).isNotNull();
        assertThat(request.reasoning().effort()).isEqualTo(Reasoning.Effort.HIGH);
    }

    @Test
    void createRequestWithToolDefinitions() {
        OpenRouterApi.FunctionTool tool = new OpenRouterApi.FunctionTool(OpenRouterApi.FunctionTool.Type.FUNCTION,
                new OpenRouterApi.FunctionTool.Function("weather", "Get the weather", "{}"));

        Prompt prompt = new Prompt("Test message content",
                OpenRouterChatOptions.builder().tools(List.of(tool)).build());

        ChatCompletionRequest request = this.client.createRequest(prompt, false);

        assertThat(request.tools()).hasSize(1);
        assertThat(request.tools().get(0).getFunction().getName()).isEqualTo("weather");
    }

    @Test
    void createRequestWithoutResponseFormat() {
        ChatCompletionRequest request = this.client.createRequest(new Prompt("Test message content",
                OpenRouterChatOptions.builder().build()), false);

        assertThat(request.responseFormat()).isNull();
    }

    @Test
    void createRequestWithOutputSchema() {
        ChatCompletionRequest request = this.client.createRequest(new Prompt("Test message content",
                OpenRouterChatOptions.builder()
                        .outputSchema("{\"type\":\"object\",\"properties\":{\"title\":{\"type\":\"string\"}}}")
                        .build()),
                false);

        OpenRouterApi.ResponseFormat responseFormat = request.responseFormat();
        assertThat(responseFormat).isNotNull();
        assertThat(responseFormat.type()).isEqualTo(OpenRouterApi.ResponseFormat.Type.JSON_SCHEMA);
        assertThat(responseFormat.jsonSchema().name()).isEqualTo(OpenRouterApi.JSON_SCHEMA_NAME);
        assertThat(responseFormat.jsonSchema().strict()).isTrue();
        assertThat(responseFormat.jsonSchema().schema()).containsKey("type");
        assertThat(responseFormat.jsonSchema().schema().get("type")).isEqualTo("object");
    }

    @Test
    void createRequestWithBlankOutputSchema() {
        for (String blank : new String[] { "", "   " }) {
            ChatCompletionRequest request = this.client.createRequest(new Prompt("Test message content",
                    OpenRouterChatOptions.builder().outputSchema(blank).build()), false);

            assertThat(request.responseFormat()).isNull();
        }

        ChatCompletionRequest request = this.client.createRequest(new Prompt("Test message content",
                OpenRouterChatOptions.builder().outputSchema(null).build()), false);

        assertThat(request.responseFormat()).isNull();
    }

    @Test
    void createRequestWithInvalidOutputSchema() {
        String invalid = "{not-a-json";

        assertThatThrownBy(() -> this.client.createRequest(new Prompt("Test message content",
                OpenRouterChatOptions.builder().outputSchema(invalid).build()), false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Failed to parse JSON schema")
            .hasMessageContaining(invalid);
    }

    @Test
    void createRequestWithJsonSchemaNullLiteral() {
        assertThatThrownBy(() -> this.client.createRequest(new Prompt("Test message content",
                OpenRouterChatOptions.builder().outputSchema("null").build()), false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Failed to parse JSON schema");
    }

    @Test
    void createRequestWithEmptySchemaObject() {
        ChatCompletionRequest request = this.client.createRequest(new Prompt("Test message content",
                OpenRouterChatOptions.builder().outputSchema("{}").build()), false);

        assertThat(request.responseFormat()).isNotNull();
        assertThat(request.responseFormat().jsonSchema().schema()).isEmpty();
    }

    @Test
    void createRequestWithCustomNameAndStrict() {
        ChatCompletionRequest request = this.client.createRequest(new Prompt("Test message content",
                OpenRouterChatOptions.builder()
                        .responseFormat(OpenRouterChatOptions.ResponseFormat.builder()
                                .jsonSchema("{}")
                                .name("my_schema")
                                .strict(false)
                                .build())
                        .build()),
                false);

        assertThat(request.responseFormat().jsonSchema().name()).isEqualTo("my_schema");
        assertThat(request.responseFormat().jsonSchema().strict()).isFalse();
    }

    @Test
    void createRequestWithJsonObjectType() {
        ChatCompletionRequest request = this.client.createRequest(new Prompt("Test message content",
                OpenRouterChatOptions.builder()
                        .responseFormat(OpenRouterChatOptions.ResponseFormat.builder()
                                .type(OpenRouterApi.ResponseFormat.Type.JSON_OBJECT)
                                .build())
                        .build()),
                false);

        assertThat(request.responseFormat()).isNotNull();
        assertThat(request.responseFormat().type()).isEqualTo(OpenRouterApi.ResponseFormat.Type.JSON_OBJECT);
        assertThat(request.responseFormat().jsonSchema()).isNull();
    }

    @Test
    void createRequestWithTextType() {
        ChatCompletionRequest request = this.client.createRequest(new Prompt("Test message content",
                OpenRouterChatOptions.builder()
                        .responseFormat(OpenRouterChatOptions.ResponseFormat.builder()
                                .type(OpenRouterApi.ResponseFormat.Type.TEXT)
                                .build())
                        .build()),
                false);

        assertThat(request.responseFormat()).isNotNull();
        assertThat(request.responseFormat().type()).isEqualTo(OpenRouterApi.ResponseFormat.Type.TEXT);
    }

    @Test
    void responseFormatSerializesAsJsonObject() {
        ChatCompletionRequest request = this.client.createRequest(new Prompt("Test message content",
                OpenRouterChatOptions.builder()
                        .outputSchema("{\"$schema\":\"https://json-schema.org/draft/2020-12/schema\","
                                + "\"type\":\"object\",\"properties\":{\"title\":{\"type\":\"string\"}}}")
                        .build()),
                false);

        JsonHelper jsonHelper = new JsonHelper();
        String json = jsonHelper.toJson(request);

        assertThat(json).contains("\"type\":\"json_schema\"");
        assertThat(json).contains("\"name\":\"" + OpenRouterApi.JSON_SCHEMA_NAME + "\"");
        assertThat(json).contains("\"strict\":true");
        assertThat(json).contains("\"$schema\"");
        // schema 必须是嵌套对象，不能是被转义的字符串
        assertThat(json).contains("\"schema\":{");

        ChatCompletionRequest jsonObjectRequest = this.client.createRequest(new Prompt("Test message content",
                OpenRouterChatOptions.builder()
                        .responseFormat(OpenRouterChatOptions.ResponseFormat.builder()
                                .type(OpenRouterApi.ResponseFormat.Type.JSON_OBJECT)
                                .build())
                        .build()),
                false);

        assertThat(jsonHelper.toJson(jsonObjectRequest)).doesNotContain("json_schema");
    }

    @Test
    void outputSchemaSurvivesToolBranch() {
        OpenRouterApi.FunctionTool tool = new OpenRouterApi.FunctionTool(OpenRouterApi.FunctionTool.Type.FUNCTION,
                new OpenRouterApi.FunctionTool.Function("weather", "Get the weather", "{}"));

        ChatCompletionRequest request = this.client.createRequest(new Prompt("Test message content",
                OpenRouterChatOptions.builder().tools(List.of(tool)).outputSchema("{\"type\":\"object\"}").build()),
                false);

        assertThat(request.tools()).hasSize(1);
        assertThat(request.responseFormat()).isNotNull();
        assertThat(request.responseFormat().jsonSchema().schema()).containsKey("type");
    }

}
