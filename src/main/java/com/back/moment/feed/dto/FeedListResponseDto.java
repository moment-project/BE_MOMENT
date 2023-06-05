package com.back.moment.feed.dto;

import com.back.moment.photos.dto.PhotoFeedResponseDto;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.List;

@Getter
@NoArgsConstructor
public class FeedListResponseDto {
    private List<PhotoFeedResponseDto> photoList;
    private boolean hasMorePages;
    int currentPage;
    int totalPages;

    public FeedListResponseDto(List<PhotoFeedResponseDto> photoList, boolean hasMorePages, int currentPage, int totalPages) {
        this.photoList = photoList;
        this.hasMorePages = hasMorePages;
        this.currentPage = currentPage;
        this.totalPages = totalPages;
    }
}
