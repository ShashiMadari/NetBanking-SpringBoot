import * as ko from "knockout";
import { api } from "../services/api";
import { PurchaseAuthorisation } from "../services/purchaseAuthorisation";
import * as AccUtils from "../accUtils";

interface InvestmentProfile { dematStatus: string; dematNumber?: string; linkedAccountId?: number; }
interface Product { id: number; code: string; name: string; productType: string; requiresDemat: string; minimumAmount: number; currentPrice?: number; exchangeName?: string; riskLevel: string; }
interface Wallet { balance: number; }

class StocksViewModel {
  products = ko.observableArray<Product>([]);
  profile = ko.observable<InvestmentProfile>({ dematStatus: "NOT_REQUESTED" });
  wallet = ko.observable<Wallet>({ balance: 0 });
  selectedProduct = ko.observable<Product | null>(null);
  loading = ko.observable(true);
  error = ko.observable("");
  notice = ko.observable("");
  purchase = new PurchaseAuthorisation();
  stockUnits = ko.observable("");
  exchange = ko.observable("NSE");
  demoPrice = ko.observable(0);
  chartValues = ko.observableArray<number>([]);
  private marketTimer: number | undefined;

  dematActive = ko.pureComputed(() => this.profile().dematStatus === "ACTIVE");
  stockProducts = ko.pureComputed(() => this.products().filter((product) => ["EQUITY", "ETF"].includes(product.productType)));
  selectedProductId = ko.pureComputed(() => this.selectedProduct()?.id ?? 0);
  calculatedAmount = ko.pureComputed(() => {
    const product = this.selectedProduct();
    const units = Number(this.stockUnits());
    const price = Number(product?.currentPrice ?? 0);
    return Number.isFinite(units) && units > 0 && price > 0 ? Number((units * price).toFixed(2)) : 0;
  });
  chartLine = ko.pureComputed(() => {
    const values = this.chartValues();
    if (values.length < 2) return "";
    const lowest = Math.min(...values);
    const highest = Math.max(...values);
    const range = highest - lowest || 1;
    return values.map((value, index) => `${(index / (values.length - 1)) * 320},${96 - ((value - lowest) / range) * 72}`).join(" ");
  });
  demoChange = ko.pureComputed(() => {
    const values = this.chartValues();
    if (values.length < 2 || !values[0]) return 0;
    return Number((((values[values.length - 1] - values[0]) / values[0]) * 100).toFixed(2));
  });
  demoChangeLabel = ko.pureComputed(() => `${this.demoChange() >= 0 ? "+" : ""}${this.demoChange().toFixed(2)}%`);
  demoChangeClass = ko.pureComputed(() => this.demoChange() >= 0 ? "market-up" : "market-down");
  formatInr = (amount: number | undefined): string => `INR ${Number(amount ?? 0).toLocaleString("en-IN", { minimumFractionDigits: 2 })}`;

  async connected(): Promise<void> {
    AccUtils.announce("Stocks and ETFs page loaded.");
    document.title = "Stocks & ETFs | O-Bank";
    try {
      const [dashboard, products, wallet] = await Promise.all([
        api.get<{ profile: InvestmentProfile }>("/api/investments/dashboard"),
        api.get<Product[]>("/api/investments/products"),
        api.get<Wallet>("/api/investments/wallet")
      ]);
      this.profile(dashboard.profile);
      this.products(products);
      this.wallet(wallet);
    } catch (error) { this.error(this.message(error)); } finally { this.loading(false); }
  }

  chooseProduct = (product: Product): void => {
    this.error(""); this.notice(""); this.selectedProduct(product); this.purchase.reset(); this.stockUnits(""); this.startDemoMarketFeed(product);
  };

  disconnected(): void {
    if (this.marketTimer !== undefined) window.clearInterval(this.marketTimer);
  }

  private startDemoMarketFeed(product: Product): void {
    if (this.marketTimer !== undefined) window.clearInterval(this.marketTimer);
    let price = Number(product.currentPrice ?? product.minimumAmount ?? 100);
    const history: number[] = [];
    for (let index = 0; index < 14; index += 1) {
      price = Number((price * (1 + Math.sin(index * 1.71) * 0.003)).toFixed(2));
      history.push(price);
    }
    this.chartValues(history);
    this.demoPrice(price);
    this.marketTimer = window.setInterval(() => {
      const nextPrice = Number((this.demoPrice() * (1 + (Math.random() - 0.48) * 0.008)).toFixed(2));
      const nextHistory = [...this.chartValues(), nextPrice].slice(-20);
      this.demoPrice(nextPrice);
      this.chartValues(nextHistory);
    }, 5000);
  }

  placeOrder = async (): Promise<void> => {
    const product = this.selectedProduct();
    this.error(""); this.notice("");
    if (!product) return;
    if (!this.dematActive()) { this.error("Open and verify a Demat account before buying Stocks or ETFs."); return; }
    const linkedAccountId = Number(this.profile().linkedAccountId);
    if (!Number.isInteger(linkedAccountId) || linkedAccountId <= 0) { this.error("Your active Demat profile needs a linked bank account before you can place a market order."); return; }
    const quantity = Number(this.stockUnits());
    const amount = this.calculatedAmount();
    if (!Number.isFinite(quantity) || quantity <= 0) { this.error("Enter the number of units you want to buy."); return; }
    if (amount <= 0) { this.error("The product price is unavailable. Please choose the product again."); return; }
    if (amount > Number(this.wallet().balance)) { this.error("Your Trading Wallet balance is lower than this stock or ETF order."); return; }
    try {
      const result = await this.purchase.start({
        // The selector is intentionally not shown: this is only the
        // Demat-linked account recorded for audit by the Investment service.
        // The funds are still debited exclusively from the Trading Wallet.
        productId: product.id, accountId: linkedAccountId, amount, quantity, mode: "LUMPSUM", exchange: this.exchange()
      });
      this.notice(result.message ?? "Purchase OTP sent. Enter it to authorise the market order.");
    } catch (error) { this.error(this.message(error)); }
  };

  authorisePurchase = async (): Promise<void> => {
    this.error(""); this.notice("");
    try {
      const result = await this.purchase.authorise();
      this.wallet(await api.get<Wallet>("/api/investments/wallet")); this.stockUnits(""); this.notice(result.message ?? "Market order authorised.");
    } catch (error) { this.error(this.message(error)); }
  };

  private message(error: unknown): string { return error instanceof Error ? error.message : "The Investment service could not complete this request."; }
}

export = StocksViewModel;
