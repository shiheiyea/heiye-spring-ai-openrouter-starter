package world.heiye.spring.ai.openrouter.aot;

import org.springframework.ai.aot.AiRuntimeHints;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import world.heiye.spring.ai.openrouter.api.OpenRouterApi;

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
