// Interop harness: drives the bridge's own Noise responder (packages/runtime) over
// stdin/stdout so the Kotlin initiator can be checked against the real thing.
//
//   -> pub <responder static hex>        (printed on start)
//   <- pattern XX|IK
//   <- msg <hex>   -> msg <hex> | done <initiator static hex>
//   <- ct <hex>    -> ct <hex>           (decrypts, upper-cases, re-encrypts)
import { createInterface } from "node:readline";
import { generateKeyPair, NoiseHandshake, type NoiseSession } from "../../../packages/runtime/src/pairing-security/noise.ts";

const hex = (b: Uint8Array) => Buffer.from(b).toString("hex");
const bytes = (h: string) => new Uint8Array(Buffer.from(h, "hex"));

const s = generateKeyPair();
console.log(`pub ${hex(s.publicKey)}`);

let hs: NoiseHandshake | null = null;
let session: NoiseSession | null = null;

for await (const line of createInterface({ input: process.stdin })) {
  const [cmd, arg = ""] = line.trim().split(" ");
  if (cmd === "pattern") {
    hs = new NoiseHandshake(arg as "XX" | "IK", "responder", s);
  } else if (cmd === "msg" && hs) {
    hs.readMessage(bytes(arg));
    if (!hs.isComplete()) console.log(`msg ${hex(hs.writeMessage())}`);
    if (hs.isComplete()) {
      session = hs.finalize();
      console.log(`done ${hex(session.remoteStaticKey)}`);
    }
  } else if (cmd === "ct" && session) {
    const text = new TextDecoder().decode(session.decrypt(bytes(arg)));
    console.log(`ct ${hex(session.encrypt(new TextEncoder().encode(text.toUpperCase())))}`);
  }
}
