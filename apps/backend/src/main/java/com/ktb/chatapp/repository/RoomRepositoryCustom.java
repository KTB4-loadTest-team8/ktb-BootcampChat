package com.ktb.chatapp.repository;

import com.ktb.chatapp.model.Room;
import java.util.Optional;

public interface RoomRepositoryCustom {

    Optional<Room> addParticipantAndReturn(String roomId, String userId);
}
