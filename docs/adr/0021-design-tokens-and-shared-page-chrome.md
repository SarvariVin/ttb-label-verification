# ADR-0021: Build the UI from design tokens and shared page chrome

- **Status:** Accepted (extends ADR-0003)
- **Date:** 2026-09-27
- **Author:** SarvariVin

## Context

ADR-0003 settled on server-rendered Thymeleaf pages, one stylesheet and a strict CSP. Over time each template repeated its own header markup and pieces of layout. There was no indication of the current page, the stat tiles broke unevenly, and on phones the label-detail page scrolled sideways because a large image could widen a `1fr` grid track. Status was carried mainly by color.

## Decision

- Every page takes its chrome from `fragments/layout.html`: `head(title)`, `topbar` (a skip link, role-aware nav with `aria-current`, and a user chip), `flash` and `footer`. `GlobalModelAdvice` adds `currentPath` to the model so the nav can mark the current page.
- `app.css` starts with design tokens (surfaces, lines, text, brand, status, shape), overridden once for dark mode. Components use only tokens.
- A fixed set of components is documented in [docs/ui.md](../ui.md): card, stat tile, badge with a dot and a label, segmented tabs, table, status-edged field row, alert, timeline, empty state and avatar. New screens are built from these.
- Grid tracks use `minmax(0, 1fr)` and grid children use `min-width: 0`. Tables that don't fit scroll inside their card, and field rows stack under 700 px.
- The CSP stays as it is: no inline styles or scripts, and the only CSS image is a `data:` SVG, which `img-src` already allows.

## Consequences

- Pages look and behave the same way, and a new page is mostly composition. The UI guide explains how.
- Dark mode and future theming change the tokens, not the components.
- Markup hooks that `app.js` and the tests use (element ids, `data-*` attributes, `.prefilled`, `.ready` and `.busy`) are kept, so behavior is unchanged.
- Any new component has to be added to `app.css` and to docs/ui.md.

## Alternatives considered

- A CSS framework such as Bootstrap or Tailwind: it would bring a build step or a large stylesheet, and utility classes compete with the CSP-friendly single stylesheet.
- A Thymeleaf layout dialect: it adds a dependency, and plain fragments are enough for one shared layout.
