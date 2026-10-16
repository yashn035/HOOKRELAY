"use client";

import { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
import {
  AlertCircle,
  AlertTriangle,
  ArrowRight,
  CheckCircle2,
  Clock,
  ExternalLink,
  Filter,
  Play,
  RefreshCw,
  RotateCcw,
  ShieldAlert
} from "lucide-react";
import { api } from "@/lib/api";

export default function DeliveriesPage() {
  const params = useParams();
  const router = useRouter();
  const projectId = params.id as string;

  const [deliveries, setDeliveries] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [statusFilter, setStatusFilter] = useState("");
  const [replayingId, setReplayingId] = useState<string | null>(null);
  const [toastMessage, setToastMessage] = useState("");

  const loadDeliveries = () => {
    setLoading(true);
    api.getDeliveries(projectId, statusFilter || undefined)
      .then((res) => setDeliveries(res.content || []))
      .catch((err) => console.error(err))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    if (projectId) {
      loadDeliveries();
    }
  }, [projectId, statusFilter]);

  const handleReplay = async (deliveryId: string, e: React.MouseEvent) => {
    e.stopPropagation();
    setReplayingId(deliveryId);
    try {
      await api.replayDelivery(projectId, deliveryId);
      setToastMessage("Delivery requeued for immediate dispatch!");
      setTimeout(() => setToastMessage(""), 4000);
      loadDeliveries();
    } catch (err: any) {
      alert("Replay failed: " + err.message);
    } finally {
      setReplayingId(null);
    }
  };

  const handleBulkReplay = async () => {
    if (!confirm("Replay all failed/dead-letter deliveries for this project?")) return;
    try {
      const res = await api.bulkReplay(projectId, { status: "DEAD_LETTER" });
      setToastMessage(`Bulk replay scheduled: ${res.count} deliveries re-queued.`);
      setTimeout(() => setToastMessage(""), 4000);
      loadDeliveries();
    } catch (err: any) {
      alert("Bulk replay failed: " + err.message);
    }
  };

  const getStatusBadge = (status: string) => {
    switch (status) {
      case "SUCCEEDED":
        return (
          <span className="inline-flex items-center space-x-1 px-2 py-0.5 rounded-md bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 text-[10px] font-semibold">
            <CheckCircle2 className="w-3 h-3" />
            <span>SUCCEEDED</span>
          </span>
        );
      case "PENDING":
      case "PROCESSING":
        return (
          <span className="inline-flex items-center space-x-1 px-2 py-0.5 rounded-md bg-cyan-500/10 text-cyan-400 border border-cyan-500/20 text-[10px] font-semibold">
            <Clock className="w-3 h-3 animate-spin" />
            <span>{status}</span>
          </span>
        );
      case "DEAD_LETTER":
        return (
          <span className="inline-flex items-center space-x-1 px-2 py-0.5 rounded-md bg-rose-500/10 text-rose-400 border border-rose-500/20 text-[10px] font-semibold">
            <AlertCircle className="w-3 h-3" />
            <span>DEAD LETTER</span>
          </span>
        );
      case "FAILED":
      default:
        return (
          <span className="inline-flex items-center space-x-1 px-2 py-0.5 rounded-md bg-amber-500/10 text-amber-400 border border-amber-500/20 text-[10px] font-semibold">
            <AlertTriangle className="w-3 h-3" />
            <span>FAILED</span>
          </span>
        );
    }
  };

  return (
    <div className="space-y-6">
      {/* Toast Notification */}
      {toastMessage && (
        <div className="fixed bottom-6 right-6 z-50 flex items-center space-x-2 px-4 py-3 rounded-xl bg-emerald-500/10 border border-emerald-500/30 text-emerald-400 text-sm shadow-2xl backdrop-blur-md">
          <CheckCircle2 className="w-4 h-4 shrink-0" />
          <span>{toastMessage}</span>
        </div>
      )}

      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-white">Delivery Queue & History</h1>
          <p className="text-xs text-slate-400 mt-1">
            Real-time delivery attempts, exponential backoff retries, and dead letter records
          </p>
        </div>

        <div className="flex items-center space-x-3">
          <button
            onClick={handleBulkReplay}
            className="px-3 py-2 rounded-xl border border-rose-500/30 bg-rose-500/10 hover:bg-rose-500/20 text-rose-400 text-xs font-semibold flex items-center space-x-2 transition-colors"
          >
            <RotateCcw className="w-3.5 h-3.5" />
            <span>Bulk Replay Dead Letters</span>
          </button>
        </div>
      </div>

      {/* Filter Tabs */}
      <div className="flex items-center space-x-2 border-b border-border/80 pb-3 text-xs overflow-x-auto">
        {[
          { label: "All Deliveries", value: "" },
          { label: "Succeeded", value: "SUCCEEDED" },
          { label: "Pending Retries", value: "PENDING" },
          { label: "Dead Letter Queue", value: "DEAD_LETTER" },
        ].map((tab) => (
          <button
            key={tab.value}
            onClick={() => setStatusFilter(tab.value)}
            className={`px-3 py-1.5 rounded-lg font-medium transition-colors ${
              statusFilter === tab.value
                ? "bg-slate-800 text-emerald-400 border border-slate-700"
                : "text-slate-400 hover:text-white"
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {/* Deliveries Table */}
      <div className="glass-panel rounded-2xl overflow-hidden">
        {loading ? (
          <div className="py-16 text-center text-xs text-slate-500">Loading deliveries...</div>
        ) : deliveries.length === 0 ? (
          <div className="p-12 text-center space-y-2">
            <RefreshCw className="w-8 h-8 text-slate-500 mx-auto" />
            <h3 className="text-sm font-semibold text-slate-200">No deliveries recorded</h3>
            <p className="text-xs text-slate-400">Deliveries will appear here as soon as events fan out to endpoints.</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead>
                <tr className="border-b border-border/80 bg-slate-900/60 text-slate-400">
                  <th className="py-3 px-4">Event Type</th>
                  <th className="py-3 px-4">Destination Endpoint</th>
                  <th className="py-3 px-4">Status</th>
                  <th className="py-3 px-4">Attempts</th>
                  <th className="py-3 px-4">Next Retry / Timestamp</th>
                  <th className="py-3 px-4 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border/40">
                {deliveries.map((del) => (
                  <tr
                    key={del.id}
                    onClick={() => router.push(`/projects/${projectId}/deliveries/${del.id}`)}
                    className="hover:bg-slate-900/40 cursor-pointer transition-colors"
                  >
                    <td className="py-3 px-4">
                      <span className="font-mono font-medium text-slate-200">{del.eventType}</span>
                    </td>
                    <td className="py-3 px-4">
                      <div className="font-semibold text-slate-300">{del.endpointName}</div>
                      <div className="font-mono text-[10px] text-slate-500 truncate max-w-xs">{del.endpointUrl}</div>
                    </td>
                    <td className="py-3 px-4">{getStatusBadge(del.status)}</td>
                    <td className="py-3 px-4">
                      <span className="font-mono text-[11px] text-slate-400">
                        {del.attemptCount} / {del.maxAttempts}
                      </span>
                    </td>
                    <td className="py-3 px-4 text-slate-400 text-[11px]">
                      {del.status === "PENDING"
                        ? `Next retry in: ${new Date(del.nextAttemptAt).toLocaleTimeString()}`
                        : new Date(del.createdAt).toLocaleString()}
                    </td>
                    <td className="py-3 px-4 text-right">
                      <div className="flex items-center justify-end space-x-2">
                        <button
                          onClick={(e) => handleReplay(del.id, e)}
                          disabled={replayingId === del.id}
                          title="Replay Webhook"
                          className="px-2.5 py-1 rounded-lg bg-emerald-500/10 hover:bg-emerald-500/20 text-emerald-400 border border-emerald-500/30 text-[11px] font-medium flex items-center space-x-1 transition-colors"
                        >
                          <Play className="w-3 h-3" />
                          <span>{replayingId === del.id ? "Replaying..." : "Replay"}</span>
                        </button>
                        <ArrowRight className="w-3.5 h-3.5 text-slate-500" />
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
