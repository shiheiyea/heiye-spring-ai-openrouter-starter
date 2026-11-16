/*
 * Copyright 2023-present the original author or authors.
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

package io.github.shiheiyea.spring.ai.openrouter.chat;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import io.github.shiheiyea.spring.ai.openrouter.OpenRouterChatModel;
import io.github.shiheiyea.spring.ai.openrouter.OpenRouterChatOptions;
import io.github.shiheiyea.spring.ai.openrouter.OpenRouterTestConfiguration;
import io.github.shiheiyea.spring.ai.openrouter.api.OpenRouterApi;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 工具调用的集成测试，需要 OPENROUTER_API_KEY 环境变量，默认关闭。
 */
@Disabled("Requires a real OPENROUTER_API_KEY and network access")
@SpringBootTest(classes = OpenRouterTestConfiguration.class)
class OpenRouterChatModelFunctionCallingIT {

    private static final OpenRouterApi.FunctionTool FUNCTION_TOOL = new OpenRouterApi.FunctionTool(
            OpenRouterApi.FunctionTool.Type.FUNCTION, new OpenRouterApi.FunctionTool.Function(
                    "Get the weather in location. Return temperature in 30°F or 30°C format.", "getCurrentWeather", """
                            {
                                "type": "object",
                                "properties": {
                                    "location": {
                                        "type": "string",
                                        "description": "The city and state e.g. San Francisco, CA"
                                    },
                                    "lat": {
                                        "type": "number",
                                        "description": "The city latitude"
                                    },
                                    "lon": {
                                        "type": "number",
                                        "description": "The city longitude"
                                    },
                                    "unit": {
                                        "type": "string",
                                        "enum": ["C", "F"]
                                    }
                                },
                                "required": ["location", "lat", "lon", "unit"]
                            }
                            """));

    @Autowired
    OpenRouterChatModel chatModel;

    @Test
    void toolFunctionCallWithUsage() {
        OpenRouterChatOptions promptOptions = OpenRouterChatOptions.builder()
                .tools(Arrays.asList(FUNCTION_TOOL))
                .toolCallbacks(List.of(FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
                        .description("Get the weather in location")
                        .inputType(MockWeatherService.Request.class)
                        .build()))
                .build();

        ToolCallingManager toolCallingManager = DefaultToolCallingManager.builder().build();

        Prompt prompt = new Prompt("What's the weather like in San Francisco? Return the temperature in Celsius.",
                promptOptions);

        ChatResponse chatResponse = this.chatModel.call(prompt);

        while (chatResponse.hasToolCalls()) {
            ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, chatResponse);
            prompt = new Prompt(toolExecutionResult.conversationHistory(), promptOptions);
            chatResponse = this.chatModel.call(prompt);
        }

        assertThat(chatResponse).isNotNull();
        assertThat(chatResponse.getResult().getOutput().getText()).contains("San Francisco").contains("30");
        assertThat(chatResponse.getMetadata().getUsage()).isNotNull();
    }

    @Test
    void streamFunctionCallUsage() {
        OpenRouterChatOptions promptOptions = OpenRouterChatOptions.builder()
                .tools(Arrays.asList(FUNCTION_TOOL))
                .toolCallbacks(List.of(FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
                        .description("Get the weather in location")
                        .inputType(MockWeatherService.Request.class)
                        .build()))
                .build();

        ToolCallingManager toolCallingManager = DefaultToolCallingManager.builder().build();

        Prompt prompt = new Prompt("What's the weather like in San Francisco? Return the temperature in Celsius.",
                promptOptions);

        AtomicReference<ChatResponse> aggregatedRef = new AtomicReference<>();
        new MessageAggregator().aggregate(this.chatModel.stream(prompt), aggregatedRef::set).collectList().block();

        while (aggregatedRef.get().hasToolCalls()) {
            ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, aggregatedRef.get());
            prompt = new Prompt(toolExecutionResult.conversationHistory(), promptOptions);
            aggregatedRef.set(null);
            new MessageAggregator().aggregate(this.chatModel.stream(prompt), aggregatedRef::set).collectList().block();
        }

        ChatResponse chatResponse = aggregatedRef.get();

        assertThat(chatResponse).isNotNull();
        assertThat(chatResponse.getMetadata()).isNotNull();
        assertThat(chatResponse.getMetadata().getUsage()).isNotNull();
    }
}
