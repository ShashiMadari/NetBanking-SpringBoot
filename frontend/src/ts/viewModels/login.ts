import * as ko from "knockout";
import * as AccUtils from "../accUtils";
import { login, resendLoginOtp, sendLoginOtp } from "../services/auth";

class LoginViewModel {
  email = ko.observable("");
  password = ko.observable("");
  otp = ko.observable("");
  loginAsAdmin = ko.observable(false);
  otpSent = ko.observable(false);
  busy = ko.observable(false);
  error = ko.observable("");
  notice = ko.observable("");

  connected(): void {
    AccUtils.announce("Secure sign in page loaded.");
    document.title = "Sign in | O-Bank";
    const query = new URLSearchParams(window.location.search);
    if (query.get("sessionExpired") === "true") {
      this.notice("Your session has ended. Please sign in again.");
    } else if (query.get("registered") === "true") {
      this.notice("Registration completed. Sign in with your email and new password.");
    } else if (query.get("passwordReset") === "true") {
      this.notice("Password reset complete. Sign in with your new password.");
    }
  }

  sendOtp = async (): Promise<void> => {
    this.error("");
    this.notice("");
    if (!this.email() || !this.password()) {
      this.error("Enter your registered email address and password.");
      return;
    }
    this.busy(true);
    try {
      await sendLoginOtp(this.email(), this.password(), this.loginAsAdmin());
      this.otpSent(true);
      this.notice("A six-digit OTP was sent to your registered email address.");
    } catch (error) {
      this.error(error instanceof Error ? error.message : "Unable to send the OTP.");
    } finally {
      this.busy(false);
    }
  };

  resendOtp = async (): Promise<void> => {
    this.error("");
    this.notice("");
    this.busy(true);
    try {
      await resendLoginOtp(this.email(), this.password(), this.loginAsAdmin());
      this.otp("");
      this.notice("A new six-digit OTP was sent to your registered email address.");
    } catch (error) {
      this.error(error instanceof Error ? error.message : "Unable to resend the OTP.");
    } finally {
      this.busy(false);
    }
  };

  signIn = async (): Promise<void> => {
    this.error("");
    if (!this.otp()) {
      this.error("Enter the six-digit OTP.");
      return;
    }
    this.busy(true);
    try {
      const response = await login(this.email(), this.password(), this.otp(), this.loginAsAdmin());
      window.location.assign(response.role === "ADMIN" ? "?ojr=admin" : "?ojr=dashboard");
    } catch (error) {
      this.error(error instanceof Error ? error.message : "Unable to sign in.");
    } finally {
      this.busy(false);
    }
  };
}

export = LoginViewModel;
