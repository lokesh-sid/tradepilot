package tradingbot.infrastructure.marketdata.hyperliquid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestTemplate;

import tradingbot.bot.service.BinanceFuturesService.Candle;
import tradingbot.bot.service.OrderResult;
import tradingbot.bot.service.Ticker24hrStats;

/**
 * Unit tests for {@link HyperliquidFuturesService}.
 *
 * <p>HTTP calls are intercepted via a subclass that accepts an injected {@link RestTemplate},
 * keeping all tests entirely offline.
 */
@DisplayName("HyperliquidFuturesService")
class HyperliquidFuturesServiceTest {

    // -------------------------------------------------------------------------
    // Helper conversion methods (no HTTP)
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("toCoin")
    class ToCoin {

        @Test
        void stripsUsdtSuffix() {
            assertThat(HyperliquidFuturesService.toCoin("BTCUSDT")).isEqualTo("BTC");
            assertThat(HyperliquidFuturesService.toCoin("ETHUSDT")).isEqualTo("ETH");
        }

        @Test
        void passesThrough_whenNoUsdtSuffix() {
            assertThat(HyperliquidFuturesService.toCoin("BTC")).isEqualTo("BTC");
            assertThat(HyperliquidFuturesService.toCoin("SOL")).isEqualTo("SOL");
        }
    }

    @Nested
    @DisplayName("toResolution")
    class ToResolution {

        @Test
        void recognisesNumericMinuteForms() {
            assertThat(HyperliquidFuturesService.toResolution("1")).isEqualTo("1m");
            assertThat(HyperliquidFuturesService.toResolution("5")).isEqualTo("5m");
            assertThat(HyperliquidFuturesService.toResolution("15")).isEqualTo("15m");
            assertThat(HyperliquidFuturesService.toResolution("60")).isEqualTo("1h");
            assertThat(HyperliquidFuturesService.toResolution("240")).isEqualTo("4h");
        }

        @Test
        void recognisesSuffixedForms() {
            assertThat(HyperliquidFuturesService.toResolution("1m")).isEqualTo("1m");
            assertThat(HyperliquidFuturesService.toResolution("1h")).isEqualTo("1h");
            assertThat(HyperliquidFuturesService.toResolution("4h")).isEqualTo("4h");
            assertThat(HyperliquidFuturesService.toResolution("1d")).isEqualTo("1d");
            assertThat(HyperliquidFuturesService.toResolution("1w")).isEqualTo("1w");
        }

        @Test
        void passesUnrecognisedInputThrough() {
            assertThat(HyperliquidFuturesService.toResolution("45m")).isEqualTo("45m");
            assertThat(HyperliquidFuturesService.toResolution("unknown")).isEqualTo("unknown");
        }
    }

    @Nested
    @DisplayName("toIntervalMillis")
    class ToIntervalMillis {

        @Test
        void returnsCorrectMilliseconds() {
            assertThat(HyperliquidFuturesService.toIntervalMillis("1m")).isEqualTo(60_000L);
            assertThat(HyperliquidFuturesService.toIntervalMillis("1h")).isEqualTo(3_600_000L);
            assertThat(HyperliquidFuturesService.toIntervalMillis("1d")).isEqualTo(86_400_000L);
            assertThat(HyperliquidFuturesService.toIntervalMillis("1w")).isEqualTo(604_800_000L);
        }

        @Test
        void throwsForUnrecognisedFormat() {
            assertThatThrownBy(() -> HyperliquidFuturesService.toIntervalMillis("bad"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void handlesArbitraryValidResolutions() {
            assertThat(HyperliquidFuturesService.toIntervalMillis("45m")).isEqualTo(2_700_000L);
            assertThat(HyperliquidFuturesService.toIntervalMillis("24h")).isEqualTo(86_400_000L);
            assertThat(HyperliquidFuturesService.toIntervalMillis("2d")).isEqualTo(172_800_000L);
        }
    }

    @Nested
    @DisplayName("formatPrice")
    class FormatPrice {

        @Test
        void roundsToFiveSignificantFigures() {
            assertThat(HyperliquidFuturesService.formatPrice(50000.123456)).isEqualTo("50000");
            assertThat(HyperliquidFuturesService.formatPrice(3141.59265)).isEqualTo("3141.6");
            assertThat(HyperliquidFuturesService.formatPrice(0.000123456)).isEqualTo("0.00012346");
        }

        @Test
        void stripsTrailingZeros() {
            assertThat(HyperliquidFuturesService.formatPrice(50000.0)).isEqualTo("50000");
            assertThat(HyperliquidFuturesService.formatPrice(1.5000)).isEqualTo("1.5");
        }
    }

    @Nested
    @DisplayName("formatSize")
    class FormatSize {

        @Test
        void roundsDownToSzDecimals() {
            assertThat(HyperliquidFuturesService.formatSize(0.0019999, 5)).isEqualTo("0.00199");
            assertThat(HyperliquidFuturesService.formatSize(1.23456789, 4)).isEqualTo("1.2345");
        }

        @Test
        void handlesWholeNumbers() {
            assertThat(HyperliquidFuturesService.formatSize(1.0, 3)).isEqualTo("1.000");
        }
    }

    // -------------------------------------------------------------------------
    // HTTP-backed market data methods — mocked RestTemplate, no signer
    // -------------------------------------------------------------------------

    private TestableService service;
    private RestTemplate restTemplate;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        service = new TestableService("https://api.hyperliquid-testnet.xyz", null, restTemplate);
    }

    @Nested
    @DisplayName("getCurrentPrice")
    class GetCurrentPrice {

        @Test
        void returnsPrice_forKnownCoin() {
            stubInfoPost("{\"BTC\":\"65000.5\",\"ETH\":\"3500.0\"}");
            assertThat(service.getCurrentPrice("BTCUSDT")).isEqualTo(65000.5);
        }

        @Test
        void returnsZero_whenCoinNotPresent() {
            stubInfoPost("{\"ETH\":\"3500.0\"}");
            assertThat(service.getCurrentPrice("BTCUSDT")).isEqualTo(0.0);
        }

        @Test
        void returnsZero_onHttpError() {
            doThrow(new RuntimeException("network error"))
                    .when(restTemplate).postForObject(anyString(), any(), eq(String.class));
            assertThat(service.getCurrentPrice("BTCUSDT")).isEqualTo(0.0);
        }
    }

    @Nested
    @DisplayName("fetchOhlcv")
    class FetchOhlcv {

        @Test
        void parsesCandles_fromCandleSnapshotResponse() {
            String body = """
                    [
                      {"t":1710000000000,"T":1710000060000,"o":"64000","h":"65000","l":"63500","c":"64800","v":"1.5"},
                      {"t":1710000060000,"T":1710000120000,"o":"64800","h":"65100","l":"64700","c":"65000","v":"2.0"}
                    ]
                    """;
            stubInfoPost(body);

            List<Candle> candles = service.fetchOhlcv("BTCUSDT", "1m", 2);

            assertThat(candles).hasSize(2);
            assertThat(candles.get(0).getOpen()).isEqualByComparingTo("64000");
            assertThat(candles.get(0).getClose()).isEqualByComparingTo("64800");
            assertThat(candles.get(1).getVolume()).isEqualByComparingTo("2.0");
        }

        @Test
        void returnsEmptyList_onHttpError() {
            doThrow(new RuntimeException("network error"))
                    .when(restTemplate).postForObject(anyString(), any(), eq(String.class));
            assertThat(service.fetchOhlcv("BTCUSDT", "1m", 10)).isEmpty();
        }

        @Test
        void returnsEmptyList_whenResponseIsNotArray() {
            stubInfoPost("{\"error\":\"bad\"}");
            assertThat(service.fetchOhlcv("BTCUSDT", "1m", 10)).isEmpty();
        }
    }

    @Nested
    @DisplayName("get24HourStats")
    class Get24HourStats {

        private static final String META_RESPONSE = """
                [
                  {"universe":[{"name":"BTC"},{"name":"ETH"}]},
                  [
                    {"markPx":"65000","prevDayPx":"63000","dayNtlVlm":"1300000"},
                    {"markPx":"3500","prevDayPx":"3400","dayNtlVlm":"35000"}
                  ]
                ]
                """;

        @Test
        void returnsCorrectStats_forKnownSymbol() {
            stubInfoPost(META_RESPONSE);

            Ticker24hrStats stats = service.get24HourStats("BTCUSDT");

            assertThat(stats.getSymbol()).isEqualTo("BTCUSDT");
            assertThat(stats.getLastPrice()).isEqualTo(65000.0);
            assertThat(stats.getOpenPrice()).isEqualTo(63000.0);
            assertThat(stats.getPriceChange()).isEqualTo(2000.0);
            assertThat(stats.getPriceChangePercent())
                    .isEqualByComparingTo(2000.0 / 63000.0 * 100.0);
            assertThat(stats.getQuoteVolume()).isEqualTo(1_300_000.0);
        }

        @Test
        void returnsEmptyStats_whenCoinNotInUniverse() {
            stubInfoPost(META_RESPONSE);
            Ticker24hrStats stats = service.get24HourStats("SOLUSDT");
            assertThat(stats.getSymbol()).isEqualTo("SOLUSDT");
            assertThat(stats.getLastPrice()).isEqualTo(0.0);
        }

        @Test
        void returnsEmptyStats_onHttpError() {
            doThrow(new RuntimeException("timeout"))
                    .when(restTemplate).postForObject(anyString(), any(), eq(String.class));
            Ticker24hrStats stats = service.get24HourStats("BTCUSDT");
            assertThat(stats.getSymbol()).isEqualTo("BTCUSDT");
            assertThat(stats.getLastPrice()).isEqualTo(0.0);
        }
    }

    @Nested
    @DisplayName("getMarginBalance")
    class GetMarginBalance {

        @Test
        void returnsAccountValue_whenWalletConfigured() {
            TestableService withWallet = new TestableService(
                    "https://api.hyperliquid-testnet.xyz", "0xWallet", restTemplate);
            stubInfoPost("{\"marginSummary\":{\"accountValue\":\"5000.00\"}}");
            assertThat(withWallet.getMarginBalance()).isEqualTo(5000.0);
        }

        @Test
        void returnsZero_whenNoWalletConfigured() {
            assertThat(service.getMarginBalance()).isEqualTo(0.0);
        }

        @Test
        void returnsZero_whenMarginSummaryMissing() {
            stubInfoPost("{\"other\":\"data\"}");
            TestableService withWallet = new TestableService(
                    "https://api.hyperliquid-testnet.xyz", "0xWallet", restTemplate);
            assertThat(withWallet.getMarginBalance()).isEqualTo(0.0);
        }
    }

    // -------------------------------------------------------------------------
    // Paper fallback — order methods without a signer
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("paper fallback (no signer)")
    class PaperFallback {

        @Test
        void enterLongPosition_returnsPaperResult() {
            // 0.001 BTC at $50,000 paper price = $50 required margin — well within $10k balance
            OrderResult result = service.enterLongPosition("BTCUSDT", 0.001);
            assertThat(result).isNotNull();
            assertThat(result.getExchangeOrderId()).startsWith("PAPER-");
        }

        @Test
        void exitLongPosition_returnsPaperResult() {
            // Enter first so the paper service has a position to close
            service.enterLongPosition("BTCUSDT", 0.001);
            OrderResult result = service.exitLongPosition("BTCUSDT", 0.001);
            assertThat(result.getExchangeOrderId()).startsWith("PAPER-");
        }

        @Test
        void enterShortPosition_returnsPaperResult() {
            OrderResult result = service.enterShortPosition("BTCUSDT", 0.001);
            assertThat(result.getExchangeOrderId()).startsWith("PAPER-");
        }

        @Test
        void exitShortPosition_returnsPaperResult() {
            // Enter first so the paper service has a position to close
            service.enterShortPosition("BTCUSDT", 0.001);
            OrderResult result = service.exitShortPosition("BTCUSDT", 0.001);
            assertThat(result.getExchangeOrderId()).startsWith("PAPER-");
        }

        @Test
        void placeStopLossOrder_returnsPaperResult() {
            OrderResult result = service.placeStopLossOrder("BTCUSDT", "sell", 0.001, 48000.0);
            assertThat(result.getExchangeOrderId()).startsWith("PAPER-");
        }

        @Test
        void placeTakeProfitOrder_returnsPaperResult() {
            OrderResult result = service.placeTakeProfitOrder("BTCUSDT", "sell", 0.001, 52000.0);
            assertThat(result.getExchangeOrderId()).startsWith("PAPER-");
        }

        @Test
        void setLeverage_isNoOp() {
            // Must not throw; no /exchange call made
            service.setLeverage("BTCUSDT", 10);
            verify(restTemplate, times(0))
                    .postForObject(contains("/exchange"), any(), eq(String.class));
        }
    }

    // -------------------------------------------------------------------------
    // Live order execution — with signer
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("order execution (with signer)")
    class OrderExecutionWithSigner {

        /**
         * Ganache account #0 — a well-known test key.
         * Address: 0x90F8bf6A479f320ead074411a4B0e7944Ea8c9C1
         */
        private static final String TEST_KEY =
                "0x4f3edf983ac636a65a842ce7c78d9aa706d3b113bce9c46f30d7d21715b23b1d";

        // BTC at index 0, szDecimals=5
        private static final String META_JSON =
                "{\"universe\":[{\"name\":\"BTC\",\"szDecimals\":5},{\"name\":\"ETH\",\"szDecimals\":4}]}";
        private static final String ALL_MIDS_JSON =
                "{\"BTC\":\"50000.0\",\"ETH\":\"3000.0\"}";

        private static final String RESTING_RESPONSE =
                "{\"status\":\"ok\",\"response\":{\"type\":\"order\",\"data\":{\"statuses\":[{\"resting\":{\"oid\":12345}}]}}}";
        private static final String FILLED_RESPONSE =
                "{\"status\":\"ok\",\"response\":{\"type\":\"order\",\"data\":{\"statuses\":[{\"filled\":{\"oid\":12346,\"totalSz\":\"0.002\",\"avgPx\":\"50100.5\"}}]}}}";
        private static final String REJECTED_RESPONSE =
                "{\"status\":\"err\",\"response\":\"insufficient margin\"}";
        private static final String ERROR_STATUS_RESPONSE =
                "{\"status\":\"ok\",\"response\":{\"type\":\"order\",\"data\":{\"statuses\":[{\"error\":\"Order size too small\"}]}}}";
        private static final String LEVERAGE_OK_RESPONSE =
                "{\"status\":\"ok\",\"response\":{\"type\":\"default\"}}";

        private TestableService signerService;
        private RestTemplate rt;

        @BeforeEach
        void setUp() {
            rt = mock(RestTemplate.class);
            HyperliquidOrderSigner signer = new HyperliquidOrderSigner(TEST_KEY, true);
            signerService = new TestableService(
                    "https://api.hyperliquid-testnet.xyz", null, signer, rt);
            // Route /info calls to the right fixture by request body content
            doAnswer(inv -> {
                String body = (String) ((HttpEntity<?>) inv.getArgument(1)).getBody();
                if (body.contains(":\"meta\""))    return META_JSON;
                if (body.contains("allMids"))      return ALL_MIDS_JSON;
                return "{}";
            }).when(rt).postForObject(contains("/info"), any(), eq(String.class));
        }

        // --- enterLongPosition ---

        @Test
        void enterLongPosition_restingOrder_returnsNew() {
            doReturn(RESTING_RESPONSE).when(rt)
                    .postForObject(contains("/exchange"), any(), eq(String.class));

            OrderResult r = signerService.enterLongPosition("BTCUSDT", 100.0);

            assertThat(r.getExchangeOrderId()).isEqualTo("12345");
            assertThat(r.getStatus()).isEqualTo(OrderResult.OrderStatus.NEW);
            assertThat(r.getSymbol()).isEqualTo("BTCUSDT");
            assertThat(r.getSide()).isEqualTo("buy");
            assertThat(r.getOrderedQuantity()).isPositive();
            assertThat(r.getFilledQuantity()).isEqualTo(0.0);
        }

        @Test
        void enterLongPosition_filledOrder_returnsFilled() {
            doReturn(FILLED_RESPONSE).when(rt)
                    .postForObject(contains("/exchange"), any(), eq(String.class));

            OrderResult r = signerService.enterLongPosition("BTCUSDT", 100.0);

            assertThat(r.getExchangeOrderId()).isEqualTo("12346");
            assertThat(r.getStatus()).isEqualTo(OrderResult.OrderStatus.FILLED);
            assertThat(r.getFilledQuantity()).isEqualTo(0.002);
            assertThat(r.getAvgFillPrice()).isEqualTo(50100.5);
        }

        @Test
        void enterLongPosition_exchangeRejects_returnsRejected() {
            doReturn(REJECTED_RESPONSE).when(rt)
                    .postForObject(contains("/exchange"), any(), eq(String.class));

            OrderResult r = signerService.enterLongPosition("BTCUSDT", 100.0);
            assertThat(r.getStatus()).isEqualTo(OrderResult.OrderStatus.REJECTED);
        }

        @Test
        void enterLongPosition_errorStatus_returnsRejected() {
            doReturn(ERROR_STATUS_RESPONSE).when(rt)
                    .postForObject(contains("/exchange"), any(), eq(String.class));

            OrderResult r = signerService.enterLongPosition("BTCUSDT", 100.0);
            assertThat(r.getStatus()).isEqualTo(OrderResult.OrderStatus.REJECTED);
        }

        @Test
        void enterLongPosition_networkError_returnsRejected() {
            doThrow(new RuntimeException("connection reset")).when(rt)
                    .postForObject(contains("/exchange"), any(), eq(String.class));

            OrderResult r = signerService.enterLongPosition("BTCUSDT", 100.0);
            assertThat(r.getStatus()).isEqualTo(OrderResult.OrderStatus.REJECTED);
        }

        // --- side and reduceOnly per method ---

        @Test
        void exitLongPosition_sellSide() {
            doReturn(RESTING_RESPONSE).when(rt)
                    .postForObject(contains("/exchange"), any(), eq(String.class));

            OrderResult r = signerService.exitLongPosition("BTCUSDT", 100.0);
            assertThat(r.getSide()).isEqualTo("sell");
            assertThat(r.getStatus()).isEqualTo(OrderResult.OrderStatus.NEW);
        }

        @Test
        void enterShortPosition_sellSide() {
            doReturn(RESTING_RESPONSE).when(rt)
                    .postForObject(contains("/exchange"), any(), eq(String.class));

            OrderResult r = signerService.enterShortPosition("BTCUSDT", 100.0);
            assertThat(r.getSide()).isEqualTo("sell");
        }

        @Test
        void exitShortPosition_buySide() {
            doReturn(RESTING_RESPONSE).when(rt)
                    .postForObject(contains("/exchange"), any(), eq(String.class));

            OrderResult r = signerService.exitShortPosition("BTCUSDT", 100.0);
            assertThat(r.getSide()).isEqualTo("buy");
        }

        // --- trigger orders ---

        @Test
        void placeStopLossOrder_poststoExchange_returnsSellSide() {
            doReturn(RESTING_RESPONSE).when(rt)
                    .postForObject(contains("/exchange"), any(), eq(String.class));

            OrderResult r = signerService.placeStopLossOrder("BTCUSDT", "sell", 0.001, 48000.0);

            assertThat(r.getSide()).isEqualTo("sell");
            assertThat(r.getStatus()).isEqualTo(OrderResult.OrderStatus.NEW);
            assertThat(r.getOrderedQuantity()).isEqualTo(0.001);
            verify(rt).postForObject(contains("/exchange"), any(), eq(String.class));
        }

        @Test
        void placeTakeProfitOrder_poststoExchange() {
            doReturn(RESTING_RESPONSE).when(rt)
                    .postForObject(contains("/exchange"), any(), eq(String.class));

            OrderResult r = signerService.placeTakeProfitOrder("BTCUSDT", "sell", 0.001, 52000.0);

            assertThat(r.getSide()).isEqualTo("sell");
            assertThat(r.getStatus()).isEqualTo(OrderResult.OrderStatus.NEW);
            verify(rt).postForObject(contains("/exchange"), any(), eq(String.class));
        }

        // --- setLeverage ---

        @Test
        void setLeverage_postsUpdateLeverageAction() {
            doReturn(LEVERAGE_OK_RESPONSE).when(rt)
                    .postForObject(contains("/exchange"), any(), eq(String.class));

            signerService.setLeverage("BTCUSDT", 10);

            verify(rt).postForObject(contains("/exchange"), any(), eq(String.class));
        }

        @Test
        void setLeverage_exchangeError_doesNotThrow() {
            doReturn("{\"status\":\"err\",\"response\":\"unknown asset\"}").when(rt)
                    .postForObject(contains("/exchange"), any(), eq(String.class));

            // Should log the error but not propagate
            signerService.setLeverage("BTCUSDT", 10);
        }

        // --- coin meta caching ---

        @Test
        void coinMetaIsCached_metaFetchedOnce_acrossMultipleCalls() {
            doReturn(RESTING_RESPONSE).when(rt)
                    .postForObject(contains("/exchange"), any(), eq(String.class));

            signerService.enterLongPosition("BTCUSDT", 100.0);  // populates cache
            signerService.enterLongPosition("BTCUSDT", 100.0);  // cache hit

            // allMids called twice (one per enterLong), meta called once (cached)
            verify(rt, times(3)).postForObject(contains("/info"), any(), eq(String.class));
            verify(rt, times(2)).postForObject(contains("/exchange"), any(), eq(String.class));
        }

        @Test
        void triggerOrder_doesNotCallAllMids_onlyMeta() {
            doReturn(RESTING_RESPONSE).when(rt)
                    .postForObject(contains("/exchange"), any(), eq(String.class));

            signerService.placeStopLossOrder("BTCUSDT", "sell", 0.001, 48000.0);

            // Trigger orders use the provided price — no getCurrentPrice call needed
            verify(rt, times(1)).postForObject(contains("/info"), any(), eq(String.class));
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void stubInfoPost(String responseBody) {
        doReturn(responseBody)
                .when(restTemplate)
                .postForObject(anyString(), any(), eq(String.class));
    }

    /**
     * Subclass that accepts an injected {@link RestTemplate} so HTTP calls can be mocked.
     * The 3-arg constructor (paper mode) preserves compatibility with existing market-data tests.
     */
    static class TestableService extends HyperliquidFuturesService {

        TestableService(String baseUrl, String walletAddress, RestTemplate restTemplate) {
            super(baseUrl, walletAddress, restTemplate);
        }

        TestableService(String baseUrl, String walletAddress,
                        HyperliquidOrderSigner signer, RestTemplate restTemplate) {
            super(baseUrl, walletAddress, signer, restTemplate);
        }
    }
}
