# Viewer Service — Config Modes

The viewer tracks content consumption and rolls it up into enrolment progress. **Config Modes** change *where a completion counts* — the same view can count only in its own course/batch, everywhere the content appears, or be carried into other courses by rule. After this doc you can set the mode + rules for an instance and verify the result.

## Highlights

- Three env keys drive everything: `viewer_context_mode`, `viewer_carry_scope`, `viewer_carry_forward_rules`.
- Config is **instance-global** and read at **startup** — change it, restart the pod.
- Default `viewer_context_mode=strict` = today's behavior (no-op). Modes need `viewer_enabled=true`.
- Rules are a **filter** for `copy` mode only. Empty rules ⇒ carry **nothing** (fail-closed).

## Config keys

| Key | Values | Default | Meaning |
|---|---|---|---|
| `viewer_context_mode` | `strict` \| `noContext` \| `copy` | `strict` | how a completion is keyed / carried |
| `viewer_carry_scope` | `content` \| `collection` | `content` | granularity for `noContext` / `copy` |
| `viewer_carry_forward_rules` | JSON (see below) | *(blank)* | which completions `copy` carries |

Read via `ProjectUtil.getConfigValue` — **env var overrides** `externalresource.properties`. Prerequisites: `viewer_enabled=true`; the enrol hook fires only when `deployment_mode=monolith`.

## The five effective modes

| `context_mode` | `carry_scope` | A completion counts… |
|---|---|---|
| `strict` | *(any)* | only in the exact course + batch it was done in (today's behavior) |
| `noContext` | `content` | everywhere that content appears (keyed to the content itself) |
| `noContext` | `collection` | in every batch of the same collection |
| `copy` | `content` | strict, **plus** any prior completion carried in by rule (organic allowed) |
| `copy` | `collection` | strict, **plus** prior *in-collection* completions carried in by rule |

## Rules (copy mode only)

`viewer_carry_forward_rules` is a JSON object: a `match` and a list of `rules`. A completion is carried only if it passes.

```json
{ "match": "all", "rules": [ { "type": "completedWithin", "unit": "months", "value": 3 } ] }
```

| Rule `type` | Params | Carries a completion if… |
|---|---|---|
| `completedWithin` | `unit` (`days`\|`months`), `value` | it was done within N days/months before now |
| `withinContextDuration` | *(none)* | it was done inside the **target batch's** `start_date`–`end_date` window (open-ended batch ⇒ just "after start") |
| `always` | *(none)* | unconditionally |

- `match: "all"` = every rule must pass; `match: "any"` = at least one passes.
- **Empty / omitted / unparseable rules ⇒ carry nothing** (fail-closed) and a warning is logged. To carry everything, use `always`.
- A rule that can't be evaluated (e.g. `withinContextDuration` on a batch with no dates) abstains — other rules decide.

## Example configs

Each block is `externalresource.properties` (or env vars). `viewer_enabled=true` is assumed.

**Strict (default — no-op):**
```properties
viewer_context_mode=strict
```

**NoContext Content** — a content counts as done in every course containing it:
```properties
viewer_context_mode=noContext
viewer_carry_scope=content
```

**NoContext Collection** — progress is shared across all batches of a collection:
```properties
viewer_context_mode=noContext
viewer_carry_scope=collection
```

**Copy Content, carry anything ever completed:**
```properties
viewer_context_mode=copy
viewer_carry_scope=content
viewer_carry_forward_rules={"match":"all","rules":[{"type":"always"}]}
```

**Copy Collection, carry only recent (last 3 months) in-collection completions:**
```properties
viewer_context_mode=copy
viewer_carry_scope=collection
viewer_carry_forward_rules={"match":"all","rules":[{"type":"completedWithin","unit":"months","value":3}]}
```

**Copy Collection, carry only work done inside the target batch's dates:**
```properties
viewer_context_mode=copy
viewer_carry_scope=collection
viewer_carry_forward_rules={"match":"all","rules":[{"type":"withinContextDuration"}]}
```

**Combined — recent AND in-window (`all`):**
```properties
viewer_carry_forward_rules={"match":"all","rules":[{"type":"completedWithin","unit":"months","value":6},{"type":"withinContextDuration"}]}
```

**Combined — in-window OR always as fallback (`any`):**
```properties
viewer_carry_forward_rules={"match":"any","rules":[{"type":"withinContextDuration"},{"type":"always"}]}
```

## Applying config

```bash
kubectl -n sunbird set env deploy/lern-service \
  viewer_enabled=true viewer_context_mode=copy viewer_carry_scope=collection \
  viewer_carry_forward_rules='{"match":"all","rules":[{"type":"completedWithin","unit":"months","value":3}]}'
```

The change is picked up on the rolling restart. The mode is the same for the whole instance — test one mode at a time with test users.

## Verifying

| Check | Where |
|---|---|
| Where a view landed | `user_content_consumption` row key (`collectionid` / `contextid`) |
| Rolled-up progress | `user_enrolments` `progress` / `status` / `contentstatus` / `completedon` |
| Carried scores | `assessment_aggregator` rows re-keyed to the target `collection_id` / `context_id` |
| ToC / summary | `POST /v1/summary/read` (NoContext Content resolves per-leaf on read) |
