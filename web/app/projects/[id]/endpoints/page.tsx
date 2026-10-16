"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import {
  AlertTriangle,
  Check,
  CheckCircle2,
  Code2,
  Copy,
  ExternalLink,
  Key,
  Play,
  Plus,
  Radio,
  RefreshCw,
  ShieldAlert,
  Sparkles,
  Trash2,
  Zap
} from "lucide-react";
import { api } from "@/lib/api";

interface TestTransformResult {
  valid: boolean;
  transformedPayload?: string;
  errorMessage?: string;
}

export default function EndpointsPage() {
  const params = useParams();
  const projectId = params.id as string;

  const [endpoints, setEndpoints] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [modalOpen, setModalOpen] = useState(false);
  const [testModalOpen, setTestModalOpen] = useState(false);
  const [copiedSecretId, setCopiedSecretId] = useState<string | null>(null);
  const [toastMessage, setToastMessage] = useState("");

  // Create Form State
  const [name, setName] = useState("");
  const [url, setUrl] = useState("https://httpbin.org/post");
  const [eventFilters, setEventFilters] = useState("order.created, payment.succeeded");
  const [secret, setSecret] = useState("");
  const [transformRules, setTransformRules] = useState('{\n  "orderId": "$.data.orderId",\n  "amount_fmt": "$.data.amount | number:\'0.00\'",\n  "customer": "$.data.customer.name | upper"\n}');
  const [creating, setCreating] = useState(false);

  // Test Transform Preview State
  const [testSamplePayload, setTestSamplePayload] = useState('{\n  "event": "order.created",\n  "data": {\n    "orderId": "ord_9981",\n    "amount": 259.4,\n    "customer": {\n      "name": "Jane Doe"\n    }\n  }\n}');
  const [testRules, setTestRules] = useState('{\n  "orderId": "$.data.orderId",\n  "amount_fmt": "$.data.amount | number:\'0.00\'",\n  "customer": "$.data.customer.name | upper"\n}');
  const [testResult, setTestResult] = useState<TestTransformResult | null>(null);
  const [testingTransform, setTestingTransform] = useState(false);

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
        transformRules: transformRules.trim() || undefined,
      });

      setModalOpen(false);
      setName("");
      setUrl("https://httpbin.org/post");
      setEventFilters("order.created");
      setSecret("");
      setTransformRules("");
      setToastMessage("Subscriber endpoint registered successfully!");
      setTimeout(() => setToastMessage(""), 4000);
      loadEndpoints();
    } catch (err: any) {
      alert("Error: " + err.message);
    } finally {
      setCreating(false);
    }
  };

  const runTestTransform = async () => {
    setTestingTransform(true);
    setTestResult(null);
    try {
      const res = await api.testTransform(projectId, {
        payload: testSamplePayload,
        rules: testRules,
      });
      setTestResult(res);
    } catch (err: any) {
      setTestResult({
        valid: false,
        errorMessage: err.message || "Failed to execute transform",
      });
    } finally {
      setTestingTransform(false);
    }
  };

  const handleRotateSecret = async (endpointId: string) => {
    if (!confirm("Rotate webhook signing secret? Existing signature verifications will fail until updated.")) return;
    try {
      await api.rotateSecret(projectId, endpointId);
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
        <div className="fixed bottom-6 right-6 z-50 flex items-center space-x-2 px-4 py-3 rounded-xl bg-emerald-500/10 border border-emerald-500/30 text-emerald-400 text-xs shadow-2xl backdrop-blur-md">
          <CheckCircle2 className="w-4 h-4 shrink-0" />
          <span>{toastMessage}</span>
        </div>
      )}

      {/* Top Bar */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-xl font-bold tracking-tight text-white">Subscriber Endpoints</h1>
          <p className="text-xs text-slate-400 mt-1">
            Destination webhooks, HMAC secrets, per-endpoint circuit breakers, and JSONPath payload transformation rules.
          </p>
        </div>

        <div className="flex items-center space-x-3">
          <button
            onClick={() => setTestModalOpen(true)}
            className="flex items-center space-x-2 px-3 py-2 rounded-xl glass-panel text-slate-300 hover:text-white border border-border text-xs font-medium transition-all"
          >
            <Sparkles className="w-3.5 h-3.5 text-purple-400" />
            <span>Test Transform</span>
          </button>
          <button
            onClick={() => setModalOpen(true)}
            className="flex items-center space-x-2 px-4 py-2 rounded-xl bg-gradient-to-r from-emerald-500 to-teal-600 hover:from-emerald-400 hover:to-teal-500 text-slate-950 text-xs font-semibold shadow-lg shadow-emerald-500/20 transition-all"
          >
            <Plus className="w-4 h-4" />
            <span>Register Endpoint</span>
          </button>
        </div>
      </div>

      {/* Endpoints List */}
      {loading ? (
        <div className="p-12 text-center text-xs text-slate-500">Loading subscriber endpoints...</div>
      ) : endpoints.length === 0 ? (
        <div className="p-12 text-center rounded-2xl glass-panel border border-border space-y-3">
          <div className="w-10 h-10 rounded-full bg-slate-800/80 flex items-center justify-center mx-auto text-slate-400">
            <Radio className="w-5 h-5 text-emerald-400" />
          </div>
          <p className="text-sm font-semibold text-slate-200">No subscriber endpoints configured</p>
          <p className="text-xs text-slate-500 max-w-sm mx-auto">
            Add destination endpoints to start fanning out ingested webhook events with guaranteed delivery.
          </p>
        </div>
      ) : (
        <div className="space-y-4">
          {endpoints.map((ep) => (
            <div
              key={ep.id}
              className={`p-5 rounded-2xl glass-panel border transition-all ${
                ep.circuitBroken
                  ? "border-rose-500/40 bg-rose-950/10"
                  : "border-border hover:border-slate-700"
              } space-y-4`}
            >
              <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4">
                <div className="space-y-1">
                  <div className="flex items-center space-x-3">
                    <span className="font-semibold text-sm text-slate-100">{ep.name}</span>
                    {ep.circuitBroken ? (
                      <span className="inline-flex items-center space-x-1 px-2.5 py-0.5 rounded-full text-[10px] font-semibold bg-rose-500/20 border border-rose-500/40 text-rose-400">
                        <ShieldAlert className="w-3 h-3" />
                        <span>Circuit Broken ({ep.consecutiveFailures} consecutive 5xx)</span>
                      </span>
                    ) : ep.active ? (
                      <span className="inline-flex items-center space-x-1 px-2 py-0.5 rounded-full text-[10px] font-medium bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                        <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse" />
                        <span>Active</span>
                      </span>
                    ) : (
                      <span className="px-2 py-0.5 rounded-full text-[10px] font-medium bg-slate-800 text-slate-400 border border-border">
                        Disabled
                      </span>
                    )}

                    {ep.transformRules && (
                      <span className="inline-flex items-center space-x-1 px-2 py-0.5 rounded-full text-[10px] font-medium bg-purple-500/10 text-purple-400 border border-purple-500/20">
                        <Sparkles className="w-3 h-3" />
                        <span>JSONPath Transform</span>
                      </span>
                    )}
                  </div>

                  <div className="flex items-center space-x-2 text-xs font-mono text-slate-400">
                    <span className="text-slate-500">URL:</span>
                    <span className="text-slate-300 truncate max-w-md">{ep.url}</span>
                    <a href={ep.url} target="_blank" rel="noreferrer" className="text-slate-500 hover:text-slate-300">
                      <ExternalLink className="w-3.5 h-3.5" />
                    </a>
                  </div>
                </div>

                <div className="flex items-center space-x-2 shrink-0">
                  {ep.circuitBroken && (
                    <button
                      onClick={() => handleResetCircuit(ep.id)}
                      className="px-3 py-1.5 rounded-xl text-xs font-semibold bg-rose-500/20 hover:bg-rose-500/30 text-rose-300 border border-rose-500/30 transition-all flex items-center space-x-1"
                    >
                      <Zap className="w-3.5 h-3.5" />
                      <span>Reset Circuit</span>
                    </button>
                  )}

                  <button
                    onClick={() => {
                      if (ep.transformRules) {
                        setTestRules(ep.transformRules);
                      }
                      setTestModalOpen(true);
                    }}
                    title="Test JSONPath Transform"
                    className="p-2 rounded-xl text-slate-400 hover:text-purple-400 hover:bg-slate-800/60 border border-transparent hover:border-slate-700 transition-all text-xs flex items-center gap-1"
                  >
                    <Sparkles className="w-3.5 h-3.5 text-purple-400" />
                    <span className="text-[11px] hidden sm:inline">Test Rules</span>
                  </button>

                  <button
                    onClick={() => handleRotateSecret(ep.id)}
                    title="Rotate Signing Secret"
                    className="p-2 rounded-xl text-slate-400 hover:text-cyan-400 hover:bg-slate-800/60 border border-transparent hover:border-slate-700 transition-all"
                  >
                    <RefreshCw className="w-4 h-4" />
                  </button>

                  <button
                    onClick={() => handleDelete(ep.id)}
                    title="Delete Endpoint"
                    className="p-2 rounded-xl text-slate-400 hover:text-rose-400 hover:bg-slate-800/60 border border-transparent hover:border-slate-700 transition-all"
                  >
                    <Trash2 className="w-4 h-4" />
                  </button>
                </div>
              </div>

              {/* Secret Bar */}
              <div className="flex items-center justify-between p-2.5 rounded-xl bg-slate-900/60 border border-border text-xs">
                <div className="flex items-center space-x-2 font-mono">
                  <Key className="w-3.5 h-3.5 text-amber-400 shrink-0" />
                  <span className="text-slate-500">Signing Secret:</span>
                  <span className="text-slate-300 select-all">{ep.secret}</span>
                </div>
                <button
                  onClick={() => copyToClipboard(ep.secret, ep.id)}
                  className="px-2 py-1 rounded-lg hover:bg-slate-800 text-slate-400 hover:text-slate-200 transition-all flex items-center space-x-1"
                >
                  {copiedSecretId === ep.id ? (
                    <>
                      <Check className="w-3 h-3 text-emerald-400" />
                      <span className="text-[10px] text-emerald-400">Copied</span>
                    </>
                  ) : (
                    <>
                      <Copy className="w-3 h-3" />
                      <span className="text-[10px]">Copy</span>
                    </>
                  )}
                </button>
              </div>

              {/* Transform Rules Preview (if present) */}
              {ep.transformRules && (
                <div className="p-3 rounded-xl bg-slate-950/70 border border-purple-900/40 text-xs space-y-1.5 font-mono">
                  <div className="flex items-center justify-between text-[11px] text-purple-400">
                    <span className="flex items-center gap-1 font-semibold">
                      <Code2 className="w-3.5 h-3.5" /> Outbound Payload Transform Rule:
                    </span>
                    <span className="text-[10px] text-slate-500">Applied before HMAC signature</span>
                  </div>
                  <pre className="text-slate-300 text-[11px] overflow-x-auto whitespace-pre-wrap">{ep.transformRules}</pre>
                </div>
              )}

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

      {/* Test Transform Interactive Modal */}
      {testModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-sm">
          <div className="w-full max-w-2xl p-6 rounded-2xl glass-panel space-y-5 shadow-2xl border border-purple-500/30">
            <div className="flex items-center justify-between border-b border-border pb-3">
              <div className="flex items-center space-x-2">
                <Sparkles className="w-5 h-5 text-purple-400" />
                <div>
                  <h2 className="text-base font-bold text-white">Live JSONPath Payload Transformation Tester</h2>
                  <p className="text-xs text-slate-400">Verify schema mappings, JSONPath extraction, and formatting pipes in real-time</p>
                </div>
              </div>
              <button onClick={() => setTestModalOpen(false)} className="text-slate-400 hover:text-white text-sm">✕</button>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-xs">
              <div className="space-y-1.5">
                <label className="font-semibold text-slate-300">1. Sample Inbound Payload (JSON)</label>
                <textarea
                  rows={8}
                  value={testSamplePayload}
                  onChange={(e) => setTestSamplePayload(e.target.value)}
                  className="w-full p-2.5 bg-slate-950 border border-border rounded-xl font-mono text-[11px] text-slate-200 focus:outline-none focus:border-purple-500"
                />
              </div>

              <div className="space-y-1.5">
                <label className="font-semibold text-slate-300">2. Transformation Mapping Rules (JSONPath)</label>
                <textarea
                  rows={8}
                  value={testRules}
                  onChange={(e) => setTestRules(e.target.value)}
                  className="w-full p-2.5 bg-slate-950 border border-border rounded-xl font-mono text-[11px] text-purple-300 focus:outline-none focus:border-purple-500"
                />
              </div>
            </div>

            <div className="flex justify-between items-center pt-2">
              <p className="text-[11px] text-slate-500">Supports: <code className="text-purple-400">$.path</code>, <code className="text-purple-400">| number:'0.00'</code>, <code className="text-purple-400">| upper</code>, <code className="text-purple-400">| trim</code></p>
              <button
                type="button"
                onClick={runTestTransform}
                disabled={testingTransform}
                className="flex items-center space-x-2 px-4 py-2 rounded-xl bg-gradient-to-r from-purple-500 to-indigo-600 hover:from-purple-400 hover:to-indigo-500 text-white font-semibold text-xs shadow-lg shadow-purple-500/20"
              >
                <Play className="w-3.5 h-3.5" />
                <span>{testingTransform ? "Transforming..." : "Execute Transform"}</span>
              </button>
            </div>

            {testResult && (
              <div className={`p-4 rounded-xl border text-xs font-mono space-y-2 ${
                testResult.valid ? "bg-emerald-950/30 border-emerald-500/40" : "bg-rose-950/30 border-rose-500/40"
              }`}>
                <div className="flex items-center justify-between">
                  <span className={`font-semibold ${testResult.valid ? "text-emerald-400" : "text-rose-400"}`}>
                    {testResult.valid ? "✓ Valid Output Transformed Payload:" : "✕ Validation / Transform Error:"}
                  </span>
                  {testResult.valid && (
                    <span className="text-[10px] text-slate-400">HMAC-SHA256 will sign this transformed body</span>
                  )}
                </div>
                <pre className="text-slate-200 text-[11px] whitespace-pre-wrap overflow-x-auto">
                  {testResult.valid ? testResult.transformedPayload : testResult.errorMessage}
                </pre>
              </div>
            )}
          </div>
        </div>
      )}

      {/* Register Endpoint Modal */}
      {modalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-sm">
          <div className="w-full max-w-lg p-6 rounded-2xl glass-panel space-y-4 shadow-2xl border border-border">
            <div>
              <h2 className="text-lg font-bold text-white">Register Subscriber Endpoint</h2>
              <p className="text-xs text-slate-400 mt-1">Configure HTTPS destination, event filters, signing secret, and payload transformation</p>
            </div>

            <form onSubmit={handleCreate} className="space-y-3.5 text-xs">
              <div className="space-y-1">
                <label className="font-medium text-slate-300">Endpoint Friendly Name</label>
                <input
                  type="text"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  required
                  placeholder="e.g. Stripe Accounting Sink"
                  className="w-full px-3 py-2 bg-slate-900 border border-border rounded-xl text-slate-100 focus:outline-none focus:border-emerald-500"
                />
              </div>

              <div className="space-y-1">
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

              <div className="space-y-1">
                <label className="font-medium text-slate-300">Event Types Filter (Comma-separated or leave blank for all)</label>
                <input
                  type="text"
                  value={eventFilters}
                  onChange={(e) => setEventFilters(e.target.value)}
                  placeholder="order.created, payment.succeeded, user.*"
                  className="w-full px-3 py-2 bg-slate-900 border border-border rounded-xl text-slate-100 font-mono focus:outline-none focus:border-emerald-500"
                />
              </div>

              <div className="space-y-1">
                <div className="flex items-center justify-between">
                  <label className="font-medium text-purple-300 flex items-center gap-1">
                    <Sparkles className="w-3.5 h-3.5" /> Payload Transformation Rules (JSONPath)
                  </label>
                  <span className="text-[10px] text-slate-500">Optional</span>
                </div>
                <textarea
                  rows={4}
                  value={transformRules}
                  onChange={(e) => setTransformRules(e.target.value)}
                  placeholder={"{\"orderId\": \"$.data.orderId\", \"amount\": \"$.data.amount | number:'0.00'\"}"}
                  className="w-full px-3 py-2 bg-slate-900 border border-purple-900/50 rounded-xl text-purple-200 font-mono text-[11px] focus:outline-none focus:border-purple-400"
                />
              </div>

              <div className="space-y-1">
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
