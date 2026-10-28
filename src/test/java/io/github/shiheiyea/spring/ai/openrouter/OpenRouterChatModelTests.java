package io.github.shiheiyea.spring.ai.openrouter;

import java.util.List;

import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.retry.support.RetryTemplate;
import io.github.shiheiyea.spring.ai.openrouter.api.OpenRouterApi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OpenRouterChatModelTests {

    private OpenRouterChatModel model(OpenRouterChatOptions defaults) {
        return new OpenRouterChatModel(mock(OpenRouterApi.class), defaults,
                ToolCallingManager.builder().build(), new RetryTemplate(), ObservationRegistry.NOOP);
    }

    @Test
    void transfersReasoningAndExplicitToolOptionsToRequest() {
        var options = OpenRouterChatOptions.builder().model("test-model")
                .effort("high").thinkMaxTokens(1024).exclude(false).enable(true)
                .tools(List.of(new OpenRouterApi.FunctionTool(
                        new OpenRouterApi.FunctionTool.Function("test", "description", java.util.Map.of()))))
                .toolChoice("auto").build();
        var model = model(options);
        var request = model.createRequest(model.buildRequestPrompt(new Prompt("hello")), true);

        assertThat(request.model()).isEqualTo("test-model");
        assertThat(request.stream()).isTrue();
        assertThat(request.messages()).hasSize(1);
        assertThat(request.reasoning()).isEqualTo(new OpenRouterApi.Think(
                OpenRouterApi.Effort.HIGH, 1024, false, true));
        assertThat(request.tools()).hasSize(1);
        assertThat(request.toolChoice()).isEqualTo("auto");
        var json = ModelOptionsUtils.objectToMap(options);
        assertThat(json).containsKey("reasoning")
                .doesNotContainKeys("effort", "think.max_Tokens", "thinkMaxTokens", "exclude", "enabled", "enable");
    }

    @Test
    void copiesAndMergesStandardChatOptionsWithoutInventingZeroDefaults() {
        var defaults = OpenRouterChatOptions.builder().model("default")
                .maxTokens(512).topP(0.8).topK(20).frequencyPenalty(0.1)
                .presencePenalty(0.2).stop(List.of("END")).stream(false).effort("LOW").build();
        var copy = defaults.copy();
        assertThat(ModelOptionsUtils.objectToMap(copy)).isEqualTo(ModelOptionsUtils.objectToMap(defaults));
        assertThat(copy.getStop()).isNotSameAs(defaults.getStop());
        var model = model(defaults);
        var runtime = ChatOptions.builder().temperature(0.7).maxTokens(256).build();
        var request = model.createRequest(model.buildRequestPrompt(new Prompt("hello", runtime)), false);

        assertThat(request.maxTokens()).isEqualTo(256);
        assertThat(request.temperature()).isEqualTo(0.7);
        assertThat(request.topP()).isEqualTo(0.8);
        assertThat(request.topK()).isEqualTo(20);
        assertThat(request.frequencyPenalty()).isEqualTo(0.1);
        assertThat(request.presencePenalty()).isEqualTo(0.2);
        assertThat(request.stop()).containsExactly("END");
        assertThat(request.reasoning().effort()).isEqualTo(OpenRouterApi.Effort.LOW);
        assertThat(ModelOptionsUtils.objectToMap(OpenRouterChatOptions.builder().build())).isEmpty();
    }

    @Test
    void runtimeReasoningReplacesDefaultReasoningAsAWhole() {
        var model = model(OpenRouterChatOptions.builder().effort("high").thinkMaxTokens(1024).build());
        var runtime = OpenRouterChatOptions.builder().enable(false).build();
        var request = model.createRequest(model.buildRequestPrompt(new Prompt("hello", runtime)), false);
        assertThat(request.reasoning()).isEqualTo(new OpenRouterApi.Think(null, null, null, false));
    }
}
