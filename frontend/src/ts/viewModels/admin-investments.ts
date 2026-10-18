import * as ko from "knockout";
import * as AccUtils from "../accUtils";
import { api } from "../services/api";
import { currentRole } from "../services/auth";
import AdminViewModel = require("./admin");

interface InvestmentOrder {
  orderId: number;
  customerUserId: number;
  accountId: number;
  productId: number;
  productCode: string;
  productName: string;
  productType: string;
  scheduleId?: number;
  orderKind: string;
  investmentMode: string;
  amount: number;
  quantity: number;
  status: string;
  referenceNumber: string;
  bankingTransactionId?: number;
  bankingReferenceNumber?: string;
  failureCode?: string;
  failureReason?: string;
  requestedAt?: string;
  authorisedAt?: string;
  debitedAt?: string;
  allottedAt?: string;
}

interface MaturedFixedDeposit {
  holdingId: number;
  customerUserId: number;
  productName: string;
  investedAmount: number;
  recordedValue: number;
  tenureMonths: number;
  maturityDate: string;
  status: string;
}

interface InvestmentSchedule {
  scheduleId: number;
  customerUserId: number;
  accountId: number;
  productId: number;
  productCode: string;
  productName: string;
  scheduleType: string;
  amount: number;
  debitDay?: number;
  nextDebitDate?: string;
  lastDebitDate?: string;
  lastOrderId?: number;
  tenureMonths?: number;
  installmentsCompleted?: number;
  status: string;
  createdAt?: string;
  updatedAt?: string;
  completedAt?: string;
}

interface InvestmentProduct {
  productId?: number;
  id?: number;
  productCode?: string;
  code?: string;
  productName?: string;
  name?: string;
  productType?: string;
  type?: string;
  minimumAmount?: number;
  minInvestmentAmount?: number;
  riskLevel?: string;
  lockInMonths?: number;
  currentPrice?: number;
  annualRate?: number;
  requiresDemat?: boolean;
  pastReturn1y?: number;
  historicalReturn?: number;
  exchangeName?: string;
  exchange?: string;
  tenureMonths?: number;
}

class AdminInvestmentsViewModel extends AdminViewModel {
  settlementOrders = ko.observableArray<InvestmentOrder>([]);
  schedules = ko.observableArray<InvestmentSchedule>([]);
  maturedFixedDeposits = ko.observableArray<MaturedFixedDeposit>([]);
  products = ko.observableArray<InvestmentProduct>([]);
  investmentsLoading = ko.observable(false);
  selectedOrder = ko.observable<InvestmentOrder | null>(null);
  selectedProduct = ko.observable<InvestmentProduct | null>(null);
  productDetailLoading = ko.observable(false);

  constructor() {
    super();
    this.selectedAdminSection("investments");
  }

  connected(): void {
    AccUtils.announce("Investment operations page loaded.");
    document.title = "Investment operations | O-Bank";
    if (currentRole() !== "ADMIN") {
      this.error("Administrator access is required. Sign in with an active administrator account.");
      return;
    }
    void this.loadInvestmentOperations();
  }

  loadInvestmentOperations = async (): Promise<void> => {
    this.error("");
    this.notice("");
    this.investmentsLoading(true);
    try {
      const [orders, schedules, products, matured] = await Promise.all([
        api.get<InvestmentOrder[]>("/api/admin/investments/orders?status=SETTLEMENT_PENDING"),
        api.get<InvestmentSchedule[]>("/api/admin/investments/schedules"),
        api.get<InvestmentProduct[]>("/api/investments/products"),
        api.get<MaturedFixedDeposit[]>("/api/admin/investments/matured-fds")
      ]);
      this.settlementOrders(orders ?? []);
      this.schedules(schedules ?? []);
      this.products(products ?? []);
      this.maturedFixedDeposits(matured ?? []);
    } catch (error) {
      this.error(this.readError(error, "Unable to load investment operations."));
    } finally {
      this.investmentsLoading(false);
    }
  };

  openOrder = (order: InvestmentOrder): void => { this.selectedOrder(order); };
  closeOrder = (): void => { this.selectedOrder(null); };
  openProduct = async (product: InvestmentProduct): Promise<void> => {
    const productId = product.productId ?? product.id;
    if (!productId) {
      this.error("This product does not have an identifier for loading its details.");
      return;
    }
    this.error("");
    this.selectedProduct(product);
    this.productDetailLoading(true);
    try {
      this.selectedProduct(await api.get<InvestmentProduct>(`/api/investments/products/${productId}`));
    } catch (error) {
      this.error(this.readError(error, "The product details could not be loaded."));
    } finally {
      this.productDetailLoading(false);
    }
  };

  closeProduct = (): void => { this.selectedProduct(null); };
  productName = (product: InvestmentProduct): string => product.productName ?? product.name ?? "Investment product";
  productCode = (product: InvestmentProduct): string => product.productCode ?? product.code ?? "—";
  productType = (product: InvestmentProduct): string => product.productType ?? product.type ?? "—";
  minimumAmount = (product: InvestmentProduct): number => Number(product.minimumAmount ?? product.minInvestmentAmount ?? 0);
  historicalReturn = (product: InvestmentProduct): string => {
    const value = product.pastReturn1y ?? product.historicalReturn;
    return value === undefined || value === null ? "Not available" : `${value}%`;
  };
  exchange = (product: InvestmentProduct): string => product.exchangeName ?? product.exchange ?? "Not applicable";
  yesNo = (value: boolean | undefined): string => value ? "Required" : "Not required";
  shortDate = (value: string | undefined): string => value ? new Date(value).toLocaleDateString("en-IN") : "—";
  settlementDue = (value: string | undefined): string => {
    if (!value) return "Awaiting banking confirmation";
    const due = new Date(value);
    due.setHours(due.getHours() + 1);
    return due.toLocaleString("en-IN", { dateStyle: "medium", timeStyle: "short" });
  };

  private readError(error: unknown, fallback: string): string {
    return error instanceof Error && error.message ? error.message : fallback;
  }
}

export = AdminInvestmentsViewModel;
