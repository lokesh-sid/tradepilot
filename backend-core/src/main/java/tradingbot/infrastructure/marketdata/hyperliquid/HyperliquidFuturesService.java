package tradingbot.infrastructure.marketdata.hyperliquid;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

/**
 * Read-only {@link FuturesExchangeService} backed by the Hyperliquid public REST API.
 *
 * <p>All market-data operations ({@link #getCurrentPrice}, {@link #fetchOhlcv},
 * {@link #get24HourStats}, {@link #getMarginBalance}) call {@code POST /info} — no
 * authentication required. Order-execution methods delegate to
 * {@link PaperFuturesExchangeService} so agents can run a full paper-trading loop
 * against real Hyperliquid testnet prices without a wallet private key.
 *
 * <p>Symbol convention: callers use "BTCUSDT"; the Hyperliquid API uses coin names
 * ("BTC"). The conversion happens in {@link #toCoin(String)}.
 */
public class HyperliquidFuturesService implements FuturesExchangeService {

    private static final Logger log = LoggerFactory.getLogger(HyperliquidFuturesService.class);

    private final String baseUrl;
    private final String walletAddress;   // optional — only needed for getMarginBalance
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final PaperFuturesExchangeService paper;

    public HyperliquidFuturesService(String baseUrl, String walletAddress) {
        this(baseUrl, walletAddress, new RestTemplate());
    }

    /** Package-private constructor for unit testing with an injected {@link RestTemplate}. */
    HyperliquidFuturesService(String baseUrl, String walletAddress, RestTemplate restTemplate) {
        this.baseUrl       = baseUrl;
        this.walletAddress = walletAddress;
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

            JsonNode universe    = response.get(0).get("universe");
            JsonNode assetCtxs   = response.get(1);

            int index = findCoinIndex(universe, coin);
            if (index < 0) {
                log.warn("Hyperliquid metaAndAssetCtxs: coin {} not found", coin);
                return emptyStats(symbol);
            }

            JsonNode ctx = assetCtxs.get(index);
            double markPx     = parseDouble(ctx, "markPx");
            double prevDayPx  = parseDouble(ctx, "prevDayPx");
            double ntlVlm     = parseDouble(ctx, "dayNtlVlm");
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
                    .highPrice(markPx)  // Hyperliquid ctx does not expose 24h high/low
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
    // Order execution — delegated to paper service
    // -------------------------------------------------------------------------

    @Override
    public void setLeverage(String symbol, int leverage) {
        log.info("Hyperliquid setLeverage: paper mode — skipping real leverage call [{} x{}]",
                symbol, leverage);
    }

    @Override
    public OrderResult enterLongPosition(String symbol, double tradeAmount) {
        return paper.enterLongPosition(symbol, tradeAmount);
    }

    @Override
    public OrderResult exitLongPosition(String symbol, double tradeAmount) {
        return paper.exitLongPosition(symbol, tradeAmount);
    }

    @Override
    public OrderResult enterShortPosition(String symbol, double tradeAmount) {
        return paper.enterShortPosition(symbol, tradeAmount);
    }

    @Override
    public OrderResult exitShortPosition(String symbol, double tradeAmount) {
        return paper.exitShortPosition(symbol, tradeAmount);
    }

    @Override
    public OrderResult placeStopLossOrder(String symbol, String side, double quantity, double stopPrice) {
        return paper.placeStopLossOrder(symbol, side, quantity, stopPrice);
    }

    @Override
    public OrderResult placeTakeProfitOrder(String symbol, String side, double quantity, double takeProfitPrice) {
        return paper.placeTakeProfitOrder(symbol, side, quantity, takeProfitPrice);
    }

    // -------------------------------------------------------------------------
    // HTTP helper
    // -------------------------------------------------------------------------

    private JsonNode postInfo(Object body) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String json = objectMapper.writeValueAsString(body);
        HttpEntity<String> request = new HttpEntity<>(json, headers);
        String response = restTemplate.postForObject(baseUrl + "/info", request, String.class);
        return objectMapper.readTree(response);
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
    // Private Jackson DTOs (used only in tests via package-private access)
    // -------------------------------------------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    record HlCandleBar(String t, String T, String o, String h, String l, String c, String v) {}
}
