package com.example.inetbank.forex.compliance;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ComplianceReviewRequest(

        @NotBlank
        @Pattern(
                regexp = "REVIEWED|CLEARED",
                message = "status must be REVIEWED or CLEARED."
        )
        String status
) {
}