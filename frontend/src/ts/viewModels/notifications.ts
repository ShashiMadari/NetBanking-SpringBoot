import * as ko from "knockout";
import { api } from "../services/api";
import * as AccUtils from "../accUtils";
import { currentRole } from "../services/auth";

type NotificationDate = string | number[] | null;
interface Notification { notificationId: number; channel: string; eventType: string; message: string; read: boolean; createdAt: NotificationDate; }
class NotificationsViewModel {
  notifications = ko.observableArray<Notification>([]);
  orderedNotifications = ko.pureComputed(() => [...this.notifications()].sort((left, right) => {
    const leftTime = this.notificationTime(left.createdAt);
    const rightTime = this.notificationTime(right.createdAt);
    // Unknown dates go last; IDs give equal or missing timestamps a stable order.
    if (leftTime === null && rightTime !== null) return 1;
    if (rightTime === null && leftTime !== null) return -1;
    return (rightTime !== null && leftTime !== null ? rightTime - leftTime : 0)
      || right.notificationId - left.notificationId;
  }));
  error = ko.observable("");
  loading = ko.observable(true);
  markingId = ko.observable<number | null>(null);
  markingAll = ko.observable(false);
  unreadCount = ko.pureComputed(() => this.notifications().filter((notification) => !notification.read).length);

  async connected(): Promise<void> {
    // Old bookmarks must not send administrators to the customer-only API.
    if (currentRole() === "ADMIN") {
      window.location.replace("?ojr=admin-audit");
      return;
    }
    AccUtils.announce("Notifications page loaded.");
    document.title = "Notifications | O-Bank";
    try {
      this.setNotifications(await api.get<Notification[]>("/api/notifications?limit=200"));
    } catch (error) {
      this.error(error instanceof Error ? error.message : "Unable to load notifications.");
    } finally {
      this.loading(false);
    }
  }

  async markRead(notification: Notification): Promise<void> {
    if (notification.read || this.markingId() !== null) return;
    this.error(""); this.markingId(notification.notificationId);
    try {
      await api.patch(`/api/notifications/${notification.notificationId}/read`);
      this.notifications(this.notifications().map((item) => item.notificationId === notification.notificationId ? { ...item, read: true } : item));
      AccUtils.announce("Notification marked as read.");
      window.dispatchEvent(new Event("o-bank:notifications-changed"));
    } catch (error) { this.error(error instanceof Error ? error.message : "Unable to update this notification."); }
    finally { this.markingId(null); }
  }

  async markAllRead(): Promise<void> {
    if (!this.unreadCount() || this.markingAll()) return;
    this.error(""); this.markingAll(true);
    try {
      await api.patch("/api/notifications/read-all");
      this.notifications(this.notifications().map((item) => ({ ...item, read: true })));
      AccUtils.announce("All notifications marked as read.");
      window.dispatchEvent(new Event("o-bank:notifications-changed"));
    } catch (error) { this.error(error instanceof Error ? error.message : "Unable to mark all notifications as read."); }
    finally { this.markingAll(false); }
  }

  private setNotifications(items: Notification[]): void {
    this.notifications(items);
  }

  private notificationTime(value: NotificationDate): number | null {
    if (!value) return null;
    let time: number;
    if (Array.isArray(value)) {
      if (value.length < 3 || !value.every(Number.isFinite)) return null;
      const [year, month, day, hour = 0, minute = 0, second = 0, nanos = 0] = value;
      time = new Date(year, month - 1, day, hour, minute, second, Math.floor(nanos / 1000000)).getTime();
    } else {
      // Accept ISO timestamps and SQL-style timestamps with fractional seconds.
      const normalized = value.trim().replace(/^(\d{4}-\d{2}-\d{2})\s+/, "$1T")
        .replace(/(\.\d{3})\d+/, "$1");
      time = Date.parse(normalized);
    }
    return Number.isFinite(time) ? time : null;
  }

  formattedDate(value: NotificationDate): string {
    const time = this.notificationTime(value);
    return time === null ? "Date unavailable"
      : new Date(time).toLocaleString("en-IN", { dateStyle: "medium", timeStyle: "short" });
  }
}
export = NotificationsViewModel;
