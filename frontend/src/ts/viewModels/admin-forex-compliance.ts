import AdminForexViewModel = require("./admin-forex");

class AdminForexComplianceViewModel extends AdminForexViewModel {
  connected(): void {
    super.connected();
    document.title = "Compliance review | O-Bank";
  }
}
export = AdminForexComplianceViewModel;
