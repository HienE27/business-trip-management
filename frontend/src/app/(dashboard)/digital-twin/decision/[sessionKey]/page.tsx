"use client";

import { useCallback, useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import { BackButton } from "@/components/ui/BackButton";
import { Button } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";
import { Skeleton } from "@/components/ui/Skeleton";
import type {
  SandboxSession,
  DecisionGraph,
  DecisionNode,
  DecisionEdge,
  GraphStatistics,
} from "@/types/api";
import { DecisionGraphViewer } from "./DecisionGraphViewer";
import { NodeDetailPanel } from "./NodeDetailPanel";
import { GraphStats } from "./GraphStats";
import { GraphFilters } from "./GraphFilters";

/**
 * v11.1.6.5 Decision Graph Page
 *
 * Interactive visualization of decision-making process:
 * - Full decision graph
 * - Node/edge details
 * - Graph statistics
 * - Filtering and search
 */
export default function DecisionGraphPage() {
  const params = useParams();
  const router = useRouter();
  const sessionKey = params.sessionKey as string;

  const [session, setSession] = useState<SandboxSession | null>(null);
  const [graph, setGraph] = useState<DecisionGraph | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Selected node
  const [selectedNode, setSelectedNode] = useState<DecisionNode | null>(null);
  const [selectedEdge, setSelectedEdge] = useState<DecisionEdge | null>(null);

  // Filters
  const [filterStatus, setFilterStatus] = useState<"all" | "accepted" | "rejected">("all");
  const [searchQuery, setSearchQuery] = useState("");

  // Load data
  const loadData = useCallback(async () => {
    if (!sessionKey) return;

    setLoading(true);
    setError(null);

    try {
      const [sessionData, graphData] = await Promise.all([
        api.getSandboxByKey(sessionKey),
        api.getDecisionGraph(sessionKey),
      ]);

      setSession(sessionData as SandboxSession | null);
      setGraph(graphData as DecisionGraph | null);
    } catch (err) {
      setError(getErrorMessage(err, "Có lỗi xảy ra"));
    } finally {
      setLoading(false);
    }
  }, [sessionKey]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  // Filter nodes
  const filteredNodes = graph?.nodes.filter((node) => {
    // Status filter
    if (filterStatus === "accepted" && node.status !== "ACCEPTED") return false;
    if (filterStatus === "rejected" && !(node.status ?? "").startsWith("REJECTED")) return false;

    // Search filter
    if (searchQuery) {
      const query = searchQuery.toLowerCase();
      const matchStaff = node.candidateStaffName?.toLowerCase().includes(query);
      const matchConstraint = node.violatedConstraint?.toLowerCase().includes(query);
      const matchReason = node.rejectionReason?.toLowerCase().includes(query);
      if (!matchStaff && !matchConstraint && !matchReason) return false;
    }

    return true;
  }) ?? [];

  const filteredEdges = graph?.edges.filter((edge) => {
    if (filterStatus === "all") return true;

    const targetNode = graph.nodes.find((n) => n.id === edge.toId);
    if (!targetNode) return false;

    if (filterStatus === "accepted") return edge.type === "ACCEPT";
    if (filterStatus === "rejected") return edge.type === "REJECT";
    return true;
  }) ?? [];

  if (loading) {
    return (
      <div className="p-margin-desktop space-y-6">
        <Skeleton className="h-12 w-64" />
        <div className="grid grid-cols-1 lg:grid-cols-4 gap-6">
          <div className="lg:col-span-3">
            <Skeleton className="h-[600px] rounded-xl" />
          </div>
          <Skeleton className="h-[600px] rounded-xl" />
        </div>
      </div>
    );
  }

  if (error || !session || !graph) {
    return (
      <div className="p-margin-desktop">
        <BackButton href="/digital-twin/replay" />
        <div className="mt-6 p-6 bg-error-container rounded-lg text-center">
          <p className="text-on-error-container">Không tìm thấy decision graph</p>
          {error && <p className="text-label-sm mt-2 text-on-error-container/70">{error}</p>}
        </div>
      </div>
    );
  }

  return (
    <div className="p-margin-desktop space-y-6">
      {/* Header */}
      <div className="flex items-start justify-between gap-4">
        <div>
          <div className="flex items-center gap-3 mb-2">
            <BackButton href="/digital-twin/replay" />
            <h1 className="font-display-lg text-display-lg text-on-surface">Decision Graph</h1>
          </div>
          <p className="text-body-sm text-on-surface-variant">
            {session.name} • {graph.nodes.length} nodes, {graph.edges.length} edges
          </p>
        </div>

        <div className="flex gap-2">
          <Button variant="ghost" onClick={() => router.push(`/digital-twin/replay/${sessionKey}`)}>
            <span className="material-symbols-outlined text-[18px]">replay</span>
            Replay
          </Button>
        </div>
      </div>

      {/* Filters */}
      <GraphFilters
        filterStatus={filterStatus}
        onFilterChange={setFilterStatus}
        searchQuery={searchQuery}
        onSearchChange={setSearchQuery}
        nodeCount={filteredNodes.length}
        totalCount={graph.nodes.length}
      />

      {/* Main Content */}
      <div className="grid grid-cols-1 lg:grid-cols-4 gap-6">
        {/* Graph Viewer */}
        <div className="lg:col-span-3">
          <DecisionGraphViewer
            nodes={filteredNodes}
            edges={filteredEdges}
            selectedNodeId={selectedNode?.id}
            onNodeSelect={setSelectedNode}
            onEdgeSelect={setSelectedEdge}
          />
        </div>

        {/* Sidebar */}
        <div className="space-y-6">
          {/* Statistics */}
          {graph.statistics && <GraphStats stats={graph.statistics} />}

          {/* Node Detail */}
          {selectedNode && (
            <NodeDetailPanel node={selectedNode} />
          )}

          {/* Legend */}
          <div className="bg-surface-container-lowest border border-outline-variant rounded-xl p-4">
            <h3 className="font-title-lg text-title-lg text-on-surface mb-4">Legend</h3>
            <div className="space-y-2">
              <div className="flex items-center gap-2">
                <div className="w-4 h-4 rounded bg-secondary" />
                <span className="text-label-sm text-on-surface">Accepted</span>
              </div>
              <div className="flex items-center gap-2">
                <div className="w-4 h-4 rounded bg-error" />
                <span className="text-label-sm text-on-surface">Rejected</span>
              </div>
              <div className="flex items-center gap-2">
                <div className="w-4 h-4 rounded bg-surface-variant" />
                <span className="text-label-sm text-on-surface">Trying</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
