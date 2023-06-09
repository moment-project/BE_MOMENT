package com.back.moment.photos.repository;

import com.back.moment.photos.entity.Tag_Photo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface Tag_PhotoRepository extends JpaRepository<Tag_Photo, Long> {
//    void deleteAllByPhotoId(@Param("photoId") Long photoId);
    List<Tag_Photo> findByPhotoId(Long photoId);

//    @Query("select tp.photoHashTag.id from Tag_Photo tp where tp.photo.id = :phtoId")
//    List<Long> findTagIds(@Param("photoId") Long photoId);
}
