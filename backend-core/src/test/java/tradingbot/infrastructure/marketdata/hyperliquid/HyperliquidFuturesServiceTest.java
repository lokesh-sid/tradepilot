package tradingbot.infrastructure.marketdata.hyperliquid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import tradingbot.bot.service.BinanceFuturesService.Candle;
import tradingbot.bot.service.Ticker24hrStats;

/**
 * Unit tests for {@link HyperliquidFuturesService}.
 *
 * <p>HTTP calls are intercepted via a subclass that replaces {@link RestTemplate}
 * with a mock, keeping the test entirely offline.
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
        void defaultsToOneMinute_forUnknownInput() {
            assertThat(HyperliquidFuturesService.toResolution("unknown")).isEqualTo("1m");
            assertThat(HyperliquidFuturesService.toResolution("99")).isEqualTo("1m");
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
        void defaultsToOneMinute_forUnknownResolution() {
            assertThat(HyperliquidFuturesService.toIntervalMillis("bad")).isEqualTo(60_000L);
        }
    }

    // -------------------------------------------------------------------------
    // HTTP-backed methods — use a testable subclass with mocked RestTemplate
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
            String body = "{\"BTC\":\"65000.5\",\"ETH\":\"3500.0\"}";
            stubPost(body);

            assertThat(service.getCurrentPrice("BTCUSDT")).isEqualTo(65000.5);
        }

        @Test
        void returnsZero_whenCoinNotPresent() {
            stubPost("{\"ETH\":\"3500.0\"}");

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
            stubPost(body);

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
            stubPost("{\"error\":\"bad\"}");
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
            stubPost(META_RESPONSE);

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
            stubPost(META_RESPONSE);

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
            stubPost("{\"marginSummary\":{\"accountValue\":\"5000.00\"}}");

            assertThat(withWallet.getMarginBalance()).isEqualTo(5000.0);
        }

        @Test
        void returnsZero_whenNoWalletConfigured() {
            // service has walletAddress=null
            assertThat(service.getMarginBalance()).isEqualTo(0.0);
        }

        @Test
        void returnsZero_whenMarginSummaryMissing() {
            stubPost("{\"other\":\"data\"}");
            TestableService withWallet = new TestableService(
                    "https://api.hyperliquid-testnet.xyz", "0xWallet", restTemplate);

            assertThat(withWallet.getMarginBalance()).isEqualTo(0.0);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void stubPost(String responseBody) {
        // doReturn avoids Mockito varargs-matcher ambiguity for the Object... uriVars overload
        doReturn(responseBody)
                .when(restTemplate)
                .postForObject(anyString(), any(), eq(String.class));
    }

    /**
     * Subclass that accepts an injected {@link RestTemplate} so HTTP calls can be mocked.
     */
    static class TestableService extends HyperliquidFuturesService {

        TestableService(String baseUrl, String walletAddress, RestTemplate restTemplate) {
            super(baseUrl, walletAddress, restTemplate);
        }
    }
}
