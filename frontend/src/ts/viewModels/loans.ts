import * as ko from "knockout";
import { api, GATEWAY_URL, getToken } from "../services/api";
import * as AccUtils from "../accUtils";

interface Loan { loanId: number; loanAccountNumber: string; loanType: string; loanAmount: number; outstandingAmount: number; interestRate: number; tenureMonths: number; installmentsPaid: number; emiAmount: number; nextDueDate?: string | null; status: string; }
interface Account { accountId: number; accountNumber: string; accountType: string; availableBalance: number; status?: string; }
interface Emi { scheduleId: number; installmentNumber: number; dueDate: string; openingPrincipal: number; emiAmount: number; principalComponent: number; interestComponent: number; closingPrincipal: number; status: string; paidAt?: string | null; }
interface Repayment { repaymentId: number; paymentOrderId: number; sourceAccountId: number; repaymentType: string; amount: number; outstandingBefore: number; outstandingAfter: number; principalComponent: number; interestComponent: number; referenceNumber: string; paymentDate: string; }
interface PaymentOrder { paymentOrderId: number; loanId: number; paymentType: string; amount: number; referenceNumber: string; status: string; otpExpiresAt: string; message?: string; }
interface Receipt { paymentOrderId: number; loanId: number; paymentType: string; amount: number; referenceNumber: string; status: string; completedAt: string; message?: string; }
interface StatementEntry { paymentDate: string; repaymentType: string; referenceNumber: string; amount: number; principalComponent: number; interestComponent: number; outstandingBefore: number; outstandingAfter: number; }
interface Statement { loanId: number; loanAccountNumber: string; loanType: string; loanStatus: string; statementFrom: string; statementTo: string; originalLoanAmount: number; currentOutstandingAmount: number; annualInterestRate: number; emiAmount: number; nextDueDate?: string | null; totalEmiPayments: number; totalPartPayments: number; totalForeclosures: number; entries: StatementEntry[]; }

class LoansViewModel {
  loans = ko.observableArray<Loan>([]);
  accounts = ko.observableArray<Account>([]);
  selectedLoan = ko.observable<Loan | null>(null);
  schedule = ko.observableArray<Emi>([]);
  schedulePage = ko.observable(0);
  readonly schedulePageSize = 4;
  repayments = ko.observableArray<Repayment>([]);
  statement = ko.observable<Statement | null>(null);
  loading = ko.observable(true);
  detailsLoading = ko.observable(false);
  statementLoading = ko.observable(false);
  submitting = ko.observable(false);
  sendingStatement = ko.observable(false);
  error = ko.observable("");
  notice = ko.observable("");
  paymentType = ko.observable("EMI");
  paymentAmount = ko.observable("");
  sourceAccountId = ko.observable<string>("");
  paymentOrder = ko.observable<PaymentOrder | null>(null);
  otp = ko.observable("");
  receipt = ko.observable<Receipt | null>(null);
  resendSecondsRemaining = ko.observable(0);
  private resendTimer: number | null = null;
  statementYear = ko.observable(String(new Date().getFullYear()));
  statementMonth = ko.observable(String(new Date().getMonth() + 1));
  months = Array.from({ length: 12 }, (_, index) => ({ value: String(index + 1), label: new Date(2026, index, 1).toLocaleDateString("en-IN", { month: "long" }) }));

  activeLoans = ko.pureComputed(() => this.loans().filter((loan) => loan.status === "ACTIVE"));
  totalOutstanding = ko.pureComputed(() => this.loans().reduce((total, loan) => total + Number(loan.outstandingAmount || 0), 0));
  totalSchedulePrincipal = ko.pureComputed(() => this.schedule().reduce((total, item) => total + Number(item.principalComponent || 0), 0));
  totalScheduleInterest = ko.pureComputed(() => this.schedule().reduce((total, item) => total + Number(item.interestComponent || 0), 0));
  schedulePageCount = ko.pureComputed(() => Math.max(1, Math.ceil(this.schedule().length / this.schedulePageSize)));
  visibleSchedule = ko.pureComputed(() => {
    const start = this.schedulePage() * this.schedulePageSize;
    return this.schedule().slice(start, start + this.schedulePageSize);
  });
  scheduleRangeLabel = ko.pureComputed(() => {
    const total = this.schedule().length;
    if (!total) return "";
    const start = this.schedulePage() * this.schedulePageSize + 1;
    return `Showing EMIs ${start}–${Math.min(start + this.schedulePageSize - 1, total)} of ${total}`;
  });
  hasPreviousSchedulePage = ko.pureComputed(() => this.schedulePage() > 0);
  hasNextSchedulePage = ko.pureComputed(() => this.schedulePage() < this.schedulePageCount() - 1);
  selectedAccount = ko.pureComputed(() => this.accounts().find((account) => String(account.accountId) === String(this.sourceAccountId())) || null);
  paymentOrderActive = ko.pureComputed(() => !!this.paymentOrder());
  canPay = ko.pureComputed(() => !!this.selectedLoan() && this.selectedLoan()!.status === "ACTIVE" && !this.submitting());
  canResendOtp = ko.pureComputed(() => !!this.paymentOrder() && !this.submitting() && this.resendSecondsRemaining() === 0);
  resendButtonText = ko.pureComputed(() => { const seconds = this.resendSecondsRemaining(); return this.submitting() ? "Sending…" : seconds > 0 ? "Resend OTP in " + seconds + "s" : "Resend OTP"; });
  accountLabel = (account: Account): string => `${account.accountType} · •••• ${String(account.accountNumber).slice(-4)} · ${this.formatInr(account.availableBalance)}`;
  formatInr = (amount: number): string => `INR ${Number(amount ?? 0).toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
  loanTypeLabel = (type: string): string => type ? `${type.charAt(0)}${type.slice(1).toLowerCase()} loan` : "Loan";
  dateLabel = (date?: string | null): string => date ? new Date(`${date.includes("T") ? date : `${date}T00:00:00`}`).toLocaleDateString("en-IN", { day: "2-digit", month: "short", year: "numeric" }) : "—";
  dateTimeLabel = (date?: string | null): string => date ? new Date(date).toLocaleString("en-IN", { day: "2-digit", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit" }) : "—";
  paymentTypeLabel = (type: string): string => ({ EMI: "Pay next EMI", PART_PAYMENT: "Make a part payment", FORECLOSURE: "Foreclose loan" }[type] || type);
  statusClass = (status: string): string => String(status || "").toLowerCase().replaceAll("_", "-");
  isPaid = (item: Emi): boolean => item.status === "PAID";

  loanLink = (route: string, loanId: number): string => "?ojr=" + route + "&loanId=" + loanId;
  openLoan = (route: string, loan: Loan): void => { window.location.href = this.loanLink(route, loan.loanId); };
  backToOverview = (): void => { window.location.href = "?ojr=loans"; };
  previousSchedulePage = (): void => this.schedulePage(Math.max(0, this.schedulePage() - 1));
  nextSchedulePage = (): void => this.schedulePage(Math.min(this.schedulePageCount() - 1, this.schedulePage() + 1));

  async connected(): Promise<void> {
    AccUtils.announce("Loans page loaded.");
    document.title = "Loans | O-Bank";
    await this.reload();
  }

  disconnected(): void { this.stopResendCooldown(); }

  reload = async (): Promise<void> => {
    this.loading(true); this.error("");
    try {
      const [loans, accounts] = await Promise.all([api.get<Loan[]>("/api/loans"), api.get<Account[]>("/api/accounts/summary")]);
      this.loans(loans); this.accounts(accounts);
      if (!this.sourceAccountId() || !accounts.some((account) => String(account.accountId) === String(this.sourceAccountId()))) this.sourceAccountId(String(accounts[0]?.accountId ?? ""));
      const remembered = this.selectedLoan();
      const requestedLoanId = new URLSearchParams(window.location.search).get("loanId");
      const current = requestedLoanId
        ? loans.find((loan) => loan.loanId === Number(requestedLoanId)) || null
        : loans.find((loan) => loan.loanId === remembered?.loanId) || loans[0] || null;
      if (requestedLoanId && !current) this.error("The selected loan is unavailable.");
      if (current) await this.selectLoan(current, true);
      else { this.selectedLoan(null); this.schedule([]); this.repayments([]); this.statement(null); }
    } catch (error) { this.error(this.message(error)); }
    finally { this.loading(false); }
  };

  selectLoan = async (loan: Loan, preservePayment = false): Promise<void> => {
    if (!preservePayment) { this.paymentOrder(null); this.receipt(null); this.otp(""); }
    this.selectedLoan(loan); this.schedule([]); this.schedulePage(0); this.repayments([]); this.statement(null);
    this.paymentType("EMI"); this.paymentAmount(String(loan.emiAmount ?? ""));
    this.detailsLoading(true); this.error("");
    try {
      const [details, schedule, repayments] = await Promise.all([
        api.get<Loan>(`/api/loans/${loan.loanId}`),
        api.get<Emi[]>(`/api/loans/${loan.loanId}/emi-schedule`),
        api.get<Repayment[]>(`/api/loans/${loan.loanId}/repayments`)
      ]);
      if (this.selectedLoan()?.loanId === loan.loanId) {
        this.selectedLoan(details); this.schedule(schedule);
        const nextUnpaidIndex = schedule.findIndex((item) => item.status !== "PAID");
        this.schedulePage(Math.floor(Math.max(0, nextUnpaidIndex) / this.schedulePageSize));
        this.repayments(repayments); this.setSuggestedAmount();
      }
    } catch (error) { this.error(this.message(error)); }
    finally { this.detailsLoading(false); }
  };

  setSuggestedAmount = (): void => {
    const loan = this.selectedLoan(); if (!loan) return;
    if (this.paymentType() === "EMI") this.paymentAmount(String(loan.emiAmount));
    if (this.paymentType() === "FORECLOSURE") this.paymentAmount(String(loan.outstandingAmount));
    if (this.paymentType() === "PART_PAYMENT") this.paymentAmount("");
  };

  initiatePayment = async (): Promise<void> => {
    const loan = this.selectedLoan(); const account = this.selectedAccount(); const amount = Number(this.paymentAmount());
    this.error(""); this.notice(""); this.receipt(null);
    if (!loan || !account) { this.error("Choose the loan and account to debit."); return; }
    if (loan.status !== "ACTIVE") { this.error("Only an active loan can receive payments."); return; }
    if (!Number.isFinite(amount) || amount <= 0) { this.error("Enter a payment amount greater than zero."); return; }
    if (this.paymentType() === "EMI" && amount !== Number(loan.emiAmount)) { this.error(`The next EMI must be exactly ${this.formatInr(loan.emiAmount)}.`); return; }
    if (this.paymentType() === "PART_PAYMENT" && amount >= Number(loan.outstandingAmount)) { this.error("For the full outstanding balance, choose Foreclose loan."); return; }
    if (this.paymentType() === "FORECLOSURE" && amount !== Number(loan.outstandingAmount)) { this.error(`Foreclosure must be exactly ${this.formatInr(loan.outstandingAmount)}.`); return; }
    this.submitting(true);
    try {
      const result = await api.post<PaymentOrder>("/api/loan-payments/initiate", { sourceAccountId: account.accountId, loanId: loan.loanId, paymentType: this.paymentType(), amount });
      this.paymentOrder(result); this.otp(""); this.startResendCooldown(); this.notice(result.message || "A six-digit OTP was sent to your registered email.");
      AccUtils.announce("Loan payment OTP sent.");
    } catch (error) { this.error(this.message(error)); }
    finally { this.submitting(false); }
  };

  resendOtp = async (): Promise<void> => {
    const order = this.paymentOrder(); if (!order || !this.canResendOtp()) return;
    this.submitting(true); this.error(""); this.notice("");
    try { const result = await api.post<PaymentOrder>(`/api/loan-payments/${order.paymentOrderId}/resend-otp`); this.paymentOrder(result); this.startResendCooldown(); this.notice(result.message || "A replacement OTP was sent to your registered email."); }
    catch (error) { this.error(this.message(error)); }
    finally { this.submitting(false); }
  };

  confirmPayment = async (): Promise<void> => {
    const order = this.paymentOrder(); const otp = this.otp().trim(); if (!order || this.submitting()) return;
    this.error(""); this.notice("");
    if (!/^\d{6}$/.test(otp)) { this.error("Enter the six-digit OTP from your registered email."); return; }
    this.submitting(true);
    try {
      const result = await api.post<Receipt>(`/api/loan-payments/${order.paymentOrderId}/confirm`, { otp });
      this.receipt(result); this.paymentOrder(null); this.otp(""); this.stopResendCooldown(); this.notice(result.message || "Loan payment completed successfully.");
      await this.reload();
      AccUtils.announce("Loan payment completed successfully.");
    } catch (error) { this.error(this.message(error)); }
    finally { this.submitting(false); }
  };

  cancelPayment = (): void => { if (!this.submitting()) { this.paymentOrder(null); this.otp(""); this.stopResendCooldown(); this.notice(""); } };

  private startResendCooldown(): void {
    this.stopResendCooldown();
    this.resendSecondsRemaining(30);
    this.resendTimer = window.setInterval(() => {
      const remaining = this.resendSecondsRemaining() - 1;
      this.resendSecondsRemaining(Math.max(0, remaining));
      if (remaining <= 0) this.stopResendCooldown();
    }, 1000);
  }

  private stopResendCooldown(): void {
    if (this.resendTimer !== null) window.clearInterval(this.resendTimer);
    this.resendTimer = null;
    this.resendSecondsRemaining(0);
  }

  loadStatement = async (): Promise<void> => {
    const loan = this.selectedLoan(); const year = Number(this.statementYear()); const month = Number(this.statementMonth()); if (!loan) return;
    this.statementLoading(true); this.error("");
    try { this.statement(await api.get<Statement>(`/api/loans/${loan.loanId}/monthly-statement?year=${year}&month=${month}`)); }
    catch (error) { this.statement(null); this.error(this.message(error)); }
    finally { this.statementLoading(false); }
  };

  downloadStatement = async (): Promise<void> => {
    const loan = this.selectedLoan(); if (!loan || this.statementLoading()) return;
    this.error(""); this.notice(""); this.statementLoading(true);
    try {
      const token = getToken(); if (!token) throw new Error("Please sign in again to download a statement.");
      const response = await fetch(`${GATEWAY_URL}/api/loans/${loan.loanId}/monthly-statement/pdf?year=${Number(this.statementYear())}&month=${Number(this.statementMonth())}`, { headers: { Authorization: `Bearer ${token}` } });
      if (!response.ok) throw new Error("The loan statement PDF could not be downloaded. Please try again.");
      const url = URL.createObjectURL(await response.blob()); const link = document.createElement("a"); link.href = url; link.download = `O-Bank-Loan-Statement-${loan.loanId}-${this.statementYear()}-${this.statementMonth().padStart(2, "0")}.pdf`; document.body.appendChild(link); link.click(); link.remove(); window.setTimeout(() => URL.revokeObjectURL(url), 1000);
      this.notice("Your loan statement PDF download has started.");
    } catch (error) { this.error(this.message(error)); }
    finally { this.statementLoading(false); }
  };

  emailStatement = async (): Promise<void> => {
    const loan = this.selectedLoan(); if (!loan || this.sendingStatement()) return;
    this.sendingStatement(true); this.error(""); this.notice("");
    try { const result = await api.post<{ message?: string }>(`/api/loans/${loan.loanId}/monthly-statement/email?year=${Number(this.statementYear())}&month=${Number(this.statementMonth())}`); this.notice(result.message || "The monthly loan statement was sent to your registered email."); }
    catch (error) { this.error(this.message(error)); }
    finally { this.sendingStatement(false); }
  };

  private message(error: unknown): string { return error instanceof Error ? error.message : "The Loans service could not complete this request. Please try again."; }
}
export = LoansViewModel;
