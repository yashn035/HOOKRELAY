"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import {
  AlertTriangle,
  Check,
  CheckCircle2,
  Copy,
  Key,
  Plus,
  ShieldAlert,
  Trash2,
  Zap
} from "lucide-react";
import { api } from "@/lib/api";

export default function ApiKeysPage() {
  const params = useParams();
  const projectId = params.id as string;

  const [keys, setKeys] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [modalOpen, setModalOpen] = useState(false);
  const [keyName, setKeyName] = useState("");
  const [creating, setCreating] = useState(false);
  const [generatedKey, setGeneratedKey] = useState<any | null>(null);
  const [copiedKey, setCopiedKey] = useState(false);
  const [toastMessage, setToastMessage] = useState("");

  const loadKeys = () => {
    setLoading(true);
    api.getApiKeys(projectId)
      .then((data) => setKeys(data))
      .catch((err) => console.error(err))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    if (projectId) {
      loadKeys();
    }
  }, [projectId]);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    setCreating(true);
    try {
      const res = await api.createApiKey(projectId, { name: keyName });
      setGeneratedKey(res);
      setKeyName("");
      loadKeys();
    } catch (err: any) {
      alert("Failed to create key: " + err.message);
    } finally {
      setCreating(false);
    }
  };

  const handleRevoke = async (keyId: string) => {
    if (!confirm("Revoke this API key? Ingestion requests authenticated with this token will immediately be rejected.")) return;
    try {
      await api.revokeApiKey(projectId, keyId);
      setToastMessage("API key revoked.");
      setTimeout(() => setToastMessage(""), 4000);
      loadKeys();
    } catch (err: any) {
      alert("Failed to revoke: " + err.message);
    }
  };

  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text);
    setCopiedKey(true);
    setTimeout(() => setCopiedKey(false), 2500);
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
          <h1 className="text-2xl font-bold tracking-tight text-white">Tenant API Keys</h1>
          <p className="text-xs text-slate-400 mt-1">
            Authenticate webhook event ingestion with Bearer tokens (hr_live_...). Keys are hashed with SHA-256 at rest.
          </p>
        </div>

        <button
          onClick={() => {
            setGeneratedKey(null);
            setModalOpen(true);
          }}
          className="px-4 py-2.5 rounded-xl bg-gradient-to-r from-emerald-500 to-teal-600 hover:from-emerald-400 hover:to-teal-500 text-slate-950 font-semibold text-xs shadow-lg shadow-emerald-500/20 hover:scale-[1.02] transition-all flex items-center space-x-2"
        >
          <Plus className="w-4 h-4" />
          <span>Generate API Key</span>
        </button>
      </div>

      {/* Keys Table */}
      <div className="glass-panel rounded-2xl overflow-hidden">
        {loading ? (
          <div className="py-16 text-center text-xs text-slate-500">Loading API keys...</div>
        ) : keys.length === 0 ? (
          <div className="p-12 text-center space-y-2">
            <Key className="w-8 h-8 text-slate-500 mx-auto" />
            <h3 className="text-sm font-semibold text-slate-200">No API keys created</h3>
            <p className="text-xs text-slate-400">Generate a secret key to begin publishing webhooks.</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead>
                <tr className="border-b border-border/80 bg-slate-900/60 text-slate-400">
                  <th className="py-3 px-4">Key Name</th>
                  <th className="py-3 px-4">Key Prefix</th>
                  <th className="py-3 px-4">Status</th>
                  <th className="py-3 px-4">Last Used</th>
                  <th className="py-3 px-4">Created At</th>
                  <th className="py-3 px-4 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border/40">
                {keys.map((k) => (
                  <tr key={k.id} className="hover:bg-slate-900/40">
                    <td className="py-3 px-4 font-semibold text-slate-200">{k.name}</td>
                    <td className="py-3 px-4">
                      <span className="font-mono text-cyan-400 bg-cyan-500/10 px-2 py-0.5 rounded border border-cyan-500/20">
                        {k.keyPrefix}••••••••
                      </span>
                    </td>
                    <td className="py-3 px-4">
                      {k.revoked ? (
                        <span className="px-2 py-0.5 rounded text-[10px] font-semibold bg-rose-500/10 text-rose-400 border border-rose-500/20">
                          REVOKED
                        </span>
                      ) : (
                        <span className="px-2 py-0.5 rounded text-[10px] font-semibold bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                          ACTIVE
                        </span>
                      )}
                    </td>
                    <td className="py-3 px-4 text-slate-400 text-[11px]">
                      {k.lastUsedAt ? new Date(k.lastUsedAt).toLocaleString() : "Never used"}
                    </td>
                    <td className="py-3 px-4 text-slate-400 text-[11px]">
                      {new Date(k.createdAt).toLocaleDateString()}
                    </td>
                    <td className="py-3 px-4 text-right">
                      {!k.revoked && (
                        <button
                          onClick={() => handleRevoke(k.id)}
                          className="px-2.5 py-1 rounded-lg text-rose-400 hover:bg-rose-500/10 border border-transparent hover:border-rose-500/20 text-[11px] font-medium transition-colors"
                        >
                          Revoke
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Generate Key Modal */}
      {modalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-sm">
          <div className="w-full max-w-md p-6 rounded-2xl glass-panel space-y-5 shadow-2xl border border-border">
            {!generatedKey ? (
              <>
                <div>
                  <h2 className="text-lg font-bold text-white">Generate Tenant API Key</h2>
                  <p className="text-xs text-slate-400 mt-1">
                    API keys allow your backend servers to publish events into HookRelay
                  </p>
                </div>

                <form onSubmit={handleCreate} className="space-y-4 text-xs">
                  <div className="space-y-1.5">
                    <label className="font-medium text-slate-300">Key Identifier / Name</label>
                    <input
                      type="text"
                      value={keyName}
                      onChange={(e) => setKeyName(e.target.value)}
                      required
                      placeholder="e.g. Production Ingestion Service"
                      className="w-full px-3 py-2.5 bg-slate-900 border border-border rounded-xl text-slate-100 focus:outline-none focus:border-emerald-500"
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
                      {creating ? "Generating..." : "Generate Key"}
                    </button>
                  </div>
                </form>
              </>
            ) : (
              <div className="space-y-4">
                <div className="flex items-center space-x-2 text-emerald-400">
                  <CheckCircle2 className="w-5 h-5" />
                  <h2 className="text-base font-bold text-white">API Key Created</h2>
                </div>

                <div className="p-3 rounded-xl bg-amber-500/10 border border-amber-500/20 text-amber-300 text-xs flex items-start space-x-2">
                  <AlertTriangle className="w-4 h-4 shrink-0 mt-0.5" />
                  <span>
                    Copy this key immediately. For security, we never store raw secret keys and will never show it again.
                  </span>
                </div>

                <div className="space-y-1.5">
                  <span className="text-[11px] font-medium text-slate-400">Your Raw Secret Key:</span>
                  <div className="p-3 rounded-xl bg-slate-950 border border-border flex items-center justify-between">
                    <span className="font-mono text-xs text-emerald-400 break-all select-all">
                      {generatedKey.rawSecretKey}
                    </span>
                    <button
                      onClick={() => copyToClipboard(generatedKey.rawSecretKey)}
                      className="ml-3 p-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-200 shrink-0"
                    >
                      {copiedKey ? <Check className="w-4 h-4 text-emerald-400" /> : <Copy className="w-4 h-4" />}
                    </button>
                  </div>
                </div>

                <button
                  type="button"
                  onClick={() => {
                    setModalOpen(false);
                    setGeneratedKey(null);
                  }}
                  className="w-full py-2.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-semibold"
                >
                  I have copied my key safely
                </button>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
