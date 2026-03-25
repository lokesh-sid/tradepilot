package tradingbot.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TradingSafetyServiceTest {

    @Test
    @DisplayName("Paper mode is always allowed by default")
    void paperModeIsAlwaysAllowed() {
        TradingSafetyService service = new TradingSafetyService("paper", "paper", "TESTNET_DOMAIN", false);

        assertDoesNotThrow(() -> service.validateBotStartMode(true));
        assertDoesNotThrow(service::validateConfiguredExchangeAccess);
    }

    @Test
    @DisplayName("External exchange execution requires live execution mode")
    void externalExecutionRequiresLiveMode() {
        TradingSafetyService service = new TradingSafetyService("bybit", "paper", "TESTNET_DOMAIN", false);

        assertThrows(IllegalArgumentException.class, () -> service.validateBotStartMode(false));
    }

    @Test
    @DisplayName("External exchange execution rejects paper provider")
    void externalExecutionRejectsPaperProvider() {
        TradingSafetyService service = new TradingSafetyService("paper", "live", "TESTNET_DOMAIN", false);

        assertThrows(IllegalArgumentException.class, () -> service.validateBotStartMode(false));
    }

    @Test
    @DisplayName("Mainnet exchange access requires explicit opt in")
    void mainnetAccessRequiresExplicitOptIn() {
        TradingSafetyService service = new TradingSafetyService("binance", "live", "MAINNET_DOMAIN", false);

        assertThrows(IllegalArgumentException.class, () -> service.validateBotStartMode(false));
        assertThrows(IllegalStateException.class, service::validateConfiguredExchangeAccess);
    }

    @Test
    @DisplayName("Bybit testnet is allowed without mainnet opt in")
    void bybitTestnetAllowedWithoutMainnetOptIn() {
        TradingSafetyService service = new TradingSafetyService("bybit", "live", "TESTNET_DOMAIN", false);

        assertDoesNotThrow(() -> service.validateBotStartMode(false));
        assertDoesNotThrow(service::validateConfiguredExchangeAccess);
    }
}