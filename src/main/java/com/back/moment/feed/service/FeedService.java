package com.back.moment.feed.service;

import com.back.moment.boards.entity.Board;
import com.back.moment.exception.ApiException;
import com.back.moment.exception.ExceptionEnum;
import com.back.moment.feed.dto.FeedDetailResponseDto;
import com.back.moment.feed.dto.FeedListResponseDto;
//import com.back.moment.feed.dto.FeedRequestDto;
import com.back.moment.feed.dto.LoveCheckResponseDto;
import com.back.moment.feed.dto.UsersInLoveListResponseDto;
import com.back.moment.love.entity.Love;
import com.back.moment.love.repository.LoveRepository;
import com.back.moment.photos.dto.PhotoFeedResponseDto;
import com.back.moment.photos.entity.Photo;
import com.back.moment.photos.entity.PhotoHashTag;
import com.back.moment.photos.entity.Tag_Photo;
import com.back.moment.photos.repository.PhotoHashTagRepository;
import com.back.moment.photos.repository.PhotoRepository;
import com.back.moment.photos.repository.Tag_PhotoRepository;
import com.back.moment.photos.repository.feedSearch.FeedSearch;
import com.back.moment.photos.repository.getAll.GetAllPhoto;
import com.back.moment.photos.repository.getAll.GetAllPhotoByLove;
import com.back.moment.photos.repository.getPhoto.GetPhoto;
import com.back.moment.photos.repository.getPhotoWhoLove.GetPhotoWhoLove;
import com.back.moment.s3.S3Uploader;
import com.back.moment.users.entity.Users;
import com.back.moment.users.repository.UsersRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FeedService {
    private final S3Uploader s3Uploader;
    private final PhotoRepository photoRepository;
    private final LoveRepository loveRepository;
    private final Tag_PhotoRepository tag_photoRepository;
    private final PhotoHashTagRepository photoHashTagRepository;
    private final UsersRepository usersRepository;
    private final GetAllPhoto getAllPhoto;
    private final GetAllPhotoByLove getAllPhotoByLove;
    private final GetPhoto getPhoto;
    private final GetPhotoWhoLove getPhotoWhoLove;
    private final FeedSearch feedSearch;

    @Transactional
    public ResponseEntity<Void> uploadImages(String contents, List<String> photoHashTags, List<MultipartFile> images, Users users) throws IOException {
        if (Objects.equals(users.getRole(), "NONE")) {
            throw new ApiException(ExceptionEnum.NOT_FOUND_ROLE);
        }

        List<Photo> uploadedPhotos = new ArrayList<>();

        int uploadCount = photoRepository.countByUsers(users) + 1;
        // Upload and save each image
        for (MultipartFile image : images) {
            String imageUrl = s3Uploader.upload(image);

            Photo photo = new Photo(users, imageUrl);
            photo.updateContents(contents);
            photo.setUploadCnt(uploadCount);
            photoRepository.save(photo);
            uploadedPhotos.add(photo);
        }

        // Process photo hash tags
        if (photoHashTags != null && !photoHashTags.isEmpty()) {
            List<String> validHashTags = photoHashTags.stream()
                    .filter(tag -> tag.startsWith("#"))
                    .toList();

            for (String hashTag : validHashTags) {
                String photoHashTagString = hashTag.substring(1);
                PhotoHashTag existTag = photoHashTagRepository.findByHashTag(photoHashTagString);
                PhotoHashTag photoHashTagTable;

                if (existTag != null) {
                    photoHashTagTable = existTag;
                } else {
                    photoHashTagTable = new PhotoHashTag(photoHashTagString);
                    photoHashTagRepository.save(photoHashTagTable);
                }

                for (Photo photo : uploadedPhotos) {
                    Tag_Photo tag_photo = new Tag_Photo(photoHashTagTable, photo);
                    tag_photoRepository.save(tag_photo);
                }
            }
        }
        return ResponseEntity.ok(null);
    }


    @Transactional
    public ResponseEntity<LoveCheckResponseDto> lovePhoto(Long photoId, Users users) {
        Photo photo = photoRepository.findById(photoId).orElseThrow(
                () -> new ApiException(ExceptionEnum.NOT_FOUND_PHOTO)
        );

        Love existLove = loveRepository.findExistLove(photoId, users.getId());

        String message;
        LoveCheckResponseDto loveCheckResponseDto;

        if(existLove != null){
            loveRepository.delete(existLove);
            message = "좋아요 취소";
            photo.getUsers().setTotalLoveCnt(photo.getUsers().getTotalLoveCnt() - 1);
            loveCheckResponseDto = new LoveCheckResponseDto(false);
        }else {
            Love love = new Love(users, photo);
            message = "좋아요 등록";
            loveRepository.save(love);
            photo.getUsers().setTotalLoveCnt(photo.getUsers().getTotalLoveCnt() + 1);
            loveCheckResponseDto = new LoveCheckResponseDto(true);
        }
        int loveCnt = loveRepository.findCntByPhotoId(photoId);
        photo.setLoveCnt(loveCnt);
        loveCheckResponseDto.setTotalLoveCnt(loveCnt);
        photoRepository.save(photo);

        return new ResponseEntity<>(loveCheckResponseDto, HttpStatus.OK);
    }



    @Transactional(readOnly = true)
    public ResponseEntity<FeedListResponseDto> getAllFeeds(Pageable pageable, Users users) {
        List<Photo> allPhoto = getAllPhoto.getAllPhoto();
        List<Photo> allPhotoByLove = getAllPhotoByLove.getAllPhotoWithTagByLove();

        Page<PhotoFeedResponseDto> page1 = createResponsePhotoPage(pageable, allPhoto, users);
        Page<PhotoFeedResponseDto> page2 = createResponsePhotoPage(pageable, allPhotoByLove, users);

        FeedListResponseDto responseDto = new FeedListResponseDto(page1, page2);
        return new ResponseEntity<>(responseDto, HttpStatus.OK);
    }

    private Page<PhotoFeedResponseDto> createResponsePhotoPage(Pageable pageable, List<Photo> photos, Users users) {
        List<Long> photoIdList = photos.stream().map(Photo::getId).collect(Collectors.toList());
        Map<Long, Boolean> photoLoveMap = getPhotoWhoLove.findPhotoLoveMap(photoIdList, users != null ? users.getId() : null);

        List<PhotoFeedResponseDto> responsePhotoList = photos.stream()
                .map(photo -> new PhotoFeedResponseDto(photo, photoLoveMap.getOrDefault(photo.getId(), false)))
                .collect(Collectors.toList());

        int startIndex = (int) pageable.getOffset();
        int endIndex = Math.min(startIndex + pageable.getPageSize(), photos.size());
        List<PhotoFeedResponseDto> pageItems = responsePhotoList.subList(startIndex, endIndex);

        int totalPages = (int) Math.ceil((double) photos.size() / pageable.getPageSize());

        boolean isFirstPage = startIndex == 0;
        boolean isLastPage = endIndex >= photos.size();

        Pageable modifiedPageable = totalPages > 0 && isLastPage ? pageable.withPage(totalPages - 1) : pageable;

        return new PageImpl<>(pageItems, modifiedPageable, photos.size());
    }
//    @Transactional(readOnly = true)
//    public ResponseEntity<FeedListResponseDto> getAllFeeds(Pageable pageable, Users users) {
//        Page<Photo> allPhotoPage = getAllPhoto.getAllPhoto(pageable);
//        Page<Photo> allPhotoByLovePage = getAllPhotoByLove.getAllPhotoWithTagByLove(pageable);
//
//        List<PhotoFeedResponseDto> page1 = createResponsePhotoList(allPhotoPage.getContent(), users);
//        List<PhotoFeedResponseDto> page2 = createResponsePhotoList(allPhotoByLovePage.getContent(), users);
//
//        FeedListResponseDto responseDto = new FeedListResponseDto(new PageImpl<>(page1, pageable, allPhotoPage.getTotalElements()),
//                new PageImpl<>(page2, pageable, allPhotoByLovePage.getTotalElements()));
//        return new ResponseEntity<>(responseDto, HttpStatus.OK);
//    }
//
//        private List<PhotoFeedResponseDto> createResponsePhotoList(List<Photo> photos, Users users) {
//            List<Long> photoIdList = photos.stream().map(Photo::getId).collect(Collectors.toList());
//            Map<Long, Boolean> photoLoveMap = getPhotoWhoLove.findPhotoLoveMap(photoIdList, users != null ? users.getId() : null);
//
//            return photos.stream()
//                    .map(photo -> new PhotoFeedResponseDto(photo, photoLoveMap.getOrDefault(photo.getId(), false)))
//                    .collect(Collectors.toList());
//        }





    @Transactional(readOnly = true)
    public ResponseEntity<FeedDetailResponseDto> getFeed(Long photoId, Users users){
        checkUsers(users);
        Photo photo = existPhoto(photoId);

        List<Photo> photoList = getPhoto.findPhotosByCreatedAtAndUsers(photo.getUploadCnt(), photo.getUsers());
        List<String> photoUrlList = new ArrayList<>();
        for(Photo eachPhoto : photoList){
            photoUrlList.add(eachPhoto.getImagUrl());
        }

        FeedDetailResponseDto feedDetailResponseDto = new FeedDetailResponseDto(photo, photoUrlList);
        feedDetailResponseDto.setCheckLove(loveRepository.checkLove(photo.getId(), users.getId()));

        return new ResponseEntity<>(feedDetailResponseDto, HttpStatus.OK);
    }

    @Transactional(readOnly = true)
    public ResponseEntity<Page<PhotoFeedResponseDto>> searchPhoto(String tag, String userNickName, String contents, Pageable pageable, Users users){
        Long currentUserId = (users != null) ? users.getId() : null;
        Page<PhotoFeedResponseDto> photoPage = feedSearch.feedSearch(userNickName, tag, contents, pageable, currentUserId);
        return ResponseEntity.ok(photoPage);
    }

    @Transactional
    public ResponseEntity<Void> writeContents(Long photoId, String content, Users users){
        Photo photo = existPhoto(photoId);
        if(!Objects.equals(photo.getUsers().getId(), users.getId()))
            throw new ApiException(ExceptionEnum.NOT_MATCH_USERS);

        photo.updateContents(content);
        photoRepository.save(photo);

        return ResponseEntity.ok(null);
    }

    @Transactional(readOnly = true)
    public ResponseEntity<List<UsersInLoveListResponseDto>> whoLoveCheck(Long photoId) {
        Photo photo = existPhoto(photoId);

        List<Love> loveList = photo.getLoveList();

        Comparator<Love> comparator = Comparator.comparing(Love::getId, Comparator.reverseOrder());

        loveList.sort(comparator);



        List<UsersInLoveListResponseDto> usersInLoveListResponseDtoList = new ArrayList<>();
        for (Love love : loveList) {
            UsersInLoveListResponseDto usersInLoveListResponseDto = new UsersInLoveListResponseDto(love.getUsers());
            usersInLoveListResponseDtoList.add(usersInLoveListResponseDto);
        }

        return ResponseEntity.ok(usersInLoveListResponseDtoList);
    }


    public Photo existPhoto(Long photoId){
        return photoRepository.findExistPhoto(photoId).orElseThrow(
                () -> new ApiException(ExceptionEnum.NOT_FOUND_POST)
        );
    }

    @Transactional(readOnly = true)
    public ResponseEntity<Void> forTest(){
        return ResponseEntity.ok().build();
    }

    protected void checkUsers(Users users){
        if(users == null){
            throw new ApiException(ExceptionEnum.LOGIN);
        }
    }
}
