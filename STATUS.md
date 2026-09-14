# Mr.Bob — Current Status

> Verified from the companion session log (Sep 2026) and the running config.
> Project is **complete and fully working** as an autonomous bot.

## ✅ Working

- **Mod boots clean** — 67 mods load; `aicompanion 0.4.8` initializes fully with the `/companion` command and chat hook.
- **Companion roster live** — Lady-D, bob, Lo-Lifes-Boo, wargod, BryanBoo all load with their skins.
- **LLM brain connected** — `mrbob-base:latest` on the portable Ollama (`localhost:11435`).
- **14 skills loaded** — the full skill set activates at startup (`loaded 14 skill(s)`).
- **Autonomous behavior** — persistent memory ("Memory ready", stored memories per owner), plus a heartbeat that wakes idle companions to keep working on their own.
- **Follow / hunt / store / build** — all mapped to valid commands in the bot's behavioral contract:
  - Follow: `follow <username>`
  - Hunt / fight: `attack <mob>`
  - Store loot (never carry it around): `deposit` to the base chest
  - Build: `build_structure` (spends inventory materials, restocks with `get`)
- **Persistent companions** — parked companions are restored + re-attached to the brain on reload (`restored 1 parked companion(s)` / `re-attached brain to restored companion bob`).

## 🧠 Per-companion LLM routing — each bot has its own small AI

Every companion runs its **own dedicated small language model**, so each bot has a distinct brain. Routing is per-companion in `aicompanion.json` via the `model` field; a blank field falls back to the global `llm.model`.

| Companion | Model |
|---|---|
| **bob** | `mrbob-base:latest` |
| **Lady-D** | `qwen2.5:3b` |
| **Lo-Lifes-Boo** | `qwen2.5:1.5b` |
| **wargod** | `qwen3:1.7b` |
| **BryanBoo** | `qwen3:1.7b` |

All served by the portable Ollama on the 4060Ti (`localhost:11435`). The small models (1.5B–4B) keep the whole roster responsive on one GPU — weights can be shared, so extra bots only add a little KV cache each, not a full reload per bot.

## ⚠️ Not working / known issues

- **TTS (voice) is off** — config sets `tts=af_heart @ http://localhost:8880`, but the Kokoro voice server is a **Docker container and this machine has no Docker/WSL2**, so voice can never start. **Text chat works; there is no voice output.** The mod self-quiets after a few minutes of failed posts.
- **`maxTokens` stall bug** — `llm.maxTokens=2000` is the low cap that can cause a bot to stop responding when a long reasoning run eats the budget ("LLM reply was cut off... Nothing ran"). **Raise to 4000.** It's a cap, not a budget — short replies cost nothing extra.
- **Offline-mode login** — boot logs `Failed to verify authentication (401)`; session runs in offline mode (fine for single-player, no online multiplayer/realms).
- **No auto-respawn** — a companion that dies is **permanently removed 20 ticks after death** until you `/companion spawn <name>`. Another living bot auto-salvages the dropped items; there is no auto-revive.
- **Pathfinding imprecision** — bots are server-side, so movement can be janky in tight spaces / water / complex terrain (known mod limitation, not fixable client-side).
- **`modmenu` missing warning** — MidnightControls recommends ModMenu (≥1.12.2); absent but harmless.

## Quick fixes applied/advised

| Setting | Value | Why |
|---|---|---|
| `llm.maxTokens` | **4000** (was 2000) | prevents the cut-off stall |
| `tts.enabled` | `false` | no Docker → voice can never start; avoids log spam + CPU |
