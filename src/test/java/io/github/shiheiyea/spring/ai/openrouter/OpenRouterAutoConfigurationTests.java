package io.github.shiheiyea.spring.ai.openrouter;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

class OpenRouterAutoConfigurationTests {

    @Test
    void discoversRelocatedModelThroughAutoConfigurationImports() {
        try (var context = new SpringApplicationBuilder(TestApplication.class)
                .web(WebApplicationType.NONE)
                .properties("spring.ai.model.chat=openrouter",
                        "spring.ai.openrouter.api-key=test-key")
                .run()) {
            assertThat(context.getBeansOfType(OpenRouterChatModel.class)).hasSize(1);
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {

        @Bean
        RestClient.Builder restClientBuilder() {
            return RestClient.builder().requestFactory(new SimpleClientHttpRequestFactory());
        }

        @Bean
        WebClient.Builder webClientBuilder() {
            // This test checks configuration discovery without creating a network client.
            return WebClient.builder().clientConnector((method, uri, callback) ->
                    Mono.error(new AssertionError("No network requests expected")));
        }
    }
}
