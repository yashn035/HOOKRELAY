"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { ArrowRight, CheckCircle2, ShieldCheck, Zap, Activity, RefreshCw } from "lucide-react";

export default function Home() {
  const router = useRouter();

  useEffect(() => {
    const token = localStorage.getItem("hookrelay_token");
    if (token) {
      router.push("/dashboard");
    }
  }, [router]);

  return (
    <div className="flex flex-col items-center justify-center min-h-[75vh] text-center space-y-8">
      <div className="inline-flex items-center space-x-2 px-3.5 py-1.5 rounded-full border border-emerald-500/30 bg-emerald-500/10 text-emerald-400 text-xs font-semibold backdrop-blur-md">
        <Zap className="w-3.5 h-3.5" />
        <span>Enterprise-Grade Multi-Tenant Webhook Engine</span>
      </div>

      <div className="max-w-3xl space-y-4">
        <h1 className="text-4xl sm:text-6xl font-extrabold tracking-tight text-white leading-[1.15]">
          Deliver webhooks with <br />
          <span className="bg-gradient-to-r from-emerald-400 via-teal-300 to-cyan-400 bg-clip-text text-transparent">
            rock-solid reliability & observability
          </span>
        </h1>
        <p className="text-slate-400 text-base sm:text-lg max-w-2xl mx-auto leading-relaxed">
          Engineered for mission-critical infrastructure. Guaranteed delivery with Postgres SKIP LOCKED queues,
          per-endpoint circuit breakers, exponential backoff with jitter, and strict SSRF defenses.
        </p>
      </div>

      <div className="flex flex-wrap items-center justify-center gap-4 pt-2">
        <Link
          href="/signup"
          className="px-6 py-3 rounded-xl bg-gradient-to-r from-emerald-500 to-teal-600 hover:from-emerald-400 hover:to-teal-500 text-slate-950 font-semibold text-sm shadow-lg shadow-emerald-500/20 hover:scale-105 transition-all flex items-center space-x-2"
        >
          <span>Get Started Free</span>
          <ArrowRight className="w-4 h-4" />
        </Link>
        <Link
          href="/login"
          className="px-6 py-3 rounded-xl border border-border bg-slate-900/60 hover:bg-slate-800 text-slate-200 font-medium text-sm transition-all"
        >
          Sign In to Dashboard
        </Link>
      </div>

      {/* Feature pillars */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6 pt-12 max-w-5xl w-full text-left">
        <div className="glass-panel p-6 rounded-2xl space-y-3">
          <div className="w-10 h-10 rounded-xl bg-emerald-500/10 border border-emerald-500/20 flex items-center justify-center text-emerald-400">
            <RefreshCw className="w-5 h-5" />
          </div>
          <h3 className="font-semibold text-slate-100 text-base">SKIP LOCKED & Lease Queue</h3>
          <p className="text-xs text-slate-400 leading-relaxed">
            Scales horizontally with non-blocking concurrency locks. Zero double-dispatch and guaranteed at-least-once delivery.
          </p>
        </div>

        <div className="glass-panel p-6 rounded-2xl space-y-3">
          <div className="w-10 h-10 rounded-xl bg-cyan-500/10 border border-cyan-500/20 flex items-center justify-center text-cyan-400">
            <ShieldCheck className="w-5 h-5" />
          </div>
          <h3 className="font-semibold text-slate-100 text-base">Circuit Breaker & SSRF Shield</h3>
          <p className="text-xs text-slate-400 leading-relaxed">
            Auto-disables failing endpoints after N failures. Resolves destination DNS to block private IP ranges and internal metadata APIs.
          </p>
        </div>

        <div className="glass-panel p-6 rounded-2xl space-y-3">
          <div className="w-10 h-10 rounded-xl bg-violet-500/10 border border-violet-500/20 flex items-center justify-center text-violet-400">
            <Activity className="w-5 h-5" />
          </div>
          <h3 className="font-semibold text-slate-100 text-base">Full Latency & Attempt Logs</h3>
          <p className="text-xs text-slate-400 leading-relaxed">
            Inspect raw HTTP request headers, timestamps, response bodies, and latencies for every dispatch with 1-click manual replay.
          </p>
        </div>
      </div>
    </div>
  );
}
