import * as ko from "knockout";
import * as AccUtils from "../accUtils";
import { api } from "../services/api";

interface Account {
  accountId: number;
  accountNumber: string;
  accountType: string;
  availableBalance: number;
  currency: string;
}

interface Card {
  cardId: number;
  lastFour: string;
  cardType: "DEBIT" | "CREDIT";
  network: string;
  creditLimit: number;
  outstandingAmount: number;
  status: string;
}

interface PaymentSource {
  key: string;
  kind: "ACCOUNT" | "CREDIT_CARD";
  id: number;
  label: string;
  availableAmount: number;
  currency: string;
}

interface Biller {
  id: number;
  billerName: string;
  billerCategory: string;
  consumerNumberLabel: string;
  status: string;
}

interface OtpResponse {
  paymentOrderId: number;
  referenceNumber: string;
  amount: number;
  status: string;
  message: string;
}

class BillPaymentsViewModel {
  accounts = ko.observableArray<Account>([]);
  cards = ko.observableArray<Card>([]);
  billers = ko.observableArray<Biller>([]);
  paymentSources = ko.pureComputed<PaymentSource[]>(() => [
    ...this.accounts().map((account) => ({
      key: `ACCOUNT:${account.accountId}`,
      kind: "ACCOUNT" as const,
      id: account.accountId,
      label: `${account.accountType} · •••• ${account.accountNumber.slice(-4)}`,
      availableAmount: Number(account.availableBalance),
      currency: account.currency
    })),
    ...this.cards().filter((card) => card.cardType === "CREDIT" && card.status === "ACTIVE").map((card) => ({
      key: `CREDIT_CARD:${card.cardId}`,
      kind: "CREDIT_CARD" as const,
      id: card.cardId,
      label: `CREDIT CARD · ${card.network} •••• ${card.lastFour}`,
      availableAmount: Number(card.creditLimit) - Number(card.outstandingAmount),
      currency: "INR"
    }))
  ]);
  selectedPaymentSourceKey = ko.observable("");
  selectedBillerId = ko.observable("");
  consumerReference = ko.observable("");
  amount = ko.observable("");
  otp = ko.observable("");
  paymentOrder = ko.observable<OtpResponse | null>(null);
  paymentFundingKind = ko.observable<PaymentSource["kind"] | null>(null);
  loading = ko.observable(true);
  submitting = ko.observable(false);
  error = ko.observable("");
  success = ko.observable("");
  selectedPaymentSource = ko.pureComputed(() => this.paymentSources().find((source) => source.key === this.selectedPaymentSourceKey()) ?? null);
  selectedBiller = ko.pureComputed(() => this.billers().find((biller) => String(biller.id) === String(this.selectedBillerId())) ?? null);

  async connected(): Promise<void> {
    AccUtils.announce("Bill payments page loaded.");
    document.title = "Bill payments | O-Bank";
    try {
      const [accounts, billers, cards] = await Promise.all([
        api.get<Account[]>("/api/accounts/summary"),
        api.get<Biller[]>("/api/billers"),
        api.get<Card[]>("/api/cards")
      ]);
      this.accounts(accounts);
      this.cards(cards);
      this.billers(billers.filter((biller) => biller.status === "ACTIVE"));
      if (this.paymentSources().length) this.selectedPaymentSourceKey(this.paymentSources()[0].key);
    } catch (error) {
      this.error(this.message(error, "Unable to load bill-payment details."));
    } finally {
      this.loading(false);
    }
  }

  initiate = async (): Promise<void> => {
    this.error("");
    this.success("");
    const source = this.selectedPaymentSource();
    const biller = this.selectedBiller();
    const amount = Number(this.amount());
    if (!source || !biller || !this.consumerReference().trim() || !Number.isFinite(amount) || amount < 1) {
      this.error("Choose a payment source and biller, then enter a consumer number and an amount of at least 1.00.");
      return;
    }
    if (amount > source.availableAmount) {
      this.error("The bill amount is more than the available credit or balance in the selected payment source.");
      return;
    }
    this.submitting(true);
    try {
      const endpoint = source.kind === "CREDIT_CARD" ? "/api/credit-card-bill-payments/initiate" : "/api/bill-payments/initiate";
      this.paymentOrder(await api.post<OtpResponse>(endpoint, {
        ...(source.kind === "CREDIT_CARD" ? { cardId: source.id } : { sourceAccountId: source.id }),
        billerId: biller.id,
        consumerReference: this.consumerReference().trim(),
        amount
      }));
      this.paymentFundingKind(source.kind);
      this.success(this.paymentOrder()?.message ?? "A bill-payment OTP was sent to your email.");
    } catch (error) {
      this.error(this.message(error, "Unable to send the bill-payment OTP."));
    } finally {
      this.submitting(false);
    }
  };

  confirm = async (): Promise<void> => {
    const order = this.paymentOrder();
    if (!order) return;
    this.error("");
    this.success("");
    if (!/^\d{6}$/.test(this.otp())) {
      this.error("Enter the six-digit OTP sent to your email.");
      return;
    }
    this.submitting(true);
    try {
      const endpoint = this.paymentFundingKind() === "CREDIT_CARD" ? `/api/credit-card-bill-payments/${order.paymentOrderId}/confirm` : `/api/bill-payments/${order.paymentOrderId}/confirm`;
      const response = await api.post<{ message: string }>(endpoint, { otp: this.otp() });
      this.success(response.message || "Bill payment completed successfully.");
      this.paymentOrder(null);
      this.paymentFundingKind(null);
      this.otp("");
      this.consumerReference("");
      this.amount("");
      window.scrollTo({ top: 0, behavior: "smooth" });
    } catch (error) {
      this.error(this.message(error, "Unable to confirm the bill payment."));
    } finally {
      this.submitting(false);
    }
  };

  private message(error: unknown, fallback: string): string {
    return error instanceof Error ? error.message : fallback;
  }
}

export = BillPaymentsViewModel;
