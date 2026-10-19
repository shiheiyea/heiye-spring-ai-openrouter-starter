package world.heiye.spring.ai.openrouter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import world.heiye.spring.ai.openrouter.OpenRouterChatOptions;
import world.heiye.spring.ai.openrouter.api.OpenRouterApi.ChatModel;

/**
 * @author: heiye
 * @date: 2026/04/18 01:32
 * @version: v1.0.0
 * @description: OpenRouter 聊天配置， 继承 OpenRouterParentProperties
 *              用于在 yml 文件中配置相关参数
 */
// 声明此类绑定配置文件中以 spring.ai.openrouter.chat 为前缀的属性。例如 spring.ai.openrouter.chat.enabled=true 会映射到类的 enabled 字段
@ConfigurationProperties("spring.ai.openrouter.chat")
public class OpenRouterChatProperties extends OpenRouterParentProperties{
    public static final String CONFIG_PREFIX = "spring.ai.openrouter.chat";
    public static final String DEFAULT_CHAT_MODEL;
    private static final Double DEFAULT_TEMPERATURE;
    public static final String DEFAULT_COMPLETIONS_PATH = "/api/v1/chat/completions";
    private boolean enabled = true;
    private String completionsPath = "/api/v1/chat/completions";

    // 标记这是嵌套配置对象
    @NestedConfigurationProperty
    private final OpenRouterChatOptions options;

    public OpenRouterChatProperties() {
        this.options = OpenRouterChatOptions.builder()
                .model(DEFAULT_CHAT_MODEL)
                .temperature(DEFAULT_TEMPERATURE)
                .build();
    }

    static {
        DEFAULT_CHAT_MODEL = ChatModel.GOOGLE_GEMINI_3_FLASH_PREVIEW.getValue();
        DEFAULT_TEMPERATURE = (double)1.0F;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getCompletionsPath() {
        return completionsPath;
    }

    public void setCompletionsPath(String completionsPath) {
        this.completionsPath = completionsPath;
    }

    public OpenRouterChatOptions getOptions() {
        return options;
    }
}
