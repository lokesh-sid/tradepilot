package tradingbot.agent.config.factory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import tradingbot.agent.config.ExchangeCredentials;
import tradingbot.agent.config.ExchangeServiceFactory;
import tradingbot.bot.service.FuturesExchangeService;
import tradingbot.infrastructure.marketdata.hyperliquid.HyperliquidFuturesService;
import tradingbot.infrastructure.marketdata.hyperliquid.HyperliquidOrderSigner;

/**
 * Factory for {@link HyperliquidFuturesService}.
 *
 * <p>Credentials mapping ({@code trading.credentials.hyperliquid.*}):
 * <ul>
 *   <li>{@code network}     — {@code "testnet"} (default) or {@code "mainnet"}</li>
 *   <li>{@code api-key}     — wallet address used for {@code getMarginBalance}</li>
 *   <li>{@code private-key} — secp256k1 private key for EIP-712 order signing;
 *                             leave blank to run in paper-trading mode</li>
 * </ul>
 */
@Component
public class HyperliquidServiceFactory implements ExchangeServiceFactory {

    private static final Logger log = LoggerFactory.getLogger(HyperliquidServiceFactory.class);

    private static final String TESTNET_URL = "https://api.hyperliquid-testnet.xyz";
    private static final String MAINNET_URL  = "https://api.hyperliquid.xyz";

    @Override
    public String exchangeName() {
        return "HYPERLIQUID";
    }

    @Override
    public FuturesExchangeService create(ExchangeCredentials creds) {
        boolean useTestnet = creds == null || !"mainnet".equalsIgnoreCase(creds.getNetwork());
        String baseUrl = useTestnet ? TESTNET_URL : MAINNET_URL;
        String walletAddress = (creds != null) ? creds.getApiKey() : null;

        HyperliquidOrderSigner signer = buildSigner(creds, useTestnet);
        log.info("HyperliquidServiceFactory: baseUrl={}, walletAddress={}, liveOrders={}",
                baseUrl, walletAddress, signer != null);

        return new HyperliquidFuturesService(baseUrl, walletAddress, signer);
    }

    private HyperliquidOrderSigner buildSigner(ExchangeCredentials creds, boolean useTestnet) {
        if (creds == null) {
            return null;
        }
        String privateKey = creds.getPrivateKey();
        if (privateKey == null || privateKey.isBlank()) {
            return null;
        }
        return new HyperliquidOrderSigner(privateKey, useTestnet);
    }
}
