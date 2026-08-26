---
name: purchase-research
description: Clarify a user's purchase intent, research products through the purchase-research MCP tools, show evidence for material claims, and reverify a selected offer.
---

# Purchase Research

## Workflow

1. Identify the product category and intended use.
2. Ask only the missing conditions that materially change results, such as size, budget, fit, deadline, or required option.
3. Mark a condition as `required` only when violating it makes the purchase unacceptable. Mark other preferences as `preferred`, and have the user confirm these priorities before search.
4. Call MCP search and collection tools; do not invent unavailable product attributes.
5. Compare candidates using explicit user priorities and return the source and collection time for important claims.
6. Distinguish official product facts, review-derived signals, and agent inference.
7. Before the user acts on a selected product, call the offer verification tool again.
8. If collection is blocked, stale, or unsupported, explain that limitation rather than treating old data as current.

## Safety

- Do not bypass authentication, CAPTCHA, robots restrictions, or access controls.
- Do not claim that review photos prove authenticity.
- Never ask for or store payment credentials.
- Treat purchasing and checkout as user-controlled actions.
