package tradingbot.agent.config.factory;

import org.springframework.stereotype.Component;

import tradingbot.agent.config.ExchangeCredentials;
import tradingbot.agent.config.ExchangeServiceFactory;
import tradingbot.bot.service.FuturesExchangeService;
import tradingbot.infrastructure.marketdata.hyperliquid.HyperliquidFuturesService;

/**
 * Factory for {@link HyperliquidFuturesService}.
 *
 * <p>Credentials mapping:
 * <ul>
 *   <li>{@code network} — {@code "testnet"} (default) or {@code "mainnet"}</li>
 *   <li>{@code apiKey}  — optional wallet address for {@code getMarginBalance}</li>
 * </ul>
 *
 * <p>No API key/secret is required for Hyperliquid market data. Order execution
 * is delegated to paper trading until EIP-712 signing is implemented.
 */
@Component
public class HyperliquidServiceFactory implements ExchangeServiceFactory {

    private static final String TESTNET_URL = "https://api.hyperliquid-testnet.xyz";
    private static final String MAINNET_URL  = "https://api.hyperliquid.xyz";

    @Override
    public String exchangeName() {
        return "HYPERLIQUID";
    }

    @Override
    public FuturesExchangeService create(ExchangeCredentials creds) {
        String baseUrl = resolveBaseUrl(creds);
        String walletAddress = (creds != null) ? creds.getApiKey() : null;
        return new HyperliquidFuturesService(baseUrl, walletAddress);
    }

    private String resolveBaseUrl(ExchangeCredentials creds) {
        if (creds != null && "mainnet".equalsIgnoreCase(creds.getNetwork())) {
            return MAINNET_URL;
        }
        return TESTNET_URL;
    }
}
