package com.back.moment.photos.repository.getPhoto;

import com.back.moment.photos.entity.Photo;
import com.back.moment.photos.entity.QPhoto;
import com.back.moment.users.entity.Users;
import com.querydsl.jpa.JPQLQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import org.joda.time.DateTime;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
@Primary
public class GetPhotoImpl implements GetPhoto{
    private final JPAQueryFactory queryFactory;

    public GetPhotoImpl(EntityManager entityManager) {
        this.queryFactory = new JPAQueryFactory(entityManager);
    }

    @Override
    public List<Photo> findPhotosByCreatedAtAndUsers(Integer uploadCnt, Users users) {
        QPhoto photo = QPhoto.photo;

        JPQLQuery<Photo> query = queryFactory
                .select(photo)
                .from(photo)
                .where(photo.users.eq(users));

        if (uploadCnt != null) {
            query = query.where(photo.uploadCnt.eq(uploadCnt));
        }  else {
            query = query.where(photo.uploadCnt.isNull()); // uploadCnt가 null이 아닌 경우만 필터링
        }

        return query.fetch();
    }
}
