import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: 'standalone',
  typescript: {
    ignoreBuildErrors: true,
  },
  redirects: async () => [
    {
      source: "/shift-swaps",
      destination: "/swap-requests",
      permanent: false,
    },
    {
      source: "/audit-logs",
      destination: "/audit-history",
      permanent: false,
    },
    {
      source: "/duty-24",
      destination: "/monthly-schedule?tab=L01",
      permanent: false,
    },
    {
      source: "/duty-24/shift-detail/:id",
      destination: "/monthly-schedule?scheduleId=:id",
      permanent: false,
    },
    {
      source: "/all-day",
      destination: "/monthly-schedule?tab=L02",
      permanent: false,
    },
    {
      source: "/service-clinic",
      destination: "/monthly-schedule?tab=L03",
      permanent: false,
    },
    {
      source: "/expert-clinic",
      destination: "/monthly-schedule?tab=L04",
      permanent: false,
    },
    {
      source: "/conflict-check",
      destination: "/monthly-schedule?panel=conflicts",
      permanent: false,
    },
    {
      source: "/schedule-summary",
      destination: "/monthly-schedule?panel=summary",
      permanent: false,
    },
  ],
};

export default nextConfig;
