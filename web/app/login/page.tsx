"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { AlertCircle, ArrowRight, Lock, Mail, Zap } from "lucide-react";
import { api, setStoredProject, setToken } from "@/lib/api";

export default function LoginPage() {
  const router = useRouter();
  const [email, setEmail] = useState("admin@hookrelay.dev");
  const [password, setPassword] = useState("password123!");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError("");

    try {
      const res = await api.login({ email, password });
      setToken(res.token);
      localStorage.setItem("hookrelay_user", JSON.stringify(res.user));
      if (res.defaultProjectId) {
        setStoredProject(res.defaultProjectId);
      }
      router.push("/dashboard");
    } catch (err: any) {
      setError(err.message || "Failed to authenticate. Please check your credentials.");
    } finally {
      setLoading(false);
    }
  };

  const handleDemoFill = () => {
    setEmail("admin@hookrelay.dev");
    setPassword("password123!");
  };

  return (
    <div className="flex items-center justify-center min-h-[75vh]">
      <div className="w-full max-w-md p-8 rounded-2xl glass-panel space-y-6 shadow-2xl">
        <div className="text-center space-y-2">
          <div className="w-12 h-12 mx-auto rounded-2xl bg-emerald-500/10 border border-emerald-500/20 flex items-center justify-center text-emerald-400">
            <Zap className="w-6 h-6" />
          </div>
          <h2 className="text-2xl font-bold tracking-tight text-white">Welcome back</h2>
          <p className="text-xs text-slate-400">Sign in to manage your webhook infrastructure</p>
        </div>

        {error && (
          <div className="flex items-center space-x-2 p-3 rounded-lg bg-rose-500/10 border border-rose-500/20 text-rose-400 text-xs">
            <AlertCircle className="w-4 h-4 shrink-0" />
            <span>{error}</span>
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-1.5">
            <label className="text-xs font-medium text-slate-300">Email Address</label>
            <div className="relative">
              <Mail className="w-4 h-4 absolute left-3 top-3 text-slate-500" />
              <input
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
                className="w-full pl-9 pr-3 py-2.5 bg-slate-900/80 border border-border rounded-xl text-sm text-slate-100 placeholder-slate-500 focus:outline-none focus:border-emerald-500/80 focus:ring-1 focus:ring-emerald-500/50"
                placeholder="you@company.com"
              />
            </div>
          </div>

          <div className="space-y-1.5">
            <label className="text-xs font-medium text-slate-300">Password</label>
            <div className="relative">
              <Lock className="w-4 h-4 absolute left-3 top-3 text-slate-500" />
              <input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
                className="w-full pl-9 pr-3 py-2.5 bg-slate-900/80 border border-border rounded-xl text-sm text-slate-100 placeholder-slate-500 focus:outline-none focus:border-emerald-500/80 focus:ring-1 focus:ring-emerald-500/50"
                placeholder="••••••••"
              />
            </div>
          </div>

          <button
            type="submit"
            disabled={loading}
            className="w-full py-2.5 rounded-xl bg-gradient-to-r from-emerald-500 to-teal-600 hover:from-emerald-400 hover:to-teal-500 text-slate-950 font-semibold text-sm transition-all shadow-lg shadow-emerald-500/20 disabled:opacity-50 flex items-center justify-center space-x-2"
          >
            <span>{loading ? "Authenticating..." : "Sign In"}</span>
            <ArrowRight className="w-4 h-4" />
          </button>
        </form>

        <div className="pt-2 border-t border-border flex items-center justify-between text-xs text-slate-400">
          <button
            type="button"
            onClick={handleDemoFill}
            className="text-emerald-400 hover:underline font-medium"
          >
            Fill Demo Credentials
          </button>
          <Link href="/signup" className="text-slate-300 hover:text-white">
            Need an account? <span className="text-emerald-400 font-medium">Sign up</span>
          </Link>
        </div>
      </div>
    </div>
  );
}
