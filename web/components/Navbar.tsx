"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import {
  Activity,
  Code,
  Key,
  Layers,
  LogOut,
  Radio,
  RefreshCw,
  Send,
  Settings,
  Zap
} from "lucide-react";
import { api, clearToken, getStoredProject, setStoredProject } from "@/lib/api";

export function Navbar() {
  const pathname = usePathname();
  const router = useRouter();
  const [projects, setProjects] = useState<any[]>([]);
  const [selectedProjectId, setSelectedProjectId] = useState<string>("");
  const [user, setUser] = useState<any>(null);

  useEffect(() => {
    const rawUser = localStorage.getItem("hookrelay_user");
    if (rawUser) {
      try {
        setUser(JSON.parse(rawUser));
      } catch (err) {
        console.error(err);
      }
    }

    api.getProjects()
      .then((data) => {
        setProjects(data);
        if (data.length > 0) {
          const stored = getStoredProject();
          const active = data.find((p) => p.id === stored) || data[0];
          setSelectedProjectId(active.id);
          setStoredProject(active.id);
        }
      })
      .catch((err) => {
        console.error("Failed to load projects", err);
      });
  }, []);

  const handleProjectChange = (id: string) => {
    setSelectedProjectId(id);
    setStoredProject(id);
    window.location.reload();
  };

  const handleLogout = () => {
    clearToken();
    router.push("/login");
  };

  if (pathname === "/login" || pathname === "/signup") {
    return null;
  }

  const pId = selectedProjectId || "default";

  const navLinks = [
    { href: `/dashboard`, label: "Dashboard", icon: Activity },
    { href: `/projects/${pId}/endpoints`, label: "Endpoints", icon: Radio },
    { href: `/projects/${pId}/events`, label: "Events", icon: Send },
    { href: `/projects/${pId}/deliveries`, label: "Deliveries", icon: RefreshCw },
    { href: `/projects/${pId}/api-keys`, label: "API Keys", icon: Key },
    { href: `/playground`, label: "Playground & Docs", icon: Code },
    { href: `/settings`, label: "Settings", icon: Settings },
  ];

  return (
    <header className="sticky top-0 z-50 border-b border-border/80 bg-[#090d16]/90 backdrop-blur-md">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex items-center justify-between h-16">
          <div className="flex items-center space-x-6">
            <Link href="/dashboard" className="flex items-center space-x-2.5 group">
              <div className="w-9 h-9 rounded-xl bg-gradient-to-tr from-emerald-600 to-cyan-500 p-0.5 shadow-lg shadow-emerald-500/20 group-hover:scale-105 transition-transform">
                <div className="w-full h-full bg-[#090d16] rounded-[10px] flex items-center justify-center">
                  <Zap className="w-4 h-4 text-emerald-400 group-hover:animate-pulse" />
                </div>
              </div>
              <div>
                <span className="font-bold text-lg tracking-tight bg-gradient-to-r from-white via-slate-200 to-slate-400 bg-clip-text text-transparent">
                  HookRelay
                </span>
                <span className="ml-1.5 px-1.5 py-0.5 text-[10px] font-semibold bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 rounded-md">
                  v1.0
                </span>
              </div>
            </Link>

            {projects.length > 0 && (
              <div className="hidden md:flex items-center space-x-2 bg-slate-900/80 border border-border px-3 py-1.5 rounded-lg text-sm">
                <Layers className="w-3.5 h-3.5 text-slate-400" />
                <select
                  value={selectedProjectId}
                  onChange={(e) => handleProjectChange(e.target.value)}
                  className="bg-transparent text-slate-200 text-xs font-medium focus:outline-none cursor-pointer"
                >
                  {projects.map((p) => (
                    <option key={p.id} value={p.id} className="bg-slate-900 text-slate-200">
                      {p.name}
                    </option>
                  ))}
                </select>
              </div>
            )}
          </div>

          <nav className="hidden lg:flex items-center space-x-1">
            {navLinks.map((item) => {
              const Icon = item.icon;
              const isActive = pathname.startsWith(item.href);
              return (
                <Link
                  key={item.href}
                  href={item.href}
                  className={`flex items-center space-x-2 px-3 py-2 rounded-lg text-xs font-medium transition-all ${
                    isActive
                      ? "bg-slate-800/90 text-emerald-400 border border-slate-700/80 shadow-sm"
                      : "text-slate-400 hover:text-slate-200 hover:bg-slate-800/40"
                  }`}
                >
                  <Icon className={`w-3.5 h-3.5 ${isActive ? "text-emerald-400" : "text-slate-400"}`} />
                  <span>{item.label}</span>
                </Link>
              );
            })}
          </nav>

          <div className="flex items-center space-x-3">
            {user && (
              <div className="hidden sm:flex flex-col text-right">
                <span className="text-xs font-semibold text-slate-200">{user.fullName}</span>
                <span className="text-[10px] text-slate-400">{user.orgName || "Enterprise"}</span>
              </div>
            )}
            <button
              onClick={handleLogout}
              title="Logout"
              className="p-2 rounded-lg text-slate-400 hover:text-rose-400 hover:bg-rose-500/10 border border-transparent hover:border-rose-500/20 transition-colors"
            >
              <LogOut className="w-4 h-4" />
            </button>
          </div>
        </div>
      </div>
    </header>
  );
}
