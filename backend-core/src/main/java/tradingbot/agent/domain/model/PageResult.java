package tradingbot.agent.domain.model;

import java.util.List;

/**
 * A simple page wrapper for the domain layer — no Spring dependency.
 *
 * @param content       items in the current page
 * @param totalElements total number of items across all pages
 */
public record PageResult<T>(List<T> content, long totalElements) {}
