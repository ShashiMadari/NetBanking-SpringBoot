import AdminViewModel = require("./admin");

class AdminAccountsViewModel extends AdminViewModel {
  constructor() {
    super();
    this.selectedAdminSection("accounts");
  }
}
export = AdminAccountsViewModel;
