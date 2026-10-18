import * as ko from "knockout";
import * as AccUtils from "../accUtils";
import { api } from "../services/api";
import { currentRole } from "../services/auth";

interface Beneficiary { id: number; beneficiaryName: string; beneficiaryAccountNumber: string; ifscCode: string; bankName: string; createdAt: string; }
interface Payment { paymentOrderId: number; referenceNumber: string; paymentType: string; amount: number; status: string; customerName: string; customerEmail: string; sourceAccountNumber: string; beneficiaryName?: string; billerName?: string; requestedAt: string; reversalEligible: boolean; }
interface PaymentPage { totalPayments: number; payments: Payment[]; }
interface Merchant { merchantId: number; merchantCode: string; merchantName: string; merchantCategory: string; rewardEligible: boolean; status: string; }
interface RewardPurchase { merchantPurchaseId: number; referenceNumber: string; merchantName: string; customerName: string; purchaseAmount: number; status: string; purchasedAt: string; }
interface RewardPurchasePage { totalPurchases: number; purchases: RewardPurchase[]; }
interface AuditEvent { auditEventId: number; action: string; entityType: string; entityId: string; reason?: string; createdAt: string; }

interface ForexRate { currencyCode: string; baseRateInr: number; currentRateInr: number; markupBps: number; status: string; referenceSource: string; referenceAsOf: string; updatedAt: string; }
interface ForexRateHistory { rateHistoryId: number; currencyCode: string; previousRateInr: number; newRateInr: number; changeReason: string; referenceSource: string; createdAt: string; }
interface ComplianceFlag { complianceFlagId: number; forexTransactionId: number; ruleCode: string; reason: string; status: string; createdAt: string; reviewedBy?: number; reviewedAt?: string; }
class AdminViewModel {
  adminSections = [
    { id: "beneficiaries", label: "Pending beneficiary approval", description: "Review beneficiaries awaiting approval.", icon: "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8M16 9l2 2 4-4" },
    { id: "accounts", label: "Account control", description: "Freeze or unfreeze customer accounts.", icon: "M3 10h18M5 10v9m4-9v9m6-9v9m4-9v9M3 19h18M12 3l9 5H3z" },
    { id: "audit", label: "Audit and notification", description: "Review the existing audit action history.", icon: "M6 3h12v18H6zM9 7h6M9 11h6M9 15h4" },
    { id: "payments", label: "Payment operations", description: "Filter payments and manage reversals.", icon: "M3 7h12M11 3l4 4-4 4M21 17H9M13 13l-4 4 4 4" },
    { id: "merchants", label: "Reward and merchant control", description: "Manage merchant status and reward eligibility.", icon: "M3 10h18M4 10v11h16V10M3 10l2-7h14l2 7M9 21v-7h6v7" },
    { id: "purchases", label: "Recent merchant purchases", description: "Review merchant purchases and reversals.", icon: "M4 5h16v14H4zM4 10h16M7 15h4" },
    { id: "investments", label: "Investment operations", description: "Settle investment orders and monitor customer schedules.", icon: "M4 19V5m0 14h16M8 15v-4m4 4V7m4 8v-6" },
    { id: "forex", label: "Forex operations", description: "Manage rates, KYC and compliance.", icon: "M3 12h18M12 3c4 4 4 14 0 18M12 3c-4 4-4 14 0 18M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0" }
  ];
  selectedAdminSection = ko.observable("");
  adminMenuOpen = ko.observable(false);
  toggleAdminMenu = (): void => { this.adminMenuOpen(!this.adminMenuOpen()); };
  adminMenuKeydown = (_data: unknown, event: KeyboardEvent): boolean => {
    if (event.key === "Escape") {
      this.adminMenuOpen(false);
      document.getElementById("admin-navigation-toggle")?.focus();
    }
    return true;
  };
  adminSectionLink = (id: string): string =>
    this.adminSections.some((section) => section.id === id) ? "?ojr=admin-" + id : "?ojr=admin";
  pendingBeneficiaries = ko.observableArray<Beneficiary>([]);
  payments = ko.observableArray<Payment>([]);
  merchants = ko.observableArray<Merchant>([]);
  rewardPurchases = ko.observableArray<RewardPurchase>([]);
  auditEvents = ko.observableArray<AuditEvent>([]);
  totalPayments = ko.observable(0);
  totalRewardPurchases = ko.observable(0);
  paymentStatus = ko.observable("ALL");
  paymentType = ko.observable("ALL");
  paymentsLoading = ko.observable(false);
  paymentsError = ko.observable("");
  private paymentRequestId = 0;
  private filterSubscriptions: ko.Subscription[] = [];
  accountId = ko.observable<number | undefined>();
  accountReason = ko.observable("");
  busy = ko.observable(false);
  error = ko.observable("");
  notice = ko.observable("");
  forexRates = ko.observableArray<ForexRate>([]);
  forexRateHistory = ko.observableArray<ForexRateHistory>([]);
  forexRateHistoryVisible = ko.observable(false);
  complianceFlags = ko.observableArray<ComplianceFlag>([]);
  forexRateCurrency = ko.observable("");
  forexBaseRateInr = ko.observable("");
  forexMarkupBps = ko.observable("");
  forexRateActive = ko.observable(true);
  complianceStatus = ko.observable("OPEN");
  kycUserId = ko.observable<number | string>("");
  kycStatus = ko.observable("APPROVED");
  forexBusy = ko.observable(false);
  activeMerchants = ko.pureComputed(() => this.merchants().filter((merchant) => merchant.status === "ACTIVE").length);

  connected(): void {
    const section = this.adminSections.find((item) => item.id === this.selectedAdminSection());
    const title = section ? section.label : "Administration";
    AccUtils.announce(title + " page loaded.");
    document.title = title + " | O-Bank";
    if (currentRole() !== "ADMIN") {
      this.error("Administrator access is required. Sign in with an active administrator account.");
      return;
    }
    this.filterSubscriptions.forEach((subscription) => subscription.dispose());
    this.filterSubscriptions = [
      this.paymentStatus.subscribe(() => { void this.loadPayments(); }),
      this.paymentType.subscribe(() => { void this.loadPayments(); })
    ];
    void this.loadDashboard();
  }

  disconnected(): void {
    this.filterSubscriptions.forEach((subscription) => subscription.dispose());
    this.filterSubscriptions = [];
    this.paymentRequestId++;
  }

  loadDashboard = async (): Promise<void> => {
    this.error(""); this.notice(""); this.busy(true);
    try {
      await Promise.all([this.loadPendingBeneficiaries(), this.loadPayments(), this.loadMerchants(), this.loadRewardPurchases(), this.loadAuditEvents(), this.loadForexAdmin()]);
    } catch (error) {
      this.error(this.messageFrom(error, "Unable to load the administration dashboard."));
    } finally { this.busy(false); }
  };

  loadPendingBeneficiaries = async (): Promise<void> => {
    this.pendingBeneficiaries(await api.get<Beneficiary[]>("/api/admin/beneficiaries/pending"));
  };

  loadPayments = async (): Promise<void> => {
    const requestId = ++this.paymentRequestId;
    const params = new URLSearchParams({ status: this.paymentStatus(), type: this.paymentType(), page: "0", size: "12" });
    this.paymentsLoading(true);
    this.paymentsError("");
    this.payments([]);
    this.totalPayments(0);
    try {
      const result = await api.get<PaymentPage>(`/api/admin/payments?${params.toString()}`);
      // A slower response for an earlier selection must not replace the latest filter.
      if (requestId !== this.paymentRequestId) return;
      this.payments(result.payments ?? []);
      this.totalPayments(result.totalPayments ?? 0);
    } catch (error) {
      if (requestId === this.paymentRequestId) {
        this.paymentsError(this.messageFrom(error, "Unable to load payments. Please try again."));
      }
    } finally {
      if (requestId === this.paymentRequestId) this.paymentsLoading(false);
    }
  };

  loadMerchants = async (): Promise<void> => { this.merchants(await api.get<Merchant[]>("/api/admin/reward-merchants")); };

  loadRewardPurchases = async (): Promise<void> => {
    const result = await api.get<RewardPurchasePage>("/api/admin/credit-card-rewards/purchases?status=ALL&page=0&size=8");
    this.rewardPurchases(result.purchases ?? []); this.totalRewardPurchases(result.totalPurchases ?? 0);
  };

  loadAuditEvents = async (): Promise<void> => { this.auditEvents(await api.get<AuditEvent[]>("/api/admin/audit-events")); };

  approveBeneficiary = async (beneficiary: Beneficiary): Promise<void> => {
    this.error(""); this.notice("");
    try {
      const result = await api.put<{ message?: string }>(`/api/admin/beneficiaries/${beneficiary.id}/approve`);
      this.notice(result.message ?? `${beneficiary.beneficiaryName} was approved.`);
      await Promise.all([this.loadPendingBeneficiaries(), this.loadAuditEvents()]);
    } catch (error) { this.error(this.messageFrom(error, "The beneficiary could not be approved.")); }
  };

  reversePayment = async (payment: Payment): Promise<void> => {
    const reason = window.prompt(`Reason for reversing ${payment.referenceNumber}:`);
    if (reason === null) return;
    if (reason.trim().length < 5) { this.error("Enter a reversal reason of at least 5 characters."); return; }
    this.error(""); this.notice("");
    try {
      await api.put(`/api/admin/payments/${payment.paymentOrderId}/reverse`, { reason: reason.trim() });
      this.notice(`Payment ${payment.referenceNumber} was reversed.`);
      await Promise.all([this.loadPayments(), this.loadAuditEvents()]);
    } catch (error) { this.error(this.messageFrom(error, "The payment could not be reversed.")); }
  };

  changeAccountStatus = async (status: "freeze" | "unfreeze"): Promise<void> => {
    const accountId = this.accountId();
    if (!accountId || !Number.isInteger(Number(accountId)) || Number(accountId) < 1) { this.error("Enter a valid account ID."); return; }
    if (this.accountReason().trim().length < 5) { this.error("Enter a reason of at least 5 characters."); return; }
    this.error(""); this.notice("");
    try {
      const result = await api.patch<{ message?: string }>(`/api/admin/accounts/${accountId}/${status}`, { reason: this.accountReason().trim() });
      this.notice(result.message ?? `Account ${accountId} was ${status}d.`); this.accountReason("");
      await this.loadAuditEvents();
    } catch (error) { this.error(this.messageFrom(error, `The account could not be ${status}d.`)); }
  };

  toggleMerchantStatus = async (merchant: Merchant): Promise<void> => {
    const status = merchant.status === "ACTIVE" ? "INACTIVE" : "ACTIVE";
    this.error(""); this.notice("");
    try {
      await api.patch(`/api/admin/reward-merchants/${merchant.merchantId}/status`, { status });
      this.notice(`${merchant.merchantName} is now ${status.toLowerCase()}.`); await this.loadMerchants();
    } catch (error) { this.error(this.messageFrom(error, "The merchant status could not be updated.")); }
  };

  toggleMerchantEligibility = async (merchant: Merchant): Promise<void> => {
    this.error(""); this.notice("");
    try {
      await api.patch(`/api/admin/reward-merchants/${merchant.merchantId}/reward-eligibility`, { rewardEligible: !merchant.rewardEligible });
      this.notice(`Reward eligibility was updated for ${merchant.merchantName}.`); await this.loadMerchants();
    } catch (error) { this.error(this.messageFrom(error, "Reward eligibility could not be updated.")); }
  };

  reverseRewardPurchase = async (purchase: RewardPurchase): Promise<void> => {
    const reason = window.prompt(`Reason for reversing ${purchase.referenceNumber}:`);
    if (reason === null) return;
    if (reason.trim().length < 5) { this.error("Enter a reversal reason of at least 5 characters."); return; }
    this.error(""); this.notice("");
    try {
      await api.post(`/api/admin/credit-card-rewards/purchases/${purchase.merchantPurchaseId}/reverse`, { reason: reason.trim() });
      this.notice(`Reward purchase ${purchase.referenceNumber} was reversed.`);
      await Promise.all([this.loadRewardPurchases(), this.loadAuditEvents()]);
    } catch (error) { this.error(this.messageFrom(error, "The reward purchase could not be reversed.")); }
  };

  loadForexAdmin = async (): Promise<void> => {
    this.forexBusy(true);
    try {
      const [rates, flags] = await Promise.all([
        api.get<ForexRate[]>("/api/admin/forex/rates"),
        api.get<ComplianceFlag[]>("/api/admin/forex/compliance-flags?status=" + this.complianceStatus())
      ]);
      this.forexRates(rates); this.complianceFlags(flags);
      if (!this.forexRateCurrency() && rates.length) this.selectForexRate(rates[0]);
    } catch (error) { this.error(this.messageFrom(error, "Unable to load Forex administration data.")); }
    finally { this.forexBusy(false); }
  };

  selectForexRate = (rate: ForexRate): void => {
    this.forexRateCurrency(rate.currencyCode);
    this.forexBaseRateInr(String(rate.baseRateInr));
    this.forexMarkupBps(String(rate.markupBps)); this.forexRateActive(rate.status === "ACTIVE");
    this.forexRateHistory([]);
    this.forexRateHistoryVisible(false);
  };

  updateForexRate = async (): Promise<void> => {
    const currency = this.forexRateCurrency(); const baseRate = Number(this.forexBaseRateInr()); const markup = Number(this.forexMarkupBps());
    if (!currency || !this.forexBaseRateInr().trim() || !Number.isFinite(baseRate) || baseRate < 0.000001 || !Number.isInteger(markup) || markup < 0 || markup > 1000) {
      this.error("Choose a Forex currency, a positive base INR rate, and a markup from 0 to 1000 basis points."); return;
    }
    this.forexBusy(true); this.error(""); this.notice("");
    try {
      await api.put("/api/admin/forex/rates/" + currency, { baseRateInr: baseRate, markupBps: markup, active: this.forexRateActive() });
      this.notice(currency + " Forex settings were updated. Market rates continue to update automatically."); await this.loadForexAdmin();
      this.forexRateHistory([]); this.forexRateHistoryVisible(false);
    } catch (error) { this.error(this.messageFrom(error, "The Forex rate could not be updated.")); }
    finally { this.forexBusy(false); }
  };

  loadForexRateHistory = async (currency = this.forexRateCurrency()): Promise<void> => {
    if (!currency) { this.forexRateHistory([]); return; }
    try { this.forexRateHistory(await api.get<ForexRateHistory[]>("/api/admin/forex/rates/" + currency + "/history")); }
    catch (error) { this.error(this.messageFrom(error, "Forex rate history could not be loaded.")); }
  };

  toggleForexRateHistory = async (): Promise<void> => {
    if (this.forexRateHistoryVisible()) {
      this.forexRateHistoryVisible(false);
      return;
    }
    this.error("");
    await this.loadForexRateHistory();
    if (!this.error()) this.forexRateHistoryVisible(true);
  };

  loadComplianceFlags = async (): Promise<void> => {
    try { this.complianceFlags(await api.get<ComplianceFlag[]>("/api/admin/forex/compliance-flags?status=" + this.complianceStatus())); }
    catch (error) { this.error(this.messageFrom(error, "Compliance flags could not be loaded.")); }
  };

  reviewCompliance = async (flag: ComplianceFlag, status: "REVIEWED" | "CLEARED"): Promise<void> => {
    this.error(""); this.notice("");
    try { await api.put("/api/admin/forex/compliance-flags/" + flag.complianceFlagId, { status }); this.notice("Compliance flag was marked " + status.toLowerCase() + "."); await this.loadComplianceFlags(); }
    catch (error) { this.error(this.messageFrom(error, "The compliance flag could not be updated.")); }
  };

  updateForexKyc = async (): Promise<void> => {
    const userId = Number(this.kycUserId());
    if (!Number.isInteger(userId) || userId < 1) { this.error("Enter a valid customer user ID."); return; }
    this.error(""); this.notice("");
    try { await api.put("/api/admin/forex/users/" + userId + "/kyc", { status: this.kycStatus() }); this.notice("Forex KYC for user " + userId + " is now " + this.kycStatus().toLowerCase() + "."); }
    catch (error) { this.error(this.messageFrom(error, "Forex KYC could not be updated.")); }
  };

  formatCurrency = (amount: number): string => new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", minimumFractionDigits: 2 }).format(Number(amount ?? 0));
  formatDate = (value: string): string => value ? new Date(value).toLocaleString("en-IN") : "—";
  paymentRecipient = (payment: Payment): string => payment.beneficiaryName || payment.billerName || "—";
  private messageFrom(error: unknown, fallback: string): string { return error instanceof Error && error.message ? error.message : fallback; }
}

export = AdminViewModel;
