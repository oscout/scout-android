#!/usr/bin/env bash
# Hand the host's live pairing payload to Scout for Android over adb.
#
# The emulator can't scan a QR off your monitor, so this reads the pairing
# runtime's snapshot (~/.scout/pairing/runtime.json — the same payload the QR
# encodes) and opens it in the app as a scout://pair deep link. The app then
# runs the normal Noise XX pairing against the host's relay; inside the
# emulator it tries the 10.0.2.2 host alias first.
#
# Usage:
#   scripts/pair-emulator.sh            # emulator / first adb device
#   scripts/pair-emulator.sh --reverse  # USB device: adb reverse the relay port
#   ANDROID_SERIAL=emulator-5554 scripts/pair-emulator.sh
#
# Env: SCOUT_ANDROID_PACKAGE (default app.openscout.scout.debug),
#      SCOUT_PAIRING_SNAPSHOT (default ~/.scout/pairing/runtime.json).
set -euo pipefail

PACKAGE="${SCOUT_ANDROID_PACKAGE:-app.openscout.scout.debug}"
SNAPSHOT="${SCOUT_PAIRING_SNAPSHOT:-$HOME/.scout/pairing/runtime.json}"
REVERSE=0
for arg in "$@"; do
  case "$arg" in
    --reverse) REVERSE=1 ;;
    -h|--help) sed -n '2,18p' "$0"; exit 0 ;;
    *) echo "unknown argument: $arg" >&2; exit 2 ;;
  esac
done

ADB="adb"
if ! command -v adb >/dev/null 2>&1; then
  for sdk in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "$HOME/Android/Sdk" "$HOME/Library/Android/sdk"; do
    if [ -n "$sdk" ] && [ -x "$sdk/platform-tools/adb" ]; then ADB="$sdk/platform-tools/adb"; break; fi
  done
fi
command -v "$ADB" >/dev/null 2>&1 || [ -x "$ADB" ] || { echo "adb not found; set ANDROID_HOME" >&2; exit 1; }
command -v bun >/dev/null 2>&1 || { echo "bun is required (repo toolchain)" >&2; exit 1; }

if [ ! -f "$SNAPSHOT" ]; then
  cat >&2 <<EOF
No pairing snapshot at $SNAPSHOT.
Start the pairing runtime first, e.g. from an OpenScout checkout:
  bun packages/runtime/bin/openscout-runtime.mjs broker &      # broker (if not already running)
  bun packages/web/server/pairing-runtime-controller.ts &      # pairing relay + bridge
EOF
  exit 1
fi

LINK="$(SNAPSHOT="$SNAPSHOT" REVERSE="$REVERSE" bun -e '
const fs = require("node:fs");
const snap = JSON.parse(fs.readFileSync(process.env.SNAPSHOT, "utf8"));
const p = snap.pairing;
if (!p) { console.error(`pairing runtime status is "${snap.status}" with no active QR; is the controller running?`); process.exit(1); }
if (p.expiresAt && p.expiresAt < Date.now()) { console.error("the pairing QR in the snapshot has expired; the controller refreshes it — retry in a few seconds"); process.exit(1); }
let relay = p.relay;
let fallbacks = p.fallbackRelays ?? [];
if (process.env.REVERSE === "1") {
  const port = new URL(relay).port || "43131";
  fallbacks = [relay, ...fallbacks];
  relay = `ws://127.0.0.1:${port}`;
}
const payload = { v: 1, relay, fallbackRelays: fallbacks, room: p.room, publicKey: p.publicKey, expiresAt: p.expiresAt };
if (p.webPort) payload.webPort = p.webPort;
process.stdout.write("scout://pair?payload=" + encodeURIComponent(JSON.stringify(payload)));
')"

if [ "$REVERSE" = "1" ]; then
  PORT="$(SNAPSHOT="$SNAPSHOT" bun -e 'const s=JSON.parse(require("node:fs").readFileSync(process.env.SNAPSHOT,"utf8"));process.stdout.write(new URL(s.pairing.relay).port||"43131")')"
  "$ADB" reverse "tcp:$PORT" "tcp:$PORT"
  echo "adb reverse tcp:$PORT -> host relay"
fi

echo "Opening pairing link in $PACKAGE…"
"$ADB" shell am start -a android.intent.action.VIEW -d "'$LINK'" "$PACKAGE" >/dev/null
echo "Done. Watch the app: it should say \"Establishing trust…\" and land on Home."
