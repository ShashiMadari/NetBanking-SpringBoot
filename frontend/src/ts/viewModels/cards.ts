import * as ko from "knockout";
import { api } from "../services/api";
import * as AccUtils from "../accUtils";
interface Card {
  id?: number;
  cardId?: number;
  accountId?: number;
  lastFour: string;
  status: string;
  cardType: "DEBIT" | "CREDIT";
  network: string;
  dailyLimit: number;
  creditLimit: number;
  outstandingAmount: number;
  minimumDueAmount?: number;
  paymentDueDate?: string;
}
interface Account { accountId: number; accountNumber: string; accountType: string; }
interface RewardBalance { cardId: number; availablePoints: number; pointValueRule: string; }
interface CreditCardActivity {
  activityType: string;
  direction: string;
  amount: number;
  referenceNumber: string;
  status: string;
  description: string;
  occurredAt: string;
}
interface CreditCardActivityPage { activities: CreditCardActivity[]; }
interface AccountTransaction {
  transactionType: string;
  entrySide: string;
  amount: number;
  referenceNumber: string;
  status: string;
  transactionDate: string;
}
interface CardTransaction {
  transactionType: string;
  direction: string;
  amount: number;
  referenceNumber: string;
  status: string;
  description: string;
  occurredAt: string;
}
class CardsViewModel {
  cards = ko.observableArray<Card>([]);
  accounts = ko.observableArray<Account>([]);
  rewardBalances = ko.observable<Record<number, RewardBalance>>({});
  selectedCard = ko.observable<Card | null>(null);
  cardTransactions = ko.observableArray<CardTransaction>([]);
  loadingCardTransactions = ko.observable(false);
  cardTransactionsError = ko.observable("");
  error = ko.observable("");
  notice = ko.observable("");
  loading = ko.observable(true);
  cardActionBusy = ko.observable(false);
  editingDailyLimitCardId = ko.observable<number | null>(null);
  dailyLimitInput = ko.observable("");
  dailyLimitBusy = ko.observable(false);
  pendingCardAction = ko.observable<{ cardId: number; lastFour: string; action: "block" | "unblock" } | null>(null);
  selectedCardId = ko.pureComputed(() => this.selectedCard() ? this.cardId(this.selectedCard() as Card) : 0);
  accountLabel = (account: Account): string => `${account.accountType} · •••• ${account.accountNumber.slice(-4)}`;

  async connected(): Promise<void> {
    AccUtils.announce("Cards and Credit page loaded.");
    document.title = "Cards & Credit | O-Bank";
    try {
      const [cards, accounts] = await Promise.all([
        api.get<Card[]>("/api/cards"),
        api.get<Account[]>("/api/accounts/summary")
      ]);
      const normalisedCards = this.normaliseCards(cards);
      this.cards(normalisedCards);
      this.accounts(accounts);
      await this.loadRewardBalances(this.cards());
      if (normalisedCards.length) await this.selectCard(normalisedCards[0]);
    } catch (e) { this.error(this.message(e)); } finally { this.loading(false); }
  }

  cardId = (card: Card): number => Number(card.id ?? card.cardId ?? 0);
  linkedAccountId = (card: Card): number => {
    const cardAccountReference = String(card.accountId ?? "");
    const account = this.accounts().find((candidate) =>
      String(candidate.accountId) === cardAccountReference
      || candidate.accountNumber === cardAccountReference
    );
    return account?.accountId ?? Number(cardAccountReference);
  };
  isSelectedCard = (card: Card): boolean => this.cardId(card) === this.selectedCardId();
  isActive = (card: Card): boolean => card.status === "ACTIVE";
  cardStatusLabel = (card: Card): string => card.status === "ACTIVE" ? "Active" : "Blocked — transactions disabled";
  rewardPoints = (card: Card): number => this.rewardBalances()[this.cardId(card)]?.availablePoints ?? 0;
  rewardRule = (card: Card): string => this.rewardBalances()[this.cardId(card)]?.pointValueRule ?? "1 point = INR 1";
  formatAmount = (amount: number): string => `INR ${Number(amount).toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
  formatDate = (value: string): string => {
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? value : date.toLocaleString("en-IN", { dateStyle: "medium", timeStyle: "short" });
  };

  selectCard = async (card: Card): Promise<void> => {
    this.selectedCard(card);
    this.cardTransactions([]);
    this.cardTransactionsError("");
    this.loadingCardTransactions(true);
    try {
      if (card.cardType === "CREDIT") {
        const activityPage = await api.get<CreditCardActivityPage>(`/api/credit-cards/${this.cardId(card)}/activity?page=0&size=20`);
        this.cardTransactions(activityPage.activities.map((activity) => ({
          transactionType: activity.activityType,
          direction: activity.direction,
          amount: activity.amount,
          referenceNumber: activity.referenceNumber,
          status: activity.status,
          description: activity.description,
          occurredAt: activity.occurredAt
        })));
        return;
      }

      const accountId = this.linkedAccountId(card);
      if (!accountId) {
        this.cardTransactionsError("Transactions for this Debit card are unavailable until its linked account is provided by the Card service.");
        return;
      }
      const transactions = await api.get<AccountTransaction[]>(`/api/accounts/${accountId}/transactions`);
      this.cardTransactions(transactions.map((transaction) => ({
        transactionType: transaction.transactionType,
        direction: transaction.entrySide,
        amount: transaction.amount,
        referenceNumber: transaction.referenceNumber,
        status: transaction.status,
        description: transaction.transactionType,
        occurredAt: transaction.transactionDate
      })));
    } catch (error) {
      this.cardTransactionsError(this.message(error));
    } finally {
      this.loadingCardTransactions(false);
    }
  };

  private async loadRewardBalances(cards: Card[]): Promise<void> {
    const creditCards = cards.filter((card) => card.cardType === "CREDIT");
    const balances = await Promise.all(creditCards.map(async (card) => {
      try { return await api.get<RewardBalance>(`/api/credit-card-rewards/balance?cardId=${this.cardId(card)}`); }
      catch { return null; }
    }));
    this.rewardBalances(Object.fromEntries(
      balances.filter((balance): balance is RewardBalance => balance !== null)
        .map((balance) => [balance.cardId, balance])
    ));
  }

  isEditingDailyLimit = (card: Card): boolean => this.editingDailyLimitCardId() === this.cardId(card);
  startEditingDailyLimit = (card: Card): void => { this.error(""); this.notice(""); this.editingDailyLimitCardId(this.cardId(card)); this.dailyLimitInput(String(card.dailyLimit)); };
  cancelEditingDailyLimit = (): void => { this.editingDailyLimitCardId(null); this.dailyLimitInput(""); };
  saveDailyLimit = async (card: Card): Promise<void> => {
    const dailyLimit = Number(this.dailyLimitInput());
    if (!Number.isFinite(dailyLimit) || dailyLimit < 1 || !/^\d+(\.\d{1,2})?$/.test(this.dailyLimitInput().trim())) {
      this.error("Enter a daily limit of at least INR 1 with at most two decimal places."); return;
    }
    if (card.cardType === "CREDIT" && dailyLimit > Number(card.creditLimit)) {
      this.error("Daily limit cannot exceed the credit limit of INR " + Number(card.creditLimit).toLocaleString("en-IN") + "."); return;
    }
    this.error(""); this.notice(""); this.dailyLimitBusy(true);
    try {
      await api.put("/api/cards/" + this.cardId(card) + "/daily-limit", { dailyLimit });
      this.notice("Daily card limit updated successfully.");
      this.cancelEditingDailyLimit();
      const cards = this.normaliseCards(await api.get<Card[]>("/api/cards"));
      const selectedCardId = this.selectedCardId();
      this.cards(cards); await this.loadRewardBalances(cards);
      const selectedCard = cards.find((item) => this.cardId(item) === selectedCardId) ?? cards[0];
      if (selectedCard) await this.selectCard(selectedCard);
    } catch (e) { this.error(this.message(e)); } finally { this.dailyLimitBusy(false); }
  };

  requestCardAction = (card: Card, action: "block" | "unblock"): void => {
    this.error(""); this.notice("");
    this.pendingCardAction({ cardId: this.cardId(card), lastFour: card.lastFour, action });
  };
  cancelCardAction = (): void => { this.pendingCardAction(null); };
  confirmCardAction = async (): Promise<void> => {
    const pending = this.pendingCardAction();
    if (!pending || this.cardActionBusy()) return;
    this.error(""); this.notice(""); this.cardActionBusy(true);
    try {
      await api.put("/api/cards/" + pending.cardId + "/" + pending.action);
      this.notice("Card action completed.");
      this.pendingCardAction(null);
      const cards = this.normaliseCards(await api.get<Card[]>("/api/cards"));
      const selectedCardId = this.selectedCardId();
      this.cards(cards); await this.loadRewardBalances(cards);
      const selectedCard = cards.find((card) => this.cardId(card) === selectedCardId) ?? cards[0];
      if (selectedCard) await this.selectCard(selectedCard);
    } catch (e) { this.error(this.message(e)); } finally { this.cardActionBusy(false); }
  };
  private normaliseCards(cards: Card[]): Card[] { return cards.map((card) => ({ ...card, id: this.cardId(card) })); }
  private message(e: unknown): string { return e instanceof Error ? e.message : "Credit service request failed."; }
}
export = CardsViewModel;
