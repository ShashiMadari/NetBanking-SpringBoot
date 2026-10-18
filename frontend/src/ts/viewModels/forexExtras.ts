import * as ko from "knockout";
import { api, ApiError } from "../services/api";

interface Wallet { walletId: number; currencyCode: string; availableBalance: number; }
interface Rate { currencyCode: string; status: string; }
interface Card { forexCardId: number; walletId: number; cardNumberMasked: string; status: string; spendLimit: number; availableBalance: number; issuedAt: string; }
interface Beneficiary { forexBeneficiaryId: number; beneficiaryName: string; countryCode: string; currencyCode: string; swiftBic: string; maskedAccountNumber: string; status: string; }
interface Result { status: string; referenceNumber: string; fromCurrency: string; fromAmount: number; }
interface Review { path: string; method: "POST" | "PUT"; body: Record<string, string | number>; summary: string; money: boolean; }
interface Remittance { forexBeneficiaryId: number; amount: number; purpose: string; idempotencyKey: string; }
interface Transfer { request: Remittance; summary: string; }

class ForexExtras {
  cards = ko.observableArray<Card>([]);
  wallets = ko.observableArray<Wallet>([]);
  beneficiaries = ko.observableArray<Beneficiary>([]);
  rates = ko.observableArray<Rate>([]);
  loading = ko.observable(false);
  busy = ko.observable(false);
  dataError = ko.observable("");
  error = ko.observable("");
  notice = ko.observable("");
  receipt = ko.observable<Result | null>(null);
  review = ko.observable<Review | null>(null);
  uncertain = ko.observable("");
  reviewedRecords = ko.observable(false);
  pendingTransfer = ko.observable<Transfer | null>(null);
  transferReview = ko.observable<Transfer | null>(null);

  issueCurrency = ko.observable("");
  issueLimit = ko.observable("");
  cardId = ko.observable<number | string>("");
  action = ko.observable("LOAD");
  cardAmount = ko.observable("");
  cardStatus = ko.observable("BLOCKED");
  merchant = ko.observable("");

  beneficiaryName = ko.observable("");
  country = ko.observable("");
  beneficiaryCurrency = ko.observable("");
  bic = ko.observable("");
  beneficiaryAccount = ko.observable("");
  beneficiaryId = ko.observable<number | string>("");
  transferAmount = ko.observable("");
  purpose = ko.observable("");

  selectedCard = ko.pureComputed(() => this.cards().find(c => String(c.forexCardId) === String(this.cardId())) || null);
  selectedBeneficiary = ko.pureComputed(() => this.beneficiaries().find(b => String(b.forexBeneficiaryId) === String(this.beneficiaryId())) || null);
  activeBeneficiaries = ko.pureComputed(() => this.beneficiaries().filter(b => b.status === "ACTIVE"));
  issueCurrencies = ko.pureComputed(() => this.wallets().filter(w =>
    w.currencyCode !== "INR" && this.rates().some(r => r.currencyCode === w.currencyCode && r.status === "ACTIVE")));
  blocked = ko.pureComputed(() => this.busy() || this.loading() || !!this.uncertain() ||
    !!this.pendingTransfer() || !!this.review() || !!this.transferReview() || this.externalBusy());

  private active = false;
  private version = 0;
  private key = "";
  private transferSelectionKey = "";
  constructor(private onChange: () => Promise<void>, private externalBusy: () => boolean) {}

  async connected(): Promise<void> {
    this.active = true;
    this.key = "o-bank.forex.extra." + (sessionStorage.getItem("o-bank.email") || "customer");
    this.transferSelectionKey = this.key + ".selected-beneficiary";
    try {
      const pending = sessionStorage.getItem(this.key + ".transfer");
      if (pending) {
        const saved = JSON.parse(pending) as Transfer;
        if (saved.request && Number.isInteger(saved.request.forexBeneficiaryId) && saved.request.forexBeneficiaryId > 0 &&
            Number.isFinite(saved.request.amount) && saved.request.amount > 0 &&
            typeof saved.request.purpose === "string" && saved.request.purpose.length <= 200 &&
            /^[a-f0-9]{32}$/.test(saved.request.idempotencyKey)) this.pendingTransfer(saved);
      }
      this.uncertain(sessionStorage.getItem(this.key + ".uncertain") || "");
    } catch { this.error("A saved Forex request could not be read."); }
    await this.refresh();
  }

  refresh = async (): Promise<void> => {
    const version = ++this.version;
    this.loading(true); this.dataError("");
    const results = await Promise.allSettled([
      api.get<Card[]>("/api/forex/cards"), api.get<Wallet[]>("/api/forex/wallets"),
      api.get<Beneficiary[]>("/api/forex/beneficiaries"), api.get<Rate[]>("/api/forex/rates")
    ]);
    if (!this.active || version !== this.version) return;
    const errors: string[] = [];
    const [cards, wallets, beneficiaries, rates] = results;
    if (cards.status === "fulfilled") this.cards(cards.value);
    else { this.cards([]); errors.push("Cards: " + this.message(cards.reason)); }
    if (wallets.status === "fulfilled") this.wallets(wallets.value);
    else { this.wallets([]); errors.push("Wallets: " + this.message(wallets.reason)); }
    if (beneficiaries.status === "fulfilled") this.beneficiaries(beneficiaries.value);
    else { this.beneficiaries([]); errors.push("Beneficiaries: " + this.message(beneficiaries.reason)); }
    if (rates.status === "fulfilled") this.rates(rates.value.filter(r => r.status === "ACTIVE" && r.currencyCode !== "INR"));
    else { this.rates([]); errors.push("Currencies: " + this.message(rates.reason)); }
    if (!this.selectedCard()) this.cardId(this.cards()[0]?.forexCardId || "");
    if (!this.issueCurrencies().some(w => w.currencyCode === this.issueCurrency())) this.issueCurrency(this.issueCurrencies()[0]?.currencyCode || "");
    if (!this.rates().some(r => r.currencyCode === this.beneficiaryCurrency())) this.beneficiaryCurrency(this.rates()[0]?.currencyCode || "");
    let savedBeneficiaryId = "";
    try { savedBeneficiaryId = sessionStorage.getItem(this.transferSelectionKey) || ""; } catch { /* Selection is optional. */ }
    if (this.activeBeneficiaries().some(b => String(b.forexBeneficiaryId) === savedBeneficiaryId)) this.beneficiaryId(savedBeneficiaryId);
    else if (!this.activeBeneficiaries().some(b => String(b.forexBeneficiaryId) === String(this.beneficiaryId()))) this.beneficiaryId(this.activeBeneficiaries()[0]?.forexBeneficiaryId || "");
    this.dataError(errors.join(" ")); this.loading(false);
  };

  reviewIssue = (): void => {
    if (this.blocked()) return;
    this.error(""); this.notice("");
    const limit = this.amount(this.issueLimit());
    if (limit === null || limit < 1 || !this.issueCurrencies().some(w => w.currencyCode === this.issueCurrency())) {
      this.error("Choose an existing currency wallet and a single-purchase limit of at least 1."); return;
    }
    this.review({ path: "/api/forex/cards/issue", method: "POST", money: false,
      body: { currencyCode: this.issueCurrency(), spendLimit: limit },
      summary: "Issue a " + this.issueCurrency() + " Forex card with a single-purchase limit of " + this.money(limit, this.issueCurrency()) + ". The card starts with no loaded balance." });
  };

  selectCard = (card: Card): void => {
    if (this.blocked()) return;
    this.cardId(card.forexCardId);
    this.action("LOAD");
    this.cardAmount("");
    this.merchant("");
    this.error("");
    this.notice("Card selected. Enter an amount below to load it from the matching Forex wallet.");
    document.getElementById("forex-card-management")?.scrollIntoView({
      behavior: "smooth",
      block: "center"
    });
  };

  reviewCard = (): void => {
    if (this.blocked()) return;
    this.error(""); this.notice("");
    const card = this.selectedCard();
    if (!card) { this.error("Choose a Forex card."); return; }
    if (card.status === "CLOSED") { this.error("A closed card cannot be changed from this page."); return; }
    const currency = this.cardCurrency(card);
    const prefix = "/api/forex/cards/" + card.forexCardId;
    const value = this.amount(this.cardAmount());
    if (this.action() === "STATUS") {
      const status = this.cardStatus();
      if (!["ACTIVE", "BLOCKED", "CLOSED"].includes(status) || status === card.status) {
        this.error("Choose a different valid card status."); return;
      }
      if (status === "CLOSED" && Number(card.availableBalance) > 0) {
        this.error("This card has a loaded balance. The backend has no unload/refund endpoint; do not close it until the balance is resolved."); return;
      }
      this.review({ path: prefix + "/status", method: "PUT", body: { status }, money: false,
        summary: "Change " + card.cardNumberMasked + " to " + status + "." + (status === "CLOSED" ? " Closure cannot be reversed from this page." : "") }); return;
    }
    if (!currency || value === null) { this.error("Enter a positive amount with up to six decimal places. Wallet currency must be available."); return; }
    if (this.action() === "LIMIT") {
      this.review({ path: prefix + "/spend-limit", method: "PUT", body: { amount: value }, money: false,
        summary: "Set the single-purchase limit for " + card.cardNumberMasked + " to " + this.money(value, currency) + "." }); return;
    }
    if (card.status !== "ACTIVE") { this.error("Activate the card before loading or simulating a purchase."); return; }
    if (this.action() === "LOAD") {
      const wallet = this.wallets().find(w => w.walletId === card.walletId);
      if (!wallet || value > Number(wallet.availableBalance)) { this.error("The matching Forex wallet has insufficient balance."); return; }
      this.review({ path: prefix + "/load", method: "POST", body: { amount: value }, money: true,
        summary: "Move " + this.money(value, currency) + " from your wallet to " + card.cardNumberMasked + ". This reduces your wallet balance." }); return;
    }
    if (this.action() !== "SPEND") return;
    const merchant = this.merchant().trim();
    if (!merchant || merchant.length > 120) { this.error("Enter a merchant name of 1–120 characters."); return; }
    if (value > Number(card.availableBalance) || value > Number(card.spendLimit)) {
      this.error("The purchase exceeds the card's loaded balance or single-purchase limit."); return;
    }
    this.review({ path: prefix + "/simulate-spend", method: "POST", body: { merchantName: merchant, amount: value }, money: true,
      summary: "Simulate a purchase at " + merchant + " for " + this.money(value, currency) + " using " + card.cardNumberMasked + ". This deducts the stored card balance." });
  };

  reviewBeneficiary = (): void => {
    if (this.blocked()) return;
    this.error(""); this.notice("");
    const body = { beneficiaryName: this.beneficiaryName().trim(), countryCode: this.country().trim().toUpperCase(),
      currencyCode: this.beneficiaryCurrency(), swiftBic: this.bic().trim().toUpperCase(), accountNumber: this.beneficiaryAccount().trim() };
    if (!body.beneficiaryName || !/^[A-Z]{2}$/.test(body.countryCode) || !/^[A-Z]{3}$/.test(body.currencyCode) ||
        !/^[A-Z0-9]{8}([A-Z0-9]{3})?$/.test(body.swiftBic) || !/^[A-Za-z0-9-]{6,34}$/.test(body.accountNumber)) {
      this.error("Enter a name, two-letter country code, currency, 8/11-character SWIFT/BIC, and a 6–34-character account number using letters, digits or hyphens."); return;
    }
    this.review({ path: "/api/forex/beneficiaries", method: "POST", body, money: false,
      summary: "Add " + body.beneficiaryName + " in " + body.countryCode + " for " + body.currencyCode + ". Account ending " + body.accountNumber.slice(-4) + "; SWIFT/BIC " + body.swiftBic + "." });
  };

  cancelReview = (): void => { if (!this.busy()) { this.review(null); this.transferReview(null); } };

  confirmReview = async (): Promise<void> => {
    const review = this.review();
    if (!review || this.busy() || this.externalBusy()) return;
    // These POST endpoints have no idempotency field. Never auto-retry them.
    const tracked = review.method === "POST";
    if (tracked && !this.save(".uncertain", review.summary)) return;
    this.busy(true); this.error(""); this.notice("");
    try {
      const result = review.method === "POST"
        ? await api.post<Card | Beneficiary | Result>(review.path, review.body)
        : await api.put<Card>(review.path, review.body);
      this.review(null);
      if (tracked) { this.remove(".uncertain"); this.uncertain(""); }
      if ("referenceNumber" in result) this.receipt(result);
      if ("forexBeneficiaryId" in result) this.beneficiaryAccount("");
      this.notice("The requested action was completed by the backend.");
      if (this.active) await Promise.all([this.refresh(), this.onChange()]);
    } catch (error) {
      this.error(this.message(error)); this.review(null);
      if (tracked) {
        if (error instanceof ApiError && error.status >= 400 && error.status < 500 && ![401, 403, 408, 429].includes(error.status)) this.remove(".uncertain");
        else { this.uncertain(review.summary); this.reviewedRecords(false); }
      }
    } finally { this.busy(false); }
  };

  reconcile = async (): Promise<void> => {
    if (this.busy() || this.loading()) return;
    this.reviewedRecords(false);
    await Promise.all([this.refresh(), this.onChange()]);
    if (this.active && !this.dataError()) this.reviewedRecords(true);
  };
  acknowledgeReview = (): void => {
    if (!this.reviewedRecords() || this.busy()) return;
    this.remove(".uncertain"); this.uncertain(""); this.error(""); this.reviewedRecords(false);
    this.notice("The previous request was acknowledged. Check its result before submitting another action.");
  };

  selectBeneficiaryForTransfer = (beneficiary: Beneficiary): void => {
    if (beneficiary.status !== "ACTIVE") { this.error("Only an active beneficiary can receive an outward remittance."); return; }
    this.beneficiaryId(beneficiary.forexBeneficiaryId);
    try { sessionStorage.setItem(this.transferSelectionKey, String(beneficiary.forexBeneficiaryId)); } catch { /* The current selection remains available for this page. */ }
    window.location.assign("?ojr=forex-transfer");
  };

  reviewRemittance = (): void => {
    if (this.blocked()) return;
    this.error(""); this.notice("");
    const beneficiary = this.selectedBeneficiary();
    const value = this.amount(this.transferAmount());
    const purpose = this.purpose().trim();
    if (!beneficiary || beneficiary.status !== "ACTIVE" || value === null || !purpose || purpose.length > 200) {
      this.error("Choose an active beneficiary, a positive amount, and a purpose of 1–200 characters."); return;
    }
    const wallet = this.wallets().find(w => w.currencyCode === beneficiary.currencyCode);
    if (!wallet || value > Number(wallet.availableBalance)) { this.error("The beneficiary's currency wallet has insufficient balance."); return; }
    this.transferReview({ request: { forexBeneficiaryId: beneficiary.forexBeneficiaryId, amount: value,
      purpose, idempotencyKey: this.newKey() },
      summary: "Submit " + this.money(value, beneficiary.currencyCode) + " from your wallet to " + beneficiary.beneficiaryName +
        " (" + beneficiary.maskedAccountNumber + ", " + beneficiary.swiftBic + "). Purpose: " + purpose });
  };
  confirmRemittance = async (): Promise<void> => {
    if (this.busy() || this.externalBusy()) return;
    const transfer = this.pendingTransfer() || this.transferReview();
    if (!transfer) return;
    if (!this.pendingTransfer()) {
      if (!this.save(".transfer", JSON.stringify(transfer))) return;
      this.pendingTransfer(transfer); this.transferReview(null);
    }
    this.busy(true); this.error(""); this.notice("");
    try {
      const result = await api.post<Result>("/api/forex/remittances/outward", transfer.request);
      this.receipt(result); this.pendingTransfer(null); this.remove(".transfer");
      this.notice("The backend recorded your outward remittance. This response does not confirm delivery to an external bank.");
      if (this.active) await Promise.all([this.refresh(), this.onChange()]);
    } catch (error) {
      this.error(this.message(error));
      if (error instanceof ApiError && error.status >= 400 && error.status < 500 && ![401, 403, 408, 429].includes(error.status)) {
        this.pendingTransfer(null); this.remove(".transfer");
      }
    } finally { this.busy(false); }
  };

  cardCurrency = (card: Card): string => this.wallets().find(w => w.walletId === card.walletId)?.currencyCode || "";
  cardLabel = (card: Card): string => card.cardNumberMasked + " · " + (this.cardCurrency(card) || "Currency unavailable") + " · " + card.status;
  beneficiaryLabel = (b: Beneficiary): string => b.beneficiaryName + " · " + b.currencyCode + " · " + b.maskedAccountNumber;
  walletBalance = (currency: string): string => {
    const wallet = this.wallets().find(w => w.currencyCode === currency);
    return wallet ? this.money(wallet.availableBalance, currency) : "No wallet available";
  };
  money = (value: number, currency: string): string => (currency || "Currency unavailable") + " " + Number(value).toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 6 });
  private amount(value: string): number | null {
    const raw = value.trim(); const amount = Number(raw);
    return /^\d+(?:\.\d{1,6})?$/.test(raw) && Number.isFinite(amount) && amount > 0 ? amount : null;
  }
  private newKey(): string { const bytes = new Uint8Array(16); window.crypto.getRandomValues(bytes); return Array.from(bytes, b => b.toString(16).padStart(2, "0")).join(""); }
  private save(suffix: string, value: string): boolean {
    try { sessionStorage.setItem(this.key + suffix, value); return true; }
    catch { this.error("Enable browser session storage before submitting this request."); return false; }
  }
  private remove(suffix: string): void { try { sessionStorage.removeItem(this.key + suffix); } catch { /* Keep the server result visible. */ } }
  private message(error: unknown): string { return error instanceof Error ? error.message : "Unable to complete the request."; }
  disconnected(): void { this.active = false; ++this.version; this.loading(false); }
}
export = ForexExtras;
