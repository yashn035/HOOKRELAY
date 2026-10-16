"use client";

import { useState } from "react";
import { Check, Code, Copy, Key, ShieldCheck, Terminal, Zap } from "lucide-react";

export default function PlaygroundPage() {
  const [activeTab, setActiveTab] = useState<"node" | "python" | "java" | "go">("node");
  const [payload, setPayload] = useState('{\n  "event": "order.completed",\n  "amount": 149.50,\n  "currency": "USD",\n  "customerId": "cust_9981"\n}');
  const [secret, setSecret] = useState("whsec_demo_secret_key_848492021");
  const [timestamp, setTimestamp] = useState(Math.floor(Date.now() / 1000).toString());
  const [signatureOutput, setSignatureOutput] = useState("");
  const [copiedSnippet, setCopiedSnippet] = useState(false);

  const calculateSignatureDemo = async () => {
    try {
      const enc = new TextEncoder();
      const keyData = enc.encode(secret);
      const key = await window.crypto.subtle.importKey(
        "raw",
        keyData,
        { name: "HMAC", hash: "SHA-256" },
        false,
        ["sign"]
      );
      const signatureData = enc.encode(`${timestamp}.${payload}`);
      const hmacBuffer = await window.crypto.subtle.sign("HMAC", key, signatureData);
      const hashArray = Array.from(new Uint8Array(hmacBuffer));
      const hex = hashArray.map((b) => b.toString(16).padStart(2, "0")).join("");
      setSignatureOutput(`t=${timestamp},v1=${hex}`);
    } catch (e: any) {
      setSignatureOutput("Error: " + e.message);
    }
  };

  const snippets = {
    node: `// Node.js (Express / Fastify) HMAC Verification
const crypto = require('crypto');

function verifyHookRelaySignature(rawBody, signatureHeader, secret, toleranceSeconds = 300) {
  if (!signatureHeader || !secret) return false;

  const parts = Object.fromEntries(
    signatureHeader.split(',').map((p) => p.trim().split('='))
  );

  if (!parts.t || !parts.v1) return false;

  // Replay attack protection (tolerance check)
  const timestamp = parseInt(parts.t, 10);
  const now = Math.floor(Date.now() / 1000);
  if (Math.abs(now - timestamp) > toleranceSeconds) {
    return false;
  }

  const expected = crypto
    .createHmac('sha256', secret)
    .update(\`\${parts.t}.\${rawBody}\`)
    .digest('hex');

  // Constant-time comparison to prevent timing attacks
  return crypto.timingSafeEqual(Buffer.from(expected), Buffer.from(parts.v1));
}`,
    python: `# Python (FastAPI / Flask / Django) HMAC Verification
import hmac
import hashlib
import time

def verify_hookrelay_signature(raw_body: str, signature_header: str, secret: str, tolerance_seconds: int = 300) -> bool:
    if not signature_header or not secret:
        return False
        
    parts = dict(p.strip().split('=', 1) for p in signature_header.split(','))
    if 't' not in parts or 'v1' not in parts:
        return False

    # Timestamp freshness check
    timestamp = int(parts['t'])
    if abs(time.time() - timestamp) > tolerance_seconds:
        return False

    signed_payload = f"{parts['t']}.{raw_body}".encode('utf-8')
    expected = hmac.new(secret.encode('utf-8'), signed_payload, hashlib.sha256).hexdigest()

    # Constant-time comparison
    return hmac.compare_digest(expected, parts['v1'])`,
    java: `// Java 21 / Spring Boot HMAC Verification
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

public class WebhookVerifier {

    public static boolean verify(String rawBody, String header, String secret, long toleranceSeconds) {
        if (header == null || secret == null) return false;

        String[] parts = header.split(",");
        String t = null, v1 = null;
        for (String p : parts) {
            String[] kv = p.trim().split("=", 2);
            if (kv[0].equalsIgnoreCase("t")) t = kv[1];
            if (kv[0].equalsIgnoreCase("v1")) v1 = kv[1];
        }

        if (t == null || v1 == null) return false;

        long timestamp = Long.parseLong(t);
        if (Math.abs(Instant.now().getEpochSecond() - timestamp) > toleranceSeconds) {
            return false;
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = HexFormat.of().formatHex(mac.doFinal((t + "." + rawBody).getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(expected.getBytes(), v1.getBytes());
        } catch (Exception e) {
            return false;
        }
    }
}`,
    go: `// Go 1.22+ HMAC Verification
package main

import (
	"crypto/hmac"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"math"
	"strconv"
	"strings"
	"time"
)

func VerifySignature(rawBody, header, secret string, tolerance time.Duration) bool {
	parts := strings.Split(header, ",")
	var tStr, v1 string
	for _, p := range parts {
		kv := strings.SplitN(strings.TrimSpace(p), "=", 2)
		if len(kv) == 2 {
			if kv[0] == "t" { tStr = kv[1] }
			if kv[0] == "v1" { v1 = kv[1] }
		}
	}
	if tStr == "" || v1 == "" { return false }

	ts, err := strconv.ParseInt(tStr, 10, 64)
	if err != nil || math.Abs(float64(time.Now().Unix()-ts)) > tolerance.Seconds() {
		return false
	}

	mac := hmac.New(sha256.New, []byte(secret))
	mac.Write([]byte(fmt.Sprintf("%s.%s", tStr, rawBody)))
	expected := hex.EncodeToString(mac.Sum(nil))

	return hmac.Equal([]byte(expected), []byte(v1))
}`
  };

  const copySnippet = () => {
    navigator.clipboard.writeText(snippets[activeTab]);
    setCopiedSnippet(true);
    setTimeout(() => setCopiedSnippet(false), 2000);
  };

  return (
    <div className="space-y-8 max-w-5xl mx-auto">
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-white">Signature Playground & SDK Docs</h1>
        <p className="text-xs text-slate-400 mt-1">
          HookRelay signs every webhook with HMAC-SHA256 to ensure authenticity and prevent replay attacks
        </p>
      </div>

      {/* Interactive Generator */}
      <div className="glass-panel p-6 rounded-2xl space-y-4">
        <div className="flex items-center space-x-2 text-emerald-400">
          <Zap className="w-5 h-5" />
          <h2 className="text-sm font-bold text-white">Live Signature Generator</h2>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-xs">
          <div className="space-y-1.5">
            <label className="font-medium text-slate-300">Raw JSON Payload</label>
            <textarea
              rows={7}
              value={payload}
              onChange={(e) => setPayload(e.target.value)}
              className="w-full p-2.5 rounded-xl bg-slate-900 border border-border text-emerald-300 font-mono focus:outline-none focus:border-emerald-500"
            />
          </div>

          <div className="space-y-3">
            <div className="space-y-1.5">
              <label className="font-medium text-slate-300">Endpoint Secret (whsec_...)</label>
              <input
                type="text"
                value={secret}
                onChange={(e) => setSecret(e.target.value)}
                className="w-full px-3 py-2 rounded-xl bg-slate-900 border border-border text-slate-200 font-mono focus:outline-none focus:border-emerald-500"
              />
            </div>

            <div className="space-y-1.5">
              <label className="font-medium text-slate-300">Timestamp (Epoch Seconds)</label>
              <input
                type="text"
                value={timestamp}
                onChange={(e) => setTimestamp(e.target.value)}
                className="w-full px-3 py-2 rounded-xl bg-slate-900 border border-border text-slate-200 font-mono focus:outline-none focus:border-emerald-500"
              />
            </div>

            <button
              onClick={calculateSignatureDemo}
              className="w-full py-2.5 rounded-xl bg-emerald-500/10 hover:bg-emerald-500/20 text-emerald-400 border border-emerald-500/30 font-semibold transition-colors"
            >
              Compute X-HookRelay-Signature
            </button>
          </div>
        </div>

        {signatureOutput && (
          <div className="mt-3 p-3 rounded-xl bg-slate-950 border border-border flex items-center justify-between text-xs font-mono">
            <div>
              <span className="text-slate-500">Header: </span>
              <span className="text-cyan-400">{signatureOutput}</span>
            </div>
          </div>
        )}
      </div>

      {/* Language Snippets */}
      <div className="glass-panel p-6 rounded-2xl space-y-4">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div className="flex items-center space-x-2 text-cyan-400">
            <Terminal className="w-5 h-5" />
            <h2 className="text-sm font-bold text-white">Receiver Verification Code Snippets</h2>
          </div>

          <div className="flex items-center space-x-1.5 bg-slate-900/80 p-1 rounded-xl border border-border text-xs">
            {(["node", "python", "java", "go"] as const).map((lang) => (
              <button
                key={lang}
                onClick={() => setActiveTab(lang)}
                className={`px-3 py-1 rounded-lg font-medium capitalize transition-colors ${
                  activeTab === lang ? "bg-slate-800 text-emerald-400" : "text-slate-400 hover:text-white"
                }`}
              >
                {lang}
              </button>
            ))}
          </div>
        </div>

        <div className="relative">
          <button
            onClick={copySnippet}
            className="absolute top-3 right-3 p-2 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 text-xs flex items-center space-x-1 border border-border"
          >
            {copiedSnippet ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
            <span>{copiedSnippet ? "Copied" : "Copy"}</span>
          </button>
          <pre className="p-4 rounded-xl bg-slate-950 border border-border text-xs font-mono text-slate-300 overflow-x-auto leading-relaxed">
            {snippets[activeTab]}
          </pre>
        </div>
      </div>
    </div>
  );
}
