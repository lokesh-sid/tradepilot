package tradingbot.agent.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import tradingbot.agent.api.dto.AgentMapper;
import tradingbot.agent.api.dto.AgentResponse;
import tradingbot.agent.api.dto.CreateAgentRequest;
import tradingbot.agent.api.dto.PaginatedAgentResponse;
import tradingbot.agent.api.dto.PerformanceResponse;
import tradingbot.agent.application.AgentService;
import tradingbot.agent.application.PerformanceTrackingService;
import tradingbot.agent.domain.model.Agent;
import tradingbot.agent.domain.model.AgentId;
import tradingbot.bot.controller.dto.response.ErrorResponse;
import tradingbot.bot.controller.validation.ValidBotId;

/**
 * Agent Controller - Manages AI trading agents
 * 
 * Provides REST API endpoints for creating, managing, and controlling
 * autonomous trading agents with goal-based decision making.
 * 
 * Security Features:
 * - Input validation on all endpoints
 * - UUID validation for agent IDs
 * - Global exception handling
 * 
 * API Path Pattern: /api/agents/{id}
 */
@RestController
@RequestMapping("/api/v1/agents")
@Validated
@Tag(name = "Agent Controller", description = "API for managing AI trading agents with autonomous decision-making capabilities")
public class AgentController {
    
    private final AgentService agentService;
    private final AgentMapper agentMapper;
    private final PerformanceTrackingService performanceService;
    
    public AgentController(AgentService agentService, AgentMapper agentMapper, PerformanceTrackingService performanceService) {
        this.agentService = agentService;
        this.agentMapper = agentMapper;
        this.performanceService = performanceService;
    }
    
    /**
     * Create a new agent
     */
    @PostMapping
    @Operation(summary = "Create a new AI trading agent",
               description = "Creates a new autonomous trading agent with specified goals and parameters")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Agent created successfully",
                    content = @Content(mediaType = "application/json", 
                    schema = @Schema(implementation = AgentResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request parameters",
                    content = @Content(mediaType = "application/json", 
                    schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(mediaType = "application/json", 
                    schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<AgentResponse> createAgent(
            @Valid @RequestBody CreateAgentRequest request) {
        Agent agent = agentService.createAgent(request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(agentMapper.toResponse(agent));
    }
    
    /**
     * Get agents for the authenticated user (paginated, cached via Redis)
     */
    @GetMapping
    @Operation(summary = "List AI trading agents for the authenticated user",
               description = "Returns a paginated list of the authenticated user's trading agents")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Agents retrieved successfully",
                    content = @Content(mediaType = "application/json")),
        @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(mediaType = "application/json", 
                    schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PaginatedAgentResponse> getAgents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String ownerId = authentication.getName();
        PaginatedAgentResponse response = agentService.getAgentsByOwner(ownerId, page, size);
        return ResponseEntity.ok(response);
    }

    /**
     * Get agent by ID
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @agentSecurity.isOwner(#id, authentication)")
    @Operation(summary = "Get agent by ID",
               description = "Returns detailed information about a specific trading agent")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Agent retrieved successfully",
                    content = @Content(mediaType = "application/json", 
                    schema = @Schema(implementation = AgentResponse.class))),
        @ApiResponse(responseCode = "404", description = "Agent not found",
                    content = @Content(mediaType = "application/json", 
                    schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(mediaType = "application/json", 
                    schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<AgentResponse> getAgent(
            @Parameter(description = "Unique agent identifier (UUID format)")
            @PathVariable @ValidBotId String id) {
        Agent agent = agentService.getAgent(new AgentId(id));
        return ResponseEntity.ok(agentMapper.toResponse(agent));
    }

    /**
     * Get agent performance
     */
    @GetMapping("/{id}/performance")
    @PreAuthorize("hasRole('ADMIN') or @agentSecurity.isOwner(#id, authentication)")
    @Operation(summary = "Get agent performance metrics",
               description = "Retrieves performance statistics for a specific agent")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Performance metrics retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Agent not found")
    })
    public ResponseEntity<PerformanceResponse> getAgentPerformance(
            @Parameter(description = "Unique agent identifier (UUID format)")
            @PathVariable @ValidBotId String id) {
        PerformanceResponse response = performanceService.getPerformance(id);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Activate an agent
     */
    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN') or @agentSecurity.isOwner(#id, authentication)")
    @Operation(summary = "Activate an agent",
               description = "Activates a paused or stopped agent to begin autonomous trading")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Agent activated successfully",
                    content = @Content(mediaType = "application/json", 
                    schema = @Schema(implementation = AgentResponse.class))),
        @ApiResponse(responseCode = "404", description = "Agent not found",
                    content = @Content(mediaType = "application/json", 
                    schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Agent already active",
                    content = @Content(mediaType = "application/json", 
                    schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(mediaType = "application/json", 
                    schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<AgentResponse> activateAgent(
            @Parameter(description = "Unique agent identifier (UUID format)")
            @PathVariable @ValidBotId String id) {
        Agent agent = agentService.activateAgent(new AgentId(id));
        return ResponseEntity.ok(agentMapper.toResponse(agent));
    }
    
    /**
     * Pause an agent
     */
    @PostMapping("/{id}/pause")
    @PreAuthorize("hasRole('ADMIN') or @agentSecurity.isOwner(#id, authentication)")
    @Operation(summary = "Pause an agent",
               description = "Temporarily pauses an active agent without stopping it completely")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Agent paused successfully",
                    content = @Content(mediaType = "application/json", 
                    schema = @Schema(implementation = AgentResponse.class))),
        @ApiResponse(responseCode = "404", description = "Agent not found",
                    content = @Content(mediaType = "application/json", 
                    schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Agent not in active state",
                    content = @Content(mediaType = "application/json", 
                    schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(mediaType = "application/json", 
                    schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<AgentResponse> pauseAgent(
            @Parameter(description = "Unique agent identifier (UUID format)")
            @PathVariable @ValidBotId String id) {
        Agent agent = agentService.pauseAgent(new AgentId(id));
        return ResponseEntity.ok(agentMapper.toResponse(agent));
    }
    
    /**
     * Stop and delete an agent
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @agentSecurity.isOwner(#id, authentication)")
    @Operation(summary = "Stop and delete an agent",
               description = "Permanently stops and removes a trading agent")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Agent stopped and deleted successfully"),
        @ApiResponse(responseCode = "404", description = "Agent not found",
                    content = @Content(mediaType = "application/json", 
                    schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(mediaType = "application/json", 
                    schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> stopAgent(
            @Parameter(description = "Unique agent identifier (UUID format)")
            @PathVariable @ValidBotId String id) {
        agentService.deleteAgent(new AgentId(id));
        return ResponseEntity.noContent().build();
    }
}
