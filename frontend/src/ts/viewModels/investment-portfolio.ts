import * as ko from "knockout";
import { api } from "../services/api";
import * as AccUtils from "../accUtils";

interface Account { accountId: number; accountNumber: string; accountType: string; }
interface Holding { id: number; productName: string; productType: string; quantity: number; investedAmount: number; currentValue: number; gainLoss: number; status: string; tenureMonths?: number | null; maturityDate?: string | null; }
interface Dashboard { holdings: Holding[]; investedAmount: number; currentValue: number; gainLoss: number; }
interface SettlementOrder { orderId: number; productName: string; productType: string; amount: number; referenceNumber: string; debitedAt?: string; }
class InvestmentPortfolioViewModel {
  holdings = ko.observableArray<Holding>([]); accounts = ko.observableArray<Account>([]);
  investedAmount = ko.observable(0); currentValue = ko.observable(0); gainLoss = ko.observable(0);
  settlingOrders = ko.observableArray<SettlementOrder>([]);
  selectedHolding = ko.observable<Holding | null>(null); redemptionAccountId = ko.observable(""); redemptionQuantity = ko.observable(""); redemptionOrderId = ko.observable(""); redemptionOtp = ko.observable(""); submitting = ko.observable(false); loading = ko.observable(true); error = ko.observable(""); notice = ko.observable("");
  accountLabel = (a: Account): string => `${a.accountType} · •••• ${a.accountNumber.slice(-4)}`;
  formatInr = (v: number | undefined): string => `INR ${Number(v ?? 0).toLocaleString("en-IN", { minimumFractionDigits: 2 })}`;
  formatQuantity = (v: number | undefined): string => Number(v ?? 0).toLocaleString("en-IN", { maximumFractionDigits: 4 });
  gainClass = (v: number): string => v >= 0 ? "investment-positive" : "investment-negative";
  holdingName = (holding: Holding): string => holding.productType === "FD" ? "Fixed Deposit" : holding.productName;
  fdTerm = (holding: Holding): string => {
    if (holding.productType !== "FD" || !holding.tenureMonths) return "";
    const maturity = holding.maturityDate ? new Date(holding.maturityDate) : null;
    const dateText = maturity && !Number.isNaN(maturity.getTime())
      ? ` · Matures ${maturity.toLocaleDateString("en-IN", { dateStyle: "medium" })}` : "";
    return `${holding.tenureMonths}-month term${dateText}`;
  };
  settlementDue = (_order: SettlementOrder): string => "Awaiting automatic allotment.";
  async connected(): Promise<void> { AccUtils.announce("Investment portfolio loaded."); document.title = "Investment Portfolio | O-Bank"; await this.reload(); }
  async reload(): Promise<void> { this.loading(true); this.error(""); try { const [d, a, settling] = await Promise.all([api.get<Dashboard>("/api/investments/dashboard"), api.get<Account[]>("/api/accounts/summary"), api.get<SettlementOrder[]>("/api/investments/orders?status=SETTLEMENT_PENDING")]); this.holdings(d.holdings ?? []); this.investedAmount(Number(d.investedAmount ?? 0)); this.currentValue(Number(d.currentValue ?? 0)); this.gainLoss(Number(d.gainLoss ?? 0)); this.accounts(a); this.settlingOrders(settling ?? []); this.redemptionAccountId(String(a[0]?.accountId ?? "")); } catch (e) { this.error(this.message(e)); } finally { this.loading(false); } }
  chooseHolding = (h: Holding): void => { this.error(""); this.notice(""); this.selectedHolding(h); this.redemptionQuantity(String(h.quantity)); this.redemptionOrderId(""); this.redemptionOtp(""); };
  sendRedemptionOtp = async (): Promise<void> => { const h = this.selectedHolding(); const q = Number(this.redemptionQuantity()); this.error(""); this.notice(""); if (!h) return; if (!Number.isFinite(q) || q <= 0 || q > Number(h.quantity)) { this.error("Enter a quantity up to the units currently held."); return; } try { const r = await api.post<{ orderId: number; calculatedAmount: number; message?: string }>(`/api/investments/holdings/${h.id}/redeem/send-otp`, { accountId: Number(this.redemptionAccountId()), quantity: q }); this.redemptionOrderId(String(r.orderId)); this.notice(r.message ?? `A redemption OTP was sent. Estimated payout: ${this.formatInr(r.calculatedAmount)}.`); } catch (e) { this.error(this.message(e)); } };
  resendRedemptionOtp = async (): Promise<void> => {
    if (!this.redemptionOrderId() || this.submitting()) return;
    this.error(""); this.notice("");
    try { const result = await api.post<{ message?: string }>("/api/investments/orders/" + Number(this.redemptionOrderId()) + "/resend-otp"); this.notice(result.message ?? "A replacement redemption OTP was sent."); }
    catch (error) { this.error(this.message(error)); }
  };

  authoriseRedemption = async (): Promise<void> => { if (this.submitting() || !this.redemptionOrderId()) return; this.error(""); this.notice(""); this.submitting(true); try { const r = await api.post<{ message?: string }>(`/api/investments/orders/${Number(this.redemptionOrderId())}/authorise`, { otp: this.redemptionOtp() }); this.notice(r.message ?? "Redemption authorised successfully."); this.selectedHolding(null); this.redemptionOrderId(""); this.redemptionOtp(""); await this.reload(); } catch (e) { this.error(this.message(e)); } finally { this.submitting(false); } };
  private message(e: unknown): string { return e instanceof Error ? e.message : "The Investment service could not complete this request."; }
}
export = InvestmentPortfolioViewModel;
