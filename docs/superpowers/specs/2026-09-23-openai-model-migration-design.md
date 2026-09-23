# OpenAI model migration design

## Goal

Update every model-backed example in the lab to use the OpenAI API with
`gpt-6-luna` at medium reasoning effort, and make `OPENAI_API_KEY` the only
provider key documented and forwarded by the examples.

## Scope

- Migrate Gemini-backed Quarkus, Spring Boot, and Jakarta EE/WildFly examples
  in both `exercises/` and `solutions/`.
- Replace Google AI dependencies and provider-specific Java model construction
  with OpenAI integrations.
- Use the OpenAI Responses API for tool calling so the examples can keep
  medium reasoning effort. Remove temperature settings from these requests.
- Update properties, Maven and Python dependency declarations, Compose
  environment forwarding, launch instructions, troubleshooting text, README
  files, and exercise walkthroughs to use OpenAI and `OPENAI_API_KEY`.
- Replace the stale Ollama/Granite setup text and preflight checks in the
  workshop and full-system launcher. The container stacks continue to provide
  only the existing database and observability services.
- Change the unused optional Python LangChain integration from the Google
  provider to the OpenAI provider. The travel agent remains rule-based and
  makes no model API calls.

References to Google protobuf or the A2A protocol are outside provider scope
and remain unchanged.

## Design

Keep the existing LangChain4j AI Service and tool definitions. Configure
Quarkus OpenAI integrations to use Responses mode and medium reasoning effort.
For Spring Boot and Jakarta EE, provide a LangChain4j Responses API
`ChatModel` to the existing AI Services. Upgrade the pinned integration
version only where required for Responses support. Keep the application-level
agent interfaces and tool execution flow intact.

Every model-backed example uses the same model ID, reasoning effort, and
environment variable. No example should send a temperature parameter with
this reasoning configuration. Compose files pass `OPENAI_API_KEY` only to
services that call the model.

## User-facing setup

Document that these examples call the OpenAI API and require an API key with
API billing enabled. A ChatGPT subscription does not pay for API usage. The
GPT-6 Luna API Free tier is unsupported. This note prevents attendees from
expecting the model to use their ChatGPT subscription or a free API tier.

The workshop setup should describe only the database and Grafana LGTM
containers. It should not ask participants to install, pull, or run Ollama or
Granite.

## Acceptance criteria

- Active Java model providers use OpenAI Responses API configuration with
  model `gpt-6-luna` and medium reasoning effort.
- `OPENAI_API_KEY` is used in local setup, Compose forwarding, and
  troubleshooting instructions; Gemini-specific API configuration is gone
  from examples and solutions.
- The documented full-system setup does not require Ollama or Granite.
- The Python travel agent stays rule-based and has no Google-specific optional
  LLM dependency.
- Unrelated Google references, such as protobuf and A2A documentation, remain
  intact.

## Constraints

OpenAI API usage is billed separately from ChatGPT subscriptions. GPT-6 Luna
has no Free API tier. Its tool calls at medium reasoning effort require the
Responses API; Chat Completions function calling for Luna is limited to
`none` reasoning effort. These constraints inform the provider configuration
and setup instructions.
