"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { Check, CheckCircle2, Copy, Filter, Plus, Send, Zap } from "lucide-react";
import { api } from "@/lib/api";

export default function EventsPage() {
  const params = useParams();
  const projectId = params.id as string;

  const [events, setEvents] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [filterType, setFilterType] = useState("");
  const [selectedEvent, setSelectedEvent] = useState<any | null>(null);
  const [toastMessage, setToastMessage] = useState("");
  const [copiedId, setCopiedId] = useState<string | null>(null);

  const loadEvents = () => {
    setLoading(true);
    api.getEvents(projectId, filterType || undefined)
      .then((res) => {
        setEvents(res.content || []);
      })
      .catch((err) => console.error(err))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    if (projectId) {
      loadEvents();
    }
  }, [projectId, filterType]);

  const copyToClipboard = (text: string, id: string) => {
    navigator.clipboard.writeText(text);
    setCopiedId(id);
    setTimeout(() => setCopiedId(null), 2000);
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
          <h1 className="text-2xl font-bold tracking-tight text-white">Ingested Events</h1>
          <p className="text-xs text-slate-400 mt-1">
            Complete audit trail of incoming webhook payloads and fan-out deliveries
          </p>
        </div>

        <div className="flex items-center space-x-3">
          <div className="relative">
            <Filter className="w-3.5 h-3.5 absolute left-3 top-3 text-slate-500" />
            <input
              type="text"
              value={filterType}
              onChange={(e) => setFilterType(e.target.value)}
              placeholder="Filter by event type..."
              className="pl-8 pr-3 py-2 bg-slate-900 border border-border rounded-xl text-xs text-slate-200 placeholder-slate-500 focus:outline-none focus:border-emerald-500"
            />
          </div>
        </div>
      </div>

      {/* Events Table */}
      <div className="glass-panel rounded-2xl overflow-hidden">
        {loading ? (
          <div className="py-16 text-center text-xs text-slate-500">Loading events...</div>
        ) : events.length === 0 ? (
          <div className="p-12 text-center space-y-2">
            <Send className="w-8 h-8 text-slate-500 mx-auto" />
            <h3 className="text-sm font-semibold text-slate-200">No events ingested yet</h3>
            <p className="text-xs text-slate-400">
              Publish events using POST /api/v1/events with your tenant API key.
            </p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead>
                <tr className="border-b border-border/80 bg-slate-900/60 text-slate-400">
                  <th className="py-3 px-4">Event ID</th>
                  <th className="py-3 px-4">Type</th>
                  <th className="py-3 px-4">Idempotency Key</th>
                  <th className="py-3 px-4">Timestamp</th>
                  <th className="py-3 px-4 text-right">Payload</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border/40">
                {events.map((ev) => (
                  <tr key={ev.id} className="hover:bg-slate-900/40">
                    <td className="py-3 px-4">
                      <div className="flex items-center space-x-1.5 font-mono text-[11px] text-slate-300">
                        <span>{ev.id.substring(0, 8)}...</span>
                        <button onClick={() => copyToClipboard(ev.id, ev.id)} className="text-slate-500 hover:text-emerald-400">
                          {copiedId === ev.id ? <Check className="w-3 h-3 text-emerald-400" /> : <Copy className="w-3 h-3" />}
                        </button>
                      </div>
                    </td>
                    <td className="py-3 px-4">
                      <span className="px-2 py-0.5 rounded-md bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 font-mono font-medium">
                        {ev.type}
                      </span>
                    </td>
                    <td className="py-3 px-4 font-mono text-[11px] text-slate-400">
                      {ev.idempotencyKey || "—"}
                    </td>
                    <td className="py-3 px-4 text-slate-400 text-[11px]">
                      {new Date(ev.createdAt).toLocaleString()}
                    </td>
                    <td className="py-3 px-4 text-right">
                      <button
                        onClick={() => setSelectedEvent(ev)}
                        className="px-2.5 py-1 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-200 border border-border text-[11px]"
                      >
                        View JSON
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Raw Payload Modal */}
      {selectedEvent && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-sm">
          <div className="w-full max-w-xl p-6 rounded-2xl glass-panel space-y-4 shadow-2xl border border-border">
            <div className="flex items-center justify-between">
              <div>
                <h3 className="text-sm font-bold text-white">Event Payload: {selectedEvent.type}</h3>
                <p className="text-[11px] font-mono text-slate-400 mt-0.5">{selectedEvent.id}</p>
              </div>
              <button
                onClick={() => setSelectedEvent(null)}
                className="text-slate-400 hover:text-white text-xs"
              >
                Close
              </button>
            </div>

            <div className="p-3 rounded-xl bg-slate-950 border border-border max-h-96 overflow-y-auto">
              <pre className="text-[11px] font-mono text-emerald-300 leading-relaxed whitespace-pre-wrap">
                {JSON.stringify(JSON.parse(selectedEvent.data || "{}"), null, 2)}
              </pre>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
