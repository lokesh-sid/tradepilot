package tradingbot.config;

import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Centralizes safe defaults and explicit opt-in checks for non-paper trading.
 */
@Component
public class TradingSafetyService {

    private static final String PAPER_PROVIDER = "paper";
    private static final String BYBIT_PROVIDER = "bybit";
    private static final String BINANCE_PROVIDER = "binance";
    private static final String LIVE_MODE = "live";
    private static final String TESTNET_DOMAIN = "TESTNET_DOMAIN";

    private final String provider;
    private final String executionMode;
    private final String bybitDomain;
    private final boolean liveTradingEnabled;

    public TradingSafetyService(
            @Value("${trading.exchange.provider:paper}") String provider,
            @Value("${trading.execution.mode:paper}") String executionMode,
            @Value("${trading.bybit.domain:TESTNET_DOMAIN}") String bybitDomain,
            @Value("${trading.live.enabled:false}") boolean liveTradingEnabled) {
        this.provider = normalize(provider);
        this.executionMode = normalize(executionMode);
        this.bybitDomain = bybitDomain == null ? TESTNET_DOMAIN : bybitDomain.trim();
        this.liveTradingEnabled = liveTradingEnabled;
    }

    public void validateConfiguredExchangeAccess() {
        if (requiresMainnetOptIn() && !liveTradingEnabled) {
            throw new IllegalStateException(
                "Mainnet trading is disabled by default. Set trading.live.enabled=true "
                    + "and provide explicit live credentials before using a mainnet exchange."
            );
        }
    }

    public void validateBotStartMode(boolean paperMode) {
        if (paperMode) {
            return;
        }

        if (!isLiveExecutionMode()) {
            throw new IllegalArgumentException(
                "External exchange execution requires trading.execution.mode=live. "
                    + "Keep paper=true for the safe default."
            );
        }

        if (isPaperProvider()) {
            throw new IllegalArgumentException(
                "External exchange execution requires trading.exchange.provider to be set to bybit or binance."
            );
        }

        if (requiresMainnetOptIn() && !liveTradingEnabled) {
            throw new IllegalArgumentException(
                "Mainnet trading is disabled by default. Activate the explicit live configuration "
                    + "before starting a non-paper bot against a mainnet exchange."
            );
        }
    }

    public boolean requiresMainnetOptIn() {
        return isBinanceProvider() || (isBybitProvider() && !isBybitTestnet());
    }

    private boolean isLiveExecutionMode() {
        return LIVE_MODE.equals(executionMode);
    }

    private boolean isPaperProvider() {
        return PAPER_PROVIDER.equals(provider);
    }

    private boolean isBinanceProvider() {
        return BINANCE_PROVIDER.equals(provider);
    }

    private boolean isBybitProvider() {
        return BYBIT_PROVIDER.equals(provider);
    }

    private boolean isBybitTestnet() {
        return TESTNET_DOMAIN.equalsIgnoreCase(bybitDomain);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}