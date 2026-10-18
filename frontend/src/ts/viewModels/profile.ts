import * as ko from "knockout";
import { api } from "../services/api";
import { logout } from "../services/auth";
import * as AccUtils from "../accUtils";

interface CustomerProfile {
  fullName: string;
  email: string;
  phone?: string;
  status: string;
  netBankingStatus: string;
}

interface AccountSummary {
  accountNumber: string;
}

class ProfileViewModel {
  profile = ko.observable<CustomerProfile | null>(null);
  accountNumber = ko.observable("");
  error = ko.observable("");
  loading = ko.observable(true);

  signOut = (): void => {
    logout();
    window.location.assign("?ojr=login");
  };

  async connected(): Promise<void> {
    AccUtils.announce("Profile page loaded.");
    document.title = "Profile | O-Bank";
    try {
      const [profile, accounts] = await Promise.all([
        api.get<CustomerProfile>("/api/users/profile"),
        api.get<AccountSummary[]>("/api/accounts/summary")
      ]);
      this.profile(profile);
      this.accountNumber(accounts[0]?.accountNumber ?? "Not available");
    } catch (error) {
      this.error(error instanceof Error ? error.message : "Unable to load your profile.");
    } finally {
      this.loading(false);
    }
  }
}

export = ProfileViewModel;
