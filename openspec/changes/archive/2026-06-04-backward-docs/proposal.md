# Proposal: Backward Documentation — MTG Collection Manager

## Why

The MTG Collection Manager has been built incrementally through v0.1.89 without formal planning artifacts. As the project grows and gains new contributors (or future AI-assisted development sessions), the absence of structured documentation means every change starts from scratch understanding the system. This change retroactively captures the project's intent, capabilities, architecture, and decision rationale in OpenSpec format so that future changes have a foundation to build on.

## What Changes

- No code is changed. This is a documentation-only change.
- A complete set of OpenSpec planning artifacts is created to describe the existing system.
- These artifacts become the baseline `main` specs after archiving.

## Capabilities

### New Capabilities

- `project-overview`: High-level description of what the MTG Collection Manager is and who uses it
- `collection-browsing`: Spec for the `/show` page — per-set card grid with multi-dimensional filtering
- `set-comparison`: Spec for the `/compare` page — diff view between two users' collections for a set
- `csv-import`: Spec for the `/import` flow — DragonShield web, app, and inventory CSV formats
- `statistics`: Spec for the `/statistics` page — collection analytics, set completion, price trends
- `sell-suggestions`: Spec for `/sell-suggestions` — surfacing sellable duplicate cards with revenue estimates
- `price-watch`: Spec for `/price-watch` — daily price snapshot tracking and winner/loser reporting
- `card-search`: Spec for `/search` — cross-set card search by name or collector number
- `deck-suggest`: Spec for `/deck-suggest` — meta-deck matching against user collection
- `my-decks`: Spec for `/my-decks` — user's physical decks parsed from DragonShield folder conventions
- `auth-and-user-mapping`: Spec for Azure Entra ID OAuth2 login and email→app-user mapping
- `nightly-automation`: Spec for the nightly price update and report pre-computation pipeline
- `architecture-design`: Design document covering stack decisions, data model, and key patterns

## Impact

- `openspec/changes/backward-docs/proposal.md`: this file
- `openspec/changes/backward-docs/specs/*.md`: one spec file per feature area (10 files)
- `openspec/changes/backward-docs/design.md`: architecture and decision record
- `openspec/changes/backward-docs/tasks.md`: implementation record (all tasks pre-completed)
- `COSTS.md`: cost tracking row added for this session
