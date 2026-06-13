"use client";

import { memo } from "react";
import { CoverageInspector } from "@/components/schedule-summary/CoverageInspector";

export type CoverageSectionProps = {
  coverageGaps: string[];
  hasCoverageGaps: boolean;
  totalCoverageGaps: number;
};

export const CoverageSection = memo(function CoverageSection(props: CoverageSectionProps) {
  return <CoverageInspector {...props} />;
});
