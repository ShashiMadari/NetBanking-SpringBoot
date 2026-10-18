import * as ko from "knockout";
import { api } from "./api";

interface PurchaseStartResponse { orderId: number; message?: string; }
interface PurchaseAuthoriseResponse { message?: string; status?: string; }

export class PurchaseAuthorisation {
  orderId = ko.observable<number | null>(null);
  otp = ko.observable("");
  busy = ko.observable(false);

  reset(): void {
    this.orderId(null);
    this.otp("");
  }

  async start(request: Record<string, unknown>): Promise<PurchaseStartResponse> {
    if (this.busy()) throw new Error("Please wait for the current request.");
    this.busy(true);
    try {
      const result = await api.post<PurchaseStartResponse>("/api/investments/purchase/send-otp", request);
      if (!Number.isInteger(result.orderId) || result.orderId <= 0) throw new Error("The purchase order number was not returned.");
      this.orderId(result.orderId);
      this.otp("");
      return result;
    } finally {
      this.busy(false);
    }
  }

  async authorise(): Promise<PurchaseAuthoriseResponse> {
    const id = this.orderId();
    const otp = this.otp().trim();
    if (!id) throw new Error("Request a purchase OTP first.");
    if (!/^[0-9]{6}$/.test(otp)) throw new Error("Enter the six-digit purchase OTP.");
    if (this.busy()) throw new Error("Please wait for the current request.");
    this.busy(true);
    try {
      const result = await api.post<PurchaseAuthoriseResponse>(`/api/investments/orders/${id}/authorise`, { otp });
      this.reset();
      return result;
    } finally {
      this.busy(false);
    }
  }
}
