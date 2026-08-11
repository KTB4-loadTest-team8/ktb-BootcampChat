package com.ktb.chatapp.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ktb.chatapp.model.User;
import com.ktb.chatapp.service.FileUrl;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserResponse {
    private String id;
    private String name;
    private String email;
    private String profileImage;

    public static UserResponse from(User user) {
        String profileImageUrl = FileUrl.of(user.getProfileImage());
        return UserResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .profileImage(profileImageUrl != null ? profileImageUrl : "")
                .build();
    }
}
