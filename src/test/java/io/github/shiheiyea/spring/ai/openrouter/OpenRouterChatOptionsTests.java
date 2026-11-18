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

import org.springframework.ai.model.tool.StructuredOutputChatOptions;
import org.springframework.ai.test.options.AbstractChatOptionsTests;

import io.github.shiheiyea.spring.ai.openrouter.OpenRouterChatOptions.Builder;
import io.github.shiheiyea.spring.ai.openrouter.api.OpenRouterApi;

import static org.assertj.core.api.Assertions.assertThat;

class OpenRouterChatOptionsTests extends AbstractChatOptionsTests<OpenRouterChatOptions, Builder> {

    @Override
    protected Class<OpenRouterChatOptions> getConcreteOptionsClass() {
        return OpenRouterChatOptions.class;
    }

    @Override
    protected Builder readyToBuildBuilder() {
        return OpenRouterChatOptions.builder().model(OpenRouterApi.DEFAULT_CHAT_MODEL);
    }

    @Test
    void testTemperatureDefaultsTo07() {
        assertThat(OpenRouterChatOptions.builder().build().getTemperature()).isEqualTo(0.7);
        assertThat(OpenRouterChatOptions.builder().temperature(0.2).build().getTemperature()).isEqualTo(0.2);
    }

    @Test
    void testModelDefaultsToOpenRouterDefault() {
        assertThat(OpenRouterChatOptions.builder().build().getModel())
                .isEqualTo(OpenRouterApi.DEFAULT_CHAT_MODEL.getValue());
    }

    @Test
    void testCombineWithCollections() {
        OpenRouterApi.FunctionTool baseTool = functionTool("base-function");
        OpenRouterApi.FunctionTool overrideTool = functionTool("override-function");

        OpenRouterChatOptions base = OpenRouterChatOptions.builder().tools(List.of(baseTool)).build();
        OpenRouterChatOptions override = OpenRouterChatOptions.builder().tools(List.of(overrideTool)).build();

        OpenRouterChatOptions merged = base.mutate().combineWith(override.mutate()).build();

        assertThat(merged.getTools()).containsExactlyInAnyOrder(baseTool, overrideTool);
    }

    @Test
    void testCombineWithExtraBody() {
        OpenRouterChatOptions base = OpenRouterChatOptions.builder()
                .extraBody(Map.of("debug", Map.of("echo_upstream_body", true), "trace", Map.of("enabled", true)))
                .build();
        OpenRouterChatOptions override = OpenRouterChatOptions.builder()
                .extraBody(Map.of("trace", Map.of("enabled", false)))
                .build();

        OpenRouterChatOptions merged = base.mutate().combineWith(override.mutate()).build();

        assertThat(merged.getExtraBody()).containsKeys("debug", "trace");
        assertThat(merged.getExtraBody().get("trace")).isEqualTo(Map.of("enabled", false));
    }

    @Test
    void testCombineWithScalarOverride() {
        OpenRouterChatOptions base = OpenRouterChatOptions.builder().temperature(0.2).topP(0.9).build();
        OpenRouterChatOptions override = OpenRouterChatOptions.builder().temperature(0.8).build();

        OpenRouterChatOptions merged = base.mutate().combineWith(override.mutate()).build();

        assertThat(merged.getTemperature()).isEqualTo(0.8);
        assertThat(merged.getTopP()).isEqualTo(0.9);
    }

    @Test
    void testExtraBodyIsDefensivelyCopied() {
        Map<String, Object> extraBody = new java.util.HashMap<>();
        extraBody.put("debug", Map.of("echo_upstream_body", true));

        OpenRouterChatOptions options = OpenRouterChatOptions.builder().extraBody(extraBody).build();
        extraBody.put("route", "fallback");

        assertThat(options.getExtraBody()).doesNotContainKey("route");
    }

    @Test
    void testLogitBiasIsDefensivelyCopied() {
        Map<String, Number> logitBias = new java.util.HashMap<>();
        logitBias.put("50256", -100);

        OpenRouterChatOptions options = OpenRouterChatOptions.builder().logitBias(logitBias).build();
        logitBias.put("1234", 5);

        assertThat(options.getLogitBias()).containsOnlyKeys("50256");
    }

    @Test
    void testCombineWithLogitBias() {
        OpenRouterChatOptions base = OpenRouterChatOptions.builder().logitBias(Map.of("50256", -100)).build();
        OpenRouterChatOptions override = OpenRouterChatOptions.builder().logitBias(Map.of("1234", 5)).build();

        assertThat(base.mutate().combineWith(override.mutate()).build().getLogitBias())
                .containsOnlyKeys("1234");
        assertThat(base.mutate().combineWith(OpenRouterChatOptions.builder().build().mutate()).build()
                .getLogitBias()).containsOnlyKeys("50256");
    }

    @Test
    void testStopSequencesAlias() {
        OpenRouterChatOptions options = OpenRouterChatOptions.builder().stop(List.of("STOP")).build();

        assertThat(options.getStopSequences()).containsExactly("STOP");
    }

    @Test
    void testResponseFormatDefaultsToNull() {
        OpenRouterChatOptions options = OpenRouterChatOptions.builder().build();

        assertThat(options.getResponseFormat()).isNull();
        assertThat(options.getOutputSchema()).isNull();
    }

    @Test
    void testOutputSchemaWritesThroughToResponseFormat() {
        String schema = "{\"type\":\"object\",\"properties\":{\"title\":{\"type\":\"string\"}}}";

        OpenRouterChatOptions options = OpenRouterChatOptions.builder().outputSchema(schema).build();

        assertThat(options.getResponseFormat()).isNotNull();
        assertThat(options.getResponseFormat().getType())
                .isEqualTo(OpenRouterApi.ResponseFormat.Type.JSON_SCHEMA);
        // 逐字相等，证明中间没有重新序列化
        assertThat(options.getResponseFormat().getJsonSchema()).isEqualTo(schema);
        assertThat(options.getOutputSchema()).isEqualTo(schema);
    }

    @Test
    void testOutputSchemaRoundTripsThroughMutate() {
        String schema = "{\"type\":\"object\"}";

        OpenRouterChatOptions options = OpenRouterChatOptions.builder().outputSchema(schema).build();
        OpenRouterChatOptions mutated = options.mutate().build();

        assertThat(mutated.getOutputSchema()).isEqualTo(schema);
        assertThat(mutated).isEqualTo(options);
    }

    @Test
    void testResponseFormatSurvivesClone() {
        String schema = "{\"type\":\"object\"}";

        OpenRouterChatOptions options = OpenRouterChatOptions.builder().outputSchema(schema).build();
        OpenRouterChatOptions.Builder cloned = options.mutate().clone();

        assertThat(cloned.build().getOutputSchema()).isEqualTo(schema);
    }

    @Test
    void testResponseFormatSurvivesCombineWith() {
        String schema = "{\"type\":\"object\"}";

        OpenRouterChatOptions base = OpenRouterChatOptions.builder().build();
        OpenRouterChatOptions override = OpenRouterChatOptions.builder().outputSchema(schema).build();

        OpenRouterChatOptions merged = base.mutate().combineWith(override.mutate()).build();

        assertThat(merged.getOutputSchema()).isEqualTo(schema);
    }

    @Test
    void testImplementsStructuredOutputChatOptions() {
        assertThat(OpenRouterChatOptions.class).isAssignableTo(StructuredOutputChatOptions.class);

        StructuredOutputChatOptions structured = OpenRouterChatOptions.builder().build();

        StructuredOutputChatOptions built = (StructuredOutputChatOptions) structured.mutate().outputSchema("{}").build();

        assertThat(built.getOutputSchema()).isEqualTo("{}");
    }

    @Test
    void testResponseFormatTypeAndStrictAreSettable() {
        OpenRouterChatOptions options = OpenRouterChatOptions.builder()
                .responseFormat(OpenRouterChatOptions.ResponseFormat.builder()
                        .type(OpenRouterApi.ResponseFormat.Type.JSON_OBJECT)
                        .strict(false)
                        .name("my_schema")
                        .build())
                .build();

        assertThat(options.getResponseFormat().getType())
                .isEqualTo(OpenRouterApi.ResponseFormat.Type.JSON_OBJECT);
        assertThat(options.getResponseFormat().getStrict()).isFalse();
        assertThat(options.getResponseFormat().getName()).isEqualTo("my_schema");
    }

    @Test
    void testResponseFormatEqualsAndHashCode() {
        OpenRouterChatOptions.ResponseFormat first = OpenRouterChatOptions.ResponseFormat.builder()
                .jsonSchema("{\"type\":\"object\"}")
                .build();
        OpenRouterChatOptions.ResponseFormat second = OpenRouterChatOptions.ResponseFormat.builder()
                .jsonSchema("{\"type\":\"object\"}")
                .build();

        assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
        assertThat(first.toString()).contains("JSON_SCHEMA");
    }

    private OpenRouterApi.FunctionTool functionTool(String name) {
        return new OpenRouterApi.FunctionTool(OpenRouterApi.FunctionTool.Type.FUNCTION,
                new OpenRouterApi.FunctionTool.Function(name, "", "{}"));
    }

}
