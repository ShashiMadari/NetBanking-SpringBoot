import * as ko from "knockout";
import { api } from "../services/api";
import { PurchaseAuthorisation } from "../services/purchaseAuthorisation";
import * as AccUtils from "../accUtils";

interface Account { accountId: number; accountNumber: string; accountType: string; availableBalance: number; currency: string; }
interface InvestmentProfile { panStatus: string; dematStatus: string; dematNumber?: string; linkedAccountId?: number; walletBalance: number; }
interface Product { id: number; code: string; name: string; productType: string; requiresDemat: string; minimumAmount: number; currentPrice?: number; annualRate?: number; tenureMonths?: number; exchangeName?: string; riskLevel: string; }
interface WalletTransaction { id: number; transactionType: string; amount: number; status: string; referenceNumber: string; createdAt: string; }
interface Wallet { balance: number; transactions: WalletTransaction[]; }
interface Holding { id: number; productName: string; productType: string; investedAmount?: number | null; currentValue: number; }

class InvestmentsViewModel {
  products = ko.observableArray<Product>([]);
  accounts = ko.observableArray<Account>([]);
  profile = ko.observable<InvestmentProfile>({ panStatus: "NOT_STARTED", dematStatus: "NOT_REQUESTED", walletBalance: 0 });
  wallet = ko.observable<Wallet>({ balance: 0, transactions: [] });
  holdings = ko.observableArray<Holding>([]);
  allocationLoaded = ko.observable(false);

  allocationItems = ko.pureComputed(() => {
    const colours = [
      "#76427c",
      "#177c55",
      "#d58a3d",
      "#2f6fae",
      "#bd4d69",
      "#5d7a98"
    ];

    const names: Record<string, string> = {
      MUTUAL_FUND: "Mutual Funds",
      ETF: "ETFs",
      EQUITY: "Stocks",
      RD: "Recurring Deposits",
      FD: "Fixed Deposits",
      PPF: "PPF",
      OTHER: "Other investments"
    };
    const typeOrder = ["MUTUAL_FUND", "ETF", "EQUITY", "RD", "FD", "PPF"];

    const grouped = new Map<string, number>();

    this.holdings().forEach((holding) => {
      const type = (holding.productType || "OTHER").trim().toUpperCase() || "OTHER";
      const value = Number(holding.currentValue ?? 0);
      if (!Number.isFinite(value) || value <= 0) return;

      grouped.set(type, (grouped.get(type) ?? 0) + value);
    });

    const total = [...grouped.values()].reduce(
      (sum, value) => sum + value,
      0
    );

    const rank = (type: string): number => {
      const index = typeOrder.indexOf(type);
      return index < 0 ? typeOrder.length : index;
    };
    const entries = [...grouped.entries()].sort(([a], [b]) => rank(a) - rank(b) || a.localeCompare(b));
    let accumulated = 0;

    return entries.map(([type, value], index) => {
      const start = (accumulated / total) * 100;
      accumulated += value;
      const percentage = Number(((value / total) * 100).toFixed(1));

      const item = {
        type,
        label: names[type] ?? type.replace(/_/g, " "),
        value,
        percentage,
        colour: rank(type) < typeOrder.length ? colours[rank(type)] : "#526078",
        start,
        end: index === entries.length - 1 ? 100 : (accumulated / total) * 100
      };

      return item;
    });
  });

  allocationChartStyle = ko.pureComputed(() => {
    const items = this.allocationItems();

    if (!items.length) {
      return "conic-gradient(#eee 0% 100%)";
    }

    const parts = items.map(
      (item) =>
        item.colour + " " + item.start + "% " + item.end + "%"
    );

    return "conic-gradient(" + parts.join(", ") + ")";
  });

  allocationTotal = ko.pureComputed(() =>
    this.allocationItems().reduce(
      (sum, item) => sum + item.value,
      0
    )
  );
  allocationChartLabel = ko.pureComputed(() => "Current portfolio value: " + this.formatInr(this.allocationTotal()) + ". " +
    this.allocationItems().map((item) => item.label + ": " + this.formatInr(item.value) + " (" + item.percentage + "%)").join("; "));
  category = ko.observable("");
  selectedProduct = ko.observable<Product | null>(null);
  loading = ko.observable(true);
  error = ko.observable("");
  notice = ko.observable("");
  purchase = new PurchaseAuthorisation();
  panNumber = ko.observable("");
  dematAccountId = ko.observable("");
  dematOtp = ko.observable("");
  dematOtpSent = ko.observable(false);
  walletAccountId = ko.observable("");
  walletAmount = ko.observable("");
  walletTransactionId = ko.observable("");
  walletOtp = ko.observable("");
  walletSubmitted = ko.observable(false);
  showWalletFunding = ko.observable(false);
  showWalletActivity = ko.observable(false);
  toggleWalletActivity = (): void => {
    this.showWalletActivity(!this.showWalletActivity());
  };
  walletTransferType = ko.observable<"FUND" | "WITHDRAW">("FUND");
  purchaseAccountId = ko.observable("");
  investmentAmount = ko.observable("");
  investmentMode = ko.observable("LUMPSUM");
  sipTenureMonths = ko.observable("12");
  sipDebitDay = ko.observable("5");
  sipDebitDays = Array.from({ length: 28 }, (_, index) => index + 1);
  exchange = ko.observable("NSE");

  dematActive = ko.pureComputed(() => this.profile().dematStatus === "ACTIVE");
  stockProduct = ko.pureComputed(() => {
    const product = this.selectedProduct();
    return product !== null && ["EQUITY", "ETF"].includes(product.productType);
  });
  mutualFund = ko.pureComputed(() => this.selectedProduct()?.productType === "MUTUAL_FUND");
  walletTransferTitle = ko.pureComputed(() => this.walletTransferType() === "FUND" ? "Add funds to Trading Wallet" : "Withdraw from Trading Wallet");
  walletTransferButton = ko.pureComputed(() => this.walletTransferType() === "FUND" ? "Fund wallet" : "Request withdrawal");
  isSip = ko.pureComputed(() => this.mutualFund() && this.investmentMode() === "SIP");
  investmentActionLabel = ko.pureComputed(() => this.isSip() ? "Start SIP" : "Invest now");
  selectedProductId = ko.pureComputed(() => this.selectedProduct()?.id ?? 0);
  filteredProducts = ko.pureComputed(() => this.products().filter((product) => {
    if (this.category() === "stocks") return ["EQUITY", "ETF"].includes(product.productType);
    if (this.category() === "mutual") return product.productType === "MUTUAL_FUND";
    if (this.category() === "fd") return product.productType === "FD";
    if (this.category() === "rd") return product.productType === "RD";
    if (this.category() === "ppf") return product.productType === "PPF";
    return false;
  }));
  accountLabel = (account: Account): string => `${account.accountType} · •••• ${account.accountNumber.slice(-4)}`;
  formatInr = (amount: number | undefined): string => `INR ${Number(amount ?? 0).toLocaleString("en-IN", { minimumFractionDigits: 2 })}`;

  async connected(): Promise<void> {
    AccUtils.announce("Investments page loaded.");
    document.title = "Investments | O-Bank";
    try {
      const [dashboard, products, wallet, accounts] = await Promise.all([
        api.get<{ profile: InvestmentProfile; holdings?: Holding[] }>("/api/investments/dashboard"),
        api.get<Product[]>("/api/investments/products"),
        api.get<Wallet>("/api/investments/wallet"),
        api.get<Account[]>("/api/accounts/summary")
      ]);
      this.profile(dashboard.profile);
      this.holdings(dashboard.holdings ?? []);
      this.allocationLoaded(true);
      this.products(products);
      this.wallet(wallet);
      this.accounts(accounts);
      const linked = dashboard.profile.linkedAccountId;
      const firstAccount = accounts[0]?.accountId;
      const selected = String(linked ?? firstAccount ?? "");
      this.dematAccountId(selected);
      this.walletAccountId(selected);
      this.purchaseAccountId(selected);
    } catch (error) { this.error(this.message(error)); } finally { this.loading(false); }
  }

  selectCategory = (category: string): void => {
    this.error(""); this.notice(""); this.category(category); this.selectedProduct(null);
  };
  chooseProduct = (product: Product): void => {
    this.error(""); this.notice(""); this.selectedProduct(product); this.purchase.reset(); this.investmentAmount(""); this.investmentMode("LUMPSUM"); this.sipTenureMonths("12"); this.sipDebitDay("5");
  };

  savePan = async (): Promise<void> => {
    this.error(""); this.notice("");
    try {
      const profile = await api.post<InvestmentProfile>("/api/investments/profile/pan", { panNumber: this.panNumber().trim().toUpperCase() });
      this.profile(profile); this.notice("PAN verified. You can now request a Demat OTP.");
    } catch (error) { this.error(this.message(error)); }
  };
  sendDematOtp = async (): Promise<void> => {
    this.error(""); this.notice("");
    try {
      await api.post("/api/investments/profile/demat/send-otp", { accountId: Number(this.dematAccountId()) });
      this.dematOtpSent(true); this.notice("Demat OTP sent to your registered email.");
    } catch (error) { this.error(this.message(error)); }
  };
  verifyDematOtp = async (): Promise<void> => {
    this.error(""); this.notice("");
    try {
      const profile = await api.post<InvestmentProfile>("/api/investments/profile/demat/verify", { otp: this.dematOtp() });
      this.profile(profile); this.dematOtpSent(false); this.dematOtp(""); this.walletAccountId(String(profile.linkedAccountId ?? "")); this.purchaseAccountId(String(profile.linkedAccountId ?? ""));
      this.notice("Demat account verified successfully.");
    } catch (error) { this.error(this.message(error)); }
  };

  sendWalletOtp = async (): Promise<void> => {
    this.error(""); this.notice("");
    const amount = Number(this.walletAmount());
    if (!Number.isFinite(amount) || amount <= 0) { this.error("Enter a wallet transfer amount greater than zero."); return; }
    try {
      const endpoint = this.walletTransferType() === "FUND" ? "/api/investments/wallet/fund/send-otp" : "/api/investments/wallet/withdraw/send-otp";
      const result = await api.post<{ walletTransactionId: number }>(endpoint, { accountId: Number(this.walletAccountId()), amount });
      this.walletTransactionId(String(result.walletTransactionId)); this.walletSubmitted(false); this.notice(this.walletTransferType() === "FUND" ? "Wallet funding OTP sent to your registered email." : "Withdrawal OTP sent to your registered email.");
    } catch (error) { this.error(this.message(error)); }
  };
  openWalletTransfer = (type: "FUND" | "WITHDRAW"): void => {
    this.error(""); this.notice(""); this.walletTransferType(type); this.walletAmount(""); this.walletTransactionId(""); this.walletOtp(""); this.showWalletFunding(true);
  };
  confirmWalletFunding = async (): Promise<void> => {
    if (this.walletSubmitted()) return;
    this.error(""); this.notice(""); this.walletSubmitted(true);
    try {
      const result = await api.post<{ walletBalance: number }>(`/api/investments/wallet/transactions/${Number(this.walletTransactionId())}/authorise`, { otp: this.walletOtp() });
      this.wallet(await api.get<Wallet>("/api/investments/wallet")); this.walletTransactionId(""); this.walletOtp(""); this.walletAmount(""); this.showWalletFunding(false); this.notice(this.walletTransferType() === "FUND" ? "Trading Wallet funded successfully." : "Withdrawal completed successfully.");
    } catch (error) { this.walletSubmitted(false); this.error(this.message(error)); }
  };

  submitInvestment = async (): Promise<void> => {
    const product = this.selectedProduct();
    this.error(""); this.notice("");
    if (!product) return;
    if (this.stockProduct() && !this.dematActive()) { this.error("Open and verify a Demat account before buying Stocks or ETFs."); return; }
    const amount = Number(this.investmentAmount());
    if (!Number.isFinite(amount) || amount <= 0) { this.error("Enter an investment amount greater than zero."); return; }
    if (this.isSip()) {
      const tenureMonths = Number(this.sipTenureMonths());
      const debitDay = Number(this.sipDebitDay());
      if (!Number.isInteger(tenureMonths) || tenureMonths < 1 || tenureMonths > 600) { this.error("Choose a SIP tenure from 1 to 600 months."); return; }
      if (!Number.isInteger(debitDay) || debitDay < 1 || debitDay > 28) { this.error("Choose a monthly SIP debit day from 1 to 28."); return; }
      try {
        const result = await api.post<{ message?: string }>("/api/investments/schedules", { productId: product.id, accountId: Number(this.purchaseAccountId()), amount, debitDay, tenureMonths });
        this.notice(result.message ?? "Your SIP instruction is active.");
        this.investmentAmount("");
      } catch (error) { this.error(this.message(error)); }
      return;
    }
    if (this.stockProduct() && amount > Number(this.wallet().balance)) { this.error("Your Trading Wallet balance is lower than this stock or ETF order."); return; }
    try {
      const result = await this.purchase.start({
        productId: product.id,
        accountId: Number(this.purchaseAccountId()),
        amount,
        quantity: this.stockProduct() && product.currentPrice ? amount / Number(product.currentPrice) : undefined,
        mode: this.mutualFund() ? this.investmentMode() : "LUMPSUM",
        exchange: this.stockProduct() ? this.exchange() : null
      });
      this.investmentAmount(""); this.notice(result.message ?? "Purchase OTP sent. Enter it to authorise the investment.");
    } catch (error) { this.error(this.message(error)); }
  };
  authorisePurchase = async (): Promise<void> => {
    this.error(""); this.notice("");
    try {
      const result = await this.purchase.authorise();
      this.notice(result.message ?? "Investment purchase authorised.");
    } catch (error) { this.error(this.message(error)); }
  };

  private message(error: unknown): string { return error instanceof Error ? error.message : "The Investment service could not complete this request."; }
}
export = InvestmentsViewModel;
