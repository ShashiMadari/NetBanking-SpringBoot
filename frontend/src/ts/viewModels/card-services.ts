import * as ko from "knockout";
import * as AccUtils from "../accUtils";
import { api } from "../services/api";

interface Card { cardId:number; lastFour:string; cardType:string; network:string; status:string; creditLimit:number; outstandingAmount:number; minimumDueAmount:number; paymentDueDate:string | null; }
interface Account { accountId:number; accountType:string; accountNumber:string; availableBalance:number; }

class CardServicesViewModel {
  cards=ko.observableArray<Card>([]); accounts=ko.observableArray<Account>([]); plans=ko.observableArray<any>([]);
  selectedCardId=ko.observable(""); selectedAccountId=ko.observable(""); selectedPlanId=ko.observable("");
  amount=ko.observable(""); otp=ko.observable(""); newPin=ko.observable(""); confirmPinValue=ko.observable("");
  year=ko.observable(new Date().getFullYear()); month=ko.observable(new Date().getMonth()+1); emiAmount=ko.observable(""); emiTenure=ko.observable("12");
  pinOrder=ko.observable<any>(null); repaymentOrder=ko.observable<any>(null); emiOrder=ko.observable<any>(null); statement=ko.observable<any>(null);
  ledger=ko.observableArray<any>([]); availablePoints=ko.observable(0); pointsEarned=ko.observable(0); pointsRedeemed=ko.observable(0); schedule=ko.observableArray<any>([]); schedulePlanId=ko.observable<number | null>(null);
  loading=ko.observable(true); busy=ko.observable(false); error=ko.observable(""); notice=ko.observable("");

  constructor(){ this.selectedCardId.subscribe(()=>{ this.selectedPlanId(""); this.schedule([]); this.schedulePlanId(null); }); }

  selectedCard=ko.pureComputed(()=>this.cards().find(c=>String(c.cardId)===String(this.selectedCardId()))??null);
  cardPlans=ko.pureComputed(()=>this.plans().filter(plan=>String(plan.cardId)===String(this.selectedCardId())));
  activeConvertedAmount=ko.pureComputed(()=>this.cardPlans().filter(plan=>plan.status==="ACTIVE").reduce((total:number,plan:any)=>total+Number(plan.remainingPrincipal||0),0));
  convertibleAmount=ko.pureComputed(()=>Math.max(0,Number(this.selectedCard()?.outstandingAmount||0)-this.activeConvertedAmount()));
  cardLabel=(c:Card)=>"CREDIT CARD - "+c.network+" .... "+c.lastFour;
  accountLabel=(a:Account)=>a.accountType+" - .... "+a.accountNumber.slice(-4);

  async connected(){
    AccUtils.announce("Card services page loaded."); document.title="Card services | O-Bank";
    try {
      const [cards,accounts,plans]=await Promise.all([api.get<Card[]>("/api/cards"),api.get<Account[]>("/api/accounts/summary"),api.get<any[]>("/api/credit-card-emi/plans")]);
      this.cards(cards.filter(c=>c.cardType==="CREDIT")); this.accounts(accounts); this.plans(plans);
      if(this.cards().length) this.selectedCardId(String(this.cards()[0].cardId));
      if(accounts.length) this.selectedAccountId(String(accounts[0].accountId));
      if(new URLSearchParams(window.location.search).get("ojr")==="card-rewards") await this.loadRewards();
    } catch(e) { this.error(this.msg(e)); } finally { this.loading(false); }
  }

  clear(){ this.error(""); this.notice(""); }
  async refreshCards(){ this.cards((await api.get<Card[]>("/api/cards")).filter(c=>c.cardType==="CREDIT")); }
  async refreshPlans(){ this.plans(await api.get<any[]>("/api/credit-card-emi/plans")); }

  async changePin(){ this.clear(); const id=Number(this.selectedCardId()); if(!id||!/^\d{4}$/.test(this.newPin())||this.newPin()!==this.confirmPinValue()){this.error("Enter matching four-digit PIN values.");return;} await this.run(async()=>{this.pinOrder(await api.post<any>("/api/cards/pin-change/initiate",{cardId:id,newPin:this.newPin(),confirmPin:this.confirmPinValue()}));this.notice("PIN-change OTP sent.");}); }
  async confirmPinChange(){ const o=this.pinOrder(); if(!o||!/^\d{6}$/.test(this.otp())){this.error("Enter the six-digit OTP.");return;} await this.run(async()=>{const r=await api.post<any>("/api/cards/pin-change/"+o.pinChangeOrderId+"/confirm",{otp:this.otp()});this.notice(r.message||"PIN changed.");this.pinOrder(null);this.otp("");this.newPin("");this.confirmPinValue("");}); }
  async resendPin(){ const o=this.pinOrder(); if(o) await this.run(async()=>{await api.post("/api/cards/pin-change/"+o.pinChangeOrderId+"/resend-otp",{});this.notice("A new PIN-change OTP was sent.");}); }

  async startRepayment(){ this.clear(); const cardId=Number(this.selectedCardId()),sourceAccountId=Number(this.selectedAccountId()),amount=Number(this.amount()); if(!cardId||!sourceAccountId||amount<1){this.error("Choose a card and account, then enter an amount.");return;} if(amount>this.convertibleAmount()){this.error("The amount exceeds the card balance available outside active EMI plans: INR "+this.convertibleAmount().toFixed(2)+".");return;} await this.run(async()=>{this.repaymentOrder(await api.post<any>("/api/cards/credit-payments/initiate",{cardId,sourceAccountId,amount}));this.notice("Credit-card repayment OTP sent.");}); }
  async confirmRepayment(){ const o=this.repaymentOrder(); if(!o||!/^\d{6}$/.test(this.otp())){this.error("Enter the six-digit OTP.");return;} await this.run(async()=>{const r=await api.post<any>("/api/cards/credit-payments/"+o.paymentOrderId+"/confirm",{otp:this.otp()});await this.refreshCards();this.notice(r.message||"Credit-card payment completed.");this.repaymentOrder(null);this.otp("");this.amount("");}); }
  async resendRepayment(){ const o=this.repaymentOrder(); if(o) await this.run(async()=>{await api.post("/api/cards/credit-payments/"+o.paymentOrderId+"/resend-otp",{});this.notice("A new repayment OTP was sent.");}); }

  async loadStatement(){ const id=Number(this.selectedCardId()); if(!id)return; await this.run(async()=>{this.statement(await api.get<any>("/api/credit-cards/"+id+"/monthly-statement?year="+this.year()+"&month="+this.month()));}); }
  async emailStatement(){ const id=Number(this.selectedCardId()); if(!id)return; await this.run(async()=>{await api.post("/api/credit-cards/"+id+"/monthly-statement/email?year="+this.year()+"&month="+this.month(),{});this.notice("Monthly statement sent to your registered email.");window.dispatchEvent(new Event("o-bank:notifications-changed"));}); }

  async loadRewards(){ const id=Number(this.selectedCardId()); if(!id)return; await this.run(async()=>{const ledgerPage=await api.get<any>("/api/credit-card-rewards/ledger?cardId="+id+"&page=0&size=100");const entries=ledgerPage.entries||ledgerPage.ledger||[];this.ledger(entries);this.availablePoints(Number(ledgerPage.availablePoints||0));this.pointsEarned(entries.filter((entry:any)=>entry.eventType==="EARN").reduce((total:number,entry:any)=>total+Math.max(0,Number(entry.points||0)),0));this.pointsRedeemed(entries.filter((entry:any)=>entry.eventType==="REDEEM").reduce((total:number,entry:any)=>total+Math.abs(Number(entry.points||0)),0));}); }

  async createEmi(){ const cardId=Number(this.selectedCardId()),conversionAmount=Number(this.emiAmount()),tenureMonths=Number(this.emiTenure()); if(!cardId||conversionAmount<1000||![3,6,9,12].includes(tenureMonths)){this.error("Enter an EMI conversion amount of at least INR 1,000 and choose a valid tenure.");return;} if(conversionAmount>this.convertibleAmount()){this.error("The amount exceeds the part of your card balance that is available to convert: INR "+this.convertibleAmount().toFixed(2)+".");return;} await this.run(async()=>{const r=await api.post<any>("/api/credit-card-emi/plans",{cardId,conversionAmount,tenureMonths});this.plans([r,...this.plans()]);this.selectedPlanId(String(r.emiPlanId));this.schedule([]);this.schedulePlanId(null);this.notice("EMI plan created.");this.emiAmount("");}); }
  loadSchedule = async (plan:any) => { if(String(plan.cardId)!==String(this.selectedCardId())){this.error("Choose the card linked to this EMI plan.");return;} await this.run(async()=>{this.selectedPlanId(String(plan.emiPlanId));this.schedulePlanId(Number(plan.emiPlanId));this.schedule(await api.get<any[]>("/api/credit-card-emi/plans/"+plan.emiPlanId+"/schedule"));}); }
  async startEmiPayment(){ const sourceAccountId=Number(this.selectedAccountId()),emiPlanId=Number(this.selectedPlanId()),plan=this.cardPlans().find(item=>Number(item.emiPlanId)===emiPlanId); if(!sourceAccountId||!plan){this.error("Choose an account and an EMI plan for the selected card.");return;} await this.run(async()=>{this.emiOrder(await api.post<any>("/api/credit-card-emi/payments/initiate",{sourceAccountId,emiPlanId}));this.notice("EMI-payment OTP sent.");}); }
  async confirmEmiPayment(){ const o=this.emiOrder(); if(!o||!/^\d{6}$/.test(this.otp())){this.error("Enter the six-digit OTP.");return;} await this.run(async()=>{const r=await api.post<any>("/api/credit-card-emi/payments/"+o.paymentOrderId+"/confirm",{otp:this.otp()});await this.refreshPlans();this.schedule([]);this.schedulePlanId(null);this.notice(r.message||"EMI payment completed.");this.emiOrder(null);this.otp("");}); }
  async resendEmiPayment(){ const o=this.emiOrder(); if(o) await this.run(async()=>{await api.post("/api/credit-card-emi/payments/"+o.paymentOrderId+"/resend-otp",{});this.notice("A new EMI-payment OTP was sent.");}); }

  async run(task:()=>Promise<void>){this.clear();this.busy(true);try{await task();}catch(e){this.error(this.msg(e));}finally{this.busy(false);}}
  rewardDate=(value?:string):string=>value?new Date(value).toLocaleString("en-IN",{day:"2-digit",month:"short",year:"numeric",hour:"2-digit",minute:"2-digit"}):"—";
  statementDate=(value?:string):string=>value?new Date(value.length===10?value+"T00:00:00":value).toLocaleDateString("en-IN",{day:"2-digit",month:"short",year:"numeric"}):"—";
  msg(e:unknown){return e instanceof Error?e.message:"Card service request failed.";}
}
export = CardServicesViewModel;
