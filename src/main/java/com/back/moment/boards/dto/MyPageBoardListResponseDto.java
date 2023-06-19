package com.back.moment.boards.dto;

import com.back.moment.boards.entity.Board;
import com.back.moment.global.dto.TagResponseDto;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Getter
@NoArgsConstructor
public class MyPageBoardListResponseDto {
    private Long boardId;
    private String title;
    private String role;
    private String nickName;
    private int totalLoveCnt;
    private String profileImgUrl;
    private String location;
    private String boardImgUrl;
    private LocalDateTime createdTime;
    private List<TagResponseDto> tag_boardList;

    public MyPageBoardListResponseDto(Board board) {
        this.boardId = board.getId();
        this.title = board.getTitle();
        this.role = board.getUsers().getRole();
        this.nickName = board.getUsers().getNickName();
        this.totalLoveCnt = board.getUsers().getTotalLoveCnt();
        this.profileImgUrl = board.getUsers().getProfileImg();
        this.location = board.getLocation();
        this.boardImgUrl = board.getBoardImgUrl();
        this.createdTime = board.getCreatedAt().plusHours(9);
        this.tag_boardList = board.getTagListWithWell();
    }
}
