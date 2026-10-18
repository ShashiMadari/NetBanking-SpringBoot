import AdminViewModel = require("./admin");

class AdminPurchasesViewModel extends AdminViewModel {
  constructor() {
    super();
    this.selectedAdminSection("purchases");
  }
}
export = AdminPurchasesViewModel;
