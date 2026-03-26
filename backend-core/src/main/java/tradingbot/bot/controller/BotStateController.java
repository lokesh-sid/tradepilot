package tradingbot.bot.controller;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import tradingbot.agent.TradingAgent;
import tradingbot.agent.infrastructure.repository.AgentEntity;
import tradingbot.agent.infrastructure.repository.JpaAgentRepository;
import tradingbot.agent.manager.AgentManager;
import tradingbot.bot.controller.dto.request.BotStateUpdateRequest;
import tradingbot.bot.controller.dto.request.BotStateUpdateRequest.BotStatus;
import tradingbot.bot.controller.dto.response.BotStateResponse;
import tradingbot.bot.controller.dto.response.ErrorResponse;
import tradingbot.bot.controller.exception.ConflictException;
import tradingbot.bot.controller.validation.BotOperationPolicy;
import tradingbot.bot.controller.validation.BotRequestValidator;
import tradingbot.config.TradingSafetyService;

/**
 * Bot State Controller - Manages bot state transitions
 *
 * Responsibilities (HTTP only):
 *  - Route GET /api/v1/bots/{botId}/state  → current state
 *  - Route PUT /api/v1/bots/{botId}/state  → state transition
 *
 * NOT responsible for:
 *  - Agent resolution or type-checking  (BotRequestValidator)
 *  - Business state rules               (BotOperationPolicy)
 *  - Safety guardrails                  (TradingSafetyService)
 *  - Paper-mode resolution              (BotRequestValidator)
 */
@RestController
@RequestMapping("/api/v1/bots/{botId}/state")
@Tag(name = "Bot State Management", description = "Manage bot state transitions (start, stop, pause)")
public class BotStateController {

    private static final Logger logger = LoggerFactory.getLogger(BotStateController.class);

    private final AgentManager agentManager;
    private final JpaAgentRepository agentRepository;
    private final TradingSafetyService tradingSafetyService;
    private final BotRequestValidator botRequestValidator;
    private final BotOperationPolicy botOperationPolicy;

    public BotStateController(
            AgentManager agentManager,
            JpaAgentRepository agentRepository,
            TradingSafetyService tradingSafetyService,
            BotRequestValidator botRequestValidator,
            BotOperationPolicy botOperationPolicy) {
        this.agentManager = agentManager;
        this.agentRepository = agentRepository;
        this.tradingSafetyService = tradingSafetyService;
        this.botRequestValidator = botRequestValidator;
        this.botOperationPolicy = botOperationPolicy;
    }

    @GetMapping
    @Operation(summary = "Get current bot state",
               description = "Returns the current state of the specified bot")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "State retrieved successfully",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = BotStateResponse.class))),
        @ApiResponse(responseCode = "404", description = "Bot not found",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<BotStateResponse> getCurrentState(@PathVariable String botId) {
        TradingAgent agent = botRequestValidator.resolveAgent(botId);

        BotStateResponse response = new BotStateResponse();
        response.setBotId(botId);
        response.setStatus(agent.isRunning() ? BotStatus.RUNNING : BotStatus.STOPPED);
        response.setTimestamp(Instant.now());

        agentRepository.findById(botId).ifPresent(entity -> {
            response.setSymbol(entity.getTradingSymbol());
            response.setPaperMode(entity.getExecutionMode() == AgentEntity.ExecutionMode.FUTURES_PAPER);
        });

        return ResponseEntity.ok(response);
    }

    @PutMapping
    @Operation(summary = "Update bot state",
               description = "Update bot state — start (RUNNING), stop (STOPPED), or pause (PAUSED)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "State updated successfully",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = BotStateResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Bot not found",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Bot already in requested state or transition not allowed",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<BotStateResponse> updateState(
            @Parameter(description = "Bot identifier") @PathVariable String botId,
            @Valid @RequestBody BotStateUpdateRequest request) {

        TradingAgent currentAgent = botRequestValidator.resolveAgent(botId);
        BotStatus currentStatus = currentAgent.isRunning() ? BotStatus.RUNNING : BotStatus.STOPPED;

        if (currentStatus == request.getStatus()) {
            throw new ConflictException("Bot is already in " + request.getStatus() + " state");
        }

        BotStateResponse response = new BotStateResponse();
        response.setBotId(botId);
        response.setPreviousStatus(currentStatus);
        response.setTimestamp(Instant.now());

        switch (request.getStatus()) {
            case RUNNING -> startBot(botId, currentAgent, request, response);
            case STOPPED -> stopBot(botId, currentAgent, request, response);
            case PAUSED  -> pauseBot(botId, currentAgent, request, response);
        }

        return ResponseEntity.ok(response);
    }

    // ------------------------------------------------------------------
    // Private state transition handlers
    // ------------------------------------------------------------------

    private void startBot(String botId, TradingAgent currentAgent,
                          BotStateUpdateRequest request, BotStateResponse response) {
        botOperationPolicy.assertCanStart(currentAgent, botId);

        boolean resolvedPaperMode = botRequestValidator.resolvePaperMode(botId, request.getPaperMode());
        tradingSafetyService.validateBotStartMode(resolvedPaperMode);

        AgentEntity.ExecutionMode mode = resolvedPaperMode
                ? AgentEntity.ExecutionMode.FUTURES_PAPER
                : AgentEntity.ExecutionMode.FUTURES;
        agentManager.startAgent(botId, mode);

        response.setStatus(BotStatus.RUNNING);
        response.setDirection(request.getDirection());
        response.setPaperMode(resolvedPaperMode);
        agentRepository.findById(botId).ifPresent(e -> response.setSymbol(e.getTradingSymbol()));
        response.setMessage("Bot started successfully"
                + (request.getReason() != null ? ": " + request.getReason() : ""));
    }

    private void stopBot(String botId, TradingAgent agent,
                         BotStateUpdateRequest request, BotStateResponse response) {
        botOperationPolicy.assertCanStop(agent, botId);

        agentManager.stopAgent(botId);

        response.setStatus(BotStatus.STOPPED);
        response.setMessage("Bot stopped successfully"
                + (request.getReason() != null ? ": " + request.getReason() : ""));
    }

    private void pauseBot(String botId, TradingAgent agent,
                          BotStateUpdateRequest request, BotStateResponse response) {
        botOperationPolicy.assertCanPause(agent, botId);

        agentManager.pauseAgent(botId);

        response.setStatus(BotStatus.PAUSED);
        response.setMessage("Bot paused successfully"
                + (request.getReason() != null ? ": " + request.getReason() : ""));
    }
}
