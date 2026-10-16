"use client";

import { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
import {
  ArrowLeft,
  CheckCircle2,
  Clock,
  Play,
  RotateCcw,
  ShieldAlert,
  Zap
} from "lucide-react";
import { api } from "@/lib/api";

export default function DeliveryDetailPage() {
  const params = useParams();
  const router = useRouter();
  const projectId = params.id as string;
  const deliveryId = params.deliveryId as string;

  const [detail, setDetail] = useState<any | null>(null);
  const [loading, setLoading] = useState(true);
  const [replaying, setReplaying] = useState(false);
  const [toastMessage, setToastMessage] = useState("");

  const loadDetail = () => {
    setLoading(true);
    api.getDeliveryDetail(projectId, deliveryId)
      .then((data) => setDetail(data))
      .catch((err) => console.error(err))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    if (projectId && deliveryId) {
      loadDetail();
    }
  }, [projectId, deliveryId]);

  const handleReplay = async () => {
    setReplaying(true);
    try {
      await api.replayDelivery(projectId, deliveryId);
      setToastMessage("Delivery requeued for immediate dispatch!");
      setTimeout(() => setToastMessage(""), 4000);
      loadDetail();
    } catch (err: any) {
      alert("Replay failed: " + err.message);
    } finally {
      setReplaying(false);
    }
  };

  if (loading) {
    return <div className="py-20 text-center text-xs text-slate-500">Loading delivery details...</div>;
  }

  if (!detail) {
    return <div className="py-20 text-center text-xs text-slate-500">Delivery not found.</div>;
  }

  const { delivery, eventPayload, attempts } = detail;

  return (
    <div className="space-y-6">
      {/* Toast Notification */}
      {toastMessage && (
        <div className="fixed bottom-6 right-6 z-50 flex items-center space-x-2 px-4 py-3 rounded-xl bg-emerald-500/10 border border-emerald-500/30 text-emerald-400 text-sm shadow-2xl backdrop-blur-md">
          <CheckCircle2 className="w-4 h-4 shrink-0" />
          <span>{toastMessage}</span>
        </div>
      )}

      {/* Back button & Action Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div className="flex items-center space-x-3">
          <Link
            href={`/projects/${projectId}/deliveries`}
            className="p-2 rounded-xl bg-slate-900 border border-border text-slate-400 hover:text-white transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
          </Link>
          <div>
            <div className="flex items-center space-x-2">
              <h1 className="text-xl font-bold tracking-tight text-white">{delivery.eventType}</h1>
              <span className="px-2 py-0.5 rounded text-[10px] font-mono bg-slate-800 text-slate-300 border border-border">
                {delivery.status}
              </span>
            </div>
            <p className="text-xs text-slate-400 mt-0.5 font-mono">
              Target: {delivery.endpointName} ({delivery.endpointUrl})
            </p>
          </div>
        </div>

        <button
          onClick={handleReplay}
          disabled={replaying}
          className="px-4 py-2 rounded-xl bg-gradient-to-r from-emerald-500 to-teal-600 hover:from-emerald-400 hover:to-teal-500 text-slate-950 font-semibold text-xs shadow-lg shadow-emerald-500/20 hover:scale-[1.02] transition-all flex items-center space-x-2 disabled:opacity-50"
        >
          <Play className="w-3.5 h-3.5" />
          <span>{replaying ? "Replaying..." : "Replay Delivery"}</span>
        </button>
      </div>

      {/* Delivery Summary Grid */}
      <div className="grid grid-cols-1 sm:grid-cols-4 gap-4 text-xs">
        <div className="glass-panel p-4 rounded-xl space-y-1">
          <span className="text-slate-400 text-[11px]">Delivery ID</span>
          <div className="font-mono text-slate-200 truncate">{delivery.id}</div>
        </div>
        <div className="glass-panel p-4 rounded-xl space-y-1">
          <span className="text-slate-400 text-[11px]">Total Attempts</span>
          <div className="font-mono text-slate-200">{delivery.attemptCount} / {delivery.maxAttempts}</div>
        </div>
        <div className="glass-panel p-4 rounded-xl space-y-1">
          <span className="text-slate-400 text-[11px]">Created At</span>
          <div className="text-slate-200">{new Date(delivery.createdAt).toLocaleString()}</div>
        </div>
        <div className="glass-panel p-4 rounded-xl space-y-1">
          <span className="text-slate-400 text-[11px]">Next Scheduled Attempt</span>
          <div className="text-slate-200">
            {delivery.status === "PENDING" ? new Date(delivery.nextAttemptAt).toLocaleTimeString() : "N/A"}
          </div>
        </div>
      </div>

      {/* Attempts Timeline */}
      <div className="glass-panel p-6 rounded-2xl space-y-6">
        <div>
          <h2 className="text-sm font-semibold text-white">Execution Timeline & HTTP Transcripts</h2>
          <p className="text-[11px] text-slate-400">Sequential dispatch attempts with response payloads and latency profiles</p>
        </div>

        {attempts.length === 0 ? (
          <div className="py-8 text-center text-xs text-slate-500">
            No dispatch attempts executed yet. Queued for worker pickup.
          </div>
        ) : (
          <div className="space-y-4">
            {attempts.map((att: any, idx: number) => {
              const is2xx = att.responseStatus >= 200 && att.responseStatus < 300;
              return (
                <div key={att.id} className="p-4 rounded-xl bg-slate-900/60 border border-border space-y-3">
                  <div className="flex flex-col sm:flex-row sm:items-center justify-between text-xs gap-2">
                    <div className="flex items-center space-x-2.5">
                      <span className="px-2 py-0.5 rounded font-mono font-bold text-xs bg-slate-800 text-slate-300">
                        Attempt #{att.attemptNumber}
                      </span>
                      <span className={`px-2 py-0.5 rounded text-[11px] font-mono font-semibold ${
                        is2xx ? "bg-emerald-500/10 text-emerald-400 border border-emerald-500/20" : "bg-rose-500/10 text-rose-400 border border-rose-500/20"
                      }`}>
                        HTTP {att.responseStatus || "ERR"}
                      </span>
                      <span className="text-slate-400 font-mono text-[11px]">{att.latencyMs || 0}ms</span>
                    </div>
                    <span className="text-slate-500 text-[11px]">{new Date(att.executedAt).toLocaleString()}</span>
                  </div>

                  {att.errorMessage && (
                    <div className="p-2.5 rounded-lg bg-rose-500/10 border border-rose-500/20 text-rose-400 text-xs font-mono">
                      {att.errorMessage}
                    </div>
                  )}

                  {att.responseBody && (
                    <div className="space-y-1">
                      <span className="text-[10px] text-slate-400 font-medium">Response Body:</span>
                      <pre className="p-2.5 rounded-lg bg-slate-950 border border-border text-[11px] font-mono text-slate-300 max-h-32 overflow-y-auto whitespace-pre-wrap">
                        {att.responseBody}
                      </pre>
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* Raw Payload Dispatched */}
      <div className="glass-panel p-6 rounded-2xl space-y-3">
        <h2 className="text-sm font-semibold text-white">Dispatched Event Payload</h2>
        <div className="p-3.5 rounded-xl bg-slate-950 border border-border max-h-64 overflow-y-auto">
          <pre className="text-xs font-mono text-emerald-300 whitespace-pre-wrap leading-relaxed">
            {eventPayload ? JSON.stringify(JSON.parse(eventPayload), null, 2) : "{}"}
          </pre>
        </div>
      </div>
    </div>
  );
}
