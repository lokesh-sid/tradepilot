package tradingbot.agent.infrastructure.llm.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * GrokApiResponse - Type-safe response structure for Grok API
 * 
 * Based on OpenAI-compatible API format used by X.AI Grok
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GrokApiResponse {
    
    private String id;
    @JsonProperty("object")
    private String objectType;
    private long created;
    private String model;
    private List<Choice> choices;
    private Usage usage;
    
    // Getters and Setters
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getObjectType() {
        return objectType;
    }
    
    public void setObjectType(String objectType) {
        this.objectType = objectType;
    }
    
    public long getCreated() {
        return created;
    }
    
    public void setCreated(long created) {
        this.created = created;
    }
    
    public String getModel() {
        return model;
    }
    
    public void setModel(String model) {
        this.model = model;
    }
    
    public List<Choice> getChoices() {
        return choices;
    }
    
    public void setChoices(List<Choice> choices) {
        this.choices = choices;
    }
    
    public Usage getUsage() {
        return usage;
    }
    
    public void setUsage(Usage usage) {
        this.usage = usage;
    }
    
    /**
     * Choice - Represents a single completion choice
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Choice {
        private int index;
        private Message message;
        
        @JsonProperty("finish_reason")
        private String finishReason;
        
        public int getIndex() {
            return index;
        }
        
        public void setIndex(int index) {
            this.index = index;
        }
        
        public Message getMessage() {
            return message;
        }
        
        public void setMessage(Message message) {
            this.message = message;
        }
        
        public String getFinishReason() {
            return finishReason;
        }
        
        public void setFinishReason(String finishReason) {
            this.finishReason = finishReason;
        }
    }
    
    /**
     * Message - Represents a message in the conversation
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Message {
        private String role;
        private String content;
        
        public String getRole() {
            return role;
        }
        
        public void setRole(String role) {
            this.role = role;
        }
        
        public String getContent() {
            return content;
        }
        
        public void setContent(String content) {
            this.content = content;
        }
    }
    
    /**
     * Usage - Token usage statistics
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Usage {
        @JsonProperty("prompt_tokens")
        private int promptTokens;
        
        @JsonProperty("completion_tokens")
        private int completionTokens;
        
        @JsonProperty("total_tokens")
        private int totalTokens;
        
        public int getPromptTokens() {
            return promptTokens;
        }
        
        public void setPromptTokens(int promptTokens) {
            this.promptTokens = promptTokens;
        }
        
        public int getCompletionTokens() {
            return completionTokens;
        }
        
        public void setCompletionTokens(int completionTokens) {
            this.completionTokens = completionTokens;
        }
        
        public int getTotalTokens() {
            return totalTokens;
        }
        
        public void setTotalTokens(int totalTokens) {
            this.totalTokens = totalTokens;
        }
    }
}
