import * as ko from "knockout";
import * as AccUtils from "../accUtils";
import ForexExtras = require("./forexExtras");
import { api, ApiError, GATEWAY_URL, getToken } from "../services/api";

interface Account { accountId: number; accountNumber: string; accountType: string; availableBalance: number; currency: string; status: string; }
interface Rate { currencyCode: string; currentRateInr: number; markupBps: number; status: string; referenceSource: string; referenceAsOf: string; updatedAt: string; }
interface Wallet { walletId: number; currencyCode: string; availableBalance: number; updatedAt: string; }
interface Quote { quoteId: string; fromCurrency: string; toCurrency: string; fromAmount: number; receiveAmount: number; customerRateInr: number; markupBps: number; expiresAt: string; status: string; }
interface Receipt { forexTransactionId: number; transactionType: string; fromCurrency: string; toCurrency: string; fromAmount: number; receiveAmount: number; customerRateInr: number; inrEquivalent: number; paymentMethod: string; status: string; referenceNumber: string; createdAt: string; }
interface Conversion { quoteId: string; sourceAccountId: number; paymentMethod: string; idempotencyKey: string; }
interface Pending { request: Conversion; quote: Quote; }
interface Statement { period: string; statementFrom: string; statementTo: string; page: number; pageSize: number; totalTransactions: number; totalPages: number; transactions: Receipt[]; }
interface Filters { period: string; from: string; to: string; pageSize: number; }
interface Dashboard { wallets: Wallet[]; recentTransactions: Receipt[]; }

class ForexViewModel {
  rates = ko.observableArray<Rate>([]);
  wallets = ko.observableArray<Wallet>([]);
  accounts = ko.observableArray<Account>([]);
  direction = ko.observable("BUY");
  currency = ko.observable("");
  amount = ko.observable("");
  accountId = ko.observable<number | string>("");
  loading = ko.observable(false);
  quoting = ko.observable(false);
  converting = ko.observable(false);
  error = ko.observable("");
  dataError = ko.observable("");
  pending = ko.observable<Pending | null>(null);
  quote = ko.observable<Quote | null>(null);
  receipt = ko.observable<Receipt | null>(null);
  walletLoaded = ko.observable(false);
  balancesVisible = ko.observable(true);
  recentTransactions = ko.observableArray<Receipt>([]);
  dashboardLoaded = ko.observable(false);
  quoteSeconds = ko.observable(0);
  period = ko.observable("RECENT");
  fromDate = ko.observable("");
  toDate = ko.observable("");
  pageSize = ko.observable("10");
  statement = ko.observable<Statement | null>(null);
  historyLoading = ko.observable(false);
  historyError = ko.observable("");
  statementBusy = ko.observable(false);
  statementNotice = ko.observable("");
  transactions = ko.observableArray<Receipt>([]);
  transactionLoading = ko.observable(false);
  transactionCurrency = ko.observable("");
  transactionType = ko.observable("");
  transactionStatus = ko.observable("");
  transactionFrom = ko.observable("");
  transactionTo = ko.observable("");
  appliedFilters = ko.observable<Filters | null>(null);
  periods = [
    { value: "RECENT", label: "Last 30 days" },
    { value: "THREE_MONTHS", label: "Last 3 months" },
    { value: "SIX_MONTHS", label: "Last 6 months" },
    { value: "FISCAL_YEAR", label: "Current financial year" },
    { value: "CUSTOM", label: "Custom dates" }
  ];
  filtersChanged = ko.pureComputed(() => {
    const f = this.appliedFilters();
    return !f || f.period !== this.period() || f.pageSize !== Number(this.pageSize()) ||
      (this.period() === "CUSTOM" && (f.from !== this.fromDate() || f.to !== this.toDate()));
  });
  canExport = ko.pureComputed(() => !!this.statement()?.transactions.length &&
    !this.historyLoading() && !this.statementBusy() && !this.filtersChanged());
  canPrevious = ko.pureComputed(() => !this.historyLoading() && !this.statementBusy() &&
    !this.filtersChanged() && !!this.statement() && this.statement()!.page > 0);
  canNext = ko.pureComputed(() => !this.historyLoading() && !this.statementBusy() &&
    !this.filtersChanged() && !!this.statement() && this.statement()!.page + 1 < this.statement()!.totalPages);
  locked = ko.pureComputed(() => this.converting() || this.pending() !== null);
  quoteExpired = ko.pureComputed(() => !!this.quote() && !this.pending() && this.quoteSeconds() <= 0);
  canConfirmQuote = ko.pureComputed(() => !!this.pending() || (!!this.quote() && this.quote()!.status === "LOCKED" && !this.quoteExpired()));
  fromCurrency = ko.pureComputed(() => this.direction() === "BUY" ? "INR" : this.currency());
  toCurrency = ko.pureComputed(() => this.direction() === "BUY" ? this.currency() : "INR");
  selectedAccount = ko.pureComputed(() => this.accounts().find(a => String(a.accountId) === String(this.accountId())) || null);
  selectedWallet = ko.pureComputed(() => this.wallets().find(w => w.currencyCode === this.currency()) || null);
  canQuote = ko.pureComputed(() => !this.loading() && !this.quoting() && !this.locked() && !this.extras.busy() && !!this.currency() && !!this.amount().trim() && !!this.selectedAccount());
  extras = new ForexExtras(
    async () => { await Promise.all([this.refresh(), this.loadHistory(0, true)]); },
    () => this.locked()
  );
  private subscriptions: ko.Subscription[] = [];
  private quoteVersion = 0;
  private refreshVersion = 0;
  private active = false;
  private storageKey = "";
  private historyVersion = 0;
  private quoteRequest = 0;
  private quoteTimer: number | undefined;

  async connected(): Promise<void> {
    this.active = true;
    document.title = "Forex | O-Bank";
    AccUtils.announce("Forex page loaded.");
    this.storageKey = "o-bank.forex.pending." + (sessionStorage.getItem("o-bank.email") || "customer");
    this.restorePending();
    if (!this.subscriptions.length) {
      const invalidate = (): void => {
        if (!this.locked()) { ++this.quoteVersion; this.quote(null); this.error(""); }
      };
      this.subscriptions.push(this.direction.subscribe(invalidate), this.currency.subscribe(invalidate),
        this.amount.subscribe(invalidate), this.accountId.subscribe(invalidate));
    }
    await Promise.all([this.refresh(), this.loadDashboard(), this.loadHistory(), this.loadTransactions(), this.extras.connected()]);
  }

  refreshAll = async (): Promise<void> => {
    if (this.converting() || this.extras.busy()) return;
    await Promise.all([this.refresh(), this.loadDashboard(), this.loadHistory(0, true), this.loadTransactions(), this.extras.refresh()]);
  };

  loadDashboard = async (): Promise<void> => {
    try {
      const dashboard = await api.get<Dashboard>("/api/forex/dashboard");
      if (!this.active) return;
      this.recentTransactions(dashboard.recentTransactions || []);
      this.dashboardLoaded(true);
    } catch (error) {
      if (this.active) this.dataError([this.dataError(), "Dashboard: " + this.message(error)].filter(Boolean).join(" "));
    }
  };

  toggleBalances = (): void => { this.balancesVisible(!this.balancesVisible()); };
  walletInrValue = (wallet: Wallet): string => {
    const rate = this.rates().find(item => item.currencyCode === wallet.currencyCode);
    return rate ? this.money(Number(wallet.availableBalance) * Number(rate.currentRateInr), "INR") : "INR value unavailable";
  };

  refresh = async (): Promise<void> => {
    const version = ++this.refreshVersion;
    this.loading(true);
    this.dataError("");
    const results = await Promise.allSettled([
      api.get<Rate[]>("/api/forex/rates"),
      api.get<Wallet[]>("/api/forex/wallets"),
      api.get<Account[]>("/api/accounts/summary")
    ]);
    if (!this.active || version !== this.refreshVersion) return;
    const messages: string[] = [];
    const [rates, wallets, accounts] = results;
    if (rates.status === "fulfilled") {
      this.rates(rates.value.filter(r => r.status === "ACTIVE" && r.currencyCode !== "INR"));
      if (!this.rates().some(r => r.currencyCode === this.currency())) this.currency(this.rates()[0]?.currencyCode || "");
    } else { this.rates([]); messages.push("Rates: " + this.message(rates.reason)); }
    if (wallets.status === "fulfilled") { this.wallets(wallets.value); this.walletLoaded(true); }
    else { this.wallets([]); this.walletLoaded(false); messages.push("Wallets: " + this.message(wallets.reason)); }
    if (accounts.status === "fulfilled") {
      this.accounts(accounts.value.filter(a => a.currency === "INR" && a.status === "ACTIVE"));
      if (!this.selectedAccount()) this.accountId(this.accounts()[0]?.accountId || "");
    } else { this.accounts([]); messages.push("Accounts: " + this.message(accounts.reason)); }
    this.dataError(messages.join(" "));
    this.loading(false);
  };

  getQuote = async (): Promise<void> => {
    if (!this.canQuote()) return;
    this.error("");
    this.quote(null);
    const digits = this.direction() === "BUY" ? 2 : 6;
    const raw = this.amount().trim();
    const pattern = new RegExp("^\\d+(?:\\.\\d{1," + digits + "})?$");
    const value = Number(raw);
    if (!pattern.test(raw) || !Number.isFinite(value) || value <= 0) {
      this.error("Enter a positive amount with at most " + digits + " decimal places."); return;
    }
    if (this.direction() === "BUY" && value > Number(this.selectedAccount()!.availableBalance)) {
      this.error("The selected INR account has insufficient available balance."); return;
    }
    if (this.direction() === "SELL") {
      if (!this.walletLoaded()) { this.error("Refresh wallet balances before selling currency."); return; }
      if (value > Number(this.selectedWallet()?.availableBalance || 0)) {
        this.error("Your " + this.currency() + " wallet has insufficient available balance."); return;
      }
    }
    const version = ++this.quoteVersion;
    const request = ++this.quoteRequest;
    this.quoting(true);
    try {
      const quote = await api.post<Quote>("/api/forex/quotes", {
        fromCurrency: this.fromCurrency(), toCurrency: this.toCurrency(), fromAmount: value
      });
      if (this.active && version === this.quoteVersion) { this.quote(quote); this.startQuoteTimer(quote.expiresAt); }
    } catch (error) {
      if (this.active && version === this.quoteVersion) this.error(this.message(error));
    } finally { if (this.active && request === this.quoteRequest) this.quoting(false); }
  };

  confirm = async (): Promise<void> => {
    if (this.converting() || this.quoting() || this.loading() || this.extras.busy()) return;
    let pending = this.pending();
    if (!pending) {
      const quote = this.quote();
      const account = this.selectedAccount();
      if (!quote || !account || quote.status !== "LOCKED" || this.quoteExpired()) {
        if (quote && this.quoteExpired()) this.error("This quote has expired. Request a new quote.");
        return;
      }
      pending = {
        quote,
        request: {
          quoteId: quote.quoteId,
          // For SELL, the backend uses this field as the INR destination account.
          sourceAccountId: account.accountId,
          paymentMethod: quote.fromCurrency === "INR" ? "INR_ACCOUNT" : "FOREX_WALLET",
          idempotencyKey: this.newKey()
        }
      };
      try { sessionStorage.setItem(this.storageKey, JSON.stringify(pending)); }
      catch { this.error("Your browser could not save this request. Enable session storage and try again."); return; }
      this.pending(pending);
    }
    this.converting(true);
    this.error("");
    try {
      const receipt = await api.post<Receipt>("/api/forex/convert", pending.request);
      this.receipt(receipt);
      this.pending(null);
      this.quote(null);
      this.stopQuoteTimer();
      sessionStorage.removeItem(this.storageKey);
      if (this.active) {
        AccUtils.announce("Conversion result received.");
        await Promise.all([this.refresh(), this.loadHistory(0, true), this.extras.refresh()]);
      }
    } catch (error) {
      this.error(this.message(error));
      // Expiry is checked before any new debit; successful retries return the
      // existing transaction before the backend performs this expiry check.
      if (error instanceof ApiError && error.status === 400 && /This Forex quote has expired/i.test(error.message)) {
        this.pending(null); this.quote(null); sessionStorage.removeItem(this.storageKey); this.stopQuoteTimer();
      }
    } finally { this.converting(false); }
  };

  private restorePending(): void {
    try {
      const raw = sessionStorage.getItem(this.storageKey);
      if (!raw) return;
      const saved = JSON.parse(raw) as Pending;
      if (!saved.request || !saved.quote || saved.request.quoteId !== saved.quote.quoteId ||
          !Number.isInteger(saved.request.sourceAccountId) || saved.request.sourceAccountId <= 0 ||
          !/^[a-f0-9]{32}$/.test(saved.request.idempotencyKey) ||
          !["INR_ACCOUNT", "FOREX_WALLET"].includes(saved.request.paymentMethod)) return;
      this.pending(saved); this.quote(saved.quote); this.startQuoteTimer(saved.quote.expiresAt);
    } catch { this.error("The saved conversion request could not be read."); }
  }

  private newKey(): string {
    const bytes = new Uint8Array(16);
    window.crypto.getRandomValues(bytes);
    return Array.from(bytes, n => n.toString(16).padStart(2, "0")).join("");
  }
  private startQuoteTimer(expiresAt: string): void {
    this.stopQuoteTimer();
    const update = (): void => {
      const expires = new Date(expiresAt.replace(/(\.\d{3})\d+/, "$1")).getTime();
      this.quoteSeconds(Number.isFinite(expires) ? Math.max(0, Math.ceil((expires - Date.now()) / 1000)) : 0);
    };
    update();
    this.quoteTimer = window.setInterval(update, 1000);
  }
  private stopQuoteTimer(): void {
    if (this.quoteTimer !== undefined) window.clearInterval(this.quoteTimer);
    this.quoteTimer = undefined;
    this.quoteSeconds(0);
  }

  applyTransactionFilters = (): void => { void this.loadTransactions(); };
  clearTransactionFilters = (): void => {
    this.transactionCurrency(""); this.transactionType(""); this.transactionStatus("");
    this.transactionFrom(""); this.transactionTo(""); void this.loadTransactions();
  };
  loadTransactions = async (): Promise<void> => {
    this.historyError("");
    const from = this.transactionFrom(); const to = this.transactionTo();
    if ((from && !this.validDate(from)) || (to && !this.validDate(to)) || (from && to && from > to)) {
      this.historyError("Choose valid transaction dates with the start date on or before the end date."); return;
    }
    const query = new URLSearchParams();
    if (this.transactionCurrency()) query.set("currency", this.transactionCurrency());
    if (this.transactionType()) query.set("type", this.transactionType());
    if (this.transactionStatus()) query.set("status", this.transactionStatus());
    if (from) query.set("from", from);
    if (to) query.set("to", to);
    this.transactionLoading(true);
    try {
      const suffix = query.toString();
      const result = await api.get<Receipt[]>("/api/forex/transactions" + (suffix ? "?" + suffix : ""));
      if (this.active) this.transactions(result);
    } catch (error) { if (this.active) this.historyError(this.message(error)); }
    finally { if (this.active) this.transactionLoading(false); }
  };

  applyHistory = (): void => { void this.loadHistory(); };
  previousPage = (): void => {
    if (this.canPrevious()) void this.loadHistory(this.statement()!.page - 1, true);
  };
  nextPage = (): void => {
    if (this.canNext()) void this.loadHistory(this.statement()!.page + 1, true);
  };

  private validDate(value: string): boolean {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
    const date = new Date(value + "T00:00:00Z");
    return !Number.isNaN(date.getTime()) && date.toISOString().slice(0, 10) === value;
  }

  loadHistory = async (page = 0, reuse = false): Promise<void> => {
    if (this.statementBusy()) return;
    const f: Filters = reuse && this.appliedFilters() ? { ...this.appliedFilters()! } : {
      period: this.period(), from: this.fromDate(), to: this.toDate(), pageSize: Number(this.pageSize())
    };
    this.historyError(""); this.statementNotice("");
    if (f.period === "CUSTOM") {
      if (!this.validDate(f.from) || !this.validDate(f.to) || f.from > f.to) {
        this.historyError("Choose valid dates with the start date on or before the end date."); return;
      }
      if ((Date.parse(f.to) - Date.parse(f.from)) / 86400000 > 366) {
        this.historyError("Choose a date range of no more than 366 days."); return;
      }
    }
    const version = ++this.historyVersion;
    this.historyLoading(true);
    const query = new URLSearchParams({ period: f.period, page: String(page), pageSize: String(f.pageSize) });
    if (f.period === "CUSTOM") { query.set("from", f.from); query.set("to", f.to); }
    try {
      const result = await api.get<Statement>("/api/forex/transactions/statement?" + query);
      if (this.active && version === this.historyVersion) {
        this.statement(result); this.appliedFilters(f);
      }
    } catch (error) {
      if (this.active && version === this.historyVersion) {
        this.statement(null); this.historyError(this.message(error));
      }
    } finally {
      if (this.active && version === this.historyVersion) this.historyLoading(false);
    }
  };

  downloadStatement = async (): Promise<void> => {
    if (!this.canExport()) return;
    const statement = this.statement()!;
    this.statementBusy(true); this.historyError(""); this.statementNotice("");
    try {
      const token = getToken();
      if (!token) throw new Error("Please sign in again to download a statement.");
      const query = new URLSearchParams({ period: "CUSTOM", from: statement.statementFrom,
        to: statement.statementTo, page: String(statement.page), pageSize: String(statement.pageSize) });
      const response = await fetch(GATEWAY_URL + "/api/forex/transactions/statement/pdf?" + query,
        { headers: { Authorization: "Bearer " + token, Accept: "application/pdf" } });
      if (!response.ok) {
        if (response.status === 401 || response.status === 403) throw new Error("Your session has ended. Please sign in again.");
        throw new Error("The statement PDF could not be downloaded. Please try again.");
      }
      if (!(response.headers.get("Content-Type") || "").includes("application/pdf")) {
        throw new Error("The server did not return a PDF statement.");
      }
      const blob = await response.blob();
      if (!this.active) return;
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = "O-Bank-Forex-" + statement.statementFrom + "-to-" + statement.statementTo + "-page-" + (statement.page + 1) + ".pdf";
      document.body.appendChild(link); link.click(); link.remove();
      window.setTimeout(() => URL.revokeObjectURL(url), 1000);
      this.statementNotice("The current statement page was downloaded.");
    } catch (error) { if (this.active) this.historyError(this.message(error)); }
    finally { this.statementBusy(false); }
  };

  emailStatement = async (): Promise<void> => {
    if (!this.canExport() || this.statement()!.totalTransactions > 100) return;
    const statement = this.statement()!;
    this.statementBusy(true); this.historyError(""); this.statementNotice("");
    try {
      const result = await api.post<{ message: string }>("/api/forex/transactions/statement/email", {
        period: "CUSTOM", from: statement.statementFrom, to: statement.statementTo
      });
      if (this.active) this.statementNotice(result.message);
    } catch (error) { if (this.active) this.historyError(this.message(error)); }
    finally { this.statementBusy(false); }
  };

  pageLabel = ko.pureComputed(() => {
    const s = this.statement();
    return s && s.totalTransactions ? "Page " + (s.page + 1) + " of " + s.totalPages + " · " + s.totalTransactions + " transactions" : "No transactions";
  });
  sourceLabel = (source: string): string => source && source.includes("SIMULATION") ? "Simulated rate" : this.label(source || "Not provided");
  label = (value: string): string => (value || "—").replace(/_/g, " ").toLowerCase().replace(/\b\w/g, c => c.toUpperCase());
  statusClass = (status: string): string => ["SUCCESS", "COMPLETED", "SUCCESSFUL"].includes(status) ? "fx-success" : ["FAILED", "REJECTED", "CANCELLED"].includes(status) ? "fx-failed" : "fx-pending";
  private message(error: unknown): string { return error instanceof Error ? error.message : "Unable to complete this request."; }
  money = (amount: number, currency: string): string => currency + " " + Number(amount).toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: currency === "INR" ? 2 : 6 });
  accountLabel = (a: Account): string => a.accountType + " · •••• " + a.accountNumber.slice(-4);
  markupLabel = (bps: number): string => (Number(bps) / 100).toFixed(2) + "%";
  dateLabel = (value?: string): string => {
    if (!value) return "—";
    // Backend LocalDateTime has no time zone: retain its server wall-clock time.
    const raw = value.replace(/(\.\d{3})\d+/, "$1");
    const date = new Date(raw.length === 10 ? raw + "T00:00:00" : raw);
    if (Number.isNaN(date.getTime())) return "—";
    const day = date.toLocaleDateString("en-IN", { day: "2-digit", month: "short", year: "numeric" });
    return value.length === 10 ? day : day + ", " + date.toLocaleTimeString("en-IN", { hour: "2-digit", minute: "2-digit", hour12: true });
  };
  disconnected(): void {
    this.extras.disconnected();
    this.active = false; ++this.quoteVersion; ++this.refreshVersion; ++this.historyVersion; ++this.quoteRequest;
    this.subscriptions.forEach(s => s.dispose()); this.subscriptions = [];
    this.quoting(false); this.loading(false); this.historyLoading(false); this.transactionLoading(false); this.stopQuoteTimer();
  }
}
export = ForexViewModel;
