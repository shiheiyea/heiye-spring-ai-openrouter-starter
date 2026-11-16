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

package io.github.shiheiyea.spring.ai.openrouter;

import io.github.shiheiyea.spring.ai.openrouter.api.OpenRouterApi;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

/**
 * 真实网络测试的装配类，密钥从 OPENROUTER_API_KEY 环境变量读取。
 */
@SpringBootConfiguration
public class OpenRouterTestConfiguration {

    @Bean
    public OpenRouterApi openRouterApi() {
        return OpenRouterApi.builder().apiKey(getApiKey()).build();
    }

    private String getApiKey() {
        String apiKey = System.getenv("OPENROUTER_API_KEY");
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalArgumentException(
                    "You must provide an API key. Put it in an environment variable named OPENROUTER_API_KEY");
        }
        return apiKey;
    }

    @Bean
    public OpenRouterChatModel openRouterChatModel(OpenRouterApi openRouterApi) {
        return OpenRouterChatModel.builder().openRouterApi(openRouterApi).build();
    }
}
