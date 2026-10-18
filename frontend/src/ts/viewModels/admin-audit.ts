import AdminViewModel = require("./admin");

class AdminAuditViewModel extends AdminViewModel {
  constructor() {
    super();
    this.selectedAdminSection("audit");
  }
}
export = AdminAuditViewModel;
