import AdminForexViewModel = require("./admin-forex");

class AdminForexRatesViewModel extends AdminForexViewModel {
  connected(): void {
    super.connected();
    document.title = "Exchange-rate maintenance | O-Bank";
  }
}
export = AdminForexRatesViewModel;
