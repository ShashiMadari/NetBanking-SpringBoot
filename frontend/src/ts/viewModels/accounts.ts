import * as ko from "knockout";
import * as AccUtils from "../accUtils";
import { api } from "../services/api";

interface Account {
  accountId: number;
  accountNumber: string;
  accountType: string;
  availableBalance: number;
  currency: string;
  status: string;
  branchName: string;
  ifscCode: string;
}

class AccountsViewModel {
  accounts = ko.observableArray<Account>([]);
  error = ko.observable("");
  loading = ko.observable(true);

  async connected(): Promise<void> {
    AccUtils.announce("Accounts page loaded.");
    document.title = "Accounts | O-Bank";
    try {
      this.accounts(await api.get<Account[]>("/api/accounts/summary"));
    } catch (error) {
      this.error(error instanceof Error ? error.message : "Unable to load your accounts.");
    } finally {
      this.loading(false);
    }
  }

  formattedAmount(account: Account): string {
    return `${account.currency} ${Number(account.availableBalance).toLocaleString("en-IN", { minimumFractionDigits: 2 })}`;
  }
}

export = AccountsViewModel;
