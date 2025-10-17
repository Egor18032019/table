package org.tablebuilder.demo.model;

import lombok.Data;

import java.util.List;

@Data
public class FileDataResponse {
    private String fileName;
    private List<SheetData> sheets;
    private PaginationInfo pagination;

    @Data
    public static class PaginationInfo {
        private int currentPage;
        private int pageSize;
        private long totalElements;
        private int totalPages;
        private boolean first;
        private boolean last;

        public PaginationInfo(int currentPage, int pageSize, long totalElements) {
            this.currentPage = currentPage;
            this.pageSize = pageSize;
            this.totalElements = totalElements;
            this.totalPages = (int) Math.ceil((double) totalElements / pageSize);
            this.first = currentPage == 0;
            this.last = currentPage >= totalPages - 1;
        }
    }
}