package tradingbot.bot.events;

/**
 * Event representing a trade execution result.
 * Published when a trade order is executed on the exchange.
 */
public class TradeExecutionEvent extends TradingEvent {
    
    private String orderId;
    private String symbol;
    private String side; // BUY, SELL
    private double quantity;
    private double price;
    private String status; // PENDING, FILLED, CANCELLED, REJECTED
    private double fee;
    private String tradeId;
    private int leverage;
    
    public TradeExecutionEvent() {
        super();
    }
    
    public TradeExecutionEvent(String botId, String orderId, String symbol) {
        super(botId, "TRADE_EXECUTED");
        this.orderId = orderId;
        this.symbol = symbol;
    }
    
    // Getters and Setters
    public String getOrderId() {
        return orderId;
    }
    
    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }
    
    public String getSymbol() {
        return symbol;
    }
    
    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }
    
    public String getSide() {
        return side;
    }
    
    public void setSide(String side) {
        this.side = side;
    }
    
    public double getQuantity() {
        return quantity;
    }
    
    public void setQuantity(double quantity) {
        this.quantity = quantity;
    }
    
    public double getPrice() {
        return price;
    }
    
    public void setPrice(double price) {
        this.price = price;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public double getFee() {
        return fee;
    }
    
    public void setFee(double fee) {
        this.fee = fee;
    }
    
    public String getTradeId() {
        return tradeId;
    }
    
    public void setTradeId(String tradeId) {
        this.tradeId = tradeId;
    }
    
    public int getLeverage() {
        return leverage;
    }
    
    public void setLeverage(int leverage) {
        this.leverage = leverage;
    }
    
    @Override
    public String toString() {
        return "TradeExecutionEvent{" +
                "orderId='" + orderId + '\'' +
                ", symbol='" + symbol + '\'' +
                ", side='" + side + '\'' +
                ", quantity=" + quantity +
                ", price=" + price +
                ", status='" + status + '\'' +
                ", eventId='" + getEventId() + '\'' +
                '}';
    }
}