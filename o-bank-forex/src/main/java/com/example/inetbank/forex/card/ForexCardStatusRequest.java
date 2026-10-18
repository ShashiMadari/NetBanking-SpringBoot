package com.example.inetbank.forex.card;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ForexCardStatusRequest(

        @NotBlank
        @Pattern(regexp = "ACTIVE|BLOCKED|CLOSED")
        String status
) {
}