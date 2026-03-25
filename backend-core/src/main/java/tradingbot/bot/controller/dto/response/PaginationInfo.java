package tradingbot.bot.controller.dto.response;

/**
 * Pagination information for paginated API responses
 */
public class PaginationInfo {
    private int page;
    private int size;
    private int totalElements;
    private int totalPages;
    private boolean hasNext;
    private boolean hasPrevious;
    private boolean isFirst;
    private boolean isLast;

    public PaginationInfo() {}

    public PaginationInfo(int page, int size, int totalElements, int totalPages,
                         boolean hasNext, boolean hasPrevious, boolean isFirst, boolean isLast) {
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
        this.hasNext = hasNext;
        this.hasPrevious = hasPrevious;
        this.isFirst = isFirst;
        this.isLast = isLast;
    }

    // Getters and setters
    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }

    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }

    public int getTotalElements() { return totalElements; }
    public void setTotalElements(int totalElements) { this.totalElements = totalElements; }

    public int getTotalPages() { return totalPages; }
    public void setTotalPages(int totalPages) { this.totalPages = totalPages; }

    public boolean isHasNext() { return hasNext; }
    public void setHasNext(boolean hasNext) { this.hasNext = hasNext; }

    public boolean isHasPrevious() { return hasPrevious; }
    public void setHasPrevious(boolean hasPrevious) { this.hasPrevious = hasPrevious; }

    public boolean isFirst() { return isFirst; }
    public void setFirst(boolean isFirst) { this.isFirst = isFirst; }

    public boolean isLast() { return isLast; }
    public void setLast(boolean isLast) { this.isLast = isLast; }
}
