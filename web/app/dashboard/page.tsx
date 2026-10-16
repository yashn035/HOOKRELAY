"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import {
  Activity,
  AlertTriangle,
  ArrowUpRight,
  CheckCircle2,
  Clock,
  ExternalLink,
  Plus,
  Radio,
  RefreshCw,
  Send,
  ShieldAlert,
  Zap
} from "lucide-react";
import {
  AreaChart,
  Area,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  Tooltip,
  ResponsiveContainer
} from "recharts";
import { api, getStoredProject } from "@/lib/api";

export default function DashboardPage() {
  const [data, setData] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [projectId, setProjectId] = useState<string>("");
  const [sendingTest, setSendingTest] = useState(false);
  const [toastMessage, setToastMessage] = useState("");

  const loadData = (pId: string) => {
    setLoading(true);
    api.getDashboardOverview(pId)
      .then((res) => setData(res))
      .catch((err) => console.error(err))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    const pId = getStoredProject();
    if (pId) {
      setProjectId(pId);
      loadData(pId);
    } else {
      api.getProjects().then((projects) => {
        if (projects.length > 0) {
          setProjectId(projects[0].id);
          loadData(projects[0].id);
        }
      });
    }
  }, []);

  const handleSendTestEvent = async () => {
    if (!projectId) return;
    setSendingTest(true);
    try {
      const payload = {
        type: "order.created",
        data: {
          orderId: "ord_" + Math.random().toString(36).substring(2, 9),
          amount: (Math.random() * 200 + 10).toFixed(2),
          currency: "USD",
          customer: { email: "customer@example.com", name: "Alex Mercer" },
          status: "PAID",
          timestamp: new Date().toISOString()
        }
      };
      const res = await api.publishEvent(projectId, payload);
      setToastMessage(`Test event ingested! Fanout: ${res.deliveriesCreated} deliveries scheduled.`);
      setTimeout(() => setToastMessage(""), 4000);
      loadData(projectId);
    } catch (err: any) {
      alert("Failed to send test event: " + err.message);
    } finally {
      setSendingTest(false);
    }
  };

  const handleResetCircuit = async (endpointId: string) => {
    try {
      await api.resetCircuitBreaker(projectId, endpointId);
      setToastMessage("Circuit breaker reset successfully!");
      setTimeout(() => setToastMessage(""), 4000);
      loadData(projectId);
    } catch (err: any) {
      alert("Reset failed: " + err.message);
    }
  };

  return (
    <div className="space-y-8">
      {/* Toast Notification */}
      {toastMessage && (
        <div className="fixed bottom-6 right-6 z-50 flex items-center space-x-2 px-4 py-3 rounded-xl bg-emerald-500/10 border border-emerald-500/30 text-emerald-400 text-sm shadow-2xl backdrop-blur-md">
          <CheckCircle2 className="w-4 h-4 shrink-0" />
          <span>{toastMessage}</span>
        </div>
      )}

      {/* Header bar */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-white">System Observability</h1>
          <p className="text-xs text-slate-400 mt-1">Real-time health, delivery throughput, and endpoint reliability metrics</p>
        </div>
        <div className="flex items-center space-x-3">
          <button
            onClick={() => loadData(projectId)}
            title="Refresh"
            className="p-2.5 rounded-xl border border-border bg-slate-900/60 hover:bg-slate-800 text-slate-300 transition-colors"
          >
            <RefreshCw className={`w-4 h-4 ${loading ? "animate-spin" : ""}`} />
          </button>
          <button
            onClick={handleSendTestEvent}
            disabled={sendingTest || !projectId}
            className="px-4 py-2.5 rounded-xl bg-gradient-to-r from-emerald-500 to-teal-600 hover:from-emerald-400 hover:to-teal-500 text-slate-950 font-semibold text-xs shadow-lg shadow-emerald-500/20 hover:scale-[1.02] transition-all flex items-center space-x-2 disabled:opacity-50"
          >
            <Zap className="w-3.5 h-3.5" />
            <span>{sendingTest ? "Ingesting..." : "Send Test Webhook"}</span>
          </button>
        </div>
      </div>

      {/* KPI Metric Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-5">
        <div className="glass-panel p-5 rounded-2xl relative overflow-hidden group">
          <div className="flex items-center justify-between">
            <span className="text-xs font-medium text-slate-400">Total Ingested</span>
            <div className="w-8 h-8 rounded-lg bg-emerald-500/10 border border-emerald-500/20 flex items-center justify-center text-emerald-400">
              <Send className="w-4 h-4" />
            </div>
          </div>
          <div className="mt-3">
            <div className="text-3xl font-extrabold text-white tracking-tight">
              {data ? data.totalEvents.toLocaleString() : "..."}
            </div>
            <p className="text-[11px] text-emerald-400 mt-1 flex items-center space-x-1">
              <span>{data?.succeededDeliveries || 0} succeeded</span>
            </p>
          </div>
        </div>

        <div className="glass-panel p-5 rounded-2xl relative overflow-hidden group">
          <div className="flex items-center justify-between">
            <span className="text-xs font-medium text-slate-400">Delivery Success Rate</span>
            <div className="w-8 h-8 rounded-lg bg-cyan-500/10 border border-cyan-500/20 flex items-center justify-center text-cyan-400">
              <CheckCircle2 className="w-4 h-4" />
            </div>
          </div>
          <div className="mt-3">
            <div className="text-3xl font-extrabold text-white tracking-tight">
              {data ? `${data.successRatePercent}%` : "..."}
            </div>
            <p className="text-[11px] text-slate-400 mt-1">Target SLA: 99.9%</p>
          </div>
        </div>

        <div className="glass-panel p-5 rounded-2xl relative overflow-hidden group">
          <div className="flex items-center justify-between">
            <span className="text-xs font-medium text-slate-400">Latency (p95)</span>
            <div className="w-8 h-8 rounded-lg bg-violet-500/10 border border-violet-500/20 flex items-center justify-center text-violet-400">
              <Clock className="w-4 h-4" />
            </div>
          </div>
          <div className="mt-3">
            <div className="text-3xl font-extrabold text-white tracking-tight">
              {data ? `${data.p95LatencyMs}ms` : "..."}
            </div>
            <p className="text-[11px] text-slate-400 mt-1">Average: {data?.avgLatencyMs || 0}ms</p>
          </div>
        </div>

        <div className="glass-panel p-5 rounded-2xl relative overflow-hidden group">
          <div className="flex items-center justify-between">
            <span className="text-xs font-medium text-slate-400">Dead Letters / Fails</span>
            <div className="w-8 h-8 rounded-lg bg-rose-500/10 border border-rose-500/20 flex items-center justify-center text-rose-400">
              <ShieldAlert className="w-4 h-4" />
            </div>
          </div>
          <div className="mt-3">
            <div className="text-3xl font-extrabold text-white tracking-tight">
              {data ? data.deadLetterDeliveries : "..."}
            </div>
            <p className="text-[11px] text-rose-400 mt-1">
              {data?.pendingDeliveries || 0} in active backoff queue
            </p>
          </div>
        </div>
      </div>

      {/* Analytics Charts */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Delivery Throughput Area Chart */}
        <div className="lg:col-span-2 glass-panel p-6 rounded-2xl space-y-4">
          <div className="flex items-center justify-between">
            <div>
              <h2 className="text-sm font-semibold text-white">Delivery Volume & Reliability (24h)</h2>
              <p className="text-[11px] text-slate-400">Hourly successful vs failed webhook dispatches</p>
            </div>
            <div className="flex items-center space-x-3 text-xs">
              <span className="flex items-center space-x-1 text-emerald-400">
                <span className="w-2 h-2 rounded-full bg-emerald-500"></span>
                <span>Success</span>
              </span>
              <span className="flex items-center space-x-1 text-rose-400">
                <span className="w-2 h-2 rounded-full bg-rose-500"></span>
                <span>Failed</span>
              </span>
            </div>
          </div>
          <div className="h-64 w-full pt-4">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={data?.deliveryChart || []}>
                <defs>
                  <linearGradient id="colorSuccess" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#10b981" stopOpacity={0.3} />
                    <stop offset="95%" stopColor="#10b981" stopOpacity={0} />
                  </linearGradient>
                  <linearGradient id="colorFail" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#f43f5e" stopOpacity={0.3} />
                    <stop offset="95%" stopColor="#f43f5e" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <XAxis dataKey="timestamp" stroke="#475569" fontSize={10} tickLine={false} />
                <YAxis stroke="#475569" fontSize={10} tickLine={false} />
                <Tooltip
                  contentStyle={{
                    backgroundColor: "#0f172a",
                    border: "1px solid #1e293b",
                    borderRadius: "0.75rem",
                    fontSize: "12px",
                  }}
                />
                <Area type="monotone" dataKey="succeeded" stroke="#10b981" strokeWidth={2} fillOpacity={1} fill="url(#colorSuccess)" />
                <Area type="monotone" dataKey="failed" stroke="#f43f5e" strokeWidth={2} fillOpacity={1} fill="url(#colorFail)" />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </div>

        {/* Latency Distribution Histogram */}
        <div className="glass-panel p-6 rounded-2xl space-y-4">
          <div>
            <h2 className="text-sm font-semibold text-white">Latency Histogram</h2>
            <p className="text-[11px] text-slate-400">End-to-end response time tiers</p>
          </div>
          <div className="h-64 w-full pt-4">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={data?.latencyDistribution || []}>
                <XAxis dataKey="range" stroke="#475569" fontSize={10} tickLine={false} />
                <YAxis stroke="#475569" fontSize={10} tickLine={false} />
                <Tooltip
                  contentStyle={{
                    backgroundColor: "#0f172a",
                    border: "1px solid #1e293b",
                    borderRadius: "0.75rem",
                    fontSize: "12px",
                  }}
                />
                <Bar dataKey="count" fill="#06b6d4" radius={[6, 6, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>
      </div>

      {/* Top Failing Endpoints & Circuit Breakers */}
      <div className="glass-panel p-6 rounded-2xl space-y-4">
        <div className="flex items-center justify-between">
          <div>
            <h2 className="text-sm font-semibold text-white">Circuit Breaker & Degraded Endpoints</h2>
            <p className="text-[11px] text-slate-400">Subscribers experiencing repeated failures or auto-tripped circuit breakers</p>
          </div>
          <Link
            href={`/projects/${projectId}/endpoints`}
            className="text-xs text-emerald-400 hover:underline flex items-center space-x-1"
          >
            <span>Manage all endpoints</span>
            <ArrowUpRight className="w-3.5 h-3.5" />
          </Link>
        </div>

        {(!data?.topFailingEndpoints || data.topFailingEndpoints.length === 0) ? (
          <div className="py-8 text-center border border-dashed border-border rounded-xl">
            <CheckCircle2 className="w-8 h-8 text-emerald-400 mx-auto opacity-70" />
            <p className="text-xs text-slate-300 font-medium mt-2">All subscriber endpoints healthy</p>
            <p className="text-[11px] text-slate-500">No active circuit trips or elevated failure rates detected.</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead>
                <tr className="border-b border-border/60 text-slate-400">
                  <th className="py-2.5 px-3">Endpoint Name</th>
                  <th className="py-2.5 px-3">Destination URL</th>
                  <th className="py-2.5 px-3">Consecutive Failures</th>
                  <th className="py-2.5 px-3">Circuit State</th>
                  <th className="py-2.5 px-3 text-right">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border/40">
                {data.topFailingEndpoints.map((ep: any) => (
                  <tr key={ep.endpointId} className="hover:bg-slate-900/40">
                    <td className="py-3 px-3 font-semibold text-slate-200">{ep.endpointName}</td>
                    <td className="py-3 px-3 font-mono text-[11px] text-slate-400 truncate max-w-xs">{ep.url}</td>
                    <td className="py-3 px-3">
                      <span className="px-2 py-0.5 rounded-md bg-rose-500/10 text-rose-400 border border-rose-500/20 font-mono">
                        {ep.consecutiveFailures} failures
                      </span>
                    </td>
                    <td className="py-3 px-3">
                      {ep.circuitBroken ? (
                        <span className="inline-flex items-center space-x-1 px-2 py-0.5 rounded-md bg-amber-500/10 text-amber-400 border border-amber-500/20 font-semibold text-[10px]">
                          <AlertTriangle className="w-3 h-3" />
                          <span>TRIPPED (DISABLED)</span>
                        </span>
                      ) : (
                        <span className="text-emerald-400 text-[11px]">Degraded</span>
                      )}
                    </td>
                    <td className="py-3 px-3 text-right">
                      {ep.circuitBroken && (
                        <button
                          onClick={() => handleResetCircuit(ep.endpointId)}
                          className="px-2.5 py-1 rounded-lg bg-emerald-500/10 hover:bg-emerald-500/20 text-emerald-400 border border-emerald-500/30 text-[11px] font-medium transition-colors"
                        >
                          Reset Breaker
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
    </div>
  );
}
