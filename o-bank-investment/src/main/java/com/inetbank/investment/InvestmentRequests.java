package com.inetbank.investment;

import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public final class InvestmentRequests {
    private InvestmentRequests() { }
    public record PanRequest(@NotBlank @Pattern(regexp = "[A-Za-z]{5}[0-9]{4}[A-Za-z]") String panNumber) { }
    public record DematOtpRequest(@NotNull Long accountId) { }
    public record OtpRequest(@NotBlank @Pattern(regexp = "[0-9]{6}") String otp) { }
    public record PurchaseRequest(@NotNull Long productId, @NotNull Long accountId, @DecimalMin("0.01") BigDecimal amount, @DecimalMin("0.0001") BigDecimal quantity, String mode, String exchange, Integer tenureMonths) { }
    public record RedemptionRequest(@NotNull Long accountId, @DecimalMin("0.01") BigDecimal amount, @DecimalMin("0.0001") BigDecimal quantity) { }
    public record ScheduleRequest(@NotNull Long productId, @NotNull Long accountId, @NotNull @DecimalMin("1.00") BigDecimal amount, @NotNull Integer debitDay, Integer tenureMonths) { }
    public record WalletRequest(@NotNull Long accountId, @NotNull @DecimalMin("1.00") BigDecimal amount) { }
}
