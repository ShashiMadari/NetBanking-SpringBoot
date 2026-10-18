import AdminViewModel = require("./admin");

class AdminBeneficiariesViewModel extends AdminViewModel {
  constructor() {
    super();
    this.selectedAdminSection("beneficiaries");
  }
}
export = AdminBeneficiariesViewModel;
