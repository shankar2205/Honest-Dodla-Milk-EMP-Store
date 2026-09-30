import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Honest Milk - Dodla Employee Store",
  description: "Admin portal for the internal employee store",
};

export default function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
