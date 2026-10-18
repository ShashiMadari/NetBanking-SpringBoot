package com.inetbank.identity.user;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('CUSTOMER')")
public class UserProfileController {

    private final UserProfileService profileService;

    public UserProfileController(
            UserProfileService profileService) {

        this.profileService = profileService;
    }

    @GetMapping("/profile")
    public UserProfileResponse profile(
            Authentication authentication) {

        return profileService.getProfile(
                authentication.getName()
        );
    }
}