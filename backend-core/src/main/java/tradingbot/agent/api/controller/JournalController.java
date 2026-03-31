package tradingbot.agent.api.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import tradingbot.agent.api.dto.AnnotateJournalRequest;
import tradingbot.agent.api.dto.JournalEntryResponse;
import tradingbot.agent.api.dto.JournalStatsResponse;
import tradingbot.agent.api.dto.JournalStatsResponse.TagStatDTO;
import tradingbot.agent.application.TradeJournalService;
import tradingbot.agent.application.TradeJournalService.JournalStats;
import tradingbot.agent.application.TradeJournalService.TagStats;
import tradingbot.agent.infrastructure.persistence.TradeJournalEntity;

@RestController
@RequestMapping("/api/journal")
@Validated
@Tag(name = "Journal", description = "Trade journal — view, filter, and annotate trade history")
public class JournalController {

    private final TradeJournalService journalService;

    public JournalController(TradeJournalService journalService) {
        this.journalService = journalService;
    }

    @GetMapping
    @Operation(summary = "List journal entries", description = "Paginated list with optional filters")
    public ResponseEntity<Page<JournalEntryResponse>> list(
            @RequestParam(required = false) String agentId,
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) Boolean flaggedForReview,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<JournalEntryResponse> result = journalService
                .list(agentId, symbol, outcome, flaggedForReview, page, size)
                .map(this::toResponse);

        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a single journal entry")
    public ResponseEntity<JournalEntryResponse> get(@PathVariable String id) {
        return journalService.findById(id)
                .map(e -> ResponseEntity.ok(toResponse(e)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Annotate a journal entry", description = "Add notes, tags, conviction, or review notes. All fields optional.")
    public ResponseEntity<JournalEntryResponse> annotate(
            @PathVariable String id,
            @Valid @RequestBody AnnotateJournalRequest request) {

        return journalService.annotateEntry(
                        id,
                        request.notes(),
                        request.tags(),
                        request.conviction(),
                        request.reviewNotes(),
                        request.markReviewed())
                .map(e -> ResponseEntity.ok(toResponse(e)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/stats")
    @Operation(summary = "Aggregate stats for an agent", description = "Win rate, avg PnL, confidence calibration, and per-tag breakdown")
    public ResponseEntity<JournalStatsResponse> stats(
            @RequestParam String agentId) {

        JournalStats stats = journalService.getStats(agentId);
        return ResponseEntity.ok(toStatsResponse(stats));
    }

    // -------------------------------------------------------------------------
    // Mapping helpers
    // -------------------------------------------------------------------------

    private JournalEntryResponse toResponse(TradeJournalEntity e) {
        return new JournalEntryResponse(
                e.getId(),
                e.getAgentId(),
                e.getSymbol(),
                e.getEntryOrderId(),
                e.getDirection() != null ? e.getDirection().name() : null,
                e.getEntryPrice(),
                e.getQuantity(),
                e.getStopLoss(),
                e.getTakeProfit(),
                e.getConfidence(),
                e.getLlmReasoning(),
                e.getDecidedAt(),
                e.getExitPrice(),
                e.getRealizedPnl(),
                e.getPnlPercent(),
                e.getOutcome() != null ? e.getOutcome().name() : null,
                e.getCloseReason(),
                e.getLlmLesson(),
                e.getClosedAt(),
                e.getNotes(),
                TradeJournalService.tagsFromString(e.getTags()),
                e.getConviction(),
                e.getReviewedAt(),
                e.getReviewNotes(),
                e.getLlmBatchAnalysis(),
                e.isFlaggedForReview(),
                e.getCreatedAt());
    }

    private JournalStatsResponse toStatsResponse(JournalStats stats) {
        List<TagStatDTO> tagDtos = stats.topTags().stream()
                .map(t -> new TagStatDTO(t.tag(), t.count(), t.winRate()))
                .toList();
        return new JournalStatsResponse(
                stats.totalTrades(),
                stats.winRate(),
                stats.avgPnlPercent(),
                stats.avgConfidenceOnWins(),
                stats.avgConfidenceOnLosses(),
                tagDtos);
    }
}
