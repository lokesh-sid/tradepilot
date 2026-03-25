package tradingbot.bot.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import tradingbot.bot.controller.exception.BotOperationException;
import tradingbot.bot.events.TradeExecutionEvent;
import tradingbot.bot.messaging.EventPublisher;
import tradingbot.bot.service.BinanceFuturesService.Candle;

/**
 * DydxFuturesService - Implementation of FuturesExchangeService for dYdX v4.
 * 
 * Uses the dYdX v4 Indexer API for market data and dYdX Chain transactions for ordering.
 * Note: This constitutes a simplified implementation focusing on REST usage.
 */
@Service("dydxFuturesService")
public class DydxFuturesService implements FuturesExchangeService {

    private static final Logger logger = LoggerFactory.getLogger(DydxFuturesService.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String privateKey;
    private final Credentials credentials;
    private final EventPublisher eventPublisher;

    public DydxFuturesService(
            @Value("${trading.dydx.network:testnet}") String network,
            @Value("${trading.dydx.mainnet.url}") String mainnetUrl,
            @Value("${trading.dydx.testnet.url}") String testnetUrl,
            @Value("${trading.dydx.eth.private.key:}") String privateKey,
            EventPublisher eventPublisher) {
        
        this.baseUrl = "mainnet".equalsIgnoreCase(network) ? mainnetUrl : testnetUrl;
        this.privateKey = privateKey;
        this.eventPublisher = eventPublisher;
        this.restTemplate = new RestTemplateBuilder().build();
        this.objectMapper = new ObjectMapper();
        
        // Initialize Web3j credentials for transaction signing
        Credentials tempCredentials = null;
        if (privateKey != null && !privateKey.isEmpty() && !privateKey.equals("YOUR_ETH_PRIVATE_KEY")) {
            try {
                tempCredentials = Credentials.create(privateKey);
                logger.info("Initialized DydxFuturesService on {} ({}) with address: {}", 
                    network, baseUrl, tempCredentials.getAddress());
            } catch (Exception e) {
                logger.warn("Failed to initialize credentials, authenticated operations will fail: {}", e.getMessage());
            }
        } else {
            logger.info("Initialized DydxFuturesService on {} ({}) without credentials (read-only mode)", 
                network, baseUrl);
        }
        this.credentials = tempCredentials;
    }

    @Override
    public List<Candle> fetchOhlcv(String symbol, String timeframe, int limit) {
        // Map common timeframe symbols to dYdX resolution
        String resolution = mapTimeframe(timeframe);
        String url = String.format("%s/candles/perpetualMarkets/%s?resolution=%s&limit=%d", 
                baseUrl, symbol, resolution, limit);

        try {
            logger.debug("Fetching OHLCV from dYdX: {}", url);
            
            ResponseEntity<DydxCandlesResponse> response = restTemplate.getForEntity(
                url, DydxCandlesResponse.class);
            
            if (response.getBody() == null || response.getBody().candles == null) {
                logger.warn("No candle data returned from dYdX for {}", symbol);
                return Collections.emptyList();
            }
            
            List<Candle> candles = new ArrayList<>();
            for (DydxCandle dydxCandle : response.getBody().candles) {
                Candle candle = new Candle();
                candle.setOpenTime(Instant.parse(dydxCandle.startedAt).toEpochMilli());
                candle.setOpen(new java.math.BigDecimal(dydxCandle.open));
                candle.setHigh(new java.math.BigDecimal(dydxCandle.high));
                candle.setLow(new java.math.BigDecimal(dydxCandle.low));
                candle.setClose(new java.math.BigDecimal(dydxCandle.close));
                candle.setVolume(new java.math.BigDecimal(dydxCandle.baseTokenVolume));
                candle.setCloseTime(Instant.parse(dydxCandle.startedAt).toEpochMilli() + 3600000); // +1h approximation
                candles.add(candle);
            }
            
            logger.debug("Fetched {} candles for {}", candles.size(), symbol);
            return candles;
            
        } catch (Exception e) {
            logger.error("Error fetching OHLCV from dYdX for {}", symbol, e);
            throw new BotOperationException("fetch_ohlcv", "Failed to fetch OHLCV data from dYdX: " + e.getMessage(), e);
        }
    }

    @Override
    public double getCurrentPrice(String symbol) {
        // dYdX v4 uses "perpetualMarkets" endpoint
        String url = String.format("%s/perpetualMarkets/%s", baseUrl, symbol);
        
        try {
            logger.debug("Fetching current price for {} from dYdX", symbol);
            
            ResponseEntity<DydxMarketResponse> response = restTemplate.getForEntity(
                url, DydxMarketResponse.class);
            
            if (response.getBody() == null || response.getBody().market == null) {
                throw new BotOperationException("fetch_price", "No market data returned for " + symbol);
            }
            
            DydxMarket market = response.getBody().market;
            double price = Double.parseDouble(market.oraclePrice);
            
            logger.debug("Current price for {}: {}", symbol, price);
            return price;
            
        } catch (Exception e) {
            logger.error("Error fetching price for {} from dYdX", symbol, e);
            throw new BotOperationException("fetch_price", "Failed to fetch price from dYdX: " + e.getMessage(), e);
        }
    }

    @Override
    public double getMarginBalance() {
        if (credentials == null) {
            logger.warn("Cannot fetch margin balance - no credentials configured");
            return 10000.0; // Mock balance when no credentials
        }
        
        try {
            String address = credentials.getAddress();
            String url = String.format("%s/addresses/%s", baseUrl, address);
            
            // Create authenticated request headers
            HttpHeaders headers = createAuthenticatedHeaders("GET", "/addresses/" + address, "");
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            logger.debug("Fetching margin balance for address: {}", address);
            
            ResponseEntity<DydxAccountResponse> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, DydxAccountResponse.class);
            
            if (response.getBody() == null || response.getBody().subaccounts == null) {
                logger.warn("No account data returned from dYdX");
                return 10000.0;
            }
            
            // Get balance from first subaccount
            if (!response.getBody().subaccounts.isEmpty()) {
                DydxSubaccount subaccount = response.getBody().subaccounts.get(0);
                double equity = Double.parseDouble(subaccount.equity != null ? subaccount.equity : "0");
                logger.debug("Margin balance: {}", equity);
                return equity;
            }
            
            return 0.0;
            
        } catch (Exception e) {
            logger.error("Error fetching margin balance from dYdX", e);
            return 10000.0; // Fallback to mock balance on error
        }
    }

    @Override
    public Ticker24hrStats get24HourStats(String symbol) {
        String url = String.format("%s/perpetualMarkets/%s", baseUrl, symbol);
        
        try {
            logger.debug("Fetching 24h stats for {} from dYdX", symbol);
            
            ResponseEntity<DydxMarketResponse> response = restTemplate.getForEntity(
                url, DydxMarketResponse.class);
            
            if (response.getBody() == null || response.getBody().market == null) {
                throw new BotOperationException("fetch_24h_stats", "No market data returned for " + symbol);
            }
            
            DydxMarket market = response.getBody().market;
            double lastPrice = Double.parseDouble(market.oraclePrice);
            double volume = Double.parseDouble(market.volume24H != null ? market.volume24H : "0");
            double priceChange = Double.parseDouble(market.priceChange24H != null ? market.priceChange24H : "0");
            
            // Calculate price change percentage
            double priceChangePercent = 0.0;
            if (lastPrice > 0 && priceChange != 0) {
                priceChangePercent = (priceChange / (lastPrice - priceChange)) * 100.0;
            }
            
            return Ticker24hrStats.builder()
                .symbol(symbol)
                .volume(volume)
                .quoteVolume(volume * lastPrice) // Approximate quote volume
                .priceChange(priceChange)
                .priceChangePercent(priceChangePercent)
                .lastPrice(lastPrice)
                .openPrice(lastPrice - priceChange)
                .highPrice(lastPrice) // dYdX doesn't provide high/low in market endpoint
                .lowPrice(lastPrice)
                .build();
                
        } catch (Exception e) {
            logger.error("Error fetching 24h stats for {} from dYdX", symbol, e);
            throw new BotOperationException("fetch_24h_stats", "Failed to fetch 24h stats from dYdX: " + e.getMessage(), e);
        }
    }

    @Override
    public void setLeverage(String symbol, int leverage) {
        logger.info("Setting leverage on dYdX is handled per-order or account config");
    }

    @Override
    public OrderResult enterLongPosition(String symbol, double tradeAmount) {
        logger.info("dYdX: Placing LONG order for {} x {}", symbol, tradeAmount);
        // Signing and broadcasting transaction via Web3j/Cosmos logic would happen here
        return createMockOrderResult(symbol, "BUY", tradeAmount);
    }

    @Override
    public OrderResult exitLongPosition(String symbol, double tradeAmount) {
        logger.info("dYdX: Closing LONG position for {} x {}", symbol, tradeAmount);
        return createMockOrderResult(symbol, "SELL", tradeAmount);
    }

    @Override
    public OrderResult enterShortPosition(String symbol, double tradeAmount) {
        logger.info("dYdX: Placing SHORT order for {} x {}", symbol, tradeAmount);
        return createMockOrderResult(symbol, "SELL", tradeAmount);
    }

    @Override
    public OrderResult exitShortPosition(String symbol, double tradeAmount) {
        logger.info("dYdX: Closing SHORT position for {} x {}", symbol, tradeAmount);
        return createMockOrderResult(symbol, "BUY", tradeAmount);
    }

    @Override
    public OrderResult placeStopLossOrder(String symbol, String side, double quantity, double stopPrice) {
        logger.info("dYdX: Placing STOP LOSS for {} at {}", symbol, stopPrice);
        return createMockOrderResult(symbol, side, quantity, "STOP_LIMIT");
    }

    @Override
    public OrderResult placeTakeProfitOrder(String symbol, String side, double quantity, double takeProfitPrice) {
        logger.info("dYdX: Placing TAKE PROFIT for {} at {}", symbol, takeProfitPrice);
        return createMockOrderResult(symbol, side, quantity, "TAKE_PROFIT");
    }

    private OrderResult createMockOrderResult(String symbol, String side, double amount) {
        return createMockOrderResult(symbol, side, amount, "MARKET");
    }
    
    private OrderResult createMockOrderResult(String symbol, String side, double amount, String type) {
        OrderResult result = OrderResult.builder()
            .exchangeOrderId(UUID.randomUUID().toString())
            .symbol(symbol)
            .side(side)
            .status(OrderResult.OrderStatus.FILLED)
            .orderedQuantity(amount)
            .filledQuantity(amount)
            .avgFillPrice(0.0) // Mock price
            .commission(0.0)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

        if (eventPublisher != null) {
            TradeExecutionEvent event = new TradeExecutionEvent();
            event.setBotId("dydx-bot");
            event.setOrderId(result.getExchangeOrderId());
            event.setSymbol(symbol);
            event.setSide(side);
            event.setQuantity(amount);
            event.setPrice(0.0); // Mock price
            event.setStatus("FILLED-MOCK");
            eventPublisher.publishTradeExecution(event);
        }

        return result;
    }

    private String mapTimeframe(String timeframe) {
        // dYdX resolutions: 1MIN, 5MIN, 15MIN, 1HOUR, 4HOURS, 1DAY
        switch (timeframe) {
            case "1m": return "1MIN";
            case "5m": return "5MIN";
            case "15m": return "15MIN";
            case "1h": return "1HOUR";
            case "4h": return "4HOURS";
            case "1d": return "1DAY";
            default: return "1HOUR";
        }
    }
    
    private HttpHeaders createAuthenticatedHeaders(String method, String path, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        if (credentials == null) {
            return headers;
        }
        
        try {
            // Create ISO timestamp
            String timestamp = Instant.now().toString();
            
            // Create signature message: timestamp + method + path + body
            String message = timestamp + method + path + body;
            byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
            
            // Sign with Ethereum private key
            Sign.SignatureData signature = Sign.signPrefixedMessage(messageBytes, credentials.getEcKeyPair());
            
            // Convert signature to hex
            String signatureHex = Numeric.toHexStringNoPrefix(signature.getR()) +
                                 Numeric.toHexStringNoPrefix(signature.getS()) +
                                 String.format("%02x", signature.getV()[0]);
            
            // Add authentication headers
            headers.set("DYDX-SIGNATURE", signatureHex);
            headers.set("DYDX-TIMESTAMP", timestamp);
            headers.set("DYDX-ETHEREUM-ADDRESS", credentials.getAddress());
            
        } catch (Exception e) {
            logger.error("Error creating authenticated headers", e);
        }
        
        return headers;
    }
    
    // Add public helper for estimated fees (for RAGService)
    public double getEstimatedFee(String symbol) {
         // dYdX fees are typically around 0.05% taker, 0.02% maker
         // Plus negligible gas on v4 chain
         return 0.0005; 
    }
    
    // DTO classes for dYdX API responses
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class DydxCandlesResponse {
        @JsonProperty("candles")
        public List<DydxCandle> candles;
    }
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class DydxCandle {
        @JsonProperty("startedAt")
        public String startedAt;
        @JsonProperty("open")
        public String open;
        @JsonProperty("high")
        public String high;
        @JsonProperty("low")
        public String low;
        @JsonProperty("close")
        public String close;
        @JsonProperty("baseTokenVolume")
        public String baseTokenVolume;
    }
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class DydxMarketResponse {
        @JsonProperty("market")
        public DydxMarket market;
    }
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class DydxMarket {
        @JsonProperty("oraclePrice")
        public String oraclePrice;
        @JsonProperty("priceChange24H")
        public String priceChange24H;
        @JsonProperty("volume24H")
        public String volume24H;
    }
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class DydxAccountResponse {
        @JsonProperty("subaccounts")
        public List<DydxSubaccount> subaccounts;
    }
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class DydxSubaccount {
        @JsonProperty("equity")
        public String equity;
        @JsonProperty("freeCollateral")
        public String freeCollateral;
    }
}
