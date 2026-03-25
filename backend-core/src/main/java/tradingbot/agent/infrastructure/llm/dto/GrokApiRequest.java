package tradingbot.agent.infrastructure.llm.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * GrokApiRequest - Type-safe request structure for Grok API
 * 
 * Based on OpenAI-compatible API format used by X.AI Grok
 */
public class GrokApiRequest {
    
    private String model;
    private List<Message> messages;
    private double temperature;
    
    @JsonProperty("max_tokens")
    private int maxTokens;
    
    // Constructors
    
    public GrokApiRequest() {
    }
    
    public GrokApiRequest(String model, List<Message> messages, double temperature, int maxTokens) {
        this.model = model;
        this.messages = messages;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
    }
    
    // Getters and Setters
    
    public String getModel() {
        return model;
    }
    
    public void setModel(String model) {
        this.model = model;
    }
    
    public List<Message> getMessages() {
        return messages;
    }
    
    public void setMessages(List<Message> messages) {
        this.messages = messages;
    }
    
    public double getTemperature() {
        return temperature;
    }
    
    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }
    
    public int getMaxTokens() {
        return maxTokens;
    }
    
    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }
    
    /**
     * Message - Represents a message in the conversation
     */
    public static class Message {
        private String role;
        private String content;
        
        public Message() {
        }
        
        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }
        
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
}
