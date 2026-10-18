package com.example.inetbank.credit.cardmanagement;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.inetbank.credit.integration.IdentityNotificationClient;
import com.example.inetbank.credit.integration.IdentitySystemNotificationClient;

@Service
public class CardPinChangeService {

    private final CardPinChangeRepository pinChangeRepository;
    private final CardPinChangeEmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final IdentityNotificationClient identityNotificationClient;
    private final IdentitySystemNotificationClient identitySystemNotificationClient;
    private final SecureRandom random = new SecureRandom();

    public CardPinChangeService(
            CardPinChangeRepository pinChangeRepository,
            CardPinChangeEmailService emailService,
            PasswordEncoder passwordEncoder,
            IdentityNotificationClient identityNotificationClient,
            IdentitySystemNotificationClient identitySystemNotificationClient) {

        this.pinChangeRepository = pinChangeRepository;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
        this.identityNotificationClient = identityNotificationClient;
        this.identitySystemNotificationClient = identitySystemNotificationClient;
    }

    @Transactional
    public CardPinChangeOtpResponse initiate(
            Long userId,
            String customerEmail,
            String bearerToken,
            InitiateCardPinChangeRequest request) {

        CardPinChangeRepository.CardDetails card =
                activeCard(request.cardId(), userId);

        String otp = generateOtp();
        LocalDateTime expiry = LocalDateTime.now().plusMinutes(5);

        Long orderId = pinChangeRepository.createOrder(
                card.cardId(),
                userId,
                passwordEncoder.encode(request.newPin()),
                passwordEncoder.encode(otp),
                expiry,
                UUID.randomUUID().toString()
        );

        emailService.sendOtp(
                customerEmail,
                otp,
                card,
                false
        );

        identityNotificationClient.createNotification(
                bearerToken,
                "CARD",
                "A card PIN change OTP was generated for your "
                        + card.cardType().toLowerCase()
                        + " card ending " + card.lastFour() + "."
        );

        identitySystemNotificationClient.createAuditEvent(
                userId,
                "CARD_PIN_CHANGE_OTP_SENT",
                "CARD",
                String.valueOf(card.cardId())
        );

        return new CardPinChangeOtpResponse(
                orderId,
                card.cardId(),
                "OTP_PENDING",
                expiry,
                "Card PIN change OTP was sent to your email. "
                        + "It expires in 5 minutes."
        );
    }

    @Transactional
    public CardPinChangeOtpResponse resendOtp(
            Long userId,
            String customerEmail,
            String bearerToken,
            Long orderId) {

        CardPinChangeRepository.PinChangeOrder order =
                order(orderId, userId);

        ensurePending(order);

        CardPinChangeRepository.CardDetails card =
                activeCard(order.cardId(), userId);

        String otp = generateOtp();
        LocalDateTime expiry = LocalDateTime.now().plusMinutes(5);

        if (!pinChangeRepository.replaceOtp(
                orderId,
                userId,
                passwordEncoder.encode(otp),
                expiry)) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "OTP cannot be resent for this PIN change request."
            );
        }

        emailService.sendOtp(
                customerEmail,
                otp,
                card,
                true
        );

        identityNotificationClient.createNotification(
                bearerToken,
                "CARD",
                "A new card PIN change OTP was generated for your "
                        + card.cardType().toLowerCase()
                        + " card ending " + card.lastFour() + "."
        );

        identitySystemNotificationClient.createAuditEvent(
                userId,
                "CARD_PIN_CHANGE_OTP_RESENT",
                "CARD",
                String.valueOf(card.cardId())
        );

        return new CardPinChangeOtpResponse(
                orderId,
                card.cardId(),
                "OTP_PENDING",
                expiry,
                "A new card PIN change OTP was sent to your email."
        );
    }

    @Transactional
    public CardPinChangeConfirmationResponse confirm(
            Long userId,
            String customerEmail,
            String bearerToken,
            Long orderId,
            ConfirmCardPinChangeRequest request) {

        CardPinChangeRepository.PinChangeOrder order =
                order(orderId, userId);

        ensurePending(order);

        if (!order.otpExpiry().isAfter(LocalDateTime.now())) {
            pinChangeRepository.markExpired(orderId, userId);

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "OTP has expired. Please resend OTP."
            );
        }

        if (!passwordEncoder.matches(
                request.otp(),
                order.otpHash())) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid OTP."
            );
        }

        CardPinChangeRepository.CardDetails card =
                activeCard(order.cardId(), userId);

        if (!pinChangeRepository.updateCardPin(
                card.cardId(),
                userId,
                order.newPinHash())) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Card PIN could not be updated."
            );
        }

        if (!pinChangeRepository.completeOrder(orderId, userId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "PIN change request is no longer pending."
            );
        }

        LocalDateTime completedAt = LocalDateTime.now();

        emailService.sendSuccess(customerEmail, card);

        identityNotificationClient.createNotification(
                bearerToken,
                "CARD",
                "PIN for your " + card.cardType().toLowerCase()
                        + " card ending " + card.lastFour()
                        + " was changed successfully."
        );

        identitySystemNotificationClient.createAuditEvent(
                userId,
                "CARD_PIN_CHANGED",
                "CARD",
                String.valueOf(card.cardId())
        );

        return new CardPinChangeConfirmationResponse(
                orderId,
                card.cardId(),
                "SUCCESS",
                completedAt,
                "Card PIN changed successfully."
        );
    }

    private CardPinChangeRepository.CardDetails activeCard(
            Long cardId,
            Long userId) {

        return pinChangeRepository.findActiveCard(cardId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Active card was not found for the authenticated customer."
                ));
    }

    private CardPinChangeRepository.PinChangeOrder order(
            Long orderId,
            Long userId) {

        return pinChangeRepository.findOrder(orderId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Card PIN change request was not found."
                ));
    }

    private void ensurePending(
            CardPinChangeRepository.PinChangeOrder order) {

        if (!"OTP_PENDING".equals(order.status())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This PIN change request is no longer pending."
            );
        }
    }

    private String generateOtp() {
        return String.format("%06d", random.nextInt(1_000_000));
    }
}