# API

Base: `/api/v1`

- `GET /ai/config` — AI mode, providers (ChatGPT + Ollama), default model, selectable models, custom-model flag
- `POST /change-reviews` multipart (optional `aiProvider`, `aiModel`, `packageName`, `schemaOwner`)
- `GET /change-reviews`, `/{id}`, `/{id}/plan`, `/activities`, `/findings`, `/information-gaps`, `/report`
- `GET /change-reviews/{id}/report.html` — downloadable evidence-backed HTML impact report
- `POST /{id}/answers`
- OpenAPI UI: `/swagger-ui.html`
- Health: `/actuator/health`
