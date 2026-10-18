import * as ko from "knockout";
import { api } from "../services/api";
import { PurchaseAuthorisation } from "../services/purchaseAuthorisation";
import * as AccUtils from "../accUtils";

interface Account { accountId: number; accountNumber: string; accountType: string; availableBalance: number; currency: string; }
interface Product { id: number; name: string; productType: string; minimumAmount: number; annualRate?: number; tenureMonths?: number; }

class FixedDepositsViewModel {
  accounts = ko.observableArray<Account>([]);
  product = ko.observable<Product | null>(null);
  loading = ko.observable(true);
  error = ko.observable("");
  notice = ko.observable("");
  purchase = new PurchaseAuthorisation();
  purchaseAccountId = ko.observable("");
  depositAmount = ko.observable("");
  tenureMonths = ko.observable("12");

  accountLabel = (account: Account): string => `${account.accountType} · •••• ${account.accountNumber.slice(-4)}`;
  fixedInterestRate = ko.pureComputed(() => Number(this.product()?.annualRate ?? 0));
  formatInr = (amount: number | undefined): string => `INR ${Number(amount ?? 0).toLocaleString("en-IN", { minimumFractionDigits: 2 })}`;

  async connected(): Promise<void> {
    AccUtils.announce("Fixed Deposits page loaded.");
    document.title = "Fixed Deposits | O-Bank";
    try {
      const [products, accounts] = await Promise.all([
        api.get<Product[]>("/api/investments/products"),
        api.get<Account[]>("/api/accounts/summary")
      ]);
      const fixedDeposit = products.find((item) => item.productType === "FD");
      if (!fixedDeposit) throw new Error("No Fixed Deposit product is currently available.");
      this.product(fixedDeposit);
      this.accounts(accounts);
      this.purchaseAccountId(String(accounts[0]?.accountId ?? ""));
    } catch (error) { this.error(this.message(error)); } finally { this.loading(false); }
  }

  submitDeposit = async (): Promise<void> => {
    const product = this.product();
    this.error(""); this.notice("");
    if (!product) return;
    const amount = Number(this.depositAmount());
    const tenureMonths = Number(this.tenureMonths());
    if (!Number.isFinite(amount) || amount < Number(product.minimumAmount)) { this.error(`Enter at least ${this.formatInr(product.minimumAmount)} for this Fixed Deposit.`); return; }
    if (!Number.isInteger(tenureMonths) || tenureMonths < 12 || tenureMonths > 600) { this.error("Choose a Fixed Deposit tenure from 12 to 600 months."); return; }
    try {
      const result = await this.purchase.start({
        productId: product.id,
        accountId: Number(this.purchaseAccountId()),
        amount,
        mode: "LUMPSUM",
        exchange: null,
        tenureMonths
      });
      this.depositAmount(""); this.notice(result.message ?? "Purchase OTP sent. Enter it to authorise the fixed deposit.");
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

export = FixedDepositsViewModel;
