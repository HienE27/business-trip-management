/* eslint-disable @next/next/no-page-custom-font */
import type { Metadata } from "next";
import { Inter } from "next/font/google";
import { AuthGuard } from "@/components/auth/AuthGuard";
import { AuthProvider } from "@/components/auth/AuthProvider";
import { NotificationProvider } from "@/components/ui/NotificationContext";
import { ToastProvider } from "@/components/ui/ToastProvider";
import { ErrorBoundary } from "@/components/ErrorBoundary";
import "./globals.css";

const inter = Inter({
  variable: "--font-inter",
  subsets: ["latin", "vietnamese"],
  weight: ["400", "500", "600", "700"],
  display: "swap",
  adjustFontFallback: true,
});

export const metadata: Metadata = {
  title: "MedSchedule Pro",
  description: "Website quản lý lịch công tác phòng khám",
  icons: {
    icon: "https://fonts.gstatic.com/s/materialsymbolsoutlined/v118/kJF2BvI19Vv5Eu0SyFb6TEb7u3K6ts1.woff2",
  },
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="vi" className={`${inter.variable} h-full antialiased`}>
      <head>
        <link rel="preconnect" href="https://fonts.googleapis.com" />
        <link
          rel="preconnect"
          href="https://fonts.gstatic.com"
          crossOrigin="anonymous"
        />
        <link
          href="https://fonts.googleapis.com/css2?family=Material+Symbols+Outlined:opsz,wght,FILL,GRAD@20..48,400,0..1,-50..12&display=swap"
          rel="stylesheet"
        />
      </head>
      <body className="min-h-full flex flex-col">
        <AuthProvider>
          <AuthGuard>
            <NotificationProvider>
              <ToastProvider>
              <ErrorBoundary>
                {children}
              </ErrorBoundary>
              </ToastProvider>
            </NotificationProvider>
          </AuthGuard>
        </AuthProvider>
      </body>
    </html>
  );
}
