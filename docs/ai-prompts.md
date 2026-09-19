# AI Prompts

Prompt version: `caa-prompt-v1` (`AiReasoningService.PROMPT_VERSION`).

Untrusted content is wrapped in `<<<UNTRUSTED_USER_CONTENT>>>` delimiters.

## Modes

| `changeassurance.ai.mode` | Behavior |
|---------------------------|----------|
| `spring-ai` / `openai` / `chatgpt` | Live ChatGPT via Spring AI OpenAI client (`OPENAI_API_KEY`) |
| `ollama` / `local` | OpenAI-compatible local server (default: Ollama at `http://localhost:11434/v1`, model `qwen3:14b`) |
| `fake` | Stub gateway for tests/local without a provider |
| `disabled` | AI off; deterministic fallbacks only |

Cloud default model: `gpt-4o-mini`. Local default model: `qwen3:14b`.

### ChatGPT

```bash
export OPENAI_API_KEY=sk-...
export CHANGEASSURANCE_AI_MODE=spring-ai
export CHANGEASSURANCE_AI_MODEL=gpt-4o-mini
```

### Local Ollama + Qwen3 (recommended offline)

```bash
ollama pull qwen3:14b
export CHANGEASSURANCE_AI_MODE=ollama
# optional overrides:
# export CHANGEASSURANCE_AI_MODEL=qwen3:14b
# export CHANGEASSURANCE_AI_BASE_URL=http://localhost:11434/v1
# export OPENAI_API_KEY=ollama
```

LM Studio (or any OpenAI-compatible server):

```bash
export CHANGEASSURANCE_AI_MODE=local
export CHANGEASSURANCE_AI_BASE_URL=http://localhost:1234/v1
export CHANGEASSURANCE_AI_MODEL=<model-id-from-lm-studio>
export OPENAI_API_KEY=lm-studio
```

If the key is missing for cloud mode, or `spring-ai-enabled=false`, `SpringAiModelGateway` fails closed and the workflow continues with deterministic reasoning. For `ollama`/`local`, a placeholder API key is used when none is set.

## Selecting a model

Clients can:

1. Read `GET /api/v1/ai/config` for providers (ChatGPT / Ollama), default model, and selectable options
2. Pass optional multipart fields `aiProvider` and `aiModel` on `POST /api/v1/change-reviews`

With `CHANGEASSURANCE_AI_MODE=spring-ai` (default), Ollama is also selectable when `CHANGEASSURANCE_AI_OLLAMA_ENABLED=true` (default) and Ollama is reachable at `CHANGEASSURANCE_AI_OLLAMA_BASE_URL` (default `http://localhost:11434/v1`).

Optional allowlist: `CHANGEASSURANCE_AI_ALLOWED_MODELS=qwen3:14b,qwen3:8b` (comma-separated). When set, submissions outside the list are rejected. When blank, any syntactically valid model id is accepted and the UI offers a custom entry.
