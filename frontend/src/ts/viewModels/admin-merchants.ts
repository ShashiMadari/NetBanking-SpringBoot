import AdminViewModel = require("./admin");

class AdminMerchantsViewModel extends AdminViewModel {
  constructor() {
    super();
    this.selectedAdminSection("merchants");
  }
}
export = AdminMerchantsViewModel;
