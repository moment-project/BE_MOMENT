package com.back.moment.matching.controller;

import com.back.moment.matching.dto.MatchAcceptResponseDto;
import com.back.moment.matching.dto.MatchApplyResponseDto;
import com.back.moment.matching.dto.MatchingApplyBoardResponseDto;
import com.back.moment.matching.dto.MatchingBoardResponseDto;
import com.back.moment.matching.service.MatchingService;
import com.back.moment.users.security.UserDetailsImpl;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("match")
public class MatchingController {

	private final MatchingService matchingService;

	// 게시글 상세 조회 매칭 신청
	@PostMapping("/apply/{boardId}")
	public ResponseEntity<Void> matchApplyBoard(@PathVariable Long boardId, @AuthenticationPrincipal UserDetailsImpl userDetails){
		return matchingService.matchApplyBoard(boardId, userDetails.getUsers());
	}

	// 매칭 수락
	@PostMapping("/accept/{boardId}/{applyUserId}")
	public ResponseEntity<MatchAcceptResponseDto> matchAcceptBoard(@PathVariable Long boardId, @PathVariable Long applyUserId, @AuthenticationPrincipal UserDetailsImpl userDetails){
		return matchingService.matchAcceptBoard(boardId, applyUserId, userDetails.getUsers());
	}

	// 매칭 요청 리스트 보기
	@GetMapping("/applyList/{boardId}")
	public ResponseEntity<List<MatchApplyResponseDto>> matchingApplyList(@PathVariable Long boardId, @AuthenticationPrincipal UserDetailsImpl userDetails) {
		return matchingService.matchingApplyList(boardId, userDetails.getUsers());
	}

	// 마이페이지에서 매칭 리스트 보기 : 내가 받은 매칭 신청 게시글 보기
	@GetMapping("/acceptList")
	public ResponseEntity<List<MatchingBoardResponseDto>> getMatchedList(
		@AuthenticationPrincipal UserDetailsImpl userDetails) {
		return matchingService.getMatchedList(userDetails.getUsers());
	}

	// 마이페이지에서 매칭 리스트 보기 : 내가 신청한 매칭 게시글 보기
	@GetMapping("/applyList")
	public ResponseEntity<List<MatchingApplyBoardResponseDto>> getMatchingApplyList(
		@AuthenticationPrincipal UserDetailsImpl userDetails) {
		return matchingService.getMatchingApplyList(userDetails.getUsers());
	}
}