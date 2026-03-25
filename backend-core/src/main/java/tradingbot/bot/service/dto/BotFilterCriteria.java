package tradingbot.bot.service.dto;

import java.time.Instant;

/**
 * Filter criteria for querying trading bots
 * Used for building dynamic queries with pagination and sorting
 */
public class BotFilterCriteria {
    private String userId;
    private String status;
    private Boolean paper;
    private String direction;
    private String search;
    private Instant createdAfter;
    private Instant createdBefore;

    public BotFilterCriteria() {
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getPaper() {
        return paper;
    }

    public void setPaper(Boolean paper) {
        this.paper = paper;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public String getSearch() {
        return search;
    }

    public void setSearch(String search) {
        this.search = search;
    }

    public Instant getCreatedAfter() {
        return createdAfter;
    }

    public void setCreatedAfter(Instant createdAfter) {
        this.createdAfter = createdAfter;
    }

    public Instant getCreatedBefore() {
        return createdBefore;
    }

    public void setCreatedBefore(Instant createdBefore) {
        this.createdBefore = createdBefore;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final BotFilterCriteria criteria = new BotFilterCriteria();

        public Builder userId(String userId) {
            criteria.setUserId(userId);
            return this;
        }

        public Builder status(String status) {
            criteria.setStatus(status);
            return this;
        }

        public Builder paper(Boolean paper) {
            criteria.setPaper(paper);
            return this;
        }

        public Builder direction(String direction) {
            criteria.setDirection(direction);
            return this;
        }

        public Builder search(String search) {
            criteria.setSearch(search);
            return this;
        }

        public Builder createdAfter(Instant createdAfter) {
            criteria.setCreatedAfter(createdAfter);
            return this;
        }

        public Builder createdBefore(Instant createdBefore) {
            criteria.setCreatedBefore(createdBefore);
            return this;
        }

        public BotFilterCriteria build() {
            return criteria;
        }
    }
}
