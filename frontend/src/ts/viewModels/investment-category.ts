import * as ko from "knockout";
import { api } from "../services/api";
import { PurchaseAuthorisation } from "../services/purchaseAuthorisation";
import * as AccUtils from "../accUtils";

interface Account { accountId: number; accountNumber: string; accountType: string; availableBalance: number; currency: string; }
interface Product { id: number; name: string; productType: string; minimumAmount: number; annualRate?: number; tenureMonths?: number; riskLevel: string; }

type RouteKey = "mutual-funds" | "fixed-deposits" | "recurring-deposits" | "ppf";
interface CategoryConfig { eyebrow: string; heading: string; description: string; productType: string; orderLabel: string; }

const categoryConfigs: Record<RouteKey, CategoryConfig> = {
  "mutual-funds": { eyebrow: "MUTUAL FUNDS", heading: "Invest for your goals.", description: "Choose a fund for a lump-sum investment or create a recurring SIP instruction.", productType: "MUTUAL_FUND", orderLabel: "Start investment" },
  "fixed-deposits": { eyebrow: "FIXED DEPOSITS", heading: "Lock in your return.", description: "Choose a fixed deposit and place your investment securely.", productType: "FD", orderLabel: "Open fixed deposit" },
  "recurring-deposits": { eyebrow: "RECURRING DEPOSITS", heading: "Save a little, regularly.", description: "Choose a recurring deposit and start your monthly savings plan.", productType: "RD", orderLabel: "Start recurring deposit" },
  "ppf": { eyebrow: "PUBLIC PROVIDENT FUND", heading: "Build long-term savings.", description: "Choose a PPF option and start your contribution.", productType: "PPF", orderLabel: "Invest in PPF" }
};

class InvestmentCategoryViewModel {
  private readonly routeKey = (new URLSearchParams(window.location.search).get("ojr") || "mutual-funds") as RouteKey;
  private readonly config = categoryConfigs[this.routeKey] || categoryConfigs["mutual-funds"];
  products = ko.observableArray<Product>([]);
  accounts = ko.observableArray<Account>([]);
  selectedProduct = ko.observable<Product | null>(null);
  loading = ko.observable(true);
  error = ko.observable("");
  notice = ko.observable("");
  purchase = new PurchaseAuthorisation();
  purchaseAccountId = ko.observable("");
  investmentAmount = ko.observable("");
  investmentMode = ko.observable("LUMPSUM");
  sipTenureMonths = ko.observable("12");
  sipDebitDay = ko.observable("5");
  sipDebitDays = Array.from({ length: 28 }, (_, index) => index + 1);
  demoPrice = ko.observable(0);
  chartValues = ko.observableArray<number>([]);
  private marketTimer: number | undefined;

  eyebrow = this.config.eyebrow;
  heading = this.config.heading;
  description = this.config.description;
  orderLabel = this.config.orderLabel;
  isMutualFund = ko.pureComputed(() => this.config.productType === "MUTUAL_FUND");
  isSip = ko.pureComputed(() => this.isMutualFund() && this.investmentMode() === "SIP");
  investmentActionLabel = ko.pureComputed(() => this.isSip() ? "Start SIP" : this.orderLabel);
  categoryProducts = ko.pureComputed(() => this.products().filter((product) => product.productType === this.config.productType));
  selectedProductId = ko.pureComputed(() => this.selectedProduct()?.id ?? 0);
  chartLine = ko.pureComputed(() => {
    const values = this.chartValues();
    if (values.length < 2) return "";
    const lowest = Math.min(...values); const highest = Math.max(...values); const range = highest - lowest || 1;
    return values.map((value, index) => `${(index / (values.length - 1)) * 320},${96 - ((value - lowest) / range) * 72}`).join(" ");
  });
  demoChange = ko.pureComputed(() => {
    const values = this.chartValues();
    return values.length < 2 || !values[0] ? 0 : Number((((values[values.length - 1] - values[0]) / values[0]) * 100).toFixed(2));
  });
  demoChangeLabel = ko.pureComputed(() => `${this.demoChange() >= 0 ? "+" : ""}${this.demoChange().toFixed(2)}%`);
  demoChangeClass = ko.pureComputed(() => this.demoChange() >= 0 ? "market-up" : "market-down");
  accountLabel = (account: Account): string => `${account.accountType} · •••• ${account.accountNumber.slice(-4)}`;
  formatInr = (amount: number | undefined): string => `INR ${Number(amount ?? 0).toLocaleString("en-IN", { minimumFractionDigits: 2 })}`;
  productDetails = (product: Product): string => {
    const parts = [`Minimum ${this.formatInr(product.minimumAmount)}`];
    if (product.annualRate) parts.push(`${product.annualRate}% p.a.`);
    if (product.tenureMonths) parts.push(`${product.tenureMonths} months`);
    return parts.join(" · ");
  };

  async connected(): Promise<void> {
    AccUtils.announce(`${this.eyebrow} page loaded.`);
    document.title = `${this.eyebrow} | O-Bank`;
    if (this.isMutualFund()) document.body.classList.add("mutual-funds-page-active");
    try {
      const [products, accounts] = await Promise.all([
        api.get<Product[]>("/api/investments/products"),
        api.get<Account[]>("/api/accounts/summary")
      ]);
      this.products(products);
      this.accounts(accounts);
      this.purchaseAccountId(String(accounts[0]?.accountId ?? ""));
    } catch (error) { this.error(this.message(error)); } finally { this.loading(false); }
  }

  chooseProduct = (product: Product): void => {
    this.error(""); this.notice(""); this.selectedProduct(product); this.purchase.reset(); this.investmentAmount(""); this.investmentMode("LUMPSUM"); this.sipTenureMonths("12"); this.sipDebitDay("5");
    if (this.isMutualFund()) this.startDemoMarketFeed(product);
  };

  disconnected(): void { if (this.marketTimer !== undefined) window.clearInterval(this.marketTimer); document.body.classList.remove("mutual-funds-page-active"); }

  private startDemoMarketFeed(product: Product): void {
    if (this.marketTimer !== undefined) window.clearInterval(this.marketTimer);
    let price = Number(product.minimumAmount || 100); const history: number[] = [];
    for (let index = 0; index < 14; index += 1) { price = Number((price * (1 + Math.sin(index * 1.71) * 0.003)).toFixed(2)); history.push(price); }
    this.chartValues(history); this.demoPrice(price);
    this.marketTimer = window.setInterval(() => {
      const nextPrice = Number((this.demoPrice() * (1 + (Math.random() - 0.48) * 0.008)).toFixed(2));
      this.chartValues([...this.chartValues(), nextPrice].slice(-20)); this.demoPrice(nextPrice);
    }, 5000);
  }

  submitInvestment = async (): Promise<void> => {
    const product = this.selectedProduct();
    this.error(""); this.notice("");
    if (!product) return;
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
    try {
      const result = await this.purchase.start({
        productId: product.id,
        accountId: Number(this.purchaseAccountId()),
        amount,
        mode: this.isMutualFund() ? this.investmentMode() : "LUMPSUM",
        exchange: null
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

export = InvestmentCategoryViewModel;
