import AdminViewModel = require("./admin");

class AdminForexViewModel extends AdminViewModel {
  constructor() {
    super();
    this.selectedAdminSection("forex");
  }
}
export = AdminForexViewModel;
