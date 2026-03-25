package tradingbot.bot.grpc;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import tradingbot.bot.controller.dto.BotState;
import tradingbot.bot.service.BotCacheService;
import tradingbot.config.TradingConfig;
import tradingbot.grpc.bot.BotManagementServiceGrpc;
import tradingbot.grpc.bot.BotStatusRequest;
import tradingbot.grpc.bot.BotStatusResponse;
import tradingbot.grpc.bot.BotSummary;
import tradingbot.grpc.bot.CreateBotRequest;
import tradingbot.grpc.bot.CreateBotResponse;
import tradingbot.grpc.bot.DeleteBotRequest;
import tradingbot.grpc.bot.DeleteBotResponse;
import tradingbot.grpc.bot.ListBotsRequest;
import tradingbot.grpc.bot.ListBotsResponse;
import tradingbot.grpc.bot.PauseBotRequest;
import tradingbot.grpc.bot.PauseBotResponse;
import tradingbot.grpc.bot.ResumeBotRequest;
import tradingbot.grpc.bot.ResumeBotResponse;
import tradingbot.grpc.bot.StartBotRequest;
import tradingbot.grpc.bot.StartBotResponse;
import tradingbot.grpc.bot.StopBotRequest;
import tradingbot.grpc.bot.StopBotResponse;
import tradingbot.grpc.bot.UpdateBotRequest;
import tradingbot.grpc.bot.UpdateBotResponse;
import tradingbot.grpc.common.ErrorResponse;

/**
 * gRPC Service implementation for Bot Management operations
 * 
 * Provides high-performance alternative to REST API for internal Gateway <-> Backend communication.
 * Delegates to existing service layer (BotCacheService) for business logic.
 */
@GrpcService
public class BotManagementServiceImpl extends BotManagementServiceGrpc.BotManagementServiceImplBase {
    
    private static final Logger logger = LoggerFactory.getLogger(BotManagementServiceImpl.class);
    
    @Autowired
    private BotCacheService botCacheService;
    
    @Override
    public void createBot(CreateBotRequest request, StreamObserver<CreateBotResponse> responseObserver) {
        logger.info("gRPC CreateBot called for user: {}", request.getUserId());
        
        try {
            // Generate unique bot ID
            String botId = UUID.randomUUID().toString();
            
            // Create TradingConfig from Proto config
            String symbol = request.getConfig().getSymbol();
            double tradeAmount = request.getConfig().getInitialCapital();
            int leverage = (int) request.getConfig().getLeverage();
            double trailingStop = request.getConfig().getTrailingStopPercentage();
            
            TradingConfig tradingConfig = new TradingConfig(
                symbol, 
                tradeAmount,
                leverage,
                trailingStop,
                14,  // Default RSI lookback
                30.0, // Default RSI oversold
                70.0, // Default RSI overbought
                12,  // Default MACD fast
                26,  // Default MACD slow
                9,   // Default MACD signal
                20,  // Default BB period
                2.0, // Default BB std
                900  // Default interval (15 min)
            );
            
            // Create bot state using builder pattern
            BotState botState = BotState.builder()
                    .botId(botId)
                    .paper(request.getConfig().getPaperTrading())
                    .running(false)
                    .config(tradingConfig)
                    .sentimentEnabled(false)
                    .currentLeverage(leverage)
                    .createdAt(Instant.now())
                    .lastUpdated(Instant.now())
                    .positionStatus("CREATED")
                    .build();
            
            // Save to cache
            botCacheService.saveBotState(botId, botState);
            
            CreateBotResponse response = CreateBotResponse.newBuilder()
                    .setBotId(botId)
                    .setSuccess(true)
                    .setMessage("Bot created successfully")
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            
            logger.info("Bot created successfully: {}", botId);
            
        } catch (Exception e) {
            logger.error("Error creating bot", e);
            
            ErrorResponse error = ErrorResponse.newBuilder()
                    .setCode(500)
                    .setMessage("Internal server error")
                    .setDetails(e.getMessage())
                    .setTimestamp(System.currentTimeMillis())
                    .build();
            
            CreateBotResponse response = CreateBotResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Failed to create bot")
                    .setError(error)
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }
    
    @Override
    public void startBot(StartBotRequest request, StreamObserver<StartBotResponse> responseObserver) {
        logger.info("gRPC StartBot called for bot: {}", request.getBotId());
        
        try {
            BotState botState = botCacheService.getBotState(request.getBotId());
            
            if (botState == null) {
                ErrorResponse error = ErrorResponse.newBuilder()
                        .setCode(404)
                        .setMessage("Bot not found")
                        .setDetails("Bot ID: " + request.getBotId())
                        .setTimestamp(System.currentTimeMillis())
                        .build();
                
                StartBotResponse response = StartBotResponse.newBuilder()
                        .setSuccess(false)
                        .setMessage("Bot not found")
                        .setState(tradingbot.grpc.common.BotState.BOT_STATE_UNSPECIFIED)
                        .setError(error)
                        .build();
                
                responseObserver.onNext(response);
                responseObserver.onCompleted();
                return;
            }
            
            // Update bot state - set running to true
            botState.setRunning(true);
            botState.setPositionStatus("RUNNING");
            botState.setLastUpdated(Instant.now());
            botCacheService.saveBotState(request.getBotId(), botState);
            
            StartBotResponse response = StartBotResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Bot started successfully")
                    .setState(tradingbot.grpc.common.BotState.RUNNING)
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            
            logger.info("Bot started successfully: {}", request.getBotId());
            
        } catch (Exception e) {
            logger.error("Error starting bot", e);
            
            ErrorResponse error = ErrorResponse.newBuilder()
                    .setCode(500)
                    .setMessage("Internal server error")
                    .setDetails(e.getMessage())
                    .setTimestamp(System.currentTimeMillis())
                    .build();
            
            StartBotResponse response = StartBotResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Failed to start bot")
                    .setState(tradingbot.grpc.common.BotState.ERROR)
                    .setError(error)
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }
    
    @Override
    public void stopBot(StopBotRequest request, StreamObserver<StopBotResponse> responseObserver) {
        logger.info("gRPC StopBot called for bot: {}", request.getBotId());
        
        try {
            BotState botState = botCacheService.getBotState(request.getBotId());
            
            if (botState == null) {
                ErrorResponse error = ErrorResponse.newBuilder()
                        .setCode(404)
                        .setMessage("Bot not found")
                        .setDetails("Bot ID: " + request.getBotId())
                        .setTimestamp(System.currentTimeMillis())
                        .build();
                
                StopBotResponse response = StopBotResponse.newBuilder()
                        .setSuccess(false)
                        .setMessage("Bot not found")
                        .setState(tradingbot.grpc.common.BotState.BOT_STATE_UNSPECIFIED)
                        .setError(error)
                        .build();
                
                responseObserver.onNext(response);
                responseObserver.onCompleted();
                return;
            }
            
            // Update bot state - set running to false
            botState.setRunning(false);
            botState.setPositionStatus("STOPPED");
            botState.setLastUpdated(Instant.now());
            botCacheService.saveBotState(request.getBotId(), botState);
            
            StopBotResponse response = StopBotResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Bot stopped successfully")
                    .setState(tradingbot.grpc.common.BotState.STOPPED)
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            
            logger.info("Bot stopped successfully: {}", request.getBotId());
            
        } catch (Exception e) {
            logger.error("Error stopping bot", e);
            
            ErrorResponse error = ErrorResponse.newBuilder()
                    .setCode(500)
                    .setMessage("Internal server error")
                    .setDetails(e.getMessage())
                    .setTimestamp(System.currentTimeMillis())
                    .build();
            
            StopBotResponse response = StopBotResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Failed to stop bot")
                    .setState(tradingbot.grpc.common.BotState.ERROR)
                    .setError(error)
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }
    
    @Override
    public void getBotStatus(BotStatusRequest request, StreamObserver<BotStatusResponse> responseObserver) {
        logger.info("gRPC GetBotStatus called for bot: {}", request.getBotId());
        
        try {
            BotState botState = botCacheService.getBotState(request.getBotId());
            
            if (botState == null) {
                responseObserver.onError(Status.NOT_FOUND
                        .withDescription("Bot not found with ID: " + request.getBotId())
                        .asRuntimeException());
                return;
            }
            
            // Convert bot state to Proto format
            tradingbot.grpc.common.BotState protoState = convertToProtoBotState(botState.getPositionStatus());
            
            BotStatusResponse response = BotStatusResponse.newBuilder()
                    .setBotId(botState.getBotId())
                    .setState(protoState)
                    .setTotalPnl(0.0)  // These fields would need to be calculated
                    .setWinRate(0.0)
                    .setTotalTrades(0)
                    .setWinningTrades(0)
                    .setLosingTrades(0)
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            
            logger.info("Bot status retrieved successfully: {}", request.getBotId());
            
        } catch (Exception e) {
            logger.error("Error getting bot status", e);
            
            ErrorResponse error = ErrorResponse.newBuilder()
                    .setCode(500)
                    .setMessage("Internal server error")
                    .setDetails(e.getMessage())
                    .setTimestamp(System.currentTimeMillis())
                    .build();
            
            BotStatusResponse response = BotStatusResponse.newBuilder()
                    .setError(error)
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }
    
    @Override
    public void updateBot(UpdateBotRequest request, StreamObserver<UpdateBotResponse> responseObserver) {
        logger.info("gRPC UpdateBot called for bot: {}", request.getBotId());
        
        try {
            BotState botState = botCacheService.getBotState(request.getBotId());
            
            if (botState == null) {
                ErrorResponse error = ErrorResponse.newBuilder()
                        .setCode(404)
                        .setMessage("Bot not found")
                        .setDetails("Bot ID: " + request.getBotId())
                        .setTimestamp(System.currentTimeMillis())
                        .build();
                
                UpdateBotResponse response = UpdateBotResponse.newBuilder()
                        .setSuccess(false)
                        .setMessage("Bot not found")
                        .setError(error)
                        .build();
                
                responseObserver.onNext(response);
                responseObserver.onCompleted();
                return;
            }
            
            // Update configuration
            tradingbot.grpc.common.TradingConfig protoConfig = request.getConfig();
            if (protoConfig != null) {
                if (protoConfig.getLeverage() > 0) {
                    botState.setCurrentLeverage(protoConfig.getLeverage());
                }
            }
            
            botState.setLastUpdated(Instant.now());
            botCacheService.saveBotState(request.getBotId(), botState);
            
            UpdateBotResponse response = UpdateBotResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Bot updated successfully")
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            
            logger.info("Bot updated successfully: {}", request.getBotId());
            
        } catch (Exception e) {
            logger.error("Error updating bot", e);
            
            ErrorResponse error = ErrorResponse.newBuilder()
                    .setCode(500)
                    .setMessage("Internal server error")
                    .setDetails(e.getMessage())
                    .setTimestamp(System.currentTimeMillis())
                    .build();
            
            UpdateBotResponse response = UpdateBotResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Failed to update bot")
                    .setError(error)
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }
    
    @Override
    public void deleteBot(DeleteBotRequest request, StreamObserver<DeleteBotResponse> responseObserver) {
        logger.info("gRPC DeleteBot called for bot: {}", request.getBotId());
        
        try {
            BotState botState = botCacheService.getBotState(request.getBotId());
            
            if (botState == null) {
                ErrorResponse error = ErrorResponse.newBuilder()
                        .setCode(404)
                        .setMessage("Bot not found")
                        .setDetails("Bot ID: " + request.getBotId())
                        .setTimestamp(System.currentTimeMillis())
                        .build();
                
                DeleteBotResponse response = DeleteBotResponse.newBuilder()
                        .setSuccess(false)
                        .setMessage("Bot not found")
                        .setError(error)
                        .build();
                
                responseObserver.onNext(response);
                responseObserver.onCompleted();
                return;
            }
            
            botCacheService.deleteBotState(request.getBotId());
            
            DeleteBotResponse response = DeleteBotResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Bot deleted successfully")
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            
            logger.info("Bot deleted successfully: {}", request.getBotId());
            
        } catch (Exception e) {
            logger.error("Error deleting bot", e);
            
            ErrorResponse error = ErrorResponse.newBuilder()
                    .setCode(500)
                    .setMessage("Internal server error")
                    .setDetails(e.getMessage())
                    .setTimestamp(System.currentTimeMillis())
                    .build();
            
            DeleteBotResponse response = DeleteBotResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Failed to delete bot")
                    .setError(error)
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }
    
    @Override
    public void listBots(ListBotsRequest request, StreamObserver<ListBotsResponse> responseObserver) {
        logger.info("gRPC ListBots called for user: {}", request.getUserId());
        
        try {
            Set<String> allBotIds = botCacheService.getAllBotIds();
            List<BotSummary> botSummaries = new ArrayList<>();
            
            for (String botId : allBotIds) {
                BotState botState = botCacheService.getBotState(botId);
                if (botState != null) {
                    BotSummary summary = BotSummary.newBuilder()
                            .setBotId(botState.getBotId())
                            .setBotName(botId)  // Use botId as name since we don't have name field
                            .setState(convertToProtoBotState(botState.getPositionStatus()))
                            .setSymbol(botState.getConfig() != null ? botState.getConfig().getSymbol() : "")
                            .setTotalPnl(0.0)  // Would need to be calculated
                            .setTotalTrades(0)
                            .setCreatedAt(botState.getCreatedAt() != null ? botState.getCreatedAt().getEpochSecond() : 0)
                            .setLastActive(botState.getLastUpdated() != null ? botState.getLastUpdated().getEpochSecond() : 0)
                            .build();
                    
                    botSummaries.add(summary);
                }
            }
            
            ListBotsResponse response = ListBotsResponse.newBuilder()
                    .addAllBots(botSummaries)
                    .setTotalCount(botSummaries.size())
                    .setPage(request.getPage())
                    .setPageSize(request.getPageSize())
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            
            logger.info("Listed {} bots for user: {}", botSummaries.size(), request.getUserId());
            
        } catch (Exception e) {
            logger.error("Error listing bots", e);
            
            ErrorResponse error = ErrorResponse.newBuilder()
                    .setCode(500)
                    .setMessage("Internal server error")
                    .setDetails(e.getMessage())
                    .setTimestamp(System.currentTimeMillis())
                    .build();
            
            ListBotsResponse response = ListBotsResponse.newBuilder()
                    .setError(error)
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }
    
    @Override
    public void pauseBot(PauseBotRequest request, StreamObserver<PauseBotResponse> responseObserver) {
        logger.info("gRPC PauseBot called for bot: {}", request.getBotId());
        
        try {
            BotState botState = botCacheService.getBotState(request.getBotId());
            
            if (botState == null) {
                ErrorResponse error = ErrorResponse.newBuilder()
                        .setCode(404)
                        .setMessage("Bot not found")
                        .setDetails("Bot ID: " + request.getBotId())
                        .setTimestamp(System.currentTimeMillis())
                        .build();
                
                PauseBotResponse response = PauseBotResponse.newBuilder()
                        .setSuccess(false)
                        .setMessage("Bot not found")
                        .setState(tradingbot.grpc.common.BotState.BOT_STATE_UNSPECIFIED)
                        .setError(error)
                        .build();
                
                responseObserver.onNext(response);
                responseObserver.onCompleted();
                return;
            }
            
            botState.setRunning(false);
            botState.setPositionStatus("PAUSED");
            botState.setLastUpdated(Instant.now());
            botCacheService.saveBotState(request.getBotId(), botState);
            
            PauseBotResponse response = PauseBotResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Bot paused successfully")
                    .setState(tradingbot.grpc.common.BotState.PAUSED)
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            
            logger.info("Bot paused successfully: {}", request.getBotId());
            
        } catch (Exception e) {
            logger.error("Error pausing bot", e);
            
            ErrorResponse error = ErrorResponse.newBuilder()
                    .setCode(500)
                    .setMessage("Internal server error")
                    .setDetails(e.getMessage())
                    .setTimestamp(System.currentTimeMillis())
                    .build();
            
            PauseBotResponse response = PauseBotResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Failed to pause bot")
                    .setState(tradingbot.grpc.common.BotState.ERROR)
                    .setError(error)
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }
    
    @Override
    public void resumeBot(ResumeBotRequest request, StreamObserver<ResumeBotResponse> responseObserver) {
        logger.info("gRPC ResumeBot called for bot: {}", request.getBotId());
        
        try {
            BotState botState = botCacheService.getBotState(request.getBotId());
            
            if (botState == null) {
                ErrorResponse error = ErrorResponse.newBuilder()
                        .setCode(404)
                        .setMessage("Bot not found")
                        .setDetails("Bot ID: " + request.getBotId())
                        .setTimestamp(System.currentTimeMillis())
                        .build();
                
                ResumeBotResponse response = ResumeBotResponse.newBuilder()
                        .setSuccess(false)
                        .setMessage("Bot not found")
                        .setState(tradingbot.grpc.common.BotState.BOT_STATE_UNSPECIFIED)
                        .setError(error)
                        .build();
                
                responseObserver.onNext(response);
                responseObserver.onCompleted();
                return;
            }
            
            botState.setRunning(true);
            botState.setPositionStatus("RUNNING");
            botState.setLastUpdated(Instant.now());
            botCacheService.saveBotState(request.getBotId(), botState);
            
            ResumeBotResponse response = ResumeBotResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Bot resumed successfully")
                    .setState(tradingbot.grpc.common.BotState.RUNNING)
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            
            logger.info("Bot resumed successfully: {}", request.getBotId());
            
        } catch (Exception e) {
            logger.error("Error resuming bot", e);
            
            ErrorResponse error = ErrorResponse.newBuilder()
                    .setCode(500)
                    .setMessage("Internal server error")
                    .setDetails(e.getMessage())
                    .setTimestamp(System.currentTimeMillis())
                    .build();
            
            ResumeBotResponse response = ResumeBotResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Failed to resume bot")
                    .setState(tradingbot.grpc.common.BotState.ERROR)
                    .setError(error)
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }
    
    // Helper method to convert string status to Proto BotState enum
    private tradingbot.grpc.common.BotState convertToProtoBotState(String status) {
        if (status == null) {
            return tradingbot.grpc.common.BotState.BOT_STATE_UNSPECIFIED;
        }
        
        switch (status.toUpperCase()) {
            case "CREATED":
                return tradingbot.grpc.common.BotState.CREATED;
            case "STARTED":
                return tradingbot.grpc.common.BotState.STARTED;
            case "RUNNING":
                return tradingbot.grpc.common.BotState.RUNNING;
            case "STOPPED":
                return tradingbot.grpc.common.BotState.STOPPED;
            case "ERROR":
                return tradingbot.grpc.common.BotState.ERROR;
            case "PAUSED":
                return tradingbot.grpc.common.BotState.PAUSED;
            default:
                return tradingbot.grpc.common.BotState.BOT_STATE_UNSPECIFIED;
        }
    }
}
