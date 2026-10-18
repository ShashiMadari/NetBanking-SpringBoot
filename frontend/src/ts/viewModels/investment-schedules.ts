import * as ko from "knockout";
import { api } from "../services/api";
import * as AccUtils from "../accUtils";
interface Account { accountId: number; accountNumber: string; accountType: string; }
interface Product { id: number; name: string; productType: string; minimumAmount: number; }
interface Schedule { scheduleId: number; productName: string; scheduleType: string; amount: number; debitDay: number; nextDebitDate: string; tenureMonths?: number | null; status: string; }
class InvestmentSchedulesViewModel {
  schedules = ko.observableArray<Schedule>([]); products = ko.observableArray<Product>([]); accounts = ko.observableArray<Account>([]); productId = ko.observable(""); accountId = ko.observable(""); amount = ko.observable(""); debitDay = ko.observable(""); tenureMonths = ko.observable(""); loading = ko.observable(true); submitting = ko.observable(false); error = ko.observable(""); notice = ko.observable("");
  eligibleProducts = ko.pureComputed(() => this.products().filter((p) => ["MUTUAL_FUND", "RD", "PPF"].includes(p.productType)));
  accountLabel = (a: Account): string => `${a.accountType} · •••• ${a.accountNumber.slice(-4)}`;
  productLabel = (p: Product): string => `${p.name} · minimum INR ${Number(p.minimumAmount).toLocaleString("en-IN")}`;
  formatInr = (v: number): string => `INR ${Number(v ?? 0).toLocaleString("en-IN", { minimumFractionDigits: 2 })}`;
  termLabel = (s: Schedule): string => s.tenureMonths ? `${s.tenureMonths} months` : "No fixed end";
  scheduleName = (s: Schedule): string => s.scheduleType === "RD" ? "Recurring Deposit" : s.productName;
  async connected(): Promise<void> { AccUtils.announce("Investment schedules loaded."); document.title = "Investment Schedules | O-Bank"; await this.reload(); }
  async reload(): Promise<void> { this.loading(true); this.error(""); try { const [s, p, a] = await Promise.all([api.get<Schedule[]>("/api/investments/schedules"), api.get<Product[]>("/api/investments/products"), api.get<Account[]>("/api/accounts/summary")]); this.schedules(s); this.products(p); this.accounts(a); this.productId(String(this.eligibleProducts()[0]?.id ?? "")); this.accountId(String(a[0]?.accountId ?? "")); } catch (e) { this.error(this.message(e)); } finally { this.loading(false); } }
  create = async (): Promise<void> => {
    const amount = Number(this.amount());
    const debitDay = Number(this.debitDay());
    const term = this.tenureMonths() ? Number(this.tenureMonths()) : null;
    const product = this.eligibleProducts().find((item) => String(item.id) === String(this.productId()));
    this.error(""); this.notice("");
    if (!Number.isFinite(amount) || amount <= 0 || !Number.isInteger(debitDay) || debitDay < 1 || debitDay > 28) {
      this.error("Enter a valid amount and a debit day from 1 to 28."); return;
    }
    if (term !== null && (!Number.isInteger(term) || term < 1 || term > 600)) {
      this.error("Choose a tenure from 1 to 600 months."); return;
    }
    if (product?.productType === "RD" && (term === null || term > 120)) {
      this.error("Choose an RD tenure from 1 to 120 months."); return;
    }
    this.submitting(true);
    try {
      const r = await api.post<{ message?: string }>("/api/investments/schedules", {
        productId: Number(this.productId()), accountId: Number(this.accountId()), amount, debitDay, tenureMonths: term
      });
      this.notice(r.message ?? "Recurring investment schedule is active.");
      this.amount(""); this.debitDay(""); this.tenureMonths("");
      await this.reload();
    } catch (e) { this.error(this.message(e)); } finally { this.submitting(false); }
  };
  changeStatus = async (s: Schedule, action: "pause" | "resume" | "cancel"): Promise<void> => { this.error(""); this.notice(""); this.submitting(true); try { const r = action === "cancel" ? await api.delete<{ message?: string }>(`/api/investments/schedules/${s.scheduleId}`) : await api.patch<{ message?: string }>(`/api/investments/schedules/${s.scheduleId}/${action}`); this.notice(r.message ?? "Schedule updated."); await this.reload(); } catch (e) { this.error(this.message(e)); } finally { this.submitting(false); } };
  private message(e: unknown): string { return e instanceof Error ? e.message : "The Investment service could not complete this request."; }
}
export = InvestmentSchedulesViewModel;
