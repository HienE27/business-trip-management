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
