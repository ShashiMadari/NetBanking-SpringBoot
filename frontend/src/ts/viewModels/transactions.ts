import * as ko from "knockout";
import * as AccUtils from "../accUtils";
import { api, GATEWAY_URL, getToken } from "../services/api";

interface Account {
  accountId: number;
  accountNumber: string;
  accountType: string;
  availableBalance: number;
  currency: string;
}

interface TransactionStatement { transactions: Transaction[]; }

interface Transaction {
  transactionId: number;
  transactionType: string;
  entrySide: string;
  amount: number;
  referenceNumber: string;
  status: string;
  transactionDate: string;
}

class TransactionsViewModel {
  accounts = ko.observableArray<Account>([]);
  transactions = ko.observableArray<Transaction>([]);
  selectedAccountId = ko.observable("");
  loading = ko.observable(true);
  loadingTransactions = ko.observable(false);
  error = ko.observable("");
  fromDate = ko.observable("");
  toDate = ko.observable("");
  statementMonth = ko.observable(new Date().toISOString().slice(0, 7));
  statementBusy = ko.observable(false);
  statementNotice = ko.observable("");
  selectedAccount = ko.pureComputed(() => this.accounts().find((account) => String(account.accountId) === String(this.selectedAccountId())) ?? null);

  async connected(): Promise<void> {
    AccUtils.announce("Transaction history page loaded.");
    document.title = "Transactions | O-Bank";
    try {
      const accounts = await api.get<Account[]>("/api/accounts/summary");
      this.accounts(accounts);
      if (accounts.length > 0) {
        this.selectedAccountId(String(accounts[0].accountId));
        await this.loadTransactions();
      }
    } catch (error) {
      this.error(this.message(error));
    } finally {
      this.loading(false);
    }
  }

  accountChanged = (): void => {
    void this.loadTransactions();
  };

  applyDateFilter = (): void => {
    const from = this.fromDate();
    const to = this.toDate();
    if ((from && !to) || (!from && to)) {
      this.error("Choose both a From and To date.");
      return;
    }
    if (from && to && from > to) {
      this.error("From date cannot be after To date.");
      return;
    }
    void this.loadTransactions();
  };

  clearDateFilter = (): void => {
    this.fromDate("");
    this.toDate("");
    void this.loadTransactions();
  };

  loadTransactions = async (): Promise<void> => {
    const accountId = Number(this.selectedAccountId());
    if (!accountId) return;
    const from = this.fromDate();
    const to = this.toDate();
    this.error("");
    this.loadingTransactions(true);
    try {
      const query = from && to
        ? "?period=CUSTOM&from=" + encodeURIComponent(from) + "&to=" + encodeURIComponent(to) + "&size=100"
        : "?period=RECENT&size=100";
      const statement = await api.get<TransactionStatement>("/api/accounts/" + accountId + "/transactions/statement" + query);
      this.transactions(statement.transactions ?? []);
    } catch (error) {
      this.error(this.message(error));
      this.transactions([]);
    } finally {
      this.loadingTransactions(false);
    }
  };

  downloadMonthlyStatement = async (): Promise<void> => {
    const accountId = Number(this.selectedAccountId());
    const [year, month] = this.statementMonth().split("-").map(Number);
    const token = getToken();
    if (!accountId || !year || !month || !token) return;

    this.error("");
    this.statementNotice("");
    this.statementBusy(true);
    try {
      const response = await fetch(GATEWAY_URL + "/api/statements/monthly?accountId=" + accountId + "&year=" + year + "&month=" + month, { headers: { Authorization: "Bearer " + token } });
      if (!response.ok) throw new Error("Unable to download the monthly statement.");
      const file = new Blob([await response.blob()], { type: "application/pdf" });
      const url = URL.createObjectURL(file);
      const link = document.createElement("a");
      link.href = url;
      link.download = "O-Bank-Account-Statement-" + accountId + "-" + this.statementMonth() + ".pdf";
      link.click();
      URL.revokeObjectURL(url);
    } catch (error) {
      this.error(this.message(error));
    } finally {
      this.statementBusy(false);
    }
  };

  emailMonthlyStatement = async (): Promise<void> => {
    const accountId = Number(this.selectedAccountId());
    const [year, month] = this.statementMonth().split("-").map(Number);
    if (!accountId || !year || !month) return;

    this.error("");
    this.statementNotice("");
    this.statementBusy(true);
    try {
      const result = await api.post<{ message?: string }>("/api/statements/monthly/email?accountId=" + accountId + "&year=" + year + "&month=" + month, {});
      this.statementNotice(result.message || "Your monthly statement was sent to your registered email.");
      window.dispatchEvent(new Event("o-bank:notifications-changed"));
    } catch (error) {
      this.error(this.message(error));
    } finally {
      this.statementBusy(false);
    }
  };

  private message(error: unknown): string {
    return error instanceof Error ? error.message : "Unable to load transaction history.";
  }
}

export = TransactionsViewModel;
