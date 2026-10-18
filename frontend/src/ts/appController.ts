/**
 * @license
 * Copyright (c) 2014, 2024, Oracle and/or its affiliates.
 * Licensed under The Universal Permissive License (UPL), Version 1.0
 * as shown at https://oss.oracle.com/licenses/upl/
 * @ignore
 */
import * as ko from "knockout";
import * as ModuleUtils from "ojs/ojmodule-element-utils";
import * as ResponsiveUtils from "ojs/ojresponsiveutils";
import * as ResponsiveKnockoutUtils from "ojs/ojresponsiveknockoututils";
import CoreRouter = require ("ojs/ojcorerouter");
import ModuleRouterAdapter = require("ojs/ojmodulerouter-adapter");
import KnockoutRouterAdapter = require("ojs/ojknockoutrouteradapter");
import UrlParamAdapter = require("ojs/ojurlparamadapter");
import ArrayDataProvider = require("ojs/ojarraydataprovider");
import "ojs/ojknockout";
import "ojs/ojmodule-element";
import { ojNavigationList } from "ojs/ojnavigationlist";
import { ojModule } from "ojs/ojmodule-element";
import Context = require("ojs/ojcontext");
import "ojs/ojdrawerpopup";
import { currentEmail, currentRole, isSignedIn } from "./services/auth";
import { api } from "./services/api";

interface CoreRouterDetail {
  label: string;
  iconClass: string;
  iconSvg?: string;
};

// The navigation-list icon font does not include every service icon we use.
// Keep the customer menu visually complete with a small, consistent SVG set.
const customerSidebarIcons = {
  dashboard: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 20V10m6 10V4m6 16v-7m4 7H2"/></svg>',
  accounts: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M3 10h18M5 10V20m5-10v10m4-10v10m5-10v10M2 20h20M12 3l9 5H3l9-5Z"/></svg>',
  banking: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M3 7h18v12H3zM3 11h18M7 16h4"/></svg>',
  bills: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 3h12v18H6zM9 8h6m-6 4h6m-6 4h3"/></svg>',
  merchant: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 10h16v10H4zM3 10l2-6h14l2 6M8 20v-5h4v5m3-10v3"/></svg>',
  cards: '<svg viewBox="0 0 24 24" aria-hidden="true"><rect x="3" y="5" width="18" height="14" rx="2"/><path d="M3 10h18M7 15h4"/></svg>',
  transactions: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M9 6h11M9 12h11M9 18h11M4 6h.01M4 12h.01M4 18h.01"/></svg>',
  loans: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M3 7h18v12H3zM7 7V5h10v2M8 15h8m-4-3v6"/></svg>',
  forex: '<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="M3 12h18M12 3c3 3 3 15 0 18M12 3c-3 3-3 15 0 18"/></svg>',
  investments: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 19V5m0 14h16M7 15l4-4 3 2 5-6M15 7h4v4"/></svg>',
  notifications: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M18 10a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 22h4"/></svg>'
};

class RootViewModel {
  readonly isAdmin = isSignedIn() && currentRole() === "ADMIN";
  manner: ko.Observable<string>;
  message: ko.Observable<string|undefined>;
  smScreen: ko.Observable<boolean>|undefined;
  mdScreen: ko.Observable<boolean>|undefined;
  router: CoreRouter<CoreRouterDetail>|undefined;
  moduleAdapter: ModuleRouterAdapter<CoreRouterDetail>;
  sideDrawerOn: ko.Observable<boolean>;
  navDataProvider: ojNavigationList<string, CoreRouter.CoreRouterState<CoreRouterDetail>>["data"];
  sidebarNavDataProvider: ojNavigationList<string, CoreRouter.CoreRouterState<CoreRouterDetail>>["data"];
  moreNavDataProvider: ojNavigationList<string, CoreRouter.CoreRouterState<CoreRouterDetail>>["data"];
  moreMenuOpen: ko.Observable<boolean>;
  appName: ko.Observable<string>;
  userLogin: ko.Observable<string>;
  footerLinks: Array<object>;
  selection: KnockoutRouterAdapter<CoreRouterDetail>;
  signedIn: ko.Observable<boolean>;
  unreadNotificationCount: ko.Observable<number>;

  constructor() {
    // handle announcements sent when pages change, for Accessibility.
    this.manner = ko.observable("polite");
    this.message = ko.observable();

    let globalBodyElement: HTMLElement = document.getElementById("globalBody") as HTMLElement;
    globalBodyElement.addEventListener("announce", this.announcementHandler, false);

    // media queries for responsive layouts
    let smQuery: string | null = ResponsiveUtils.getFrameworkQuery("sm-only");
    if (smQuery){
      this.smScreen = ResponsiveKnockoutUtils.createMediaQueryObservable(smQuery);
    }

    let mdQuery: string | null = ResponsiveUtils.getFrameworkQuery("md-up");
    if (mdQuery){
      this.mdScreen = ResponsiveKnockoutUtils.createMediaQueryObservable(mdQuery);
    }

    const publicNavData = [
      { path: "login", detail: { label: "Sign in", iconClass: "oj-ux-ico-lock" } },
      { path: "register", detail: { label: "Register", iconClass: "oj-ux-ico-contact-group" } },
      { path: "forgot-password", detail: { label: "Forgot password", iconClass: "oj-ux-ico-key" } }
    ];
    const protectedNavData = [
      { path: "dashboard", detail: { label: "Overview", iconClass: "oj-ux-ico-bar-chart", iconSvg: customerSidebarIcons.dashboard } },
      { path: "accounts", detail: { label: "Accounts", iconClass: "oj-ux-ico-bank", iconSvg: customerSidebarIcons.accounts } },
      { path: "banking", detail: { label: "Banking", iconClass: "oj-ux-ico-wallet", iconSvg: customerSidebarIcons.banking } },
      { path: "bill-payments", detail: { label: "Bill payments", iconClass: "oj-ux-ico-receipt", iconSvg: customerSidebarIcons.bills } },
      { path: "merchant-payments", detail: { label: "Pay a merchant", iconClass: "oj-ux-ico-credit-card", iconSvg: customerSidebarIcons.merchant } },
      { path: "cards", detail: { label: "Cards & Credit", iconClass: "oj-ux-ico-credit-card", iconSvg: customerSidebarIcons.cards } },
      { path: "transactions", detail: { label: "Transactions", iconClass: "oj-ux-ico-list", iconSvg: customerSidebarIcons.transactions } },
      { path: "loans", detail: { label: "Loans", iconClass: "oj-ux-ico-money", iconSvg: customerSidebarIcons.loans } },
      { path: "forex", detail: { label: "Forex", iconClass: "oj-ux-ico-globe", iconSvg: customerSidebarIcons.forex } },
      { path: "investments", detail: { label: "Investments", iconClass: "oj-ux-ico-line-chart", iconSvg: customerSidebarIcons.investments } },
      { path: "profile", detail: { label: "Profile", iconClass: "oj-ux-ico-contact-group" } },
      { path: "notifications", detail: { label: "Notifications", iconClass: "oj-ux-ico-notification", iconSvg: customerSidebarIcons.notifications } },
      { path: "admin", detail: { label: "Administration", iconClass: "oj-ux-ico-settings" } }
    ];
    const internalRoutes = [
      { path: "admin-forex", detail: { label: "Forex operations", iconClass: "oj-ux-ico-globe" } },
      { path: "admin-forex-rates", detail: { label: "Exchange-rate maintenance", iconClass: "oj-ux-ico-transfer" } },
      { path: "admin-forex-kyc", detail: { label: "Customer Forex KYC", iconClass: "oj-ux-ico-contact-group" } },
      { path: "admin-forex-compliance", detail: { label: "Compliance review", iconClass: "oj-ux-ico-list" } },
      { path: "admin-beneficiaries", detail: { label: "Pending beneficiary approval", iconClass: "oj-ux-ico-settings" } },
      { path: "admin-accounts", detail: { label: "Account control", iconClass: "oj-ux-ico-settings" } },
      { path: "admin-audit", detail: { label: "Audit and notification", iconClass: "oj-ux-ico-settings" } },
      { path: "admin-payments", detail: { label: "Payment operations", iconClass: "oj-ux-ico-settings" } },
      { path: "admin-merchants", detail: { label: "Reward and merchant control", iconClass: "oj-ux-ico-settings" } },
      { path: "admin-purchases", detail: { label: "Recent merchant purchases", iconClass: "oj-ux-ico-settings" } },
      { path: "admin-investments", detail: { label: "Investment operations", iconClass: "oj-ux-ico-line-chart" } },
      { path: "stocks", detail: { label: "Stocks & ETFs", iconClass: "oj-ux-ico-line-chart" } },
      { path: "mutual-funds", detail: { label: "Mutual funds", iconClass: "oj-ux-ico-line-chart" } },
      { path: "fixed-deposits", detail: { label: "Fixed deposits", iconClass: "oj-ux-ico-line-chart" } },
      { path: "recurring-deposits", detail: { label: "Recurring deposits", iconClass: "oj-ux-ico-line-chart" } },
      { path: "ppf", detail: { label: "PPF", iconClass: "oj-ux-ico-line-chart" } },
      { path: "investment-portfolio", detail: { label: "Investment portfolio", iconClass: "oj-ux-ico-line-chart" } },
      { path: "investment-schedules", detail: { label: "Investment schedules", iconClass: "oj-ux-ico-calendar" } },
      { path: "investment-statements", detail: { label: "Investment statements", iconClass: "oj-ux-ico-receipt" } }
      ,{ path: "beneficiaries", detail: { label: "Beneficiaries", iconClass: "oj-ux-ico-contact-group" } },
      { path: "card-services", detail: { label: "Card services", iconClass: "oj-ux-ico-credit-card" } },
      { path: "card-pin", detail: { label: "Change PIN", iconClass: "oj-ux-ico-key" } },
      { path: "card-bill", detail: { label: "Pay card bill", iconClass: "oj-ux-ico-credit-card" } },
      { path: "card-statement", detail: { label: "Card statement", iconClass: "oj-ux-ico-receipt" } },
      { path: "card-rewards", detail: { label: "Card rewards", iconClass: "oj-ux-ico-star" } },
      { path: "card-emi", detail: { label: "Card EMI", iconClass: "oj-ux-ico-calendar" } },
      { path: "forex-exchange", detail: { label: "Exchange currency", iconClass: "oj-ux-ico-transfer" } },
      { path: "forex-transactions", detail: { label: "Forex transactions", iconClass: "oj-ux-ico-list" } },
      { path: "forex-cards", detail: { label: "Forex cards", iconClass: "oj-ux-ico-credit-card" } },
      { path: "forex-beneficiaries", detail: { label: "Forex beneficiaries", iconClass: "oj-ux-ico-contact-group" } },
      { path: "forex-transfer", detail: { label: "Send money abroad", iconClass: "oj-ux-ico-globe" } },
      { path: "forex-activity", detail: { label: "Forex activity", iconClass: "oj-ux-ico-list" } },
      { path: "loan-details", detail: { label: "Loan details", iconClass: "oj-ux-ico-money" } },
      { path: "loan-repay", detail: { label: "Repay loan", iconClass: "oj-ux-ico-money" } },
      { path: "loan-statements", detail: { label: "Loan statements", iconClass: "oj-ux-ico-receipt" } }
    ];
    const navData = [
      { path: "", redirect: isSignedIn() ? "dashboard" : "register" },
      ...publicNavData,
      ...protectedNavData,
      ...internalRoutes
    ];
    // router setup
    const router = new CoreRouter(navData, {
      urlAdapter: new UrlParamAdapter()
    });
    router.sync();

    this.moduleAdapter = new ModuleRouterAdapter(router);

    this.selection = new KnockoutRouterAdapter(router);

    // Setup the navDataProvider with the routes, excluding the first redirected
    // route.
    this.signedIn = ko.observable(isSignedIn());
    this.unreadNotificationCount = ko.observable(0);
    if (isSignedIn() && currentRole() === "CUSTOMER") {
      void this.refreshUnreadNotificationCount();
      window.setInterval(() => void this.refreshUnreadNotificationCount(), 30000);
      window.addEventListener("o-bank:notifications-changed", () => void this.refreshUnreadNotificationCount());
    }
    const visibleProtectedNav = protectedNavData.filter(
      (item) => item.path !== "profile" && item.path !== "admin"
    );
    const adminNav = [
      { path: "admin", detail: { label: "Admin overview", iconClass: "oj-ux-ico-bar-chart" } },
      ...internalRoutes.filter((item) => item.path.startsWith("admin-") && !item.path.startsWith("admin-forex-"))
    ];
    const roleNavigation = this.isAdmin ? adminNav : visibleProtectedNav;
    const primarySidebarNav: typeof visibleProtectedNav = [];
    const moreSidebarNav = roleNavigation;
    this.navDataProvider = new ArrayDataProvider(
      isSignedIn() ? roleNavigation : publicNavData,
      {keyAttributes: "path"}
    );
    this.sidebarNavDataProvider = new ArrayDataProvider(
      isSignedIn() ? primarySidebarNav : publicNavData,
      {keyAttributes: "path"}
    );
    this.moreNavDataProvider = new ArrayDataProvider(
      isSignedIn() ? moreSidebarNav : [],
      {keyAttributes: "path"}
    );
    this.moreMenuOpen = ko.observable(false);

    // drawer
    this.sideDrawerOn = ko.observable(false);

    // close drawer on medium and larger screens
    this.mdScreen?.subscribe(() => {
      this.sideDrawerOn(false);
    });

    // header

    // application Name used in Branding Area
    this.appName = ko.observable("O-Bank");
    // user Info used in Global Navigation area

    this.userLogin = ko.observable(currentEmail());
    // footer
    this.footerLinks = [
      { name: "Secure banking", linkId: "secureBanking", linkTarget: "?ojr=login" },
      { name: "Profile", linkId: "profile", linkTarget: "?ojr=profile" },
      { name: "Help", linkId: "help", linkTarget: "?ojr=dashboard" }
    ];
    // release the application bootstrap busy state
    Context.getPageContext().getBusyContext().applicationBootstrapComplete();        
  }

  refreshUnreadNotificationCount = async (): Promise<void> => {
    if (!isSignedIn() || currentRole() !== "CUSTOMER") {
      this.unreadNotificationCount(0);
      return;
    }
    try {
      const result = await api.get<{ unreadCount: number }>("/api/notifications/unread-count");
      this.unreadNotificationCount(result.unreadCount || 0);
    } catch {
      this.unreadNotificationCount(0);
    }
  };

  announcementHandler = (event: any): void => {
      this.message(event.detail.message);
      this.manner(event.detail.manner);
  }

  // called by navigation drawer toggle button and after selection of nav drawer item
  toggleDrawer = (): void => {
    this.sideDrawerOn(!this.sideDrawerOn());
  }

  toggleMoreMenu = (): void => {
    this.moreMenuOpen(!this.moreMenuOpen());
  }

  closeMoreMenu = (): void => {
    this.moreMenuOpen(false);
  };


    // a close listener so we can move focus back to the toggle button when the drawer closes
    openedChangedHandler = (event: CustomEvent): void => {
    if (event.detail.value === false) {
      const drawerToggleButtonElement = document.querySelector("#drawerToggleButton") as HTMLElement;
      drawerToggleButtonElement.focus();
    }
  };
}

export default new RootViewModel();
