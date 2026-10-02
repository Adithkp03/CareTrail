import type { Metadata, Viewport } from "next";
import "./globals.css";
import BottomNav from "@/components/BottomNav";
import SwRegister from "./sw-register";
import ConnectionBanner from "@/components/ConnectionBanner";

export const metadata: Metadata = {
  title: "CareTrail",
  manifest: "/manifest.webmanifest",
  description: "Every mother's pregnancy journey - done, now, next - in her own language.",
};

export const viewport: Viewport = { width: "device-width", initialScale: 1 };

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body className="min-h-screen">
        <SwRegister />
        <ConnectionBanner />
        <div className="mx-auto max-w-md px-5 pb-32">{children}</div>
        <BottomNav />
      </body>
    </html>
  );
}
