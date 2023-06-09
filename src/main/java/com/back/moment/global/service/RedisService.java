package com.back.moment.global.service;

import com.back.moment.chat.entity.Chat;
import com.back.moment.chat.repository.ChatRepository;
import com.back.moment.photos.entity.Photo;
import com.back.moment.photos.entity.Tag_Photo;
import com.back.moment.users.entity.Users;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RedisService {

    private final RedisTemplate<String, String> redisTemplate;
    private final ChatRepository chatRepository;
    private final ObjectMapper objectMapper;

    public void setRefreshValues(String userId, String key) {
        redisTemplate.opsForValue().set("Refresh"+userId,key);
        redisTemplate.expire("Refresh"+userId,Duration.ofDays(2L));
    }

    public void setCodeValues(String userId, String key) {
        redisTemplate.opsForValue().set("Code"+userId, key);
        redisTemplate.expire("Code"+userId,Duration.ofSeconds(300));
    }

    public void setChatValues(Chat chat,Long chatRoomId,String chatId){
        String chatAsJson = null;
        try {
            chatAsJson = objectMapper.writeValueAsString(chat);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        redisTemplate.opsForValue().set("Chat"+chatRoomId+"ChatId"+chatId,chatAsJson);
        redisTemplate.expire("Chat"+chatRoomId+"ChatId"+chatId,Duration.ofDays(2L));
    }

    public String getRefreshToken(String userId){
        return redisTemplate.opsForValue().get("Refresh"+userId);
    }

    public String getCode(String userId){
        return redisTemplate.opsForValue().get("Code"+userId);
    }

    public Chat getChat(Long chatRoomId,String chatId){
        String chatJson = redisTemplate.opsForValue().get("Chat" + chatRoomId+ "ChatId"+ chatId);
        try {
            return objectMapper.readValue(chatJson, Chat.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
    public void saveChatsToDB(Long chatRoomId){
        List<Chat> chats = getChats(chatRoomId);
        chatRepository.saveAll(chats);
        redisTemplate.delete("Chat"+chatRoomId);
    }
    public void deleteValues(String key){
        redisTemplate.delete(key);
    }

    private List<Chat> getChats(Long chatRoomId){
        Set<String> chatKeys = redisTemplate.keys("Chat" + chatRoomId + "*");
        ArrayList<Chat> chatList = new ArrayList<>();
        for (String c : chatKeys) {
            try {
                String chatJson = redisTemplate.opsForValue().get(c);
                Chat chat = objectMapper.readValue(chatJson, Chat.class);
                chatList.add(chat);
                deleteValues(c);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
        return chatList;
    }
}
