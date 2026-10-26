package world.heiye.spring.ai.openrouter;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
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


    /**
     * 推理配置。运行时提供 reasoning 时整体覆盖默认配置，不进行嵌套字段合并。
     */
    @JsonProperty("reasoning")
    private OpenRouterApi.Think reasoning;

    @JsonProperty("max_tokens")
    private Integer maxTokens;

    @JsonProperty("frequency_penalty")
    private Double frequencyPenalty;

    @JsonProperty("presence_penalty")
    private Double presencePenalty;

    @JsonProperty("top_p")
    private Double topP;

    @JsonProperty("top_k")
    private Integer topK;

    @JsonProperty("stop")
    private List<String> stop;

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
        return this.frequencyPenalty;
    }

    @Override
    public Integer getMaxTokens() {
        return this.maxTokens;
    }

    @Override
    public Double getPresencePenalty() {
        return this.presencePenalty;
    }

    @Override
    @JsonIgnore
    public List<String> getStopSequences() {
        return this.stop;
    }

    public Integer getTopK() {
        return this.topK;
    }

    @Override
    public Double getTopP() {
        return this.topP;
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
                .temperature(fromOptions.getTemperature())
                .reasoning(fromOptions.getReasoning())
                .stream(fromOptions.getStream())
                .maxTokens(fromOptions.getMaxTokens())
                .frequencyPenalty(fromOptions.getFrequencyPenalty())
                .presencePenalty(fromOptions.getPresencePenalty())
                .topP(fromOptions.getTopP())
                .topK(fromOptions.getTopK())
                .stop(fromOptions.getStop() != null ? new ArrayList<>(fromOptions.getStop()) : null)
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
            this.options.setEffort(effort);
            return this;
        }

        public Builder thinkMaxTokens(Integer thinkMaxTokens) {
            this.options.setThinkMaxTokens(thinkMaxTokens);
            return this;
        }

        public Builder exclude(Boolean exclude) {
            this.options.setExclude(exclude);
            return this;
        }

        public Builder enable(Boolean enable) {
            this.options.setEnable(enable);
            return this;
        }



        public Builder temperature(Double temperature) {
            this.options.temperature = temperature;
            return this;
        }


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

        public Builder maxTokens(Integer maxTokens) {
            this.options.maxTokens = maxTokens;
            return this;
        }

        public Builder frequencyPenalty(Double frequencyPenalty) {
            this.options.frequencyPenalty = frequencyPenalty;
            return this;
        }

        public Builder presencePenalty(Double presencePenalty) {
            this.options.presencePenalty = presencePenalty;
            return this;
        }

        public Builder topP(Double topP) {
            this.options.topP = topP;
            return this;
        }

        public Builder topK(Integer topK) {
            this.options.topK = topK;
            return this;
        }

        public Builder stop(List<String> stop) {
            this.options.stop = stop;
            return this;
        }

        public Builder reasoning(OpenRouterApi.Think reasoning) {
            this.options.reasoning = reasoning;
            return this;
        }

        public Builder stream(Boolean stream) {
            this.options.stream = stream;
            return this;
        }

        public OpenRouterChatOptions build() {
            return this.options;
        }

    }

    @Nullable
    @Override
    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    @Nullable
    @Override
    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Boolean getStream() {
        return stream;
    }

    public void setStream(Boolean stream) {
        this.stream = stream;
    }

    public OpenRouterApi.Think getReasoning() {
        return this.reasoning;
    }

    public void setReasoning(OpenRouterApi.Think reasoning) {
        this.reasoning = reasoning;
    }

    @JsonIgnore
    public String getEffort() {
        return reasoning != null && reasoning.effort() != null ? reasoning.effort().name() : null;
    }

    public void setEffort(String effort) {
        OpenRouterApi.Effort value = OpenRouterApi.Effort.fromString(effort);
        Assert.isTrue(effort == null || value != null, "Unsupported reasoning effort: " + effort);
        this.reasoning = new OpenRouterApi.Think(value, getThinkMaxTokens(), getExclude(),
                reasoning != null ? reasoning.enabled() : null);
    }

    @JsonIgnore
    public Integer getThinkMaxTokens() {
        return reasoning != null ? reasoning.maxTokens() : null;
    }

    public void setThinkMaxTokens(Integer thinkMaxTokens) {
        this.reasoning = new OpenRouterApi.Think(reasoning != null ? reasoning.effort() : null,
                thinkMaxTokens, getExclude(), reasoning != null ? reasoning.enabled() : null);
    }

    @JsonIgnore
    public Boolean getExclude() {
        return reasoning != null ? reasoning.exclude() : null;
    }

    public void setExclude(Boolean exclude) {
        this.reasoning = new OpenRouterApi.Think(reasoning != null ? reasoning.effort() : null,
                getThinkMaxTokens(), exclude, reasoning != null ? reasoning.enabled() : null);
    }

    public void setEnable(Boolean enable) {
        this.reasoning = new OpenRouterApi.Think(reasoning != null ? reasoning.effort() : null,
                getThinkMaxTokens(), getExclude(), enable);
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    public void setFrequencyPenalty(Double frequencyPenalty) {
        this.frequencyPenalty = frequencyPenalty;
    }

    public void setPresencePenalty(Double presencePenalty) {
        this.presencePenalty = presencePenalty;
    }

    public void setTopP(Double topP) {
        this.topP = topP;
    }

    public void setTopK(Integer topK) {
        this.topK = topK;
    }

    public void setStop(List<String> stop) {
        this.stop = stop;
    }

    public List<String> getStop() {
        return this.stop;
    }

    public void setStopSequences(List<String> stopSequences) {
        this.stop = stopSequences;
    }
}
