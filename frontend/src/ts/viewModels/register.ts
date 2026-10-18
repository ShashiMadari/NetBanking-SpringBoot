import * as ko from "knockout";
import * as AccUtils from "../accUtils";
import { completeRegistration, logout, startRegistration } from "../services/auth";

class RegisterViewModel {
  fullName = ko.observable(""); email = ko.observable(""); newPassword = ko.observable(""); confirmPassword = ko.observable(""); demoPassword = ko.observable("");
  started = ko.observable(false); busy = ko.observable(false); error = ko.observable(""); notice = ko.observable("");
  connected(): void { AccUtils.announce("Registration page loaded."); document.title = "Register | O-Bank"; }
  start = async (): Promise<void> => { this.error(""); this.notice(""); this.busy(true); try { const result = await startRegistration(this.fullName(), this.email()); this.started(true); this.notice(result.message || "A demo password was sent to your registered email address."); } catch(e) { this.error(e instanceof Error ? e.message : "Unable to begin registration."); } finally { this.busy(false); } };
  complete = async (): Promise<void> => { this.error(""); this.busy(true); try { await completeRegistration(this.email(), this.demoPassword(), this.newPassword(), this.confirmPassword()); window.location.assign("?ojr=login&registered=true"); } catch(e) { this.error(e instanceof Error ? e.message : "Unable to complete registration."); } finally { this.busy(false); } };
  goToLogin = (): void => { logout(); window.location.assign("index.html?ojr=login"); };
}
export = RegisterViewModel;
