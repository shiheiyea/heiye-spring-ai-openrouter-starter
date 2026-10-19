package world.heiye.spring.ai.openrouter.config;

/**
 * @author: heiye
 * @date: 2026/04/18 12:12
 * @version: v1.0.0
 * @description: OpenRouter 父级配置类
 */
public class OpenRouterParentProperties {
    private String apiKey;
    private String baseUrl;

    public String getApiKey() {
        return this.apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return this.baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
}
