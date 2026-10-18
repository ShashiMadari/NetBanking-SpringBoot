import * as ko from "knockout";
import * as AccUtils from "../accUtils";
import { api } from "../services/api";

interface Account { accountId: number; accountNumber: string; accountType: string; availableBalance: number; bookBalance?: number; lienAmount?: number; currency: string; status: string; branchName: string; }
interface Dashboard { fullName: string; email: string; accounts: Account[]; }

class DashboardViewModel {
  dashboard = ko.observable<Dashboard | null>(null);
  accounts = ko.observableArray<Account>([]);
  selectedAccountId = ko.observable("");
  selectedAccount = ko.pureComputed(() => this.accounts().find((account) => String(account.accountId) === String(this.selectedAccountId())) ?? null);
  totalAvailableBalance = ko.pureComputed(() => this.accounts().reduce((total, account) => total + (Number(account.availableBalance) || 0), 0));
  balancesVisible = ko.observable(false);
  loading = ko.observable(true);
  error = ko.observable("");

  async connected(): Promise<void> {
    AccUtils.announce("Dashboard page loaded.");
    document.title = "Dashboard | O-Bank";
    this.loading(true);
    try {
      const [dashboard, accounts] = await Promise.all([api.get<Dashboard>("/api/dashboard"), api.get<Account[]>("/api/accounts/summary")]);
      this.dashboard(dashboard);
      this.accounts(accounts);
      if (accounts.length) {
        this.selectedAccountId(String(accounts[0].accountId));
      }
    } catch (error) {
      this.error(error instanceof Error ? error.message : "Unable to load your banking overview.");
    } finally { this.loading(false); }
  }

  accountLabel(account: Account): string { return `${account.accountType} · •••• ${account.accountNumber.slice(-4)}`; }
  toggleBalances = (): void => this.balancesVisible(!this.balancesVisible());
  maskedAccount(account: Account | null): string { return account ? `•••• ${account.accountNumber.slice(-4)}` : ""; }
  formatInr(value: number | undefined): string { return new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", maximumFractionDigits: 2 }).format(Number(value) || 0); }
  displayedInr(value: number | undefined): string { return this.balancesVisible() ? this.formatInr(value) : "INR ••••••"; }
  displayedBalance(account: Account | null): string {
    return this.balancesVisible() ? this.formattedBalance(account) : `${account?.currency || "INR"} ••••••`;
  }
  formattedBalance(account: Account | null): string { return account ? new Intl.NumberFormat("en-IN", { style: "currency", currency: account.currency || "INR", maximumFractionDigits: 2 }).format(Number(account.availableBalance) || 0) : "—"; }
}
export = DashboardViewModel;
