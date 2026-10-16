"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import {
  AlertTriangle,
  Check,
  CheckCircle2,
  Copy,
  ExternalLink,
  Key,
  Plus,
  Radio,
  RefreshCw,
  ShieldAlert,
  Trash2,
  Zap
} from "lucide-react";
import { api } from "@/lib/api";

export default function EndpointsPage() {
  const params = useParams();
  const projectId = params.id as string;

  const [endpoints, setEndpoints] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [modalOpen, setModalOpen] = useState(false);
  const [copiedSecretId, setCopiedSecretId] = useState<string | null>(null);
  const [toastMessage, setToastMessage] = useState("");

  // Create Form State
  const [name, setName] = useState("");
  const [url, setUrl] = useState("https://httpbin.org/post");
  const [eventFilters, setEventFilters] = useState("order.created, payment.succeeded");
  const [secret, setSecret] = useState("");
  const [creating, setCreating] = useState(false);

  const loadEndpoints = () => {
    setLoading(true);
    api.getEndpoints(projectId)
      .then((data) => setEndpoints(data))
      .catch((err) => console.error(err))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    if (projectId) {
      loadEndpoints();
    }
  }, [projectId]);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    setCreating(true);
    try {
      const filters = eventFilters
        .split(",")
        .map((s) => s.trim())
        .filter(Boolean);

      await api.createEndpoint(projectId, {
        name,
        url,
        secret: secret.trim() || undefined,
        eventTypesFilter: filters,
      });

      setModalOpen(false);
      setName("");
      setUrl("https://httpbin.org/post");
      setEventFilters("order.created");
      setSecret("");
      setToastMessage("Subscriber endpoint registered successfully!");
      setTimeout(() => setToastMessage(""), 4000);
      loadEndpoints();
    } catch (err: any) {
      alert("Error: " + err.message);
    } finally {
      setCreating(false);
    }
  };

  const handleRotateSecret = async (endpointId: string) => {
    if (!confirm("Rotate webhook signing secret? Existing signature verifications will fail until updated.")) return;
    try {
      const updated = await api.rotateSecret(projectId, endpointId);
      setToastMessage("Signing secret rotated successfully!");
      setTimeout(() => setToastMessage(""), 4000);
      loadEndpoints();
    } catch (err: any) {
      alert("Failed to rotate secret: " + err.message);
    }
  };

  const handleResetCircuit = async (endpointId: string) => {
    try {
      await api.resetCircuitBreaker(projectId, endpointId);
      setToastMessage("Circuit breaker reset! Deliveries resumed.");
      setTimeout(() => setToastMessage(""), 4000);
      loadEndpoints();
    } catch (err: any) {
      alert("Reset failed: " + err.message);
    }
  };

  const handleDelete = async (endpointId: string) => {
    if (!confirm("Delete this subscriber endpoint? All queued deliveries for it will be discarded.")) return;
    try {
      await api.deleteEndpoint(projectId, endpointId);
      setToastMessage("Endpoint deleted.");
      setTimeout(() => setToastMessage(""), 4000);
      loadEndpoints();
    } catch (err: any) {
      alert("Delete failed: " + err.message);
    }
  };

  const copyToClipboard = (text: string, id: string) => {
    navigator.clipboard.writeText(text);
    setCopiedSecretId(id);
    setTimeout(() => setCopiedSecretId(null), 2000);
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
          <h1 className="text-2xl font-bold tracking-tight text-white">Subscriber Endpoints</h1>
          <p className="text-xs text-slate-400 mt-1">
            Registered webhook destinations, event filters, signing secrets, and circuit breaker health
          </p>
        </div>
        <button
          onClick={() => setModalOpen(true)}
          className="px-4 py-2.5 rounded-xl bg-gradient-to-r from-emerald-500 to-teal-600 hover:from-emerald-400 hover:to-teal-500 text-slate-950 font-semibold text-xs shadow-lg shadow-emerald-500/20 hover:scale-[1.02] transition-all flex items-center space-x-2"
        >
          <Plus className="w-4 h-4" />
          <span>Register Endpoint</span>
        </button>
      </div>

      {/* Endpoints List */}
      {loading ? (
        <div className="py-16 text-center text-xs text-slate-500">Loading endpoints...</div>
      ) : endpoints.length === 0 ? (
        <div className="glass-panel p-12 text-center rounded-2xl border-dashed space-y-3">
          <Radio className="w-10 h-10 text-slate-500 mx-auto" />
          <h3 className="text-sm font-semibold text-slate-200">No endpoints registered yet</h3>
          <p className="text-xs text-slate-400 max-w-sm mx-auto">
            Add your first webhook endpoint to begin receiving signed event payloads.
          </p>
          <button
            onClick={() => setModalOpen(true)}
            className="mt-2 px-4 py-2 rounded-xl bg-emerald-500/10 hover:bg-emerald-500/20 text-emerald-400 border border-emerald-500/30 text-xs font-medium"
          >
            Create an Endpoint
          </button>
        </div>
      ) : (
        <div className="grid grid-cols-1 gap-4">
          {endpoints.map((ep) => (
            <div key={ep.id} className="glass-panel p-5 rounded-2xl space-y-4 hover:border-slate-700/80 transition-all">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
                <div className="flex items-center space-x-3">
                  <div className={`w-3 h-3 rounded-full ${
                    ep.circuitBroken
                      ? "bg-amber-400 shadow-md shadow-amber-400/30"
                      : ep.active
                      ? "bg-emerald-400 shadow-md shadow-emerald-400/30 animate-pulse"
                      : "bg-slate-500"
                  }`} />
                  <h3 className="text-sm font-bold text-white">{ep.name}</h3>
                  {ep.circuitBroken && (
                    <span className="px-2 py-0.5 rounded text-[10px] font-semibold bg-amber-500/10 text-amber-400 border border-amber-500/20">
                      CIRCUIT TRIPPED ({ep.consecutiveFailures} fails)
                    </span>
                  )}
                  {ep.active && !ep.circuitBroken && (
                    <span className="px-2 py-0.5 rounded text-[10px] font-semibold bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                      ACTIVE
                    </span>
                  )}
                </div>

                <div className="flex items-center space-x-2">
                  {ep.circuitBroken && (
                    <button
                      onClick={() => handleResetCircuit(ep.id)}
                      className="px-2.5 py-1 rounded-lg bg-emerald-500/10 hover:bg-emerald-500/20 text-emerald-400 border border-emerald-500/30 text-xs font-medium transition-colors"
                    >
                      Reset Circuit
                    </button>
                  )}
                  <button
                    onClick={() => handleRotateSecret(ep.id)}
                    title="Rotate Signing Secret"
                    className="p-1.5 rounded-lg text-slate-400 hover:text-slate-200 hover:bg-slate-800 transition-colors"
                  >
                    <RefreshCw className="w-3.5 h-3.5" />
                  </button>
                  <button
                    onClick={() => handleDelete(ep.id)}
                    title="Delete Endpoint"
                    className="p-1.5 rounded-lg text-slate-400 hover:text-rose-400 hover:bg-rose-500/10 transition-colors"
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                  </button>
                </div>
              </div>

              {/* URL & Secret */}
              <div className="grid grid-cols-1 md:grid-cols-2 gap-3 text-xs">
                <div className="p-2.5 rounded-xl bg-slate-900/80 border border-border flex items-center justify-between">
                  <span className="font-mono text-slate-300 truncate">{ep.url}</span>
                </div>
                <div className="p-2.5 rounded-xl bg-slate-900/80 border border-border flex items-center justify-between">
                  <div className="flex items-center space-x-2 truncate">
                    <Key className="w-3.5 h-3.5 text-slate-400 shrink-0" />
                    <span className="font-mono text-slate-400 text-[11px] truncate">{ep.secret}</span>
                  </div>
                  <button
                    onClick={() => copyToClipboard(ep.secret, ep.id)}
                    className="text-slate-400 hover:text-emerald-400 ml-2"
                  >
                    {copiedSecretId === ep.id ? (
                      <Check className="w-3.5 h-3.5 text-emerald-400" />
                    ) : (
                      <Copy className="w-3.5 h-3.5" />
                    )}
                  </button>
                </div>
              </div>

              {/* Filter Tags */}
              <div className="flex flex-wrap items-center gap-1.5 text-xs text-slate-400">
                <span className="text-[11px] text-slate-500">Subscribed Events:</span>
                {(!ep.eventTypesFilter || ep.eventTypesFilter.length === 0) ? (
                  <span className="px-2 py-0.5 rounded-md bg-slate-800 border border-border text-[11px] text-slate-300">
                    * (Wildcard / All Events)
                  </span>
                ) : (
                  ep.eventTypesFilter.map((f: string) => (
                    <span key={f} className="px-2 py-0.5 rounded-md bg-slate-800/80 border border-border text-[11px] text-cyan-400 font-mono">
                      {f}
                    </span>
                  ))
                )}
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Register Endpoint Modal */}
      {modalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-sm">
          <div className="w-full max-w-lg p-6 rounded-2xl glass-panel space-y-5 shadow-2xl border border-border">
            <div>
              <h2 className="text-lg font-bold text-white">Register Subscriber Endpoint</h2>
              <p className="text-xs text-slate-400 mt-1">Configure HTTPS destination, event filters, and signing secret</p>
            </div>

            <form onSubmit={handleCreate} className="space-y-4 text-xs">
              <div className="space-y-1.5">
                <label className="font-medium text-slate-300">Endpoint Friendly Name</label>
                <input
                  type="text"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  required
                  placeholder="e.g. Billing Microservice Webhook"
                  className="w-full px-3 py-2 bg-slate-900 border border-border rounded-xl text-slate-100 focus:outline-none focus:border-emerald-500"
                />
              </div>

              <div className="space-y-1.5">
                <label className="font-medium text-slate-300">Destination URL (SSRF & HTTPS Validated)</label>
                <input
                  type="url"
                  value={url}
                  onChange={(e) => setUrl(e.target.value)}
                  required
                  placeholder="https://api.yourdomain.com/webhooks"
                  className="w-full px-3 py-2 bg-slate-900 border border-border rounded-xl text-slate-100 font-mono focus:outline-none focus:border-emerald-500"
                />
              </div>

              <div className="space-y-1.5">
                <label className="font-medium text-slate-300">Event Types Filter (Comma-separated or leave blank for all)</label>
                <input
                  type="text"
                  value={eventFilters}
                  onChange={(e) => setEventFilters(e.target.value)}
                  placeholder="order.created, payment.succeeded, user.*"
                  className="w-full px-3 py-2 bg-slate-900 border border-border rounded-xl text-slate-100 font-mono focus:outline-none focus:border-emerald-500"
                />
              </div>

              <div className="space-y-1.5">
                <label className="font-medium text-slate-300">Signing Secret (Optional, will auto-generate whsec_... if blank)</label>
                <input
                  type="text"
                  value={secret}
                  onChange={(e) => setSecret(e.target.value)}
                  placeholder="whsec_custom_secret_key"
                  className="w-full px-3 py-2 bg-slate-900 border border-border rounded-xl text-slate-100 font-mono focus:outline-none focus:border-emerald-500"
                />
              </div>

              <div className="flex items-center justify-end space-x-3 pt-3 border-t border-border">
                <button
                  type="button"
                  onClick={() => setModalOpen(false)}
                  className="px-4 py-2 rounded-xl text-slate-400 hover:text-white"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={creating}
                  className="px-4 py-2 rounded-xl bg-gradient-to-r from-emerald-500 to-teal-600 hover:from-emerald-400 hover:to-teal-500 text-slate-950 font-semibold"
                >
                  {creating ? "Saving..." : "Create Endpoint"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
