package world.heiye.spring.ai.openrouter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author: heiye
 * @date: 2026/04/17 22:31
 * @version: v1.0.0
 * @description: OpenRouter 链接配置类, 该配置作为兜底配置，优先级低于 OpenRouterChatProperties
 */
@ConfigurationProperties("spring.ai.openrouter")
public class OpenRouterConnectionProperties extends OpenRouterParentProperties{
    public static final String CONFIG_PREFIX = "spring.ai.openrouter";
    public static final String DEFAULT_BASE_URL = "https://openrouter.ai";

    public OpenRouterConnectionProperties() {
        super.setBaseUrl("https://openrouter.ai");
    }
}
