"use client";

import { useEffect, useState } from "react";
import { Building, CheckCircle2, Shield, Users, Zap } from "lucide-react";
import { api } from "@/lib/api";

export default function SettingsPage() {
  const [user, setUser] = useState<any>(null);
  const [project, setProject] = useState<any>(null);
  const [rateLimit, setRateLimit] = useState(100);
  const [toastMessage, setToastMessage] = useState("");

  useEffect(() => {
    const rawUser = localStorage.getItem("hookrelay_user");
    if (rawUser) {
      try {
        setUser(JSON.parse(rawUser));
      } catch (e) {}
    }

    api.getProjects().then((projects) => {
      if (projects.length > 0) {
        setProject(projects[0]);
        setRateLimit(projects[0].rateLimitPerSecond || 100);
      }
    });
  }, []);

  const handleSaveRateLimit = async () => {
    if (!project) return;
    try {
      await api.updateProject(project.id, { rateLimitPerSecond: rateLimit });
      setToastMessage("Rate limits updated successfully.");
      setTimeout(() => setToastMessage(""), 4000);
    } catch (err: any) {
      alert("Failed to update: " + err.message);
    }
  };

  return (
    <div className="space-y-6 max-w-4xl mx-auto">
      {/* Toast Notification */}
      {toastMessage && (
        <div className="fixed bottom-6 right-6 z-50 flex items-center space-x-2 px-4 py-3 rounded-xl bg-emerald-500/10 border border-emerald-500/30 text-emerald-400 text-sm shadow-2xl backdrop-blur-md">
          <CheckCircle2 className="w-4 h-4 shrink-0" />
          <span>{toastMessage}</span>
        </div>
      )}

      <div>
        <h1 className="text-2xl font-bold tracking-tight text-white">Organization & Security Settings</h1>
        <p className="text-xs text-slate-400 mt-1">Manage tenant configurations, token bucket quotas, and team memberships</p>
      </div>

      {/* Org Profile */}
      <div className="glass-panel p-6 rounded-2xl space-y-4">
        <div className="flex items-center space-x-2 text-emerald-400">
          <Building className="w-5 h-5" />
          <h2 className="text-sm font-bold text-white">Organization Profile</h2>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 text-xs">
          <div className="space-y-1">
            <span className="text-slate-400">Organization Name</span>
            <div className="p-2.5 rounded-xl bg-slate-900 border border-border font-semibold text-slate-200">
              {user?.orgName || "Enterprise Tenant"}
            </div>
          </div>

          <div className="space-y-1">
            <span className="text-slate-400">Tenant Slug</span>
            <div className="p-2.5 rounded-xl bg-slate-900 border border-border font-mono text-slate-300">
              org-{user?.orgId?.substring(0, 8) || "main"}
            </div>
          </div>
        </div>
      </div>

      {/* Tenant Quotas & Rate Limits */}
      <div className="glass-panel p-6 rounded-2xl space-y-4">
        <div className="flex items-center space-x-2 text-cyan-400">
          <Zap className="w-5 h-5" />
          <h2 className="text-sm font-bold text-white">Rate Limits & Token Bucket Quota</h2>
        </div>

        <div className="space-y-3 text-xs">
          <p className="text-slate-400">
            Incoming events via <code className="text-emerald-400 font-mono">POST /api/v1/events</code> are rate-limited via a Redis sliding window. Exceeding this limit returns HTTP 429 with Retry-After.
          </p>

          <div className="flex items-center space-x-3 max-w-xs">
            <input
              type="number"
              min={1}
              max={10000}
              value={rateLimit}
              onChange={(e) => setRateLimit(parseInt(e.target.value, 10))}
              className="w-32 px-3 py-2 rounded-xl bg-slate-900 border border-border text-slate-200 font-mono focus:outline-none focus:border-emerald-500"
            />
            <span className="text-slate-400">events / second</span>
            <button
              onClick={handleSaveRateLimit}
              className="px-3.5 py-2 rounded-xl bg-emerald-500/10 hover:bg-emerald-500/20 text-emerald-400 border border-emerald-500/30 font-medium"
            >
              Save
            </button>
          </div>
        </div>
      </div>

      {/* Team Members */}
      <div className="glass-panel p-6 rounded-2xl space-y-4">
        <div className="flex items-center space-x-2 text-violet-400">
          <Users className="w-5 h-5" />
          <h2 className="text-sm font-bold text-white">Team Members</h2>
        </div>

        <div className="overflow-x-auto text-xs">
          <table className="w-full text-left">
            <thead>
              <tr className="border-b border-border/80 text-slate-400">
                <th className="py-2.5 px-3">Name</th>
                <th className="py-2.5 px-3">Email</th>
                <th className="py-2.5 px-3">Role</th>
                <th className="py-2.5 px-3 text-right">Status</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border/40">
              <tr className="hover:bg-slate-900/40">
                <td className="py-3 px-3 font-semibold text-slate-200">{user?.fullName || "Primary Admin"}</td>
                <td className="py-3 px-3 text-slate-400 font-mono">{user?.email || "admin@hookrelay.dev"}</td>
                <td className="py-3 px-3">
                  <span className="px-2 py-0.5 rounded text-[10px] font-semibold bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                    {user?.role || "ADMIN"}
                  </span>
                </td>
                <td className="py-3 px-3 text-right text-emerald-400 text-[11px]">Active</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
