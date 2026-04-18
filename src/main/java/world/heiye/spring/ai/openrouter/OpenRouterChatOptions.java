package world.heiye.spring.ai.openrouter;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import world.heiye.spring.ai.openrouter.api.OpenRouterApi;

import java.util.*;

/**
 * @author: heiye
 * @date: 2025/11/11 下午10:14
 * @version: v1.0.0
 * @description:
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OpenRouterChatOptions implements ToolCallingChatOptions {
    /**
     * 模型名称
     */
    @JsonProperty("model")
    private String model;

    /**
     * 此设置会影响模型响应的多样性。较低的值会导致更可预测和典型的响应，
     * 而较高的值则鼓励更多样化和不太常见的响应。
     * 在 0 时，模型始终对给定输入给出相同的响应。
     * Range: [0, 2]
     */
    @JsonProperty("temperature")
    private Double temperature;

    /**
     * 是否流式返回结果
     */
    @JsonProperty("stream")
    private Boolean stream;

//    /**
//     * 结构化输出响应格式
//     */
//    @JsonProperty("response_format")
//    Object responseFormat;
//
//    /**
//     * 如果模型遇到停止数组中指定的任何标记，则立即停止生成。
//     */
//    @JsonProperty("stop")
//    List<String> stop;

    /**
     * 愿意分配多少 token 用于推理
     */
    @JsonProperty("effort")
    String effort;

    /**
     * 推理的最大 token 数量
     */
    @JsonProperty("think.max_Tokens")
    Integer thinkMaxTokens;

    // 是否排除推理结果
    @JsonProperty("exclude")
    Boolean exclude;

    // 是否启用推理
    @JsonProperty("enabled")
    Boolean enable;

    @JsonProperty("tools")
    private List<OpenRouterApi.FunctionTool> tools;

    @JsonProperty("tool_choice")
    private Object toolChoice;

    // 内部工具执行
    @JsonIgnore
    private Boolean internalToolExecutionEnabled;

    // 工具调用
    @JsonIgnore
    private List<ToolCallback> toolCallbacks = new ArrayList();

    // 工具名称
    @JsonIgnore
    private Set<String> toolNames = new HashSet();

    // 工具内容
    @JsonIgnore
    private Map<String, Object> toolContext = new HashMap();

    public static Builder builder() {
        return new Builder();
    }

    public List<OpenRouterApi.FunctionTool> getTools() {
        return this.tools;
    }

    public void setTools(List<OpenRouterApi.FunctionTool> tools) {
        this.tools = tools;
    }

    public Object getToolChoice() {
        return this.toolChoice;
    }

    public void setToolChoice(Object toolChoice) {
        this.toolChoice = toolChoice;
    }

    @JsonIgnore
    public List<ToolCallback> getToolCallbacks() {
        return this.toolCallbacks;
    }

    @JsonIgnore
    public void setToolCallbacks(List<ToolCallback> toolCallbacks) {
       this.toolCallbacks = toolCallbacks;
    }

    @JsonIgnore
    public Set<String>  getToolNames() {
        return this.toolNames;
    }

    @JsonIgnore
    public void setToolNames(Set<String> toolNames) {
        this.toolNames = toolNames;
    }

    @Nullable
    @JsonIgnore
    public Boolean getInternalToolExecutionEnabled() {
        return this.internalToolExecutionEnabled;
    }

    @JsonIgnore
    public void setInternalToolExecutionEnabled(@Nullable Boolean internalToolExecutionEnabled) {
        this.internalToolExecutionEnabled = internalToolExecutionEnabled;
    }

    @Override
    public Double getFrequencyPenalty() {
        return 0.0;
    }

    @Override
    public Integer getMaxTokens() {
        return 0;
    }

    @Override
    public Double getPresencePenalty() {
        return 0.0;
    }

    @Override
    public List<String> getStopSequences() {
        return List.of();
    }

    @JsonIgnore
    public Integer getTopK() {
        return null;
    }

    @Override
    public Double getTopP() {
        return 0.0;
    }

    public Map<String, Object> getToolContext() {
        return this.toolContext;
    }

    public void setToolContext(Map<String, Object> toolContext) {
        this.toolContext = toolContext;
    }

    @Override
    public OpenRouterChatOptions copy() {
        return fromOptions(this);
    }

    public static OpenRouterChatOptions fromOptions(OpenRouterChatOptions fromOptions) {
        return builder()
                .model(fromOptions.getModel())
//                .frequencyPenalty(fromOptions.getFrequencyPenalty())
//                .logprobs(fromOptions.getLogprobs())
//                .topLogprobs(fromOptions.getTopLogprobs())
//                .maxTokens(fromOptions.getMaxTokens())
//                .presencePenalty(fromOptions.getPresencePenalty())
//                .responseFormat(fromOptions.getResponseFormat())
//                .stop(fromOptions.getStop() != null ? new ArrayList(fromOptions.getStop()) : null)
                .temperature(fromOptions.getTemperature())
                .effort(fromOptions.getEffort())
                .thinkMaxTokens(fromOptions.getThinkMaxTokens())
                .exclude(fromOptions.getExclude())
                .enable(fromOptions.getEnable())
//                .topP(fromOptions.getTopP())
                .tools(fromOptions.getTools())
                .toolChoice(fromOptions.getToolChoice())
                .toolCallbacks((List) (fromOptions.getToolCallbacks() != null ? new ArrayList(fromOptions.getToolCallbacks()) : null))
                .toolNames((Set) (fromOptions.getToolNames() != null ? new HashSet(fromOptions.getToolNames()) : null))
                .internalToolExecutionEnabled(fromOptions.getInternalToolExecutionEnabled())
                .toolContext(fromOptions.getToolContext() != null ? new HashMap(fromOptions.getToolContext()) : null)
                .build();
    }

    public static final class Builder {
        protected OpenRouterChatOptions options;

        public Builder() {
            this.options = new OpenRouterChatOptions();
        }

        public Builder(OpenRouterChatOptions options) {
            this.options = options;
        }

        public Builder model(String model) {
            this.options.model = model;
            return this;
        }

        public Builder model(OpenRouterApi.ChatModel openRouterChatModel) {
            this.options.model = openRouterChatModel.getName();
            return this;
        }

        public Builder effort(String effort) {
            this.options.effort = effort;
            return this;
        }

        public Builder thinkMaxTokens(Integer thinkMaxTokens) {
            this.options.thinkMaxTokens = thinkMaxTokens;
            return this;
        }

        public Builder exclude(Boolean exclude) {
            this.options.exclude = exclude;
            return this;
        }

        public Builder enable(Boolean enable) {
            this.options.enable = enable;
            return this;
        }

//        public Builder frequencyPenalty(Double frequencyPenalty) {
//            this.options.frequencyPenalty = frequencyPenalty;
//            return this;
//        }
//
//        public Builder logprobs(Boolean logprobs) {
//            this.options.logprobs = logprobs;
//            return this;
//        }
//
//        public Builder topLogprobs(Integer topLogprobs) {
//            this.options.topLogprobs = topLogprobs;
//            return this;
//        }
//
//        public Builder maxTokens(Integer maxTokens) {
//            this.options.maxTokens = maxTokens;
//            return this;
//        }
//
//        public Builder presencePenalty(Double presencePenalty) {
//            this.options.presencePenalty = presencePenalty;
//            return this;
//        }
//
//        public Builder responseFormat(ResponseFormat responseFormat) {
//            this.options.responseFormat = responseFormat;
//            return this;
//        }

//        public Builder stop(List<String> stop) {
//            this.options.stop = stop;
//            return this;
//        }

        public Builder temperature(Double temperature) {
            this.options.temperature = temperature;
            return this;
        }

//        public Builder topP(Double topP) {
//            this.options.topP = topP;
//            return this;
//        }

        public Builder tools(List<OpenRouterApi.FunctionTool> tools) {
            this.options.tools = tools;
            return this;
        }

        public Builder toolChoice(Object toolChoice) {
            this.options.toolChoice = toolChoice;
            return this;
        }

        public Builder toolCallbacks(List<ToolCallback> toolCallbacks) {
            this.options.setToolCallbacks(toolCallbacks);
            return this;
        }

        public Builder toolCallbacks(ToolCallback... toolCallbacks) {
            Assert.notNull(toolCallbacks, "toolCallbacks cannot be null");
            this.options.toolCallbacks.addAll(Arrays.asList(toolCallbacks));
            return this;
        }

        public Builder toolNames(Set<String> toolNames) {
            Assert.notNull(toolNames, "toolNames cannot be null");
            this.options.setToolNames(toolNames);
            return this;
        }

        public Builder toolNames(String... toolNames) {
            Assert.notNull(toolNames, "toolNames cannot be null");
            this.options.toolNames.addAll(Set.of(toolNames));
            return this;
        }

        public Builder internalToolExecutionEnabled(@Nullable Boolean internalToolExecutionEnabled) {
            this.options.setInternalToolExecutionEnabled(internalToolExecutionEnabled);
            return this;
        }

        public Builder toolContext(Map<String, Object> toolContext) {
            if (this.options.toolContext == null) {
                this.options.toolContext = toolContext;
            } else {
                this.options.toolContext.putAll(toolContext);
            }

            return this;
        }

        public OpenRouterChatOptions build() {
            return this.options;
        }

    }
}
