package tradingbot.bot.controller;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import tradingbot.bot.service.backtest.BacktestAgentExecutionService.TradeEvent;
import tradingbot.bot.service.backtest.BacktestMetricsCalculator.BacktestMetrics;
import tradingbot.bot.service.backtest.BacktestRunRegistry;
import tradingbot.bot.service.backtest.BacktestService;
import tradingbot.bot.service.backtest.EquityCurveExportService;
import tradingbot.bot.service.backtest.EquityCurvePoint;
import tradingbot.config.TradingConfig;

@RestController
@RequestMapping("/api/v1/backtest")
@Tag(name = "Backtest", description = "Backtesting API")
public class BacktestController {

    private final BacktestService backtestService;
    private final BacktestRunRegistry runRegistry;
    private final EquityCurveExportService exportService;

    public BacktestController(BacktestService backtestService,
                               BacktestRunRegistry runRegistry,
                               EquityCurveExportService exportService) {
        this.backtestService = backtestService;
        this.runRegistry     = runRegistry;
        this.exportService   = exportService;
    }

    // ── write ─────────────────────────────────────────────────────────────────

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Run Backtest",
               description = "Runs a backtest using uploaded CSV data. Returns BacktestMetrics including a runId for subsequent GET calls.")
    public ResponseEntity<BacktestMetrics> runBacktest(
            @Parameter(description = "CSV file containing historical candle data", required = true)
            @RequestPart("file") MultipartFile file,

            @Parameter(description = "Trading configuration", required = true)
            @RequestPart("config") TradingConfig config,

            @Parameter(description = "Simulated network latency in milliseconds", example = "100")
            @RequestParam(defaultValue = "0") long latencyMs,

            @Parameter(description = "Simulated slippage percentage (0.01 = 1%)", example = "0.001")
            @RequestParam(defaultValue = "0.0") double slippagePercent,

            @Parameter(description = "Simulated trading fee rate (0.0004 = 0.04%)", example = "0.0004")
            @RequestParam(defaultValue = "0.0004") double feeRate
    ) throws IOException {

        if (file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be empty");
        }

        BacktestMetrics result = backtestService.runBacktest(
                file.getInputStream(), config, latencyMs, slippagePercent, feeRate);

        return ResponseEntity.ok(result);
    }

    // ── read ──────────────────────────────────────────────────────────────────

    @GetMapping("/runs")
    @Operation(summary = "List all backtest runs",
               description = "Returns summary metrics for all runs stored in the in-memory registry since the last restart.")
    public ResponseEntity<List<BacktestMetrics>> listRuns() {
        return ResponseEntity.ok(runRegistry.findAll());
    }

    @GetMapping("/{runId}")
    @Operation(summary = "Get a backtest run",
               description = "Returns full BacktestMetrics including equity curve and trade list for the given runId.")
    public ResponseEntity<BacktestMetrics> getRun(@PathVariable String runId) {
        return runRegistry.find(runId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ── equity curve ──────────────────────────────────────────────────────────

    @GetMapping("/{runId}/equity-curve")
    @Operation(summary = "Get equity curve (JSON)",
               description = "Returns the equity curve as a JSON array of EquityCurvePoint objects.")
    public ResponseEntity<List<EquityCurvePoint>> getEquityCurve(@PathVariable String runId) {
        return runRegistry.find(runId)
                .map(m -> ResponseEntity.ok(m.equityCurve()))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping(value = "/{runId}/equity-curve.csv", produces = "text/csv")
    @Operation(summary = "Download equity curve (CSV)",
               description = "Downloads the equity curve as a CSV file with columns: barIndex, timestamp, balance, drawdownPct, action, symbol.")
    public ResponseEntity<byte[]> downloadEquityCurveCsv(@PathVariable String runId) {
        return runRegistry.find(runId)
                .map(m -> {
                    byte[] csv = exportService.equityCurveToCsv(m.equityCurve());
                    return ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_DISPOSITION,
                                    "attachment; filename=\"equity-curve-" + runId + ".csv\"")
                            .contentType(MediaType.parseMediaType("text/csv"))
                            .body(csv);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ── trades ────────────────────────────────────────────────────────────────

    @GetMapping("/{runId}/trades")
    @Operation(summary = "Get trade list (JSON)",
               description = "Returns all simulated trades for the given run as a JSON array.")
    public ResponseEntity<List<TradeEvent>> getTrades(@PathVariable String runId) {
        return runRegistry.find(runId)
                .map(m -> ResponseEntity.ok(m.trades()))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping(value = "/{runId}/trades.csv", produces = "text/csv")
    @Operation(summary = "Download trade list (CSV)",
               description = "Downloads all simulated trades as a CSV file with columns: barIndex, symbol, side, price, quantity, pnl, reasoning.")
    public ResponseEntity<byte[]> downloadTradesCsv(@PathVariable String runId) {
        return runRegistry.find(runId)
                .map(m -> {
                    byte[] csv = exportService.tradesToCsv(m.trades());
                    return ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_DISPOSITION,
                                    "attachment; filename=\"trades-" + runId + ".csv\"")
                            .contentType(MediaType.parseMediaType("text/csv"))
                            .body(csv);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @DeleteMapping("/{runId}")
    @Operation(summary = "Delete a backtest run",
               description = "Removes the run from the in-memory registry. Returns 204 No Content on success, 404 if not found.")
    public ResponseEntity<Void> deleteRun(@PathVariable String runId) {
        return runRegistry.delete(runId)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }
}

