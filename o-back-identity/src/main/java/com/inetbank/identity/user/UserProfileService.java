package com.inetbank.identity.user;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserProfileService {

    private final CustomerProfileRepository profiles;

    public UserProfileService(
            CustomerProfileRepository profiles) {

        this.profiles = profiles;
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(String email) {

        UserProfileResponse profile = profiles.findProfileByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Customer profile was not found."
                ));

        if (!"CUSTOMER".equals(profile.role())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only customers can view this profile."
            );
        }

        return profile;
    }
}