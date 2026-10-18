import * as ko from "knockout";
import * as AccUtils from "../accUtils";
import { api } from "../services/api";

interface Account { accountId: number; accountNumber: string; accountType: string; availableBalance: number; currency: string; }
interface Beneficiary {
  id: number;
  beneficiaryName: string;
  beneficiaryAccountNumber: string;
  ifscCode: string;
  bankName?: string | null;
  status: string;
}
class BankingViewModel {
  accounts = ko.observableArray<Account>([]);
  beneficiaries = ko.observableArray<Beneficiary>([]);
  loading = ko.observable(true);
  error = ko.observable("");
  notice = ko.observable("");
  paymentMode = ko.observable(false);
  sourceAccountId = ko.observable("");
  beneficiaryId = ko.observable("");
  selectedBeneficiary = ko.observable<Beneficiary | null>(null);
  transferAmount = ko.observable("");
  paymentOrderId = ko.observable("");
  otp = ko.observable("");
  otpSent = ko.observable(false);
  confirmationSubmitted = ko.observable(false);
  confirmingTransfer = ko.observable(false);
  resendingOtp = ko.observable(false);
  showAddBeneficiary = ko.observable(false);
  addingBeneficiary = ko.observable(false);
  beneficiaryName = ko.observable("");
  beneficiaryAccountNumber = ko.observable("");
  beneficiaryIfscCode = ko.observable("");
  beneficiaryBankName = ko.observable("");
  accountLabel = (account: Account): string => `${account.accountType} · •••• ${account.accountNumber.slice(-4)}`;
  beneficiaryLabel = (beneficiary: Beneficiary): string => `${beneficiary.beneficiaryName} · •••• ${beneficiary.beneficiaryAccountNumber.slice(-4)}`;
  sourceAccount = ko.pureComputed(() => this.accounts().find((account) => String(account.accountId) === String(this.sourceAccountId())) ?? null);
  savedBeneficiaries = ko.pureComputed(() => this.beneficiaries().filter((beneficiary) => beneficiary.status === "ACTIVE"));
  amountExceedsBalance = ko.pureComputed(() => {
    const source = this.sourceAccount();
    const enteredAmount = this.transferAmount().trim();
    const amount = Number(enteredAmount);
    return enteredAmount.length > 0 && source !== null && Number.isFinite(amount) && amount > Number(source.availableBalance);
  });
  showOtpConfirmation = ko.pureComputed(() => this.otpSent() && this.paymentOrderId().trim().length > 0);

  constructor() {
    this.beneficiaryId.subscribe((id) => {
      this.selectedBeneficiary(this.savedBeneficiaries().find((beneficiary) => String(beneficiary.id) === String(id)) ?? null);
      this.paymentOrderId(""); this.otp(""); this.otpSent(false); this.setOtpAreaVisible(false); this.confirmationSubmitted(false); this.confirmingTransfer(false); this.resendingOtp(false);
    });
  }

  async connected(): Promise<void> {
    AccUtils.announce("Banking services page loaded.");
    document.title = "Banking | O-Bank";
    this.paymentMode(true);
    this.paymentOrderId(""); this.otp(""); this.otpSent(false); this.setOtpAreaVisible(false); this.confirmationSubmitted(false); this.confirmingTransfer(false); this.resendingOtp(false); this.transferAmount(""); this.beneficiaryId(""); this.selectedBeneficiary(null);
    try {
      const [accounts, beneficiaries] = await Promise.all([
        api.get<Account[]>("/api/accounts/summary"),
        api.get<Beneficiary[]>("/api/beneficiaries")
      ]);
      this.accounts(accounts);
      this.beneficiaries(beneficiaries);
      if (accounts.length > 0) this.sourceAccountId(String(accounts[0].accountId));
    } catch (error) {
      this.error(this.message(error));
    } finally { this.loading(false); }
  }

  initiateTransfer = async (): Promise<void> => {
    this.error(""); this.notice("");
    const source = this.sourceAccount();
    const amount = Number(this.transferAmount());
    if (!source) {
      this.error("No active source account is available for this payment.");
      return;
    }
    if (!this.selectedBeneficiary() || !this.beneficiaryId()) {
      this.error("Choose an active beneficiary before entering a payment.");
      return;
    }
    if (!Number.isFinite(amount) || amount < 1) {
      this.error("Enter an amount of at least INR 1.00.");
      return;
    }
    if (amount > Number(source.availableBalance)) {
      this.error("The payment amount is greater than the available account balance.");
      return;
    }
    try {
      const result = await api.post<{ paymentOrderId: number }>("/api/transfers/initiate", {
        sourceAccountId: Number(this.sourceAccountId()),
        beneficiaryId: Number(this.beneficiaryId()),
        amount: Number(this.transferAmount())
      });
      this.paymentOrderId(String(result.paymentOrderId));
      this.otpSent(true);
      this.setOtpAreaVisible(true);
      this.confirmationSubmitted(false);
      this.confirmingTransfer(false); this.resendingOtp(false);
      this.notice("OTP sent. Enter the six-digit code below to confirm payment order " + result.paymentOrderId + ".");
      window.setTimeout(() => this.focusOtpInput(), 0);
    } catch (error) { this.error(this.message(error)); }
  };

  resendTransferOtp = async (): Promise<void> => {
    const paymentOrderId = Number(this.paymentOrderId());
    if (!paymentOrderId || this.resendingOtp()) return;

    this.error("");
    this.notice("");
    this.resendingOtp(true);
    try {
      await api.post("/api/transfers/" + paymentOrderId + "/resend-otp", {});
      this.otp("");
      this.confirmationSubmitted(false);
      this.notice("A new OTP was sent. The previous code can no longer be used.");
      window.setTimeout(() => this.focusOtpInput(), 0);
    } catch (error) {
      this.error(this.message(error));
    } finally {
      this.resendingOtp(false);
    }
  };

  chooseBeneficiary = (beneficiary: Beneficiary): void => {
    this.error("");
    this.notice("");
    if (beneficiary.status !== "ACTIVE") {
      this.error("Only an active beneficiary can receive a payment.");
      return;
    }
    this.selectedBeneficiary(beneficiary);
    this.beneficiaryId(String(beneficiary.id));
    this.paymentOrderId("");
    this.otp("");
    this.confirmationSubmitted(false);
    this.confirmingTransfer(false); this.resendingOtp(false);
  };

  confirmTransfer = async (): Promise<void> => {
    if (this.confirmationSubmitted()) return;

    this.error(""); this.notice("");
    this.confirmationSubmitted(true);
    this.confirmingTransfer(true);
    try {
      await api.post(`/api/transfers/${Number(this.paymentOrderId())}/confirm`, { otp: this.otp() });
      this.notice("Transfer completed successfully.");
      window.dispatchEvent(new Event("o-bank:notifications-changed"));
      this.paymentOrderId("");
      this.otp("");
      this.otpSent(false);
      this.setOtpAreaVisible(false);
      this.confirmationSubmitted(false);
      this.transferAmount("");
      this.beneficiaryId("");
      this.selectedBeneficiary(null);
      window.setTimeout(() => document.querySelector(".transfer-panel")?.scrollIntoView({ behavior: "smooth", block: "start" }), 0);
    } catch (error) {
      this.error("We could not confirm the final status. Do not submit this payment again; check Transaction history or your email before starting another payment.");
    } finally {
      this.confirmingTransfer(false); this.resendingOtp(false);
    }
  };

  openAddBeneficiary = (): void => {
    window.location.assign("?ojr=beneficiaries&add=true");
  };

  cancelAddBeneficiary = (): void => {
    this.showAddBeneficiary(false);
    this.beneficiaryName("");
    this.beneficiaryAccountNumber("");
    this.beneficiaryIfscCode("");
    this.beneficiaryBankName("");
  };

  addBeneficiary = async (): Promise<void> => {
    this.error("");
    this.notice("");
    if (!this.beneficiaryName().trim() || !this.beneficiaryAccountNumber().trim() || !this.beneficiaryIfscCode().trim()) {
      this.error("Enter the beneficiary name, account number, and IFSC code.");
      return;
    }
    this.addingBeneficiary(true);
    try {
      const beneficiary = await api.post<Beneficiary>("/api/beneficiaries", {
        beneficiaryName: this.beneficiaryName().trim(),
        beneficiaryAccountNumber: this.beneficiaryAccountNumber().trim(),
        ifscCode: this.beneficiaryIfscCode().trim(),
        bankName: this.beneficiaryBankName().trim() || null
      });
      this.beneficiaries.unshift(beneficiary);
      this.cancelAddBeneficiary();
      this.notice("Beneficiary added. It will be available for transfers after admin approval.");
    } catch (error) {
      this.error(this.message(error));
    } finally {
      this.addingBeneficiary(false);
    }
  };

  private focusOtpInput(): void {
    const otpInput = document.querySelector<HTMLInputElement>(".transfer-otp-input");
    otpInput?.closest(".payment-confirmation")?.scrollIntoView({ behavior: "smooth", block: "center" });
    otpInput?.focus();
  }

  private message(error: unknown): string {
    return error instanceof Error ? error.message : "The Banking service could not complete this request.";
  }

  private setOtpAreaVisible(visible: boolean): void {
    document.querySelector(".transfer-panel .payment-confirmation")?.classList.toggle("otp-ready", visible);
  }
}

export = BankingViewModel;
