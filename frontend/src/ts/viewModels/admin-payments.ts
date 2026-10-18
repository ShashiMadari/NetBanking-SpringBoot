import AdminViewModel = require("./admin");

class AdminPaymentsViewModel extends AdminViewModel {
  constructor() {
    super();
    this.selectedAdminSection("payments");
  }
}
export = AdminPaymentsViewModel;
