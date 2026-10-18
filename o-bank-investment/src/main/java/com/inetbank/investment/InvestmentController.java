package com.inetbank.investment;

import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/investments")
@PreAuthorize("hasRole('CUSTOMER')")
public class InvestmentController {
    private final InvestmentService service;
    public InvestmentController(InvestmentService service) { this.service = service; }
    @GetMapping("/dashboard") public Map<String, Object> dashboard(Authentication a) { return service.dashboard(a.getName()); }
    @GetMapping("/products") public List<Map<String, Object>> products() { return service.products(); }
    @GetMapping("/wallet") public Map<String, Object> wallet(Authentication a) { return service.wallet(a.getName()); }
    @GetMapping("/products/{productId}") public Map<String, Object> productDetail(@PathVariable("productId") long productId) { return service.productDetail(productId); }
    @PostMapping("/profile/pan") public Map<String, Object> pan(Authentication a, @Valid @RequestBody InvestmentRequests.PanRequest r) { return service.savePan(a.getName(), r); }
    @PostMapping("/profile/demat/send-otp") public Map<String, Object> dematOtp(Authentication a, @Valid @RequestBody InvestmentRequests.DematOtpRequest r) { return service.sendDematOtp(a.getName(), r); }
    @PostMapping("/profile/demat/verify") public Map<String, Object> dematVerify(Authentication a, @Valid @RequestBody InvestmentRequests.OtpRequest r) { return service.verifyDematOtp(a.getName(), r); }
    @PostMapping("/purchase/send-otp") public Map<String, Object> purchase(Authentication a, @Valid @RequestBody InvestmentRequests.PurchaseRequest r) { return service.startPurchase(a.getName(), r); }
    @PostMapping("/orders/{orderId}/authorise") public Map<String, Object> authorise(Authentication a, @PathVariable("orderId") long orderId, @Valid @RequestBody InvestmentRequests.OtpRequest r) { return service.authorisePurchase(a.getName(), orderId, r); }
    @PostMapping("/holdings/{holdingId}/redeem/send-otp") public Map<String, Object> redeem(Authentication a, @PathVariable("holdingId") long holdingId, @Valid @RequestBody InvestmentRequests.RedemptionRequest r) { return service.startRedemption(a.getName(), holdingId, r); }
    @PostMapping("/schedules") public Map<String, Object> schedule(Authentication a, @Valid @RequestBody InvestmentRequests.ScheduleRequest r) { return service.createSchedule(a.getName(), r); }
    @PostMapping("/wallet/fund/send-otp") public Map<String, Object> fund(Authentication a, @Valid @RequestBody InvestmentRequests.WalletRequest r) { return service.startWalletTransfer(a.getName(), r, "FUND"); }
    @PostMapping("/wallet/withdraw/send-otp") public Map<String, Object> withdraw(Authentication a, @Valid @RequestBody InvestmentRequests.WalletRequest r) { return service.startWalletTransfer(a.getName(), r, "WITHDRAW"); }
    @PostMapping("/wallet/transactions/{walletTransactionId}/authorise") public Map<String, Object> authoriseWallet(Authentication a, @PathVariable("walletTransactionId") long id, @Valid @RequestBody InvestmentRequests.OtpRequest r) { return service.authoriseWalletTransfer(a.getName(), id, r); }

    @PostMapping("/orders/{orderId}/resend-otp")
    public Map<String, Object> resendOrderOtp(
            Authentication authentication,
            @PathVariable("orderId") long orderId) {

        return service.resendOrderOtp(
                authentication.getName(),
                orderId
        );
    }


    @GetMapping("/schedules")
public List<Map<String, Object>> schedules(
        Authentication authentication) {

    return service.schedules(authentication.getName());
}

@PatchMapping("/schedules/{scheduleId}/pause")
public Map<String, Object> pauseSchedule(
        Authentication authentication,
        @PathVariable("scheduleId") long scheduleId) {

    return service.pauseSchedule(
            authentication.getName(),
            scheduleId
    );
}

@PatchMapping("/schedules/{scheduleId}/resume")
public Map<String, Object> resumeSchedule(
        Authentication authentication,
        @PathVariable("scheduleId") long scheduleId) {

    return service.resumeSchedule(
            authentication.getName(),
            scheduleId
    );
}

@DeleteMapping("/schedules/{scheduleId}")
public Map<String, Object> cancelSchedule(
        Authentication authentication,
        @PathVariable("scheduleId") long scheduleId) {

    return service.cancelSchedule(
            authentication.getName(),
            scheduleId
    );
}

@GetMapping("/orders")
public List<Map<String, Object>> orders(
        Authentication authentication,
        @RequestParam(name = "status", required = false) String status) {

    return service.orders(
            authentication.getName(),
            status
    );
}

@GetMapping("/statements/monthly")
public Map<String, Object> monthlyStatement(
        Authentication authentication,
        @RequestParam(name = "year") int year,
        @RequestParam(name = "month") int month) {

    return service.monthlyStatement(
            authentication.getName(),
            year,
            month
    );
}

@GetMapping("/portfolio/history")
public List<Map<String, Object>> portfolioHistory(
        Authentication authentication,
        @RequestParam(name = "days", defaultValue = "30") int days) {

    return service.portfolioHistory(
            authentication.getName(),
            days
    );
}
}

