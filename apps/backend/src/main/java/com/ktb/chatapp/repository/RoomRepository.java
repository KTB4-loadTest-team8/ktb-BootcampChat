package com.ktb.chatapp.repository;

import com.ktb.chatapp.model.Room;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoomRepository extends MongoRepository<Room, String>, RoomRepositoryCustom {

    // 가장 최근에 생성된 방 조회 (Health Check용)
    @Query(value = "{}", sort = "{ 'createdAt': -1 }")
    Optional<Room> findMostRecentRoom();

    // Health Check용 단순 조회 (지연 시간 측정)
    @Query(value = "{}", fields = "{ '_id': 1 }")
    Optional<Room> findOneForHealthCheck();

    /**
     * REST 방 상세·Socket.IO 권한 확인에 필요한 방 필드만 조회한다.
     * 비밀번호 해시를 포함한 전체 Room 문서를 읽지 않는다.
     */
    @Query(value = "{ '_id': ?0 }", fields =
            "{ '_id': 1, 'name': 1, 'creator': 1, 'hasPassword': 1, 'createdAt': 1, 'participantIds': 1 }")
    Optional<Room> findRoomForReadById(String roomId);

    @Query("{'_id': ?0}")
    @Update("{'$addToSet': {'participantIds': ?1}}")
    void addParticipant(String roomId, String userId);

    @Query("{'_id': ?0}")
    @Update("{'$pull': {'participantIds': ?1}}")
    void removeParticipant(String roomId, String userId);
}
