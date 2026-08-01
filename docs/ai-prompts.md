# AI Prompts

Prompt version: `caa-prompt-v1` (`AiReasoningService.PROMPT_VERSION`).

Untrusted content is wrapped in `<<<UNTRUSTED_USER_CONTENT>>>` delimiters.

## Modes

| `changeassurance.ai.mode` | Behavior |
|---------------------------|----------|
| `spring-ai` / `openai` / `chatgpt` | Live ChatGPT via Spring AI OpenAI client (`OPENAI_API_KEY`) |
| `fake` | Stub gateway for tests/local without a provider |
| `disabled` | AI off; deterministic fallbacks only |

Default model: `gpt-4o-mini` (`CHANGEASSURANCE_AI_MODEL`).

```bash
export OPENAI_API_KEY=sk-...
export CHANGEASSURANCE_AI_MODE=spring-ai
export CHANGEASSURANCE_AI_MODEL=gpt-4o-mini
```

If the key is missing or `spring-ai-enabled=false`, `SpringAiModelGateway` fails closed and the workflow continues with deterministic reasoning.
