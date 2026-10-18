import * as ko from "knockout";
import * as AccUtils from "../accUtils";
import { api, ApiError } from "../services/api";

interface Beneficiary { id: number; beneficiaryName: string; beneficiaryAccountNumber: string; ifscCode: string; bankName?: string | null; status: string; }

class BeneficiariesViewModel {
  beneficiaries = ko.observableArray<Beneficiary>([]);
  loading = ko.observable(true);
  error = ko.observable("");
  notice = ko.observable("");
  showAddBeneficiary = ko.observable(false);
  addingBeneficiary = ko.observable(false);
  beneficiaryName = ko.observable("");
  beneficiaryAccountNumber = ko.observable("");
  beneficiaryIfscCode = ko.observable("");
  beneficiaryBankName = ko.observable("");

  async connected(): Promise<void> {
    AccUtils.announce("Beneficiaries page loaded.");
    document.title = "Beneficiaries | O-Bank";
    try { this.beneficiaries(await api.get<Beneficiary[]>("/api/beneficiaries")); }
    catch (error) { this.error(this.message(error)); }
    finally {
      this.loading(false);
      if (new URLSearchParams(window.location.search).get("add") === "true") {
        this.openAddBeneficiary();
      }
    }
  }

  openAddBeneficiary = (): void => { this.error(""); this.notice(""); this.showAddBeneficiary(true); };
  cancelAddBeneficiary = (): void => {
    this.showAddBeneficiary(false); this.beneficiaryName(""); this.beneficiaryAccountNumber(""); this.beneficiaryIfscCode(""); this.beneficiaryBankName("");
  };
  addBeneficiary = async (): Promise<void> => {
    this.error(""); this.notice("");
    const beneficiaryName = this.beneficiaryName().trim();
    const beneficiaryAccountNumber = this.beneficiaryAccountNumber().trim();
    const ifscCode = this.beneficiaryIfscCode().trim().toUpperCase();
    const bankName = this.beneficiaryBankName().trim();

    if (!beneficiaryName || !beneficiaryAccountNumber || !ifscCode) {
      this.error("Enter the beneficiary name, account number, and IFSC code."); return;
    }
    if (beneficiaryAccountNumber.length < 9 || beneficiaryAccountNumber.length > 34 || /\D/.test(beneficiaryAccountNumber)) {
      this.error("Enter an account number containing 9 to 34 digits."); return;
    }
    if (ifscCode.length !== 11 || !/^[A-Z]{4}0[A-Z0-9]{6}/.test(ifscCode)) {
      this.error("Enter a valid 11-character IFSC code, for example HDFC0001234."); return;
    }
    this.addingBeneficiary(true);
    try {
      const beneficiary = await api.post<Beneficiary>("/api/beneficiaries", {
        beneficiaryName, beneficiaryAccountNumber, ifscCode, bankName: bankName || null
      });
      this.beneficiaries.unshift(beneficiary); this.cancelAddBeneficiary();
      this.notice("Beneficiary added. It will be available for transfers after approval.");
    } catch (error) { this.error(this.message(error)); }
    finally { this.addingBeneficiary(false); }
  };
  maskedAccountNumber = (accountNumber: string): string => "•••• " + accountNumber.slice(-4);
  private message(error: unknown): string {
    if (error instanceof ApiError && error.status === 500) {
      return "We could not save this beneficiary right now. Please try again shortly.";
    }
    return error instanceof Error ? error.message : "The Banking service could not complete this request.";
  }
}

export = BeneficiariesViewModel;
