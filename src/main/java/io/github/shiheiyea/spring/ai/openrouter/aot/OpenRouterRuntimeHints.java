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

package io.github.shiheiyea.spring.ai.openrouter.aot;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.aot.AiRuntimeHints;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;

import io.github.shiheiyea.spring.ai.openrouter.api.OpenRouterApi;

public class OpenRouterRuntimeHints implements RuntimeHintsRegistrar {

    /**
     * 注册运行时提示。
     *
     * 响应解析依赖反射把 JSON 映射成内部 record，原生镜像下必须把这些类型连同成员一并登记。
     */
    @Override
    public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
        // 协议类型全部是 OpenRouterApi 的内部类型，按包扫描即可覆盖
        MemberCategory[] memberCategories = MemberCategory.values();
        for (TypeReference typeReference : AiRuntimeHints.findJsonAnnotatedClassesInPackage(OpenRouterApi.class)) {
            hints.reflection().registerType(typeReference, memberCategories);
        }
    }

}
