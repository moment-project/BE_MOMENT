package com.back.moment.matching.service;

import com.back.moment.boards.entity.Board;
import com.back.moment.boards.repository.BoardRepository;
import com.back.moment.exception.ApiException;
import com.back.moment.exception.ExceptionEnum;
import com.back.moment.matching.entity.MatchStatus;
import com.back.moment.matching.dto.*;
import com.back.moment.matching.entity.Matching;
import com.back.moment.matching.entity.MatchingApply;
import com.back.moment.matching.repository.MatchingApplyRepository;
import com.back.moment.matching.repository.MatchingRepository;
import com.back.moment.sse.NotificationService;
import com.back.moment.users.entity.Users;
import com.back.moment.users.repository.UsersRepository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import lombok.RequiredArgsConstructor;
import org.springframework.format.datetime.DateFormatter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class MatchingService {

	private final BoardRepository boardRepository;
	private final UsersRepository usersRepository;
	private final MatchingApplyRepository matchingApplyRepository;
	private final MatchingRepository matchingRepository;
	private final NotificationService notificationService;

	// 신청자 : 게시글 상세에서 확인 가능 (매칭 요청) 이미 매칭 요청 한 경우 매칭 취소 : 매칭 신청 버튼을 누르면 발생하는 이벤트
	public ResponseEntity<Void> matchApplyBoard(Long boardId, Users users) {
		Board board = existBoard(boardId);
		// board 작성자인 경우
		if (board.getUsers().getId().equals(users.getId())){  // 같으면 마이페이지 보내기
			throw new ApiException(ExceptionEnum.NOT_MATCH_USERS);
		}
		LocalDate deadLineDate = LocalDate.parse(board.getDeadLine());
		LocalDate nowDate = LocalDate.now();
		int matchingApplyCnt = matchingApplyRepository.countAllMatchingWithFalseAndRefusedTrue(boardId);
		MatchingApply existMatchingApply = matchingApplyRepository.findByBoardIdAndApplicantId(boardId, users.getId());
		// 이전에 매칭 신청 안 한 경우
		if(nowDate.isBefore(deadLineDate)) {
			if (board.getMatchingFull() == null || !board.getMatchingFull()) {
				if (existMatchingApply == null) {
					if (matchingApplyCnt < 5) { // 5명보다 적게 매칭 신청된 경우
						MatchingApply matchingApply = new MatchingApply(board, users);
						matchingApplyRepository.save(matchingApply);
						// 매칭 요청 알림
						notificationService.notify(board.getUsers().getId(), new MatchNotificationResponseDto(boardId, users.getId(), users.getNickName(), users.getProfileImg(), MatchStatus.MATCH_APPLY));
						if (matchingApplyCnt == 4) {
							board.setMatchingFull(true); // 4명이 매칭 신청된 경우에만 matchingFull을 true로 변경
						}
					}
				} else { // 이미 매칭요청을 했으면 , 매칭 취소 : db 에서 삭제
					if (!Objects.requireNonNull(existMatchingApply).isApplyRefused())
						matchingApplyRepository.delete(existMatchingApply);
					else {
						throw new ApiException(ExceptionEnum.APPLY_REFUSED);
					}
				}
			} else {
				throw new ApiException(ExceptionEnum.OVER_MATCHING_COUNT);
			}
		} else {
			throw new ApiException(ExceptionEnum.DATE_OUT);
		}
		return new ResponseEntity<>(HttpStatus.OK);
	}

	// 수락자 : 마이페이지에서 수락하기 누르기 (매칭 수락) -> 보인다: 마이페이지 주인
	// 우선 마이페이지 게시글에 대한 매칭요청 리스트 -> 매칭요청 리스트중에 각 매칭요청마다
	// 해당 요청을 했던 유저정보가 이미 들어있을거잖아요?
	// matchingApply delete
	public ResponseEntity<MatchAcceptResponseDto> matchAcceptBoard(Long boardId, Long applyUserId, Users users) {
		Board board = existBoard(boardId);

		if (!board.getUsers().getId().equals(users.getId())){
			throw new ApiException(ExceptionEnum.UNAUTHORIZED);
		}
		Users applyUser = usersRepository.findById(applyUserId).orElseThrow(()->new ApiException(ExceptionEnum.NOT_FOUND_USER));
		MatchingApply matchingApply = matchingApplyRepository.findByBoardIdAndApplicantId(boardId, applyUserId);
		Matching matching = new Matching(boardId, applyUser, users);
		matchingRepository.save(matching);
		matchingApply.setMatchedCheck(true);
		board.setMatching(true);

		List<MatchingApply> RefusedMatchingApplyList = matchingApplyRepository.findAllByBoardId(boardId);
		for(MatchingApply refusedMatchingApply : RefusedMatchingApplyList){
			if(!Objects.equals(refusedMatchingApply.getApplicant().getId(), applyUserId))
				refusedMatchingApply.setApplyRefused(true);
		}

		notificationService.notify(applyUserId,new MatchNotificationResponseDto(boardId,users.getId(),users.getNickName(),users.getProfileImg(),MatchStatus.MATCH_ACCEPT));
		return ResponseEntity.ok(new MatchAcceptResponseDto(boardId, applyUser.getNickName(), users.getNickName()));
	}

	// 해당 게시글에 매칭 요청 리스트
	// 매칭이 된 게시물이라면 , 매칭이 완료된 게시물이면 , 버튼을 보이지 않아야함
	// 매칭이 안된 게시물이면 , 매칭요청 리스트보기가 있어야함
	// 리펙토링 필요
	@Transactional(readOnly = true)
	public ResponseEntity<List<MatchApplyResponseDto>> matchingApplyList(Long boardId, Users users) {
		Board board = existBoard(boardId);
		usersRepository.findById(users.getId())
				.orElseThrow(() -> new ApiException(ExceptionEnum.NOT_FOUND_USER));
		List<MatchApplyResponseDto> matchingApplyList = matchingApplyRepository.findApplyWithFalseAndRefusedTrue(boardId);
		return ResponseEntity.ok(matchingApplyList);
	}



	// 내가 받은 매칭 신청 리스트
	@Transactional(readOnly = true)
	public ResponseEntity<MatchingBoardListResponseDto> getMatchedList(Users users) {
		List<Board> boardList = boardRepository.getBoardListByHostIdWithFetch(users.getId());
		List<MatchingBoardResponseDto> matchingBoardResponseDtos = new ArrayList<>(boardList.size());
		int totalCnt = 0;

		for (Board board : boardList) {
			Matching existMatching = matchingRepository.findByBoardId(board.getId());
			String whoMatch = existMatching != null ? existMatching.getApplicant().getNickName() : null;
			Long whoMatchId = existMatching != null ? existMatching.getApplicant().getId() : null;
			int totalApplicantCnt = matchingApplyRepository.countAllMatchingWithFalseAndRefusedTrue(board.getId());
			MatchingBoardResponseDto matchingBoardResponseDto = new MatchingBoardResponseDto(board, totalApplicantCnt, whoMatch, whoMatchId);
			matchingBoardResponseDtos.add(matchingBoardResponseDto);
			totalCnt++;
		}

		return ResponseEntity.ok(new MatchingBoardListResponseDto(matchingBoardResponseDtos, totalCnt));
	}

	// 내가 신청한 매칭 게시글
	// 매칭 중(isMatched = false)/ 매칭 완료
	@Transactional(readOnly = true)
	public ResponseEntity<MatchingApplyBoardListResponseDto> getMatchingApplyList(Users users) {
		List<MatchingApply> matchingApplyList = matchingApplyRepository.findAllByApplicant(users);
		List<MatchingApplyBoardResponseDto> matchingBoardResponseDtos = new ArrayList<>(matchingApplyList.size());
		int totalCnt = 0;

		for (MatchingApply matchingApply : matchingApplyList) {
			Matching existMatching = matchingRepository.findByBoardId(matchingApply.getBoard().getId());
			boolean hasMatching = existMatching != null;
			boolean alreadyMatch = hasMatching && existMatching.getApplicant().equals(matchingApply.getApplicant());
			int totalApplicantCnt = matchingApplyRepository.countAllMatchingWithFalseAndRefusedTrue(matchingApply.getBoard().getId());

			matchingApply.getBoard().setMatching(!hasMatching);

			MatchingApplyBoardResponseDto matchingBoardResponseDto = new MatchingApplyBoardResponseDto(
					matchingApply.getBoard(),
					hasMatching,
					alreadyMatch,
					totalApplicantCnt,
					matchingApply.isApplyRefused()
			);
			matchingBoardResponseDtos.add(matchingBoardResponseDto);
			totalCnt++;
		}

		return ResponseEntity.ok(new MatchingApplyBoardListResponseDto(matchingBoardResponseDtos, totalCnt));
	}

	public ResponseEntity<Void> deleteMatchingApply(Long boardId, Long applyUserId, Users users){
		Board board = existBoard(boardId);
		checkAuthorized(board, users);

		MatchingApply matchingApply = matchingApplyRepository.findByBoardIdAndApplicantId(boardId, applyUserId);
		matchingApply.setApplyRefused(true);

		return ResponseEntity.ok(null);
	}

	public ResponseEntity<Void> deleteMatching(Long boardId, Users users){
		Board board = existBoard(boardId);
		checkAuthorized(board, users);

		Matching matching = matchingRepository.findByBoardId(boardId);
		Users applicant = matching.getApplicant();
		MatchingApply matchingApply = matchingApplyRepository.findByBoardIdAndApplicantId(boardId, applicant.getId());
		matchingApplyRepository.delete(matchingApply);
		matchingRepository.delete(matching);
		board.setMatching(false);
		// 매칭 삭제 알림
		notificationService.notify(applicant.getId(),new MatchNotificationResponseDto(boardId,users.getId(),users.getNickName(),users.getProfileImg(),MatchStatus.MATCH_DELETE));
		return ResponseEntity.ok(null);
	}

	private Board existBoard(Long boardId){
		return boardRepository.findExistBoard(boardId).orElseThrow(
			() -> new ApiException(ExceptionEnum.NOT_FOUND_POST)
		);
	}

	private void checkAuthorized(Board board, Users users){
		if (!board.getUsers().getId().equals(users.getId())){
			throw new ApiException(ExceptionEnum.UNAUTHORIZED);
		}
	}
}
