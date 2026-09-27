# ADR-0003: Server-rendered UI with progressive enhancement

- **Status:** Accepted
- **Date:** 2026-09-23

## Context

Users are TTB specialists on managed agency desktops and applicants on arbitrary browsers. Section 508 accessibility and a strict Content Security Policy are required. The UI is forms, tables and an image with overlays.

## Decision

Render pages on the server with Thymeleaf. Every page works without JavaScript; one small `app.js` adds conveniences only (select-all, confirm dialogs, overlay highlight, busy buttons). One stylesheet, no inline styles or scripts. Bounding-box overlays are SVG `<rect>` elements positioned by attributes, so the CSP needs no `unsafe-inline`.

## Consequences

- No Node toolchain; one deployable.
- Strict CSP (`script-src 'self'; style-src 'self'`).
- Rich interactions (pan/zoom, drawing annotations) require more JavaScript later; tracked in [production.md](../production.md#9-roadmap).

## Alternatives considered

- SPA (React/Angular) + REST: second build and deploy pipeline, larger attack surface, more accessibility work.
- HTMX: attractive, but unnecessary for the current interaction set.
