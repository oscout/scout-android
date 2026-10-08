## Scout for Android

The Android counterpart to [Scout for iOS](../ios/README.md): a native mobile
surface for the broker/runtime running on your computer. It pairs with the
computer's pairing bridge exactly like iOS does (same QR payload, same
`scout://pair` link, same Noise handshake, same tRPC procedures) and holds no
broker logic of its own.

- Kotlin + Jetpack Compose + Material 3, single activity, edge-to-edge, light/dark
- Package id `app.openscout.scout` (`.debug` suffix for debug builds), mirroring
  the iOS bundle id
- `compileSdk`/`targetSdk` 37, `minSdk` 28
- Gradle Kotlin DSL, version catalog (`gradle/libs.versions.toml`), wrapper checked in

### What's in it

| Screen | What it shows | Bridge procedures |
| --- | --- | --- |
| Pair | QR scan (CameraX + ML Kit), paste a pairing link, deep links | relay + Noise XX |
| Home | Link signal panel, counts, "Needs you", activity, projects | `mobile.home`, `mobile.activity`, `mobile.inbox` |
| Chats | DMs and channels, unread badges, filters | `mobile.commsConversations` |
| Thread | Messages, composer, optimistic send, mark read, live refresh | `mobile.commsMessages`, `mobile.commsSend`, `mobile.sendMessage`, `mobile.commsMarkRead` |
| Agents | Ledger (Recent / By project), attention, liveness | `mobile.agents` |
| Agent | Runtime/project facts, recent activity, Message, Interrupt | `mobile.activity`, `mobile.agentInterrupt` |
| Tail | Polled harness firehose (3s while visible), kind/project filters, pause | `mobile.tail` |
| Alerts | Approvals, questions, asks, terminal prompts, with actions | `mobile.inbox`, `actionDecide`, `questionAnswer`, `mobile.commsSend` |
| New session | Pick a project + ready harness + optional first message | `mobile.createSession` |
| Settings | Paired computers (switch/rename/forget), connection log, appearance | — |

The UI follows [`../ios/DESIGN.md`](../ios/DESIGN.md) in Material idioms:
warm paper by day and a lit warm cockpit after dark, one emerald accent, a
status triad, uppercase mono eyebrows, and the chamfered signal panel with
registration marks for the link readout. Material You (wallpaper) colors are an
opt-in in Settings.

### Build, install, run

```bash
cd apps/android
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # Noise + pairing payload tests
./gradlew lintDebug
./gradlew installDebug           # onto the running emulator / connected device
adb shell am start -n app.openscout.scout.debug/app.openscout.scout.MainActivity
```

Needs JDK 17+ (21 used here) and an Android SDK with `platforms;android-37.0`
and `build-tools;37.0.0`. Point Gradle at the SDK with `ANDROID_HOME` or a
`local.properties` containing `sdk.dir=...` (gitignored).

### How it connects

```
phone ──ws──► pairing relay (:43131) ◄──ws── bridge (:43130) ──► broker (:43110)
        Noise XX (first pair) / IK (reconnect), tRPC JSON-RPC inside
```

1. **Pair.** The QR (or `scout://pair?payload=…`, or an
   `https://openscout.app/pair#payload=…` link, or the raw JSON) carries the
   relay URLs, room, bridge public key, and expiry. The app opens
   `<relay>?room=<room>&role=client`, runs Noise XX as initiator, and trusts the
   bridge only if the learned static key matches the payload's key.
2. **Reconnect.** On launch/foreground the app asks the relay for the bridge's
   current room (`POST /resolve`), then runs Noise IK against the saved key.
   Routes are tried in order, and the one that wins is promoted. It backs off
   1s → 30s while unreachable, and drops the socket when backgrounded.
3. **Talk.** Every call is a tRPC envelope (`{id, jsonrpc, method, params:{path,input}}`)
   encrypted on the channel. Pushed `mobile:conversation:changed` and
   `operator:notify` frames refresh chats and alerts live.

The phone's X25519 identity is sealed with an Android Keystore AES key; trusted
bridges and relay routes are stored in private preferences.

### Local development against this machine's broker (emulator)

The emulator can't scan a QR off your monitor, so hand it the live pairing
payload over adb instead:

```bash
# from the repo root, once: bun install
bun packages/runtime/src/broker-daemon.ts &              # broker on :43110 (skip if already running)
bun packages/web/server/pairing-runtime-controller.ts &  # relay :43131 + bridge :43130, writes ~/.scout/pairing/runtime.json

cd apps/android && ./gradlew installDebug
scripts/pair-emulator.sh            # opens scout://pair?payload=… in the app
```

`pair-emulator.sh` reads `~/.scout/pairing/runtime.json` (the same payload the QR
encodes) and fires the deep link with `adb shell am start`. Inside the emulator
the app tries the advertised relay first (your LAN or tailnet address, NATed by
the emulator), then adds `ws://10.0.2.2:<relay port>` as a fallback. On the
API 37 image used here, the app's default (virtual Wi-Fi) network could not
reach 10.0.2.2 even though `adb shell` could, so the LAN route is what connects.

For a **USB device**, `scripts/pair-emulator.sh --reverse` runs
`adb reverse tcp:43131 tcp:43131` and puts `ws://127.0.0.1:43131` first. On a
real phone you can also just scan the QR from `scout pair` or the Mac app.

To seed something to look at, `scout broadcast "hello"` and
`scout notify --message "hi"` create a channel and an operator DM.

### Not done yet

- Terminal (SSH shell into the device-scoped tmux workspace), voice, the
  World/Deck/Mesh scenes, and the Spaces web surface
- Push notifications (the Push Relay is APNs-only today), widgets, and the
  notification service extension
- LAN pair-request discovery (Bonjour/beacon "Choose a Mac") and OpenScout
  Network rendezvous. Pairing is QR, link, or deep link only.
- Rich session transcripts (`mobile.sessionSnapshot` turns/blocks, tool calls,
  approvals inline). Threads render broker messages as plain text.
- Attachments (upload/fetch) and markdown rendering in messages
- Multi-host fleet read scope. One active computer at a time; switch in Settings.
- Release signing and a Play Store listing
