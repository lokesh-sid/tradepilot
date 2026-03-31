package tradingbot.infrastructure.marketdata.hyperliquid;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import tradingbot.bot.service.BinanceFuturesService.Candle;
import tradingbot.bot.service.FuturesExchangeService;
import tradingbot.bot.service.OrderResult;
import tradingbot.bot.service.PaperFuturesExchangeService;
import tradingbot.bot.service.Ticker24hrStats;
import tradingbot.infrastructure.marketdata.hyperliquid.HyperliquidOrderSigner.HlSignature;

/**
 * {@link FuturesExchangeService} backed by the Hyperliquid REST API.
 *
 * <p>Market-data operations ({@link #getCurrentPrice}, {@link #fetchOhlcv},
 * {@link #get24HourStats}, {@link #getMarginBalance}) call {@code POST /info} — no
 * authentication required.
 *
 * <p>Order-execution operations call {@code POST /exchange} with an EIP-712 signed body
 * when a {@link HyperliquidOrderSigner} is configured. Without a signer all order methods
 * fall back to {@link PaperFuturesExchangeService} so agents can run a paper-trading loop
 * against real Hyperliquid prices without a private key.
 *
 * <p>Symbol convention: callers use "BTCUSDT"; the Hyperliquid API uses coin names ("BTC").
 * Conversion happens in {@link #toCoin(String)}.
 *
 * <p>Asset metadata (universe index, size decimals) is resolved on first use per coin and
 * cached in memory for the lifetime of this instance.
 */
public class HyperliquidFuturesService implements FuturesExchangeService {

    private static final Logger log = LoggerFactory.getLogger(HyperliquidFuturesService.class);

    /** IOC limit price offset used to approximate market-order fill behaviour. */
    private static final double MARKET_SLIPPAGE = 0.05; // 5 %

    private final String baseUrl;
    private final String walletAddress;           // optional — only for getMarginBalance
    private final HyperliquidOrderSigner signer;  // null → paper fallback for all order methods
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final PaperFuturesExchangeService paper;
    private final ConcurrentHashMap<String, CoinMeta> coinMetaCache = new ConcurrentHashMap<>();

    /** Production constructor — paper mode (no signing). */
    public HyperliquidFuturesService(String baseUrl, String walletAddress) {
        this(baseUrl, walletAddress, null, new RestTemplate());
    }

    /** Production constructor — live signing mode. */
    public HyperliquidFuturesService(String baseUrl, String walletAddress,
                                      HyperliquidOrderSigner signer) {
        this(baseUrl, walletAddress, signer, new RestTemplate());
    }

    /** Package-private — existing tests inject a mocked {@link RestTemplate} (paper mode). */
    HyperliquidFuturesService(String baseUrl, String walletAddress, RestTemplate restTemplate) {
        this(baseUrl, walletAddress, null, restTemplate);
    }

    /** Package-private — tests that exercise live signing inject both signer and template. */
    HyperliquidFuturesService(String baseUrl, String walletAddress,
                               HyperliquidOrderSigner signer, RestTemplate restTemplate) {
        this.baseUrl       = baseUrl;
        this.walletAddress = walletAddress;
        this.signer        = signer;
        this.restTemplate  = restTemplate;
        this.objectMapper  = new ObjectMapper();
        this.paper         = new PaperFuturesExchangeService();
    }

    // -------------------------------------------------------------------------
    // Market data — POST /info (no auth)
    // -------------------------------------------------------------------------

    /**
     * Returns the current mark price for the given symbol.
     * Uses the {@code allMids} endpoint which returns mid-prices for every coin.
     */
    @Override
    public double getCurrentPrice(String symbol) {
        try {
            JsonNode response = postInfo(Map.of("type", "allMids"));
            String coin = toCoin(symbol);
            JsonNode priceNode = response.get(coin);
            if (priceNode == null) {
                log.warn("Hyperliquid allMids: no price for coin {}", coin);
                return 0.0;
            }
            return Double.parseDouble(priceNode.asText());
        } catch (Exception e) {
            log.error("Hyperliquid getCurrentPrice failed for {}", symbol, e);
            return 0.0;
        }
    }

    /**
     * Fetches OHLCV candles using the {@code candleSnapshot} endpoint.
     * Returns up to {@code limit} candles ending at the current time.
     */
    @Override
    public List<Candle> fetchOhlcv(String symbol, String timeframe, int limit) {
        List<Candle> result = new ArrayList<>();
        try {
            String resolution = toResolution(timeframe);
            long intervalMs   = toIntervalMillis(resolution);
            long endTime      = System.currentTimeMillis();
            long startTime    = endTime - (long) limit * intervalMs;

            Map<String, Object> req = Map.of(
                    "coin",       toCoin(symbol),
                    "resolution", resolution,
                    "startTime",  startTime,
                    "endTime",    endTime);
            JsonNode response = postInfo(Map.of("type", "candleSnapshot", "req", req));

            if (!response.isArray()) return result;

            for (JsonNode node : response) {
                Candle c = new Candle();
                c.setOpenTime(node.get("t").asLong());
                c.setCloseTime(node.get("T").asLong());
                c.setOpen(new BigDecimal(node.get("o").asText()));
                c.setHigh(new BigDecimal(node.get("h").asText()));
                c.setLow(new BigDecimal(node.get("l").asText()));
                c.setClose(new BigDecimal(node.get("c").asText()));
                c.setVolume(new BigDecimal(node.get("v").asText()));
                result.add(c);
            }
        } catch (Exception e) {
            log.error("Hyperliquid fetchOhlcv failed for {}", symbol, e);
        }
        return result;
    }

    /**
     * Returns 24-hour statistics using the {@code metaAndAssetCtxs} endpoint.
     *
     * <p>The response is a two-element array: {@code [meta, [assetCtx, ...]]}. The
     * meta object contains a {@code universe} array whose indices align with the
     * asset context array — find the coin by name in universe, then read the same
     * index from assetCtxs.
     */
    @Override
    public Ticker24hrStats get24HourStats(String symbol) {
        try {
            String coin = toCoin(symbol);
            JsonNode response = postInfo(Map.of("type", "metaAndAssetCtxs"));

            JsonNode universe  = response.get(0).get("universe");
            JsonNode assetCtxs = response.get(1);

            int index = findCoinIndex(universe, coin);
            if (index < 0) {
                log.warn("Hyperliquid metaAndAssetCtxs: coin {} not found", coin);
                return emptyStats(symbol);
            }

            JsonNode ctx = assetCtxs.get(index);
            double markPx      = parseDouble(ctx, "markPx");
            double prevDayPx   = parseDouble(ctx, "prevDayPx");
            double ntlVlm      = parseDouble(ctx, "dayNtlVlm");
            double priceChange = markPx - prevDayPx;
            double changePct   = prevDayPx > 0 ? (priceChange / prevDayPx) * 100.0 : 0.0;

            return Ticker24hrStats.builder()
                    .symbol(symbol)
                    .lastPrice(markPx)
                    .openPrice(prevDayPx)
                    .priceChange(priceChange)
                    .priceChangePercent(changePct)
                    .volume(ntlVlm / Math.max(markPx, 1))   // approx base volume
                    .quoteVolume(ntlVlm)
                    .highPrice(markPx)  // Hyperliquid ctx does not expose 24 h high/low
                    .lowPrice(markPx)
                    .build();
        } catch (Exception e) {
            log.error("Hyperliquid get24HourStats failed for {}", symbol, e);
            return emptyStats(symbol);
        }
    }

    /**
     * Returns the total account equity (USDC) for the configured wallet address.
     * Returns 0 if no wallet address is configured.
     */
    @Override
    public double getMarginBalance() {
        if (walletAddress == null || walletAddress.isBlank()) {
            log.debug("Hyperliquid getMarginBalance: no wallet address configured, returning 0");
            return 0.0;
        }
        try {
            JsonNode response = postInfo(Map.of("type", "clearinghouseState", "user", walletAddress));
            JsonNode summary = response.get("marginSummary");
            if (summary == null) return 0.0;
            return parseDouble(summary, "accountValue");
        } catch (Exception e) {
            log.error("Hyperliquid getMarginBalance failed", e);
            return 0.0;
        }
    }

    // -------------------------------------------------------------------------
    // Order execution — POST /exchange (requires signer; falls back to paper)
    // -------------------------------------------------------------------------

    /**
     * Sets isolated leverage for the given symbol via the {@code updateLeverage} action.
     * No-op (with a log message) when no signer is configured.
     */
    @Override
    public void setLeverage(String symbol, int leverage) {
        if (signer == null) {
            log.info("Hyperliquid setLeverage: no signer — skipping [{} x{}]", symbol, leverage);
            return;
        }
        try {
            CoinMeta meta = resolveCoinMeta(toCoin(symbol));
            long nonce = System.currentTimeMillis();

            LinkedHashMap<String, Object> action = new LinkedHashMap<>();
            action.put("type", "updateLeverage");
            action.put("asset", meta.index());
            action.put("isCross", false);
            action.put("leverage", leverage);

            HlSignature sig = signer.sign(action, nonce);
            JsonNode response = postExchange(action, nonce, sig);
            if (!"ok".equals(response.path("status").asText())) {
                log.error("Hyperliquid setLeverage rejected for {} x{}: {}",
                        symbol, leverage, response.path("response").asText());
            } else {
                log.info("Hyperliquid setLeverage confirmed [{} x{}]", symbol, leverage);
            }
        } catch (Exception e) {
            log.error("Hyperliquid setLeverage failed for {} x{}", symbol, leverage, e);
        }
    }

    @Override
    public OrderResult enterLongPosition(String symbol, double tradeAmount) {
        if (signer == null) return paper.enterLongPosition(symbol, tradeAmount);
        return placeMarketOrder(symbol, true, false, tradeAmount, "buy");
    }

    @Override
    public OrderResult exitLongPosition(String symbol, double tradeAmount) {
        if (signer == null) return paper.exitLongPosition(symbol, tradeAmount);
        return placeMarketOrder(symbol, false, true, tradeAmount, "sell");
    }

    @Override
    public OrderResult enterShortPosition(String symbol, double tradeAmount) {
        if (signer == null) return paper.enterShortPosition(symbol, tradeAmount);
        return placeMarketOrder(symbol, false, false, tradeAmount, "sell");
    }

    @Override
    public OrderResult exitShortPosition(String symbol, double tradeAmount) {
        if (signer == null) return paper.exitShortPosition(symbol, tradeAmount);
        return placeMarketOrder(symbol, true, true, tradeAmount, "buy");
    }

    @Override
    public OrderResult placeStopLossOrder(String symbol, String side,
                                           double quantity, double stopPrice) {
        if (signer == null) return paper.placeStopLossOrder(symbol, side, quantity, stopPrice);
        return placeTriggerOrder(symbol, side, quantity, stopPrice, "sl");
    }

    @Override
    public OrderResult placeTakeProfitOrder(String symbol, String side,
                                             double quantity, double takeProfitPrice) {
        if (signer == null) return paper.placeTakeProfitOrder(symbol, side, quantity, takeProfitPrice);
        return placeTriggerOrder(symbol, side, quantity, takeProfitPrice, "tp");
    }

    // -------------------------------------------------------------------------
    // Order helpers
    // -------------------------------------------------------------------------

    /**
     * Places an IOC limit order at ±{@value #MARKET_SLIPPAGE}% from the current mark price,
     * approximating market-order fill behaviour without a dedicated market order type.
     */
    private OrderResult placeMarketOrder(String symbol, boolean isBuy, boolean reduceOnly,
                                          double tradeAmount, String side) {
        try {
            double price = getCurrentPrice(symbol);
            if (price <= 0) {
                log.error("Hyperliquid placeMarketOrder: could not fetch price for {}", symbol);
                return rejectedResult(symbol, side, 0);
            }
            double limitPx = isBuy
                    ? price * (1.0 + MARKET_SLIPPAGE)
                    : price * (1.0 - MARKET_SLIPPAGE);
            // tradeAmount is base-asset quantity (same contract as all other FuturesExchangeService impls)
            double size = tradeAmount;

            CoinMeta meta = resolveCoinMeta(toCoin(symbol));
            long nonce = System.currentTimeMillis();

            LinkedHashMap<String, Object> order = buildOrder(
                    meta.index(), isBuy,
                    formatPrice(limitPx), formatSize(size, meta.szDecimals()),
                    reduceOnly, iocOrderType());
            LinkedHashMap<String, Object> action = buildOrderAction(List.of(order));

            HlSignature sig = signer.sign(action, nonce);
            JsonNode response = postExchange(action, nonce, sig);
            return parseOrderResult(response, symbol, side, size);
        } catch (Exception e) {
            log.error("Hyperliquid placeMarketOrder failed for {}", symbol, e);
            return rejectedResult(symbol, side, 0);
        }
    }

    /**
     * Places a TP or SL trigger order at the given price with {@code reduceOnly=true}.
     * SL executes at market price on trigger; TP executes as a limit at the trigger price.
     *
     * @param tpsl {@code "sl"} for stop-loss, {@code "tp"} for take-profit
     */
    private OrderResult placeTriggerOrder(String symbol, String side, double quantity,
                                           double triggerPrice, String tpsl) {
        try {
            boolean isBuy     = "buy".equalsIgnoreCase(side);
            boolean isMarket  = "sl".equals(tpsl);   // SL → market fill; TP → limit fill
            CoinMeta meta     = resolveCoinMeta(toCoin(symbol));
            long nonce        = System.currentTimeMillis();
            String triggerPxStr = formatPrice(triggerPrice);

            LinkedHashMap<String, Object> order = buildOrder(
                    meta.index(), isBuy,
                    triggerPxStr, formatSize(quantity, meta.szDecimals()),
                    true, triggerOrderType(triggerPxStr, isMarket, tpsl));
            LinkedHashMap<String, Object> action = buildOrderAction(List.of(order));

            HlSignature sig = signer.sign(action, nonce);
            JsonNode response = postExchange(action, nonce, sig);
            return parseOrderResult(response, symbol, side, quantity);
        } catch (Exception e) {
            log.error("Hyperliquid placeTriggerOrder ({}) failed for {}", tpsl, symbol, e);
            return rejectedResult(symbol, side, quantity);
        }
    }

    // -------------------------------------------------------------------------
    // Action map builders  (key insertion order must match Python SDK)
    // -------------------------------------------------------------------------

    private static LinkedHashMap<String, Object> buildOrder(int assetIndex, boolean isBuy,
                                                              String limitPx, String sz,
                                                              boolean reduceOnly,
                                                              Map<String, Object> orderType) {
        LinkedHashMap<String, Object> order = new LinkedHashMap<>();
        order.put("a", assetIndex);
        order.put("b", isBuy);
        order.put("p", limitPx);
        order.put("s", sz);
        order.put("r", reduceOnly);
        order.put("t", orderType);
        return order;
    }

    private static LinkedHashMap<String, Object> buildOrderAction(List<Map<String, Object>> orders) {
        LinkedHashMap<String, Object> action = new LinkedHashMap<>();
        action.put("type", "order");
        action.put("orders", orders);
        action.put("grouping", "na");
        return action;
    }

    /** IOC limit — fills immediately or cancels, used for market-like behaviour. */
    private static Map<String, Object> iocOrderType() {
        return Map.of("limit", Map.of("tif", "Ioc"));
    }

    private static Map<String, Object> triggerOrderType(String triggerPx,
                                                          boolean isMarket, String tpsl) {
        LinkedHashMap<String, Object> trigger = new LinkedHashMap<>();
        trigger.put("triggerPx", triggerPx);
        trigger.put("isMarket", isMarket);
        trigger.put("tpsl", tpsl);
        return Map.of("trigger", trigger);
    }

    // -------------------------------------------------------------------------
    // HTTP helpers
    // -------------------------------------------------------------------------

    private JsonNode postInfo(Object body) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String json = objectMapper.writeValueAsString(body);
        HttpEntity<String> request = new HttpEntity<>(json, headers);
        String response = restTemplate.postForObject(baseUrl + "/info", request, String.class);
        return objectMapper.readTree(response);
    }

    private JsonNode postExchange(LinkedHashMap<String, Object> action, long nonce,
                                   HlSignature sig) throws Exception {
        LinkedHashMap<String, Object> body = new LinkedHashMap<>();
        body.put("action", action);
        body.put("nonce", nonce);
        body.put("signature", Map.of("r", sig.r(), "s", sig.s(), "v", sig.v()));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String json = objectMapper.writeValueAsString(body);
        HttpEntity<String> request = new HttpEntity<>(json, headers);
        String response = restTemplate.postForObject(baseUrl + "/exchange", request, String.class);
        return objectMapper.readTree(response);
    }

    // -------------------------------------------------------------------------
    // Response parsing
    // -------------------------------------------------------------------------

    private OrderResult parseOrderResult(JsonNode response, String symbol,
                                          String side, double orderedQty) {
        if (!"ok".equals(response.path("status").asText())) {
            log.error("Hyperliquid order rejected for {}: {}",
                    symbol, response.path("response").asText("unknown error"));
            return rejectedResult(symbol, side, orderedQty);
        }

        JsonNode statuses = response.path("response").path("data").path("statuses");
        if (!statuses.isArray() || statuses.isEmpty()) {
            log.error("Hyperliquid order: unexpected response shape for {}: {}", symbol, response);
            return rejectedResult(symbol, side, orderedQty);
        }

        JsonNode statusNode = statuses.get(0);
        Instant now = Instant.now();

        if (statusNode.has("resting")) {
            long oid = statusNode.path("resting").path("oid").asLong();
            return OrderResult.builder()
                    .exchangeOrderId(String.valueOf(oid))
                    .symbol(symbol)
                    .side(side)
                    .status(OrderResult.OrderStatus.NEW)
                    .orderedQuantity(orderedQty)
                    .filledQuantity(0.0)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
        }

        if (statusNode.has("filled")) {
            JsonNode filled = statusNode.get("filled");
            long oid        = filled.path("oid").asLong();
            double filledSz = filled.path("totalSz").asDouble();
            double avgPx    = filled.path("avgPx").asDouble();
            return OrderResult.builder()
                    .exchangeOrderId(String.valueOf(oid))
                    .symbol(symbol)
                    .side(side)
                    .status(OrderResult.OrderStatus.FILLED)
                    .orderedQuantity(orderedQty)
                    .filledQuantity(filledSz)
                    .avgFillPrice(avgPx)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
        }

        if (statusNode.has("error")) {
            log.error("Hyperliquid order error for {}: {}",
                    symbol, statusNode.path("error").asText());
            return rejectedResult(symbol, side, orderedQty);
        }

        log.error("Hyperliquid order: unrecognised status node for {}: {}", symbol, statusNode);
        return rejectedResult(symbol, side, orderedQty);
    }

    private static OrderResult rejectedResult(String symbol, String side, double orderedQty) {
        Instant now = Instant.now();
        return OrderResult.builder()
                .symbol(symbol)
                .side(side)
                .status(OrderResult.OrderStatus.REJECTED)
                .orderedQuantity(orderedQty)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    // -------------------------------------------------------------------------
    // Asset metadata resolution (cached)
    // -------------------------------------------------------------------------

    /**
     * Resolves the universe index and size decimals for a coin, caching the result.
     * Calls {@code POST /info} with {@code type: "meta"} on first access per coin.
     */
    private CoinMeta resolveCoinMeta(String coin) {
        return coinMetaCache.computeIfAbsent(coin, c -> {
            try {
                return fetchCoinMeta(c);
            } catch (Exception e) {
                throw new RuntimeException("Failed to resolve asset metadata for coin: " + c, e);
            }
        });
    }

    private CoinMeta fetchCoinMeta(String coin) throws Exception {
        JsonNode response = postInfo(Map.of("type", "meta"));
        JsonNode universe = response.get("universe");
        for (int i = 0; i < universe.size(); i++) {
            JsonNode asset = universe.get(i);
            if (coin.equals(asset.path("name").asText())) {
                int szDecimals = asset.path("szDecimals").asInt(5);
                return new CoinMeta(i, szDecimals);
            }
        }
        throw new IllegalArgumentException("Coin not found in Hyperliquid universe: " + coin);
    }

    // -------------------------------------------------------------------------
    // Formatting
    // -------------------------------------------------------------------------

    /**
     * Formats a price to 5 significant figures as required by the Hyperliquid API.
     */
    static String formatPrice(double price) {
        return new BigDecimal(Double.toString(price))
                .round(new MathContext(5, RoundingMode.HALF_UP))
                .stripTrailingZeros()
                .toPlainString();
    }

    /**
     * Formats a size to {@code szDecimals} decimal places, rounding down to avoid
     * accidentally trading more than intended.
     */
    static String formatSize(double size, int szDecimals) {
        return BigDecimal.valueOf(size)
                .setScale(szDecimals, RoundingMode.DOWN)
                .toPlainString();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** "BTCUSDT" → "BTC" */
    static String toCoin(String symbol) {
        return symbol.endsWith("USDT") ? symbol.substring(0, symbol.length() - 4) : symbol;
    }

    private static final Map<String, Long> UNIT_MILLIS = Map.of(
            "m", 60_000L,
            "h", 3_600_000L,
            "d", 86_400_000L,
            "w", 604_800_000L);

    private static final Pattern RESOLUTION_PATTERN = Pattern.compile("^(\\d+)([mhdw])$");

    /**
     * Normalises common timeframe strings to Hyperliquid resolution strings.
     * Accepts bare numbers (interpreted as minutes), shorthand letters ("d", "w"),
     * and suffixed forms ("1m", "4h"). Invalid resolutions are rejected by the API.
     */
    static String toResolution(String timeframe) {
        return normaliseTimeframe(timeframe.toLowerCase());
    }

    private static String normaliseTimeframe(String tf) {
        if (tf.equals("d")) return "1d";
        if (tf.equals("w")) return "1w";
        if (tf.matches("\\d+")) {
            long mins = Long.parseLong(tf);
            if (mins % (7L * 24 * 60) == 0) return (mins / (7L * 24 * 60)) + "w";
            if (mins % (24L * 60)     == 0) return (mins / (24L * 60))     + "d";
            if (mins % 60             == 0) return (mins / 60)             + "h";
            return mins + "m";
        }
        return tf;
    }

    /** Returns the duration in milliseconds for a resolution string (e.g. "1m", "4h", "1d"). */
    static long toIntervalMillis(String resolution) {
        Matcher m = RESOLUTION_PATTERN.matcher(resolution);
        if (!m.matches()) {
            throw new IllegalArgumentException("Unrecognised resolution format: " + resolution);
        }
        return Long.parseLong(m.group(1)) * UNIT_MILLIS.get(m.group(2));
    }

    private static int findCoinIndex(JsonNode universe, String coin) {
        for (int i = 0; i < universe.size(); i++) {
            if (coin.equals(universe.get(i).get("name").asText())) {
                return i;
            }
        }
        return -1;
    }

    private static double parseDouble(JsonNode node, String field) {
        JsonNode f = node.get(field);
        return (f != null && !f.isNull()) ? f.asDouble() : 0.0;
    }

    private static Ticker24hrStats emptyStats(String symbol) {
        return Ticker24hrStats.builder().symbol(symbol).build();
    }

    // -------------------------------------------------------------------------
    // Internal types
    // -------------------------------------------------------------------------

    /** Asset metadata resolved from the Hyperliquid universe array. */
    record CoinMeta(int index, int szDecimals) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record HlCandleBar(String t, String T, String o, String h, String l, String c, String v) {}
}
