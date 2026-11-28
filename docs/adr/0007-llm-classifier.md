# ADR 0007: LLM classifier as a slow second pass over the rules

- **Status:** Accepted
- **Date:** 2026-11-28

## Context

The rule-based classifier ([ADR 0003](0003-rule-based-classification-first.md)) is exact on what it
flags, but it misses postings whose early-career signal is buried in the text, and its domain often
follows a misleading department name ([EVALUATION.md](../EVALUATION.md)). A language model reads the
whole posting the way a person does. It is also slow, rate limited and not free, and its answers have to
fit Init's own categories.

Groq serves open models through an OpenAI-compatible API. Its free plan allows a few thousand tokens per
minute and about 200,000 per day for `openai/gpt-oss-20b`, a production model that supports strict
structured outputs: the answer is forced to match a JSON schema.

## Decision

- **Behind the existing interface, on top of the rules.** The rules still classify every posting at
  ingestion. The LLM only replaces the judgement calls (early career, opportunity type, domain,
  seniority); location, language and pay stay with the rules, which read them reliably.
- **A separate pass, not part of the ingestion.** A scheduled pass (`LlmReclassifier`, every 3 hours,
  15 postings at a time, one request every 12 seconds) sends postings the feed can show: open,
  location-eligible, not duplicates. The ingestion keeps its short transactions and its speed.
- **Cached by content hash.** `llm_content_hash` records the content the LLM judged. An unchanged
  posting is never sent again; a changed one goes back to the rules' verdict until the next pass.
- **Strict schema, safe fallback.** The request carries a JSON schema whose enums are Init's
  categories. A failed request, a rate limit or an answer outside the schema leaves the rules' verdict
  in place; three failures in a row end the pass.
- **Off by default.** `LLM_ENABLED=true` and `GROQ_API_KEY` in `.env` switch it on; the key is never
  committed. CI does not call the API.
- **Measured on the same labels.** `LlmClassifierEvaluationTests` runs it on the labelled sample when a
  key is set in the environment and writes a report next to the rules' one.

## Consequences

- With the free plan, a backlog of a few hundred postings takes a few days to work through; new
  postings follow within hours.
- Only public posting text (title, structured fields, the first 2,500 characters of the description)
  is sent to the provider; no user data exists in the system.
- A provider or model change is a configuration change (`init.llm.base-url`, `init.llm.model`), as long
  as the model supports strict JSON schemas.
- The evaluation set was labelled by one person; an LLM that agrees with that reading scores higher.
  The numbers are a comparison on the same ground, not an absolute truth.
