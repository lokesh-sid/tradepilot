package tradingbot.bot.events.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Event data for trade signal events.
 */
public class TradeSignalData {
    
    @JsonProperty("agentId")
    private String agentId;
    
    @JsonProperty("symbol")
    private String symbol;
    
    @JsonProperty("signal")
    private String signal;
    
    @JsonProperty("confidence")
    private Double confidence;
    
    @JsonProperty("price")
    private Double price;
    
    @JsonProperty("quantity")
    private Double quantity;
    
    @JsonProperty("stopLoss")
    private Double stopLoss;
    
    @JsonProperty("takeProfit")
    private Double takeProfit;
    
    @JsonProperty("reasoning")
    private String reasoning;
    
    public String getAgentId() {
        return agentId;
    }
    
    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }
    
    public String getSymbol() {
        return symbol;
    }
    
    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }
    
    public String getSignal() {
        return signal;
    }
    
    public void setSignal(String signal) {
        this.signal = signal;
    }
    
    public Double getConfidence() {
        return confidence;
    }
    
    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }
    
    public Double getPrice() {
        return price;
    }
    
    public void setPrice(Double price) {
        this.price = price;
    }
    
    public Double getQuantity() {
        return quantity;
    }
    
    public void setQuantity(Double quantity) {
        this.quantity = quantity;
    }
    
    public Double getStopLoss() {
        return stopLoss;
    }
    
    public void setStopLoss(Double stopLoss) {
        this.stopLoss = stopLoss;
    }
    
    public Double getTakeProfit() {
        return takeProfit;
    }
    
    public void setTakeProfit(Double takeProfit) {
        this.takeProfit = takeProfit;
    }
    
    public String getReasoning() {
        return reasoning;
    }
    
    public void setReasoning(String reasoning) {
        this.reasoning = reasoning;
    }
    
    @Override
    public String toString() {
        return "TradeSignalData{" +
                "agentId='" + agentId + '\'' +
                ", symbol='" + symbol + '\'' +
                ", signal='" + signal + '\'' +
                ", confidence=" + confidence +
                ", price=" + price +
                ", quantity=" + quantity +
                ", stopLoss=" + stopLoss +
                ", takeProfit=" + takeProfit +
                ", reasoning='" + reasoning + '\'' +
                '}';
    }
}
