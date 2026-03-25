package tradingbot.bot.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.springframework.http.MediaType;

import tradingbot.agent.api.dto.CreateAgentRequest;
import tradingbot.bot.controller.dto.request.LeverageUpdateRequest;
import tradingbot.bot.controller.dto.request.SentimentUpdateRequest;

class TradingBotControllerValidationTest extends AbstractControllerValidationTest {

    private static final String API_V1_BOTS = "/api/v1/bots/";
    private static final String VALID_UUID   = "123e4567-e89b-12d3-a456-426614174000";
    private static final String INVALID_UUID = "not-a-uuid";

    // ----- @ValidBotId on path variables -----

    @Test
    @DisplayName("Status: invalid botId should return 400 VALIDATION_FAILED")
    void status_invalidBotId() throws Exception {        
        mockMvc.perform(get(API_V1_BOTS + INVALID_UUID + "/status"))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.title").value("Constraint Violation"));
    }    @Test
    @DisplayName("Stop: invalid botId should return 400 VALIDATION_FAILED")
    void stop_invalidBotId() throws Exception {
        mockMvc.perform(put(API_V1_BOTS + INVALID_UUID + "/stop"))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.title").value("Constraint Violation"));
    }

    @Test
    @DisplayName("Delete: invalid botId should return 400 VALIDATION_FAILED")
    void delete_invalidBotId() throws Exception {
        mockMvc.perform(delete(API_V1_BOTS + INVALID_UUID))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.title").value("Constraint Violation"));
    }

    // ----- @ValidLeverage on LeverageUpdateRequest -----

    @Test
    @DisplayName("Leverage: below minimum (0) should return 400 VALIDATION_FAILED")
    void leverage_belowMinimum() throws Exception {
        LeverageUpdateRequest request = new LeverageUpdateRequest(0.0);

        mockMvc.perform(post(API_V1_BOTS + VALID_UUID + "/leverage")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.title").value("Validation Failed"));
    }

    @Test
    @DisplayName("Leverage: above maximum (126) should return 400 VALIDATION_FAILED")
    void leverage_aboveMaximum() throws Exception {
        LeverageUpdateRequest request = new LeverageUpdateRequest(126.0);

        mockMvc.perform(post(API_V1_BOTS + VALID_UUID + "/leverage")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.title").value("Validation Failed"));
    }

    @Test
    @DisplayName("Leverage: valid (e.g. 10) should pass validation")
    void leverage_valid() throws Exception {
        LeverageUpdateRequest request = new LeverageUpdateRequest(10.0);

        // Valid leverage should pass validation - we only care that it doesn't return 400 with validation error
        // The actual controller logic (getBotOrThrow, etc.) will fail without full mocking, 
        // but that's outside the scope of validation testing
        mockMvc.perform(post(API_V1_BOTS + VALID_UUID + "/leverage")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
               .andExpect(result -> {
                   int status = result.getResponse().getStatus();
                   // Should NOT be 400 with validation error - accept any other status
                   if (status == 400) {
                       String body = result.getResponse().getContentAsString();
                       if (body.contains("Validation Failed") || body.contains("Constraint Violation")) {
                           throw new AssertionError("Valid leverage should not fail validation");
                       }
                   }
               });
    }

    // ----- SentimentUpdateRequest (non-null enable) -----

    @Test
    @DisplayName("Sentiment: null enable should return 400 VALIDATION_FAILED")
    void sentiment_nullEnable() throws Exception {
        String requestJson = "{\"enable\": null}";

        mockMvc.perform(post(API_V1_BOTS + VALID_UUID + "/sentiment")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.title").value("Validation Failed"));
    }

    @Test
    @DisplayName("Sentiment: valid enable should pass validation")
    void sentiment_valid() throws Exception {
        SentimentUpdateRequest request = new SentimentUpdateRequest(true);

        // Valid sentiment should pass validation
        mockMvc.perform(post(API_V1_BOTS + VALID_UUID + "/sentiment")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
               .andExpect(result -> {
                   int status = result.getResponse().getStatus();
                   if (status == 400) {
                       String body = result.getResponse().getContentAsString();
                       if (body.contains("Validation Failed") || body.contains("Constraint Violation")) {
                           throw new AssertionError("Valid sentiment should not fail validation");
                       }
                   }
               });
    }

    @ParameterizedTest(name = "ValidSymbol: {0} should fail validation")
    @CsvFileSource(resources = "/valid-symbol-invalid-data.csv", numLinesToSkip = 1)
    @DisplayName("ValidSymbol: invalid symbols should fail validation")
    void validSymbol_invalid(String symbol, String description) throws Exception {
        CreateAgentRequest request = new CreateAgentRequest("Test Agent", "PROFIT_MAXIMIZATION", "Test Description", symbol, 1000.0, null);

        mockMvc.perform(post("/api/v1/agents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.title").value("Validation Failed"))
               .andExpect(jsonPath("$.fieldErrors.tradingSymbol[0]").value("Invalid trading symbol format. Must be uppercase alphanumeric (e.g., BTCUSDT)"));
    }

    @Test
    @DisplayName("ValidSymbol: valid symbol should pass validation")
    void validSymbol_valid() throws Exception {
        CreateAgentRequest request = new CreateAgentRequest("Test Agent", "PROFIT_MAXIMIZATION", "Test Description", "BTCUSDT", 1000.0, null);

        performValidRequestTest("/api/v1/agents", request);
    }
}