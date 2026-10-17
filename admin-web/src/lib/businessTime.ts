/**
 * Business calendar helpers for the Dodla Employee Store.
 *
 * Reports use India Standard Time regardless of the browser's configured
 * timezone, so a date selected in the UI maps to the same business day
 * for every administrator.
 */
export const BUSINESS_TIME_ZONE = "Asia/Kolkata";
const INDIA_OFFSET = "+05:30";

function dateParts(date: Date) {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: BUSINESS_TIME_ZONE,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(date);

  const values = Object.fromEntries(parts.map((part) => [part.type, part.value]));
  return { year: values.year, month: values.month, day: values.day };
}

export function getBusinessDateString(date = new Date()): string {
  const { year, month, day } = dateParts(date);
  return `${year}-${month}-${day}`;
}

export function getBusinessMonthString(date = new Date()): string {
  return getBusinessDateString(date).slice(0, 7);
}

function addDays(dateString: string, days: number): string {
  const date = new Date(`${dateString}T00:00:00Z`);
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}

export function getBusinessDayRange(dateString: string) {
  const nextDate = addDays(dateString, 1);
  return {
    start: new Date(`${dateString}T00:00:00${INDIA_OFFSET}`).toISOString(),
    end: new Date(`${nextDate}T00:00:00${INDIA_OFFSET}`).toISOString(),
  };
}

export function getBusinessMonthRange(monthString: string) {
  const [year, month] = monthString.split("-").map(Number);
  const nextMonthDate = new Date(Date.UTC(year, month, 1));
  const nextMonth = `${nextMonthDate.getUTCFullYear()}-${String(nextMonthDate.getUTCMonth() + 1).padStart(2, "0")}-01`;
  return getBusinessDayRange(`${monthString}-01`).start
    ? {
        start: new Date(`${monthString}-01T00:00:00${INDIA_OFFSET}`).toISOString(),
        end: new Date(`${nextMonth}T00:00:00${INDIA_OFFSET}`).toISOString(),
      }
    : { start: "", end: "" };
}

export function formatBusinessDateTime(value: string | Date): string {
  return new Date(value).toLocaleString("en-IN", { timeZone: BUSINESS_TIME_ZONE });
}

export function formatBusinessTime(value: string | Date): string {
  return new Date(value).toLocaleTimeString("en-IN", {
    timeZone: BUSINESS_TIME_ZONE,
    hour: "2-digit",
    minute: "2-digit",
  });
}
