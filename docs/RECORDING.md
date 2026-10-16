# HookRelay Demo GIF Recording Guide

This guide details the exact steps to record the high-impact 60-second demonstration GIF showcased in the README.

---

## 1. Specifications & Tooling

- **Tool**: [ScreenToGif](https://www.screentogif.com/) (Windows, Free/Open Source) or [Kap](https://getkap.co/) (macOS)
- **Target Resolution**: `1200x800` px (or browser window zoomed to 100%)
- **Target Frame Rate**: `15–20 fps` (keeps GIF under 4 MB while preserving fluid animations)
- **Target Duration**: `45–60 seconds`
- **Output Destination**: `docs/demo.gif`

---

## 2. Preparation Checklist

1. Deploy the Next.js frontend to Vercel and Spring Boot backend to Render.
2. Ensure demo data is seeded by verifying the `admin@hookrelay.dev` account exists.
3. Open your deployed Vercel URL in a clean browser window sized to `1200x800`.
4. Close bookmark bars and browser extensions for a clean aesthetic.

---

## 3. Recording Storyboard & Timing

| Step | Action | Duration | Visual Focus |
| :--- | :--- | :--- | :--- |
| **1. Login** | Navigate to `/login`, enter `admin@hookrelay.dev` / `password123!`, click Sign In | 5s | Clean login UI with instant JWT token exchange |
| **2. Overview** | Dashboard loads with real-time throughput metrics, latency charts, and stats | 3s | Recharts visual graphs, active endpoints count (3) |
| **3. Transform** | Click **Endpoints** &rarr; edit "Billing Microservice" &rarr; add JSONPath rule `{"id":"$.data.orderId"}` &rarr; click **Test Transform** | 12s | Instant before/after payload transformation preview |
| **4. Save** | Click **Save Changes** &rarr; toast confirmation appears | 3s | Mutation confirmed |
| **5. Playground** | Click **Playground** &rarr; select event `order.created` &rarr; click **Send Test Webhook** | 10s | HTTP 202 Accepted response with generated event ID |
| **6. Deliveries** | Click **Deliveries** &rarr; show newest delivery `SUCCEEDED` with HMAC `v1=` signature | 12s | Verification of timestamp and HMAC digest |
| **7. Replay** | Filter by `DEAD_LETTER` &rarr; open failed delivery &rarr; click **Manual Replay** &rarr; status updates to `SUCCEEDED` | 10s | Idempotent recovery and retry timeline |

---

## 4. Post-Processing & Optimization

1. In ScreenToGif Editor, delete any idle or waiting frames.
2. Apply a subtle border or drop shadow if desired.
3. Use ScreenToGif's **Gifski** or **FFmpeg** encoder with color quantization set to 256 colors.
4. Verify the final file size is under **4 MB**.
5. Save the file to `docs/demo.gif`.
