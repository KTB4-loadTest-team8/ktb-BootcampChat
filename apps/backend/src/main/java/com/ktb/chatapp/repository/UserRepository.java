package com.ktb.chatapp.repository;

import com.ktb.chatapp.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Collection;
import java.util.List;

@Repository
public interface UserRepository extends MongoRepository<User, String> {
    Optional<User> findByEmail(String email);

    /**
     * 방 목록·참가자 응답에 필요한 사용자 필드만 조회한다.
     * 비밀번호, 암호화 이메일, 활동 시간 등은 방 응답에 사용하지 않는다.
     */
    @org.springframework.data.mongodb.repository.Query(
            value = "{ '_id': { '$in': ?0 } }",
            fields = "{ '_id': 1, 'name': 1, 'email': 1, 'profileImage': 1 }")
    List<User> findAllRoomSummariesById(Collection<String> userIds);
}
