package tradingbot.bot.controller.validation;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for ValidSymbolValidator
 */
class ValidSymbolValidatorTest {

    private final ValidSymbolValidator validator = new ValidSymbolValidator();

    @Test
    @DisplayName("Should accept null values")
    void shouldAcceptNullValues() {
        assertTrue(validator.isValid(null, null));
    }

    @Test
    @DisplayName("Should accept empty strings")
    void shouldAcceptEmptyStrings() {
        assertTrue(validator.isValid("", null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"BTCUSDT", "ETHUSDT", "ADAUSDT", "SOLUSDT", "DOTUSDT", "LINKUSDT", "UNIUSDT", "AAVEUSDT"})
    @DisplayName("Should accept valid trading symbols")
    void shouldAcceptValidSymbols(String symbol) {
        assertTrue(validator.isValid(symbol, null), "Symbol '" + symbol + "' should be valid");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "btcusdt",      // lowercase
        "BTC-USDT",     // contains dash
        "BTC/USDT",     // contains slash
        "BTC USDT",     // contains space
        "BTC",          // too short (3 chars)
        "VERYLONGTRADINGSYMBOLNAME", // too long (21 chars)
        "btcusdt123",   // lowercase with numbers
        "BtcUsdt",      // mixed case
        "BTC_USDT",     // contains underscore
        "BTC@USDT",     // contains special character
        "BTC.USDT"      // contains dot
    })
    @DisplayName("Should reject invalid trading symbols")
    void shouldRejectInvalidSymbols(String symbol) {
        assertFalse(validator.isValid(symbol, null), "Symbol '" + symbol + "' should be invalid");
    }

    @Test
    @DisplayName("Should accept minimum length symbols")
    void shouldAcceptMinimumLength() {
        assertTrue(validator.isValid("ABCD", null));
    }

    @Test
    @DisplayName("Should accept maximum length symbols")
    void shouldAcceptMaximumLength() {
        assertTrue(validator.isValid("ABCDEFGHIJKLMNOPQRST", null)); // 20 characters
    }

    @Test
    @DisplayName("Should reject symbols shorter than minimum")
    void shouldRejectTooShort() {
        assertFalse(validator.isValid("ABC", null)); // 3 characters
    }

    @Test
    @DisplayName("Should reject symbols longer than maximum")
    void shouldRejectTooLong() {
        assertFalse(validator.isValid("ABCDEFGHIJKLMNOPQRSTU", null)); // 21 characters
    }

    @Test
    @DisplayName("Should accept symbols with numbers")
    void shouldAcceptSymbolsWithNumbers() {
        assertTrue(validator.isValid("BTC123", null));
        assertTrue(validator.isValid("ETH456", null));
    }

    @Test
    @DisplayName("Should reject symbols with lowercase letters")
    void shouldRejectLowercase() {
        assertFalse(validator.isValid("btcusdt", null));
        assertFalse(validator.isValid("BtcUsdt", null));
        assertFalse(validator.isValid("BTCusdt", null));
    }
}