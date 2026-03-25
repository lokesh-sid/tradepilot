package tradingbot.agent.api.dto;

import java.time.Instant;

public class OrderResponse {
    public String id;
    public String agentId;
    public String symbol;
    public String direction;
    public double price;
    public double quantity;
    public Double stopLoss;
    public Double takeProfit;
    public Integer leverage;
    public String status;
    public Instant createdAt;
    public Instant executedAt;
    public String exchangeOrderId;
    public String failureReason;

    public OrderResponse() {}

    public OrderResponse(
        String id,
        String agentId,
        String symbol,
        String direction,
        double price,
        double quantity,
        Double stopLoss,
        Double takeProfit,
        Integer leverage,
        String status,
        Instant createdAt,
        Instant executedAt,
        String exchangeOrderId,
        String failureReason
    ) {
        this.id = id;
        this.agentId = agentId;
        this.symbol = symbol;
        this.direction = direction;
        this.price = price;
        this.quantity = quantity;
        this.stopLoss = stopLoss;
        this.takeProfit = takeProfit;
        this.leverage = leverage;
        this.status = status;
        this.createdAt = createdAt;
        this.executedAt = executedAt;
        this.exchangeOrderId = exchangeOrderId;
        this.failureReason = failureReason;
    }
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("OrderResponse{");
        sb.append("id='").append(id).append("', ");
        sb.append("agentId='").append(agentId).append("', ");
        sb.append("symbol='").append(symbol).append("', ");
        sb.append("direction='").append(direction).append("', ");
        sb.append("price=").append(price).append(", ");
        sb.append("quantity=").append(quantity).append(", ");
        sb.append("stopLoss=").append(stopLoss).append(", ");
        sb.append("takeProfit=").append(takeProfit).append(", ");
        sb.append("leverage=").append(leverage).append(", ");
        sb.append("status='").append(status).append("', ");
        sb.append("createdAt=").append(createdAt).append(", ");
        sb.append("executedAt=").append(executedAt).append(", ");
        sb.append("exchangeOrderId='").append(exchangeOrderId).append("', ");
        sb.append("failureReason='").append(failureReason).append("'}");
        return sb.toString();
    }
}
