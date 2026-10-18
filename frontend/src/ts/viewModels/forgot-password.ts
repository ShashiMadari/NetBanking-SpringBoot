import * as ko from "knockout";
import * as AccUtils from "../accUtils";
import { resetPassword, sendPasswordResetOtp } from "../services/auth";

class ForgotPasswordViewModel {
  email = ko.observable("");
  otp = ko.observable("");
  newPassword = ko.observable("");
  confirmPassword = ko.observable("");
  otpSent = ko.observable(false);
  busy = ko.observable(false);
  error = ko.observable("");
  notice = ko.observable("");

  connected(): void {
    AccUtils.announce("Password recovery page loaded.");
    document.title = "Reset password | O-Bank";
  }

  sendOtp = async (): Promise<void> => {
    this.error(""); this.notice("");
    if (!this.email()) { this.error("Enter your registered email address."); return; }
    this.busy(true);
    try {
      const result = await sendPasswordResetOtp(this.email());
      this.otpSent(true);
      this.notice(result.message || "If the email is registered, a reset OTP was sent.");
    } catch (error) {
      this.error(error instanceof Error ? error.message : "Unable to send the reset OTP.");
    } finally { this.busy(false); }
  };

  reset = async (): Promise<void> => {
    this.error(""); this.notice("");
    if (!this.otp() || !this.newPassword() || !this.confirmPassword()) {
      this.error("Enter the OTP and your new password."); return;
    }
    this.busy(true);
    try {
      await resetPassword(this.email(), this.otp(), this.newPassword(), this.confirmPassword());
      window.location.assign("?ojr=login&passwordReset=true");
    } catch (error) {
      this.error(error instanceof Error ? error.message : "Unable to reset the password.");
    } finally { this.busy(false); }
  };
}

export = ForgotPasswordViewModel;
