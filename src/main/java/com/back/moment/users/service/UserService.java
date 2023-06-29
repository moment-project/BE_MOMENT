package com.back.moment.users.service;

import static com.back.moment.users.jwt.JwtUtil.ACCESS_KEY;
import static com.back.moment.users.jwt.JwtUtil.REFRESH_KEY;

import com.back.moment.boards.entity.Board;
import com.back.moment.boards.repository.BoardRepository;
import com.back.moment.chat.entity.ChatRoom;
import com.back.moment.chat.repository.ChatRoomRepository;
import com.back.moment.email.service.EmailService;
import com.back.moment.exception.ApiException;
import com.back.moment.exception.ExceptionEnum;
import com.back.moment.global.service.RedisService;
import com.back.moment.photos.entity.Photo;
import com.back.moment.photos.repository.PhotoRepository;
import com.back.moment.s3.S3Uploader;
import com.back.moment.users.dto.LoginRequestDto;
import com.back.moment.users.dto.SignupRequestDto;
import com.back.moment.users.dto.TokenDto;
import com.back.moment.users.dto.UserInfoResponseDto;
import com.back.moment.users.entity.Users;
import com.back.moment.users.jwt.JwtUtil;
import com.back.moment.users.repository.UsersRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class UserService {

    @Value("${jwt.secret.key}")
    private String secretKey; // 암호화/복호화에 필요

    private final UsersRepository usersRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final PhotoRepository photoRepository;
    private final BoardRepository boardRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    public static final String BEARER_PREFIX = "Bearer ";
    private final S3Uploader s3Uploader;
    private final JwtUtil jwtUtil;
    private final RedisService redisService;

    @Transactional
    public ResponseEntity<Void> signup(SignupRequestDto requestDto, MultipartFile profileImg) {
        if (requestDto.getEmail() == null ||
            requestDto.getPassword() == null ||
            requestDto.getNickName() == null
        ) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        Optional<Users> findEmail = usersRepository.findByEmail(requestDto.getEmail());
        if (usersRepository.findByNickName(requestDto.getNickName()).isPresent()){
            throw new ApiException(ExceptionEnum.DUPLICATED_NICKNAME);
        }
        if (findEmail.isPresent()) {
            throw new ApiException(ExceptionEnum.EXIST_MAIL);
        }
        Users users = new Users();
        String password = passwordEncoder.encode(requestDto.getPassword());
        String gender = requestDto.getGender();
        String role = requestDto.getRole();

        users.saveUsers(requestDto, password, gender, role);
        usersRepository.save(users);
        // 프로필 이미지 처리
        if (profileImg != null) {
            try {
                String imgPath = s3Uploader.upload(profileImg);
                users.setProfileImg(imgPath);
            } catch (IOException e) {
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }
        } else{
            if(Objects.equals(users.getGender(), "FEMALE")){
                users.setProfileImg("https://moment-photo-resized.s3.ap-northeast-2.amazonaws.com/%EC%97%AC%EC%9E%90.jpg");
            } else{
                users.setProfileImg("https://moment-photo-resized.s3.ap-northeast-2.amazonaws.com/%EB%82%A8%EC%9E%90.jpg");
            }
        }

        return new ResponseEntity<>(HttpStatus.OK);
    }

    @Transactional
    public ResponseEntity<UserInfoResponseDto> login(LoginRequestDto loginRequestDto,
        HttpServletResponse response) {
        String email = loginRequestDto.getEmail();
        String password = loginRequestDto.getPassword();

        try {
            Users users = usersRepository.findByEmail(email).orElseThrow(
                () -> new ApiException(ExceptionEnum.NOT_MATCH_USERS)
            );

            if (!passwordEncoder.matches(password, users.getPassword())) {
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }
            jwtUtil.init();
            // 토큰에 모델인지 작가인지 판단하는 role 입력
            TokenDto tokenDto = jwtUtil.createAllToken(users, users.getRole());

            String redisKey = tokenDto.getRefreshToken().substring(7);
            String refreshRedis = redisService.getRefreshToken(users.getEmail());
            if (refreshRedis == null) {
                redisService.setRefreshValues(users.getEmail(),redisKey);
            }

//            Claims claim = Jwts.parser().setSigningKey(secretKey)
//                .parseClaimsJws(tokenDto.getAccessToken().substring(7)).getBody();
//            Long userId = claim.get("userId", Long.class);
//            String nickName = claim.get("nickName", String.class);
//            String profileImg = claim.get("profileImg", String.class);
//            String role = claim.get("role", String.class);

            UserInfoResponseDto userInfoResponseDto = new UserInfoResponseDto(users.getId(),
                users.getNickName(), users.getProfileImg(), users.getRole());
            //응답 헤더에 토큰 추가
            setHeader(response, tokenDto);

            return ResponseEntity.ok(userInfoResponseDto);
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    private void setHeader(HttpServletResponse response, TokenDto tokenDto) {
        response.addHeader(ACCESS_KEY, tokenDto.getAccessToken());
        response.addHeader(REFRESH_KEY, tokenDto.getRefreshToken());
    }

    @Transactional
    public ResponseEntity<Void> deleteUsersHard(Users users) {
        List<String> urlsToDelete = new ArrayList<>();
        urlsToDelete.add(users.getProfileImg());

        // Delete the entities from the database
        // photoList 삭제
        List<Photo> photoList = photoRepository.findByUsers(users);
        if (photoList != null && !photoList.isEmpty()) {
            for (Photo photo : photoList) {
                photo.setUsers(null);
                urlsToDelete.add(photo.getImagUrl());
            }
            photoList.clear();
            photoRepository.deleteAll(photoList);
        }

        // boardList 삭제
        List<Board> boardList = boardRepository.findByUsers(users);
        if (boardList != null && !boardList.isEmpty()) {
            for (Board board : boardList) {
                board.setUsers(null);
                urlsToDelete.add(board.getBoardImgUrl());
            }
            boardList.clear();
            boardRepository.deleteAll(boardList);
        }

        // Delete the URLs from the S3 bucket
        s3Uploader.deleteBatch(urlsToDelete);

        // Delete the users entity
        List<ChatRoom> findAllChatRoom = chatRoomRepository.findAllByHostOrGuest(users, users);
        for (ChatRoom chatRoom : findAllChatRoom) {
            chatRoomRepository.deleteById(chatRoom.getId());
        }

        usersRepository.delete(users);

        return ResponseEntity.ok(null);
    }

}
