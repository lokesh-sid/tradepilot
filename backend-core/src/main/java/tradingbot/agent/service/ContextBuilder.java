package tradingbot.agent.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import tradingbot.agent.domain.model.Perception;
import tradingbot.agent.domain.model.ReasoningContext;
import tradingbot.agent.domain.model.TradeMemory;

/**
 * ContextBuilder - Builds augmented prompts for RAG-enhanced LLM reasoning
 * 
 * This service formats retrieved trading memories into readable context
 * that augments the LLM prompt with relevant historical experiences.
 */
@Service
public class ContextBuilder {
    
    private static final String MEMORY_SECTION_HEADER = 
        "\n=== RELEVANT PAST EXPERIENCES ===\n" +
        "Here are similar trading scenarios from your memory:\n\n";
    
    private static final String MEMORY_SECTION_FOOTER = 
        "\n=== END OF PAST EXPERIENCES ===\n" +
        "Use these experiences to inform your current decision, but adapt to the current market conditions.\n";
    
    private static final String NO_MEMORIES_MESSAGE =
        "\n=== NO RELEVANT PAST EXPERIENCES ===\n" +
        "This appears to be a novel scenario. Proceed with caution and focus on market fundamentals.\n";
    
    /**
     * Build an augmented reasoning context with retrieved memories
     * 
     * @param baseContext The original reasoning context (market data, indicators, etc)
     * @param memories Retrieved similar trading memories
     * @return Augmented context with formatted memories
     */
    public String buildAugmentedContext(ReasoningContext baseContext, List<TradeMemory> memories) {
        StringBuilder augmentedContext = new StringBuilder();
        
        // Start with base context
        augmentedContext.append(formatBaseContext(baseContext));
        
        // Add memory section
        if (memories == null || memories.isEmpty()) {
            augmentedContext.append(NO_MEMORIES_MESSAGE);
        } else {
            augmentedContext.append(MEMORY_SECTION_HEADER);
            augmentedContext.append(formatMemories(memories));
            augmentedContext.append(MEMORY_SECTION_FOOTER);
            augmentedContext.append(generateInsights(memories));
        }
        
        return augmentedContext.toString();
    }
    
    /**
     * Format the base reasoning context into a readable prompt
     */
    private String formatBaseContext(ReasoningContext context) {
        StringBuilder sb = new StringBuilder();
        Perception perception = context.getPerception();
        
        sb.append("=== CURRENT MARKET SITUATION ===\n");
        sb.append("Symbol: ").append(perception.getSymbol()).append("\n");
        sb.append("Current Price: $").append(String.format("%.2f", perception.getCurrentPrice())).append("\n");
        sb.append("Trend: ").append(perception.getTrend()).append("\n");
        
        if (perception.getVolume() > 0) {
            sb.append("Volume: $").append(formatLargeNumber(perception.getVolume())).append("\n");
        }
        
        if (perception.getSentiment() != null && !perception.getSentiment().isEmpty()) {
            sb.append("\n=== MARKET SENTIMENT ===\n");
            sb.append(perception.getSentiment()).append("\n");
        }
        
        sb.append("\n=== YOUR GOAL ===\n");
        sb.append(context.getGoal().getDescription()).append("\n");
        sb.append("Capital: $").append(String.format("%.2f", context.getCapital())).append("\n");
        
        sb.append("\n=== YOUR TASK ===\n");
        sb.append("Analyze this scenario and decide whether to:\n");
        sb.append("1. BUY (go LONG) - if you believe price will increase\n");
        sb.append("2. SELL (go SHORT) - if you believe price will decrease\n");
        sb.append("3. HOLD - if you're uncertain or market conditions are unfavorable\n\n");
        
        return sb.toString();
    }
    
    /**
     * Format memories into readable numbered list
     */
    private String formatMemories(List<TradeMemory> memories) {
        return memories.stream()
            .limit(5)  // Limit to top 5 to avoid context overflow
            .map(memory -> {
                StringBuilder sb = new StringBuilder();
                sb.append("Memory #").append(memories.indexOf(memory) + 1);
                
                if (memory.getSimilarityScore() != null) {
                    sb.append(" (").append(String.format("%.0f%% similar", memory.getSimilarityScore() * 100)).append(")");
                }
                sb.append(":\n");
                sb.append(memory.formatForPrompt());
                
                return sb.toString();
            })
            .collect(Collectors.joining("\n"));
    }
    
    /**
     * Generate insights from the collection of memories
     */
    private String generateInsights(List<TradeMemory> memories) {
        if (memories.isEmpty()) {
            return "";
        }
        
        StringBuilder insights = new StringBuilder();
        insights.append("\n=== KEY INSIGHTS FROM MEMORY ===\n");
        
        // Calculate success rate
        long profitable = memories.stream()
            .filter(TradeMemory::wasProfitable)
            .count();
        
        double successRate = (double) profitable / memories.size() * 100;
        insights.append(String.format("- Historical success rate in similar scenarios: %.0f%% (%d/%d trades)\n",
            successRate, profitable, memories.size()));
        
        // Identify common patterns
        var mostCommonDirection = memories.stream()
            .collect(Collectors.groupingBy(
                m -> m.getDirection(),
                Collectors.counting()
            ))
            .entrySet().stream()
            .max((a, b) -> Long.compare(a.getValue(), b.getValue()))
            .map(e -> e.getKey().name())
            .orElse("UNKNOWN");
        
        insights.append("- Most common direction in similar scenarios: ").append(mostCommonDirection).append("\n");
        
        // Average profit/loss
        double avgProfit = memories.stream()
            .filter(m -> m.getProfitPercent() != null)
            .mapToDouble(TradeMemory::getProfitPercent)
            .average()
            .orElse(0.0);
        
        insights.append(String.format("- Average P/L in similar scenarios: %.2f%%\n", avgProfit));
        
        // Extract key lessons
        var lessons = memories.stream()
            .filter(m -> m.getLessonLearned() != null && !m.getLessonLearned().isEmpty())
            .limit(3)
            .map(TradeMemory::getLessonLearned)
            .collect(Collectors.toList());
        
        if (!lessons.isEmpty()) {
            insights.append("\n- Key lessons learned:\n");
            lessons.forEach(lesson -> 
                insights.append("  • ").append(lesson).append("\n")
            );
        }
        
        return insights.toString();
    }
    
    /**
     * Format large numbers with K/M/B suffixes
     */
    private String formatLargeNumber(double number) {
        if (number >= 1_000_000_000) {
            return String.format("%.2fB", number / 1_000_000_000);
        } else if (number >= 1_000_000) {
            return String.format("%.2fM", number / 1_000_000);
        } else if (number >= 1_000) {
            return String.format("%.2fK", number / 1_000);
        }
        return String.format("%.2f", number);
    }
    
    /**
     * Build a scenario description from perception for embedding
     * 
     * This creates a text representation of the current market state
     * that can be embedded and compared to historical memories.
     */
    public String buildScenarioDescription(Perception perception) {
        StringBuilder sb = new StringBuilder();
        
        sb.append(perception.getSymbol());
        sb.append(" at $").append(String.format("%.2f", perception.getCurrentPrice()));
        sb.append(", trending ").append(perception.getTrend());
        
        // Add volume info
        if (perception.getVolume() > 0) {
            sb.append(", volume ").append(formatLargeNumber(perception.getVolume()));
        }
        
        // Add sentiment if available
        if (perception.getSentiment() != null && !perception.getSentiment().isEmpty()) {
            sb.append(", sentiment: ").append(perception.getSentiment());
        }
        
        return sb.toString();
    }
}
