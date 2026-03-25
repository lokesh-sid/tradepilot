package tradingbot.agent.infrastructure.llm;

import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import tradingbot.agent.domain.model.Reasoning;
import tradingbot.agent.domain.model.ReasoningContext;

/**
 * ReasoningParser - Parse LLM output into structured Reasoning
 */
public class ReasoningParser {
    
    private static final Pattern OBSERVATION_PATTERN = Pattern.compile("OBSERVATION:\\s*(.+?)(?=\\n\\nANALYSIS:|$)", Pattern.DOTALL);
    private static final Pattern ANALYSIS_PATTERN = Pattern.compile("ANALYSIS:\\s*(.+?)(?=\\n\\nRISK ASSESSMENT:|$)", Pattern.DOTALL);
    private static final Pattern RISK_PATTERN = Pattern.compile("RISK ASSESSMENT:\\s*(.+?)(?=\\n\\nRECOMMENDATION:|$)", Pattern.DOTALL);
    private static final Pattern RECOMMENDATION_PATTERN = Pattern.compile("RECOMMENDATION:\\s*(.+?)(?=\\n\\nCONFIDENCE:|$)", Pattern.DOTALL);
    private static final Pattern CONFIDENCE_PATTERN = Pattern.compile("CONFIDENCE:\\s*(\\d+)%?");
    
    /**
     * Parse LLM output into Reasoning
     */
    public static Reasoning parse(String llmOutput, ReasoningContext context) {
        String observation = extractSection(llmOutput, OBSERVATION_PATTERN, "Market data observed");
        String analysis = extractSection(llmOutput, ANALYSIS_PATTERN, "Analysis in progress");
        String riskAssessment = extractSection(llmOutput, RISK_PATTERN, "Risk assessment pending");
        String recommendation = extractSection(llmOutput, RECOMMENDATION_PATTERN, "HOLD");
        int confidence = extractConfidence(llmOutput);
        
        return new Reasoning(
            observation.trim(),
            analysis.trim(),
            riskAssessment.trim(),
            recommendation.trim(),
            confidence,
            Instant.now()
        );
    }
    
    private static String extractSection(String text, Pattern pattern, String defaultValue) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1).trim() : defaultValue;
    }
    
    private static int extractConfidence(String text) {
        Matcher matcher = CONFIDENCE_PATTERN.matcher(text);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                return 50; // Default confidence
            }
        }
        return 50;
    }
}
