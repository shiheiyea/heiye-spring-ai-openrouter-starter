package world.heiye.spring.ai.openrouter.config;

import lombok.Data;

/**
 * @author: heiye
 * @date: 2026/04/18 12:12
 * @version: v1.0.0
 * @description: OpenRouter 父级配置类
 */
@Data
public class OpenRouterParentProperties {
    private String apiKey;
    private String baseUrl;
}
