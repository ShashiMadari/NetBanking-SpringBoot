import AdminForexViewModel = require("./admin-forex");

class AdminForexKycViewModel extends AdminForexViewModel {
  connected(): void {
    super.connected();
    document.title = "Customer Forex KYC | O-Bank";
  }
}
export = AdminForexKycViewModel;
