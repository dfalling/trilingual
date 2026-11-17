# Alternate AI model providers for Trilingual

*Research only — no code changed. Facts checked against provider docs/pricing pages and
third-party trackers in November 2026; prices and model lineups in this space move fast,
so re-verify anything load-bearing before implementation.*

## 1. Where Anthropic is used today (and how coupled we are)

Stack: native Android, Kotlin 2.4.21 + Compose, manual DI in `TrilingualApp`. The single AI
dependency is `com.anthropic:anthropic-java` 3.3.0 (`gradle/libs.versions.toml:12,32`,
`app/build.gradle.kts:105`). All API traffic lives in **two files**:

- `app/src/main/java/com/dfalling/trilingual/translate/Translator.kt` (134 lines) — the only
  file that calls the API. One **non-streaming** `messages().create()` per translation:
  model + `maxTokens(16000)` + static system prompt + `output_config` structured output
  (JSON schema whose `language` enum is built per request from the user's target languages)
  + `effort: LOW`; a beta header `anthropic-beta: server-side-fallback-2026-07-01` with
  `fallbacks: "default"` on non-Haiku models (server-side retry when a safety classifier
  declines); `StopReason.REFUSAL` / `MAX_TOKENS` mapped to user-facing errors; Anthropic
  exception types mapped to error strings. A second call, `models().list(limit=1)`, verifies
  a new API key against the free model-list endpoint before saving.
- `app/src/main/java/com/dfalling/trilingual/translate/TranslationPrompt.kt` (94 lines) —
  provider-neutral system prompt, user message, JSON schema, and tolerant parser
  (`parse()` already survives duplicate/whitespace issues and throws a typed error when a
  target language is missing).

Anthropic-specific coupling outside those two files is shallow:

| Location | Coupling |
|---|---|
| `data/SettingsRepository.kt:24-37` | `ClaudeModel` enum (`claude-opus-5-5` / `claude-sonnet-5-5` / `claude-haiku-5-5`), default Opus, persisted as a raw string in DataStore |
| `ui/SettingsScreen.kt:54,104-129,153-173` | "Get a key" opens `platform.claude.com/settings/keys`; placeholder `sk-ant-…`; radio buttons iterate `ClaudeModel.entries` |
| `ui/TranslateScreen.kt` | Gates composer on `hasApiKey`; "Add API key" prompt mentions Anthropic |
| `res/values*/strings.xml` (4 locales) | ~7 Anthropic/Claude-named strings ("Anthropic rejected this key", "Claude declined to translate…") |
| `data/ApiKeyCipher.kt` | Keystore encryption — provider-neutral, reusable as-is |
| `app/proguard-rules.pro` | `-keep class com.anthropic.**` (+ Jackson rules) |
| `TrilingualApp.kt:11` | `Translator` wired concretely, no interface |

**Features actually used:** structured outputs (JSON schema), an effort knob, stop-reason
mapping, key verification, error mapping. **Not used:** streaming, tool use, prompt caching,
extended-thinking blocks, vision, multi-turn context. That's the best-case migration
profile — no tool-calling or streaming differences to worry about; the whole integration is
"one HTTPS request in, one JSON object out."

Baseline for comparison — current Anthropic pricing (per 1M tokens, Nov 2026):
Opus 5.5 $4/$20 (the app's **default** model), Sonnet 5.5 $2/$10, Haiku 5.5 $0.10/$0.50
(prompts ≤100k); 1M context, 128K max output.

## 2. Candidate providers (direct signup, user's own key)

Constraint recap: a single end user must be able to sign up and key the app directly — no
proxy/aggregator requirement (so OpenRouter-style services are out of scope, noted only for
completeness). OpenAI-compatible APIs are a plus because one client can then serve many
providers, including local ones.

### Google via Gemini API — the strongest overall fit

- **Signup:** Google account → API key from AI Studio, no credit card for the free tier.
- **Models:** Gemma 4 (31B and 26B-A4B) — Google's open-weight line, served first-party;
  Gemini 3.x Flash-Lite ("optimized for high-volume agentic tasks, **translation**, and
  simple data processing"), Gemini 3.x Flash.
- **Free tier (measured Nov 2026):** Gemma 4: 30 RPM, ~14.4K requests/day, 16K TPM, 256K
  context — generous for a phone app and free-tier-only (there is no paid Gemma tier).
  Flash-Lite: ~15 RPM / 250 requests/day. Flash models: free tier exists but limits vary
  per account (one probe saw 5–20 RPD on 3.6-flash).
- **Paid pricing:** 3.1-flash-lite $0.25/$1.50, 3.5-flash-lite $0.30/$2.50, 3.8-flash
  $0.75/$3.75 (intro through Dec 31 2026, doubles Jan 1 2027). 1M context.
- **Integration:** official OpenAI-compatible endpoint (`generativelanguage.googleapis.com/
  v1beta/openai/`) with `response_format` json_schema support ("fully recursive" per Google's
  docs), so it rides the same client as every other option below.
- **Quality evidence:** Gemini 3.1 Pro tops Intento's 2026 translation benchmark of 57
  models; Gemma 4 is the best open-weight model in 37 of 50 languages Intento measured and
  outranked Google Translate in WMT26 human evaluation (avg rank 22.0 vs 26.9).
- **Tradeoffs/risks:** free-tier content "used to improve our products" (privacy trade for
  users); Gemma 4 can't be paid-tier; OpenAI-compat layer has quirks (e.g. schema
  `propertyOrdering` handling); free-tier limits are per project and unpublished officially.

### DeepInfra — cheapest strong open-weight quality, one client for everything

- **Signup:** GitHub/Google OAuth → key from dashboard; per-token billing, no minimums,
  no upfront fees. No meaningful free tier (small trial credit at most).
- **Models (Nov 2026):** DeepSeek-V4.1-Flash-1017 $0.098/$0.294 (1M ctx, fp8, promo price),
  DeepSeek-V4-Pro-0813 $1.30/$2.60, Qwen3.5-397B-A17B $0.45/$3.00, Qwen3.8-Max $1.65/$4.95,
  Qwen3.8-27B $0.15/$1.88, plus ~90 more open models.
- **Integration:** OpenAI-compatible at `api.deepinfra.com/v1/openai`; `response_format`
  json_schema with `strict: true` supported on many models (plus json_object and even regex
  formats).
- **Quality evidence:** Qwen3.5-397B is the top open-weight model in BenchLM's translation
  category (93.8); DeepSeek-V4-Pro was the strongest data-center-scale open general model in
  WMT26 human eval (avg rank 17.5, vs Gemma 4 31B at 22.0 and gpt-oss-120b at 32.1).
- **Tradeoffs/risks:** third-party hosting means fp8/quantized serving (BenchLM scores
  DeepSeek-V4.1-Flash ~25% below the maker's own evals), catalog churn, and a less polished
  consumer signup than Google. Reliability is good but it's one more company between the
  user and the weights.

### Groq — fast and free, but the self-serve catalog has narrowed into an awkward spot

- **Signup:** free tier, no card; Developer plan pay-as-you-go.
- **Free tier:** 30 RPM / 1K requests/day / 8K TPM / 200K tokens/day per model.
- **Models (Nov 2026):** self-serve is now mostly **GPT-OSS 120B/20B** ($0.15/$0.60 and
  $0.075/$0.30 — OpenAI's open-weight models) plus Qwen3.8-27B in preview ($0.80/$4.00).
  Llama 3.x/4, Kimi K2, and Qwen3-32B were deprecated off free/Developer plans during 2026;
  Llama is now Enterprise/"contact sales" only.
- **Caveat:** GPT-OSS weights come from OpenAI. If "OpenAI is out" means the provider/API,
  hosting their open weights on Groq is arguably fine — but it deserves an explicit product
  call, which is why Groq isn't a top pick here. Qwen3.8-27B alone is pricey and preview-only.
- **Strengths if used:** ~500–1000 tok/s latency, 131K context, structured outputs
  (json_schema) supported.

### DeepSeek's own API — cheapest frontier-tier quality, with strings attached

- `deepseek-flash` (V4.1-Flash-1017): $0.15/$0.60 off-peak, $0.30/$1.20 peak, cache hits
  $0.003–0.006; `deepseek-v4-pro`: $0.66–1.32 / $1.98–3.96. 1M context, 384K max output.
  OpenAI-compatible; JSON output and tool calls supported; thinking is **on by default**
  (returned in a separate `reasoning_content` field, disable-able per request).
- Fun fact for a lazy migration: DeepSeek also serves an **Anthropic-format** endpoint
  (`api.deepseek.com/anthropic`), so the existing SDK could be pointed at it — but
  `output_config`/effort support on that surface is unverified, so treat as a hack, not a plan.
- **Tradeoffs:** prepaid top-up flow (extra signup friction), no fixed free tier, and the
  company is China-based — a real privacy/data-residency consideration to surface to users
  of a personal-communications app.

### Mistral (La Plateforme) — good free tier, EU residency, open weights

- OpenAI-compatible API with JSON mode + structured outputs; Mistral Large 4 $0.70/$2.10
  (1M ctx), Small 4 $0.15/$0.60; many models Apache 2.0; EU data residency.
- Free "Experiment" tier (~1B tokens/month, ~1 RPS, positioned as evaluation-only) plus
  small monthly API credits on free accounts (~$10/mo observed). Production wants
  pay-as-you-go. A solid EU-flavored fallback; translation quality mid-pack among open
  models (Intento: Mistral Large 3 fewest-errors in ~4% of languages).

### Together / Fireworks / Cerebras — fine tech, user-hostile first steps

- **Together:** no free trial, **$15 minimum first purchase** — bad for "end user keys their
  own app." Great catalog (GLM-5.3, Kimi K3, DeepSeek V4.1 Flash $0.30/$1.20, Qwen3.8 Flash
  $0.15/$0.47, one free model).
- **Fireworks:** $1 starter credit, then needs a payment method (10 RPM without one);
  json_schema structured outputs supported (note: enabling `response_format` disables
  reasoning output). Same list prices as Together on shared models.
- **Cerebras:** $5 trial credit **requires a verified payment method first** (API stays
  inactive without it); only gpt-oss-120b and qwen-3.8-27b on shared inference; extremely
  fast; structured outputs supported.

### Self-hosted / bring-your-own endpoint (Ollama, LM Studio, vLLM, llama.cpp)

All expose OpenAI-compatible servers (Ollama `localhost:11434/v1`, LM Studio
`localhost:1234/v1`) and all enforce JSON-schema structured output (Ollama structured
outputs, vLLM guided decoding, LM Studio json_schema). Zero cost, maximal privacy, no
account — but on Android the realistic shape is "user runs Ollama on their own PC/server and
points the app at it over the network," which needs a custom base-URL field in Settings.
Model quality is hardware-bound; Google's **TranslateGemma** (4B/12B/27B, Gemma-3-based,
on Ollama) is a compelling dedicated-translation pick — it ranked #1 on a 1,002-segment
subtitle benchmark across ES/JA/KO/TH/ZH, beating Claude Sonnet and GPT-5.4 — **but** it has
a documented Simplified/Traditional Chinese bug (outputs Simplified for `zh-Hant` ~76% of
the time), and this app exposes both zh variants. Watch **Lumen** (14.8B, Apache 2.0) too:
WMT26 human eval rank 6.8, effectively next to Gemini 3.1 Pro (4.8) — the strongest
open-weight translation result of the year, if hosting becomes available.

### Translation-quality reality check (applies to every open-weight option)

Intento's 2026 benchmark: the *best* open-weight LLM still has ~2.9× more weighted errors
than the best closed LLM, median across 50 languages — though the gap is smallest exactly
where this app lives (high-resource European + major Asian languages; Gemma 4's smallest gap
is Indonesian at 1.42×). WMT26 also warns general-purpose LLMs hallucinate more than
dedicated MT on adversarial input. Practically: an open-weight or Flash-class default will be
a small but real quality step down from Opus/Sonnet for tricky text, and a wash for routine
messages. A/B-ing 2–3 candidates against real user text before committing is cheap and worth
it — the app's `TranslationPromptTest` harness is a good seed for a quick offline eval.

## 3. Recommendation

**Architecture first:** implement one OpenAI-compatible client (see §4) and make providers
presets over `(base URL, key, model list)`. That single decision makes Gemini, DeepInfra,
Groq, DeepSeek, Mistral, Together, Fireworks, and Ollama/LM Studio/vLLM all the same code
path, and turns "provider support" into a data table.

**Shortlist (in order):**

1. **Google Gemini API** — default recommendation.
   - Free tier: **Gemma 4 31B** (30 RPM / 14.4K req/day, no card) as the out-of-the-box
     experience — a genuinely free, open-weight, multilingual-strong model.
   - Paid/quality tier: **Gemini 3.1 or 3.5 Flash-Lite** ($0.25–0.30 in / $1.50–2.50 out,
     explicitly tuned for translation) for users who want the best speed/quality and accept
     the paid tier.
   - Tradeoffs: free-tier data used for training; Gemma 4 has no paid tier; compat-layer
     quirks to test (schema handling, error shapes).
2. **DeepInfra** — the open-weights workhorse.
   - **DeepSeek-V4.1-Flash-1017** ($0.098/$0.294) as the cheap default; **Qwen3.5-397B-A17B**
     or **DeepSeek-V4-Pro** for the quality tier. Strict json_schema on many models.
   - Tradeoffs: no real free tier; third-party host (quantized serving, catalog churn);
     signup less consumer-polished than Google.
3. **Bring-your-own endpoint** (Ollama / LM Studio / vLLM) as a third tier, not a default —
   zero cost and perfect privacy for the users who can run it; needs the base-URL UI anyway
   (it doubles as the escape hatch if any hosted provider enrages users later).
   Suggested models: TranslateGemma (with the zh-Hant caveat) or Gemma 4/Qwen3.5-27B.

**Flagged, not shortlisted:** Groq (great free tier and latency, but its self-serve catalog
is now mostly OpenAI's GPT-OSS weights — a product call, not a technical one; Qwen3.8-27B
there is preview-priced at $0.80/$4.00); DeepSeek direct (cheapest serious quality + an
Anthropic-format endpoint, but prepaid top-up friction and China data-residency questions);
Mistral (good EU/free-tier story, mid-pack translation quality); Together ($15 minimum) /
Fireworks ($1 credit then card) / Cerebras (card required) — all paywalled on-ramps that
clash with "end user keys the app themselves."

## 4. Migration effort for this codebase

**Client layer (1–2 days).** Two viable paths:
- `com.openai:openai-java` (currently v4.45.x) — official, supports `baseUrl` override per
  client, same OkHttp/Stainless lineage as `anthropic-java`, so Android behavior should
  match what we already ship (verify desugaring/R8 at implementation time).
- Hand-rolled OkHttp + kotlinx.serialization client (~200–300 lines) — OkHttp is already a
  transitive dependency; this removes SDK heft, avoids another ProGuard chapter, and treats
  every compat endpoint uniformly. **Slight preference for hand-rolled** given how small the
  used surface is (one POST + one GET).

`Translator.kt` becomes: `POST {base}/chat/completions` with system + user messages,
`response_format: {type: "json_schema", ...}` (strict where supported), `max_tokens 16000`,
and `finish_reason` mapping (`length` → too-long, `content_filter` → refused). The dynamic
`language` enum schema carries over verbatim. The Anthropic server-side fallback beta has no
equivalent — either drop it (accept occasional refusals) or add a one-shot client-side retry
on a second model. The effort knob maps to `reasoning_effort: "low"`/thinking-off per model;
simplest is to default to non-thinking models and skip the knob. `verifyKey` becomes
`GET {base}/models` (supported by every compat provider incl. Gemini's compat layer; skip
for local endpoints or treat 200 as valid).

**Settings/data model (2–3 days).** DataStore gains `provider` and `base_url` keys
(additive — missing keys mean defaults, no migration code). Settings UI: provider picker
(presets + "Custom endpoint"), per-provider key field with per-provider "Get a key" link,
optional base-URL field, and a provider-scoped model list plus a free-text model ID escape
hatch (model churn is real; users will want it). All ~7 Anthropic-named strings get
generalized across 4 locales. `ApiKeyCipher`, key-verify UX flow, and the `hasApiKey`
gating all carry over unchanged.

**Back-compat (0.5 day).** Existing installs hold `model = "claude-opus-5-5"` and no
provider key. Recommendation: keep Anthropic as a legacy preset (existing users' keys and
selections keep working; new installs default to the new provider) rather than a destructive
migration. This also keeps the door open if the "move off Anthropic" goal is about defaults
rather than a ban.

**Cleanup + tests (0.5–1 day).** Drop `com.anthropic` ProGuard rules and the dependency;
add MockWebServer tests for the new client (there are currently zero network-layer tests —
worth fixing while touching this code); keep `TranslationPromptTest` as-is.

**Total: ~4–6 focused days** for one new provider end-to-end; **~1.5–2 weeks** for the
multi-provider settings UI, QA across 3–4 providers (schema strictness, error shapes, rate
limits), localization, and the back-compat path.

## 5. Follow-up: DeepInfra model selection (Nov 17, 2026)

If DeepInfra is the chosen provider, letting the user pick the model is straightforward:
`GET /v1/openai/models` returns the live catalog (a `models:read` scope exists for scoped
tokens), but ~90 entries is too many for a phone UI. Recommended shape: a curated shortlist
in the picker plus a free-text custom-model-ID escape hatch (covers catalog churn between
app releases). Candidate shortlist: `deepseek-ai/DeepSeek-V4.1-Flash-1017`,
`zai-org/GLM-5.3-Flash`, `Qwen/Qwen3.5-397B-A17B`, `deepseek-ai/DeepSeek-V4-Pro-0813`.

Default pick: **DeepSeek-V4.1-Flash-1017** — cheapest ($0.098/$0.294, though that's a 51%
promo; standard V4.1-Flash is $0.20/$0.60) and ~2× faster than GLM-5.3-Flash ($0.15/$0.50).
Quality is close to a wash: DeepSeek's card claims benchmark wins over GLM 5.3, while
LMArena per-language categories slightly favor GLM-5.3-Flash (e.g. Chinese 1530 vs 1502,
French 1488 vs 1467). No direct translation benchmark exists for either. Ship both in the
picker and let usage decide; if the DeepSeek promo expires, GLM-5.3-Flash wins on price.
Verify per model before shipping: strict json_schema support (DeepInfra supports it on
"many" models — check each model page), thinking-off parameters (both think by default),
and zh-Hant correctness for both Chinese variants.

## 6. Risks

- **Structured-output differences (highest risk).** Strict `json_schema` is not universal:
  DeepSeek's own API documents json_object but not strict schemas; some compat layers
  degrade silently. Mitigation: a per-provider capability flag; fall back to
  prompt-embedded schema + `json_object`; the existing tolerant `parse()` plus one retry on
  invalid JSON covers most residue.
- **Thinking/reasoning modes.** DeepSeek V4 thinks by default; Qwen needs
  `enable_thinking=false` (via `chat_template_kwargs` on some hosts); GPT-OSS uses
  `reasoning_effort`. If reasoning leaks into `content`, JSON parsing breaks. Mitigation:
  default to non-thinking models for translation; strip `<think…>` blocks defensively.
- **Quality regression vs Opus/Sonnet.** Open-weight/Flash models trail frontier closed
  models on translation (≈1.4–2.9× errors depending on language pair). The app's language
  catalog is mostly high-resource, where the gap is smallest — but run a quick A/B on real
  user text (incl. zh-Hans vs zh-Hant, and Arabic/Hebrew RTL) before picking a default.
- **Per-user key friction.** Google is the smoothest (no card); DeepInfra needs a deposit;
  DeepSeek needs prepaid top-up; Together/Cerebras minimums are disqualifying for this
  model. Free tiers (Google, Groq) remove the friction but carry their own limits —
  Gemma 4's 14.4K req/day is ample; Gemini Flash-Lite's ~250 req/day free is fine for one
  person but will 429 for chatty users, so the UI should surface rate-limit errors
  distinctly (the error-string plumbing already exists).
- **Context length** is a non-issue (128K–1M everywhere relevant; texts are short) — the
  16K `max_tokens` budget is the only number that matters and it's fine.
- **Model/provider churn.** Groq deprecated half its catalog within a year; Gemini free-tier
  limits are unpublished and shift; DeepInfra rotates snapshots. The custom-model-ID field
  and per-provider presets keep us nimble; pin exact model IDs in the preset table and note
  the "as-of" date in code comments.
