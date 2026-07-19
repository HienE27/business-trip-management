import type { NextConfig } from "next";
import bundleAnalyzer from "@next/bundle-analyzer";

const withBundleAnalyzer = bundleAnalyzer({
  enabled: process.env.ANALYZE === "true",
});

const nextConfig: NextConfig = {
  output: 'standalone',
  async rewrites() {
    return [
      {
        source: '/api/:path*',
        destination: `${process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080/api/v1"}`.replace(/\/api\/v1$/, "/api") + '/:path*',
      },
    ];
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
  experimental: {
    // Barrel-exported paths. optimizePackageImports turns named imports
    // from these modules into deep imports at build time so unused
    // exports don't bloat the client bundle.
    optimizePackageImports: [
      "@/components/ui",         // barrel at src/components/ui/index.ts
    ],
    // Long-running auto-schedule algos (RRHC/CP-SAT/Beam) need >60s.
    // Default rewrite proxy aborts early → browser HTTP 500 / Broken pipe.
    proxyTimeout: 600_000,
  },
};

export default withBundleAnalyzer(nextConfig);