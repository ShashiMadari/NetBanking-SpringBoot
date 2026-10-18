package com.example.inetbank.forex.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record KycStatusRequest(

        @NotBlank
        @Pattern(regexp = "PENDING|APPROVED|REJECTED|EXPIRED")
        String status
) {
}