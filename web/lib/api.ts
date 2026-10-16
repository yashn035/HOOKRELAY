const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

export class ApiError extends Error {
  status: number;
  data: any;

  constructor(status: number, message: string, data?: any) {
    super(message);
    this.status = status;
    this.data = data;
  }
}

function getToken(): string | null {
  if (typeof window === "undefined") return null;
  return localStorage.getItem("hookrelay_token");
}

export function setToken(token: string) {
  if (typeof window !== "undefined") {
    localStorage.setItem("hookrelay_token", token);
  }
}

export function clearToken() {
  if (typeof window !== "undefined") {
    localStorage.removeItem("hookrelay_token");
    localStorage.removeItem("hookrelay_user");
    localStorage.removeItem("hookrelay_default_project");
  }
}

export function getStoredProject(): string | null {
  if (typeof window === "undefined") return null;
  return localStorage.getItem("hookrelay_default_project");
}

export function setStoredProject(projectId: string) {
  if (typeof window !== "undefined") {
    localStorage.setItem("hookrelay_default_project", projectId);
  }
}

async function request<T>(endpoint: string, options: RequestInit = {}): Promise<T> {
  const token = getToken();
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    ...(options.headers as Record<string, string>),
  };

  if (token) {
    headers["Authorization"] = `Bearer ${token}`;
  }

  const response = await fetch(`${API_BASE_URL}${endpoint}`, {
    ...options,
    headers,
  });

  if (response.status === 204) {
    return {} as T;
  }

  const data = await response.json().catch(() => null);

  if (!response.ok) {
    if (response.status === 401 && typeof window !== "undefined" && !window.location.pathname.startsWith("/login")) {
      clearToken();
      window.location.href = "/login";
    }
    throw new ApiError(response.status, data?.message || `HTTP ${response.status}`, data);
  }

  return data as T;
}

export const api = {
  // Auth
  signup: (body: any) => request<any>("/api/v1/auth/signup", { method: "POST", body: JSON.stringify(body) }),
  login: (body: any) => request<any>("/api/v1/auth/login", { method: "POST", body: JSON.stringify(body) }),
  me: () => request<any>("/api/v1/auth/me"),

  // Projects
  getProjects: () => request<any[]>("/api/v1/projects"),
  getProject: (id: string) => request<any>(`/api/v1/projects/${id}`),
  createProject: (body: any) => request<any>("/api/v1/projects", { method: "POST", body: JSON.stringify(body) }),
  updateProject: (id: string, body: any) => request<any>(`/api/v1/projects/${id}`, { method: "PUT", body: JSON.stringify(body) }),
  deleteProject: (id: string) => request<void>(`/api/v1/projects/${id}`, { method: "DELETE" }),

  // Dashboard Overview
  getDashboardOverview: (projectId: string) => request<any>(`/api/v1/projects/${projectId}/dashboard`),

  // Endpoints
  getEndpoints: (projectId: string) => request<any[]>(`/api/v1/projects/${projectId}/endpoints`),
  getEndpoint: (projectId: string, id: string) => request<any>(`/api/v1/projects/${projectId}/endpoints/${id}`),
  createEndpoint: (projectId: string, body: any) => request<any>(`/api/v1/projects/${projectId}/endpoints`, { method: "POST", body: JSON.stringify(body) }),
  updateEndpoint: (projectId: string, id: string, body: any) => request<any>(`/api/v1/projects/${projectId}/endpoints/${id}`, { method: "PUT", body: JSON.stringify(body) }),
  rotateSecret: (projectId: string, id: string) => request<any>(`/api/v1/projects/${projectId}/endpoints/${id}/rotate-secret`, { method: "POST" }),
  resetCircuitBreaker: (projectId: string, id: string) => request<any>(`/api/v1/projects/${projectId}/endpoints/${id}/circuit-breaker/reset`, { method: "POST" }),
  deleteEndpoint: (projectId: string, id: string) => request<void>(`/api/v1/projects/${projectId}/endpoints/${id}`, { method: "DELETE" }),

  // API Keys
  getApiKeys: (projectId: string) => request<any[]>(`/api/v1/projects/${projectId}/api-keys`),
  createApiKey: (projectId: string, body: any) => request<any>(`/api/v1/projects/${projectId}/api-keys`, { method: "POST", body: JSON.stringify(body) }),
  revokeApiKey: (projectId: string, id: string) => request<void>(`/api/v1/projects/${projectId}/api-keys/${id}`, { method: "DELETE" }),

  // Events
  getEvents: (projectId: string, type?: string, page = 0, size = 20) =>
    request<any>(`/api/v1/projects/${projectId}/events?page=${page}&size=${size}${type ? `&type=${type}` : ""}`),
  getEvent: (projectId: string, id: string) => request<any>(`/api/v1/projects/${projectId}/events/${id}`),
  publishEvent: (projectId: string, body: any, idempotencyKey?: string) =>
    request<any>(`/api/v1/events?projectId=${projectId}`, {
      method: "POST",
      headers: idempotencyKey ? { "Idempotency-Key": idempotencyKey } : {},
      body: JSON.stringify(body),
    }),

  // Deliveries
  getDeliveries: (projectId: string, status?: string, page = 0, size = 20) =>
    request<any>(`/api/v1/projects/${projectId}/deliveries?page=${page}&size=${size}${status ? `&status=${status}` : ""}`),
  getDeliveryDetail: (projectId: string, deliveryId: string) =>
    request<any>(`/api/v1/projects/${projectId}/deliveries/${deliveryId}`),
  replayDelivery: (projectId: string, deliveryId: string) =>
    request<any>(`/api/v1/projects/${projectId}/deliveries/${deliveryId}/replay`, { method: "POST" }),
  bulkReplay: (projectId: string, body: any) =>
    request<any>(`/api/v1/projects/${projectId}/deliveries/bulk-replay`, { method: "POST", body: JSON.stringify(body) }),
};
