"use client";

import { useCallback, useMemo } from "react";
import type { DecisionNode, DecisionEdge } from "@/types/api";

interface DecisionGraphViewerProps {
  nodes: DecisionNode[];
  edges: DecisionEdge[];
  selectedNodeId?: string;
  onNodeSelect: (node: DecisionNode | null) => void;
  onEdgeSelect: (edge: DecisionEdge | null) => void;
}

/**
 * Decision graph viewer using CSS positioning.
 * For production, consider using React Flow or similar library.
 */
export function DecisionGraphViewer({
  nodes,
  edges,
  selectedNodeId,
  onNodeSelect,
}: DecisionGraphViewerProps) {
  // Build adjacency map
  const adjacencyMap = useMemo(() => {
    const map = new Map<string, string[]>();
    for (const edge of edges) {
      const sourceId = edge.fromId ?? edge.source;
      const targetId = edge.toId ?? edge.target;
      if (sourceId && targetId) {
        const children = map.get(sourceId) || [];
        children.push(targetId);
        map.set(sourceId, children);
      }
    }
    return map;
  }, [edges]);

  // Group nodes by iteration
  const nodesByIteration = useMemo(() => {
    const grouped = new Map<number, DecisionNode[]>();
    for (const node of nodes) {
      const list = grouped.get(node.iteration) || [];
      list.push(node);
      grouped.set(node.iteration, list);
    }
    return grouped;
  }, [nodes]);

  // Get node color based on status
  const getNodeColor = (status: string) => {
    switch (status) {
      case "ACCEPTED":
        return "bg-secondary text-on-secondary border-secondary";
      case "REJECTED":
      case "REJECTED_HARD":
      case "REJECTED_SOFT":
        return "bg-error text-on-error border-error";
      case "TRYING":
        return "bg-primary text-on-primary border-primary";
      default:
        return "bg-surface-variant text-on-surface border-outline";
    }
  };

  // Get edge color based on type
  const getEdgeColor = (type: string) => {
    switch (type) {
      case "ACCEPT":
        return "stroke-secondary";
      case "REJECT":
        return "stroke-error";
      default:
        return "stroke-outline";
    }
  };

  const iterations = Array.from(nodesByIteration.keys()).sort((a, b) => a - b);
  const colWidth = 180;
  const rowHeight = 80;

  return (
    <div className="bg-surface-container-lowest border border-outline-variant rounded-xl overflow-hidden">
      {/* Toolbar */}
      <div className="flex items-center justify-between p-4 border-b border-outline-variant bg-surface-container-low">
        <h3 className="font-title-lg text-title-lg text-on-surface">Graph View</h3>
        <div className="flex gap-2">
          <button className="p-2 rounded-lg hover:bg-surface-container-high transition-colors" title="Fit">
            <span className="material-symbols-outlined text-[20px]">fit_screen</span>
          </button>
          <button className="p-2 rounded-lg hover:bg-surface-container-high transition-colors" title="Zoom In">
            <span className="material-symbols-outlined text-[20px]">zoom_in</span>
          </button>
          <button className="p-2 rounded-lg hover:bg-surface-container-high transition-colors" title="Zoom Out">
            <span className="material-symbols-outlined text-[20px]">zoom_out</span>
          </button>
        </div>
      </div>

      {/* Graph container */}
      <div className="overflow-auto p-4" style={{ maxHeight: "calc(100vh - 300px)" }}>
        {nodes.length === 0 ? (
          <div className="flex items-center justify-center h-64">
            <p className="text-on-surface-variant text-label-md">Không có dữ liệu</p>
          </div>
        ) : (
          <div
            className="relative"
            style={{
              width: iterations.length * colWidth + 100,
              height: Math.max(...iterations.map((i) => (nodesByIteration.get(i)?.length || 0) * rowHeight + 100), 400),
            }}
          >
            {/* Edges */}
            <svg className="absolute inset-0 w-full h-full pointer-events-none" style={{ zIndex: 0 }}>
              {edges.map((edge, idx) => {
                const edgeFromId = edge.fromId ?? edge.source;
                const edgeToId = edge.toId ?? edge.target;
                const fromNode = nodes.find((n) => n.id === edgeFromId);
                const toNode = nodes.find((n) => n.id === edgeToId);
                if (!fromNode || !toNode) return null;

                const fromX = iterations.indexOf(fromNode.iteration) * colWidth + colWidth / 2 + 50;
                const fromY = (nodesByIteration.get(fromNode.iteration)?.indexOf(fromNode) || 0) * rowHeight + 40;
                const toX = iterations.indexOf(toNode.iteration) * colWidth + colWidth / 2 + 50;
                const toY = (nodesByIteration.get(toNode.iteration)?.indexOf(toNode) || 0) * rowHeight + 40;
                const edgeType = edge.type ?? "ACCEPT";

                return (
                  <g key={idx}>
                    <line
                      x1={fromX}
                      y1={fromY}
                      x2={toX}
                      y2={toY}
                      className={getEdgeColor(edgeType)}
                      strokeWidth="2"
                      strokeDasharray={edgeType === "REJECT" ? "4,2" : "none"}
                    />
                    {/* Arrow */}
                    <polygon
                      points={`${toX},${toY} ${toX - 6},${toY - 10} ${toX + 6},${toY - 10}`}
                      className={getEdgeColor(edgeType)}
                      fill="currentColor"
                    />
                  </g>
                );
              })}
            </svg>

            {/* Nodes */}
            {nodes.map((node) => {
              const iterationIdx = iterations.indexOf(node.iteration);
              const nodeIdx = nodesByIteration.get(node.iteration)?.indexOf(node) || 0;
              const x = iterationIdx * colWidth + 50;
              const y = nodeIdx * rowHeight + 20;
              const isSelected = node.id === selectedNodeId;
              const nodeStatus = node.status ?? node.nodeType;

              return (
                <button
                  key={node.id}
                  onClick={() => onNodeSelect(node)}
                  className={`
                    absolute rounded-lg border-2 p-3 transition-all cursor-pointer
                    ${getNodeColor(nodeStatus)}
                    ${isSelected ? "ring-4 ring-primary ring-offset-2" : "hover:shadow-md"}
                    ${nodeStatus === "ACCEPTED" ? "bg-secondary text-white" : ""}
                  `}
                  style={{
                    left: x,
                    top: y,
                    width: colWidth - 20,
                    minHeight: 60,
                    zIndex: 1,
                  }}
                >
                  <div className="text-label-sm font-medium truncate">
                    {node.candidateStaffName || "Slot #" + node.slotId}
                  </div>
                  <div className="text-label-xs opacity-75 mt-1">
                    #{node.iteration}
                  </div>
                  {node.violatedConstraint && (
                    <div className="text-label-xs mt-1 bg-white/20 px-1 rounded">
                      {node.violatedConstraint}
                    </div>
                  )}
                  {node.status === "ACCEPTED" && (
                    <span className="absolute top-1 right-1 material-symbols-outlined text-[12px]">
                      check_circle
                    </span>
                  )}
                </button>
              );
            })}

            {/* Iteration labels */}
            {iterations.map((iter, idx) => (
              <div
                key={iter}
                className="absolute text-label-xs text-on-surface-variant"
                style={{
                  left: idx * colWidth + colWidth / 2 + 50 - 15,
                  top: -20,
                }}
              >
                Iter {iter}
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

