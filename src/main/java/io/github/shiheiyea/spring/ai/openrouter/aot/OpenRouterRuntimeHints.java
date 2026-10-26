/*
 * Copyright 2023-2025 the original author or authors.
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

package io.github.shiheiyea.spring.ai.openrouter.aot;

import org.springframework.ai.aot.AiRuntimeHints;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import io.github.shiheiyea.spring.ai.openrouter.api.OpenRouterApi;

/**
 * @author: heiye
 * @date: 2026/04/18 15:37
 * @version: v1.0.0
 * @description: OpenRouter 运行时提示，在构建期扫描 Jackson 相关的 DTO 类，并向 GraalVM 注册"这些类需要保留反射信息"。
 */
public class OpenRouterRuntimeHints implements RuntimeHintsRegistrar {
    /**
     * 注册运行时提示
     *
     * @param hints
     * @param classLoader
     */
    @Override
    public void registerHints(@NonNull RuntimeHints hints, @Nullable ClassLoader classLoader) {
        // 获取所有 MemberCategory
        MemberCategory[] mcs = MemberCategory.values();

        // 扫描所有有 Json 注解的类
        for(TypeReference tr : AiRuntimeHints.findJsonAnnotatedClassesInPackage(OpenRouterApi.class)) {
            // 注册类
            hints.reflection().registerType(tr, mcs);
        }
    }
}
