import type { NextConfig } from "next";
import bundleAnalyzer from "@next/bundle-analyzer";

const withBundleAnalyzer = bundleAnalyzer({
  enabled: process.env.ANALYZE === "true",
});

const nextConfig: NextConfig = {
  output: 'standalone',
  // ponytail: rewrite destination uses env var REWRITE_DEST (default localhost:8080
  // for local dev). Docker compose sets REWRITE_DEST=http://backend:8080.
  async rewrites() {
	    const dest = process.env.REWRITE_DEST || 'http://localhost:8081';
    return [
      {
        source: '/api/:path*',
        destination: `${dest}/api/:path*`,
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
  },
};

export default withBundleAnalyzer(nextConfig);