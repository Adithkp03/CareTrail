import type { Metadata, Viewport } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "CareTrail",
  description: "Every mother's pregnancy journey - done, now, next - in her own language.",
};

export const viewport: Viewport = { width: "device-width", initialScale: 1 };

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body className="min-h-screen">
        <div className="mx-auto max-w-md px-4 pb-16">{children}</div>
      </body>
    </html>
  );
}
