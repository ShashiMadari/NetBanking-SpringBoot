package com.example.inetbank.credit.cardmanagement;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record InitiateCardPinChangeRequest(

        @NotNull(message = "Card ID is required.")
        Long cardId,

        @Pattern(
                regexp = "\\d{4}",
                message = "New PIN must contain exactly four digits."
        )
        String newPin,

        @Pattern(
                regexp = "\\d{4}",
                message = "PIN confirmation must contain exactly four digits."
        )
        String confirmPin
) {

    @AssertTrue(message = "New PIN and confirm PIN must match.")
    public boolean isPinMatching() {
        return newPin != null && newPin.equals(confirmPin);
    }
}