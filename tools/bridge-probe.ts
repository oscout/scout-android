// Pair with the local bridge as a throwaway phone and print what the Android app reads.
//
//   bun tools/bridge-probe.ts [path ...]        (OPENSCOUT_DIR=<openscout checkout> outside the monorepo)
//
// Reads the live pairing code from ~/.scout/pairing/runtime.json, joins its relay room
// as role=client, runs Noise XX as the initiator, then issues tRPC queries. Each reply
// is printed as `== path` followed by pretty JSON (truncated arrays) so wire shapes
// can be checked against the Kotlin mappers.
import { readFileSync } from "node:fs";
import { homedir } from "node:os";
import { join } from "node:path";

// The bridge's own Noise code: OPENSCOUT_DIR, or the monorepo this app lives in.
const root = process.env.OPENSCOUT_DIR ?? join(import.meta.dir, "../../..");
const { generateKeyPair, NoiseHandshake } = await import(join(root, "packages/runtime/src/pairing-security/noise.ts"));

const DEFAULT_PATHS = [
  "mobile.serviceBudgets",
  "mobile.heartrate",
  "mobile.fleet",
  "mobile.tail",
  "mobile.agents",
  "mobile.activity",
];

const runtime = JSON.parse(readFileSync(join(homedir(), ".scout/pairing/runtime.json"), "utf8"));
const qr = runtime.pairing && JSON.parse(runtime.pairing.qrValue);
if (!qr) throw new Error("No live pairing code in runtime.json; is the pairing controller running?");

const inputs: Record<string, unknown> = {
  "mobile.fleet": { limit: 20 },
  "mobile.tail": { limit: 20 },
  "mobile.agents": { limit: 20 },
  "mobile.activity": { limit: 10 },
};
const paths = process.argv.slice(2).length ? process.argv.slice(2) : DEFAULT_PATHS;

// RELAY overrides the advertised relay (e.g. ws://127.0.0.1:43131 when the LAN address is unreachable).
const relay = process.env.RELAY ?? qr.relay;
const ws = new WebSocket(`${relay}?room=${qr.room}&role=client`);
const hs = new NoiseHandshake("XX", "initiator", generateKeyPair());
let session: ReturnType<NoiseHandshake["finalize"]> | null = null;
const pending = new Map<number, (v: unknown) => void>();

const b64 = (b: Uint8Array) => Buffer.from(b).toString("base64");
const send = (phase: string, bytes: Uint8Array) => ws.send(JSON.stringify({ phase, payload: b64(bytes) }));
const call = (id: number, path: string) =>
  new Promise((resolve) => {
    pending.set(id, resolve);
    const params: Record<string, unknown> = { path };
    if (inputs[path]) params.input = inputs[path];
    send("transport", session!.encrypt(new TextEncoder().encode(JSON.stringify({ id, jsonrpc: "2.0", method: "query", params }))));
  });

function clip(value: unknown, depth = 0): unknown {
  if (Array.isArray(value)) return value.slice(0, depth === 0 ? 3 : 2).map((v) => clip(v, depth + 1));
  if (value && typeof value === "object") return Object.fromEntries(Object.entries(value).map(([k, v]) => [k, clip(v, depth + 1)]));
  if (typeof value === "string" && value.length > 160) return value.slice(0, 160) + "…";
  return value;
}

ws.onopen = () => send("handshake", hs.writeMessage());
ws.onmessage = async (msg) => {
  const frame = JSON.parse(String(msg.data));
  const bytes = new Uint8Array(Buffer.from(frame.payload, "base64"));
  if (frame.phase === "handshake") {
    hs.readMessage(bytes);
    send("handshake", hs.writeMessage());
    session = hs.finalize();
    console.error(`paired; bridge key ${Buffer.from(session.remoteStaticKey).toString("hex").slice(0, 16)}…`);
    let id = 1;
    for (const path of paths) {
      const reply = (await call(id++, path)) as Record<string, unknown>;
      console.log(`== ${path}`);
      console.log(JSON.stringify(clip(reply.result ? (reply.result as { data: unknown }).data : reply.error), null, 1));
    }
    ws.close();
    process.exit(0);
  } else if (session) {
    const text = new TextDecoder().decode(session.decrypt(bytes));
    if (text === "PING") return send("transport", session.encrypt(new TextEncoder().encode("PONG")));
    try {
      const obj = JSON.parse(text);
      if (typeof obj.id === "number" && pending.has(obj.id)) pending.get(obj.id)!(obj);
    } catch {}
  }
};
ws.onclose = (e) => {
  if (!session) console.error(`closed before pairing: ${e.code} ${e.reason}`);
};
setTimeout(() => {
  console.error("timed out");
  process.exit(1);
}, 30_000);
