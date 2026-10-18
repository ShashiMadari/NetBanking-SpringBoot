import * as ko from "knockout";
import { api, GATEWAY_URL, getToken } from "../services/api";
import * as AccUtils from "../accUtils";

interface StatementEntry { orderId: number; orderKind: string; productName: string; amount: number; quantity: number; status: string; referenceNumber: string; requestedAt: string; failureReason?: string; }
interface StatementHolding { productName: string; quantity: number; investedAmount: number; currentValue: number; gainLoss: number; }
interface InvestmentStatement { statementMonth: string; totalSuccessfulPurchases: number; totalCompletedRedemptions: number; netInvestmentForMonth: number; currentInvestedAmount: number; currentPortfolioValue: number; currentGainLoss: number; entries: StatementEntry[]; activeHoldings: StatementHolding[]; }

class InvestmentStatementsViewModel {
  year = ko.observable(String(new Date().getFullYear()));
  month = ko.observable(String(new Date().getMonth() + 1));
  statement = ko.observable<InvestmentStatement | null>(null);
  loading = ko.observable(false);
  sending = ko.observable(false);
  error = ko.observable("");
  notice = ko.observable("");
  months = Array.from({ length: 12 }, (_, index) => ({ value: String(index + 1), label: new Date(2000, index, 1).toLocaleString("en-IN", { month: "long" }) }));
  formatInr = (value: number | undefined): string => `INR ${Number(value ?? 0).toLocaleString("en-IN", { minimumFractionDigits: 2 })}`;
  gainClass = (value: number): string => value >= 0 ? "investment-positive" : "investment-negative";

  async connected(): Promise<void> { AccUtils.announce("Investment statements loaded."); document.title = "Investment Statements | O-Bank"; await this.load(); }
  load = async (): Promise<void> => {
    this.loading(true); this.error(""); this.notice("");
    try { this.statement(await api.get<InvestmentStatement>(`/api/investments/statements/monthly?year=${Number(this.year())}&month=${Number(this.month())}`)); }
    catch (error) { this.error(this.message(error)); }
    finally { this.loading(false); }
  };
  emailStatement = async (): Promise<void> => {
    this.sending(true); this.error(""); this.notice("");
    try { const result = await api.post<{ message?: string }>(`/api/investments/statements/monthly/email?year=${Number(this.year())}&month=${Number(this.month())}`); this.notice(result.message ?? "Your investment statement was sent to your registered email."); }
    catch (error) { this.error(this.message(error)); }
    finally { this.sending(false); }
  };
  downloadStatement = async (): Promise<void> => {
    this.error(""); this.notice("");
    try {
      const token = getToken();
      const response = await fetch(`${GATEWAY_URL}/api/investments/statements/monthly/pdf?year=${Number(this.year())}&month=${Number(this.month())}`, { headers: token ? { Authorization: `Bearer ${token}` } : {} });
      if (!response.ok) throw new Error("The statement PDF could not be downloaded. Please try again.");
      const url = URL.createObjectURL(await response.blob());
      const link = document.createElement("a"); link.href = url; link.download = `O-Bank-Investment-Statement-${this.year()}-${this.month().padStart(2, "0")}.pdf`; document.body.appendChild(link); link.click(); link.remove(); URL.revokeObjectURL(url);
      this.notice("Your investment statement PDF download has started.");
    } catch (error) { this.error(this.message(error)); }
  };
  private message(error: unknown): string { return error instanceof Error ? error.message : "The Investment service could not complete this request."; }
}
export = InvestmentStatementsViewModel;
