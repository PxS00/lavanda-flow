# Issue #199 — Establish branded operational UI foundation

## Status

Approved implementation specification for issue #199.

The GitHub issue remains the source of truth for objective, scope, acceptance criteria, constraints, and out-of-scope behavior. This specification fixes the concrete frontend implementation decisions needed to deliver the visual foundation without changing product behavior or backend-authoritative rules.

## Objective

Refine the existing Angular operator interface into a coherent, branded, accessible Céu de Lavanda operational product while preserving all existing routes, contracts, workflows, session behavior, and backend authority.

The result must feel warm, practical, and deliberate rather than like a generic SaaS/admin template.

## Source of truth

Apply, in order:

1. GitHub issue #199;
2. root `AGENTS.md`;
3. `frontend/AGENTS.md`;
4. `docs/product/scope-v1.md`;
5. `docs/architecture/architecture.md`;
6. `docs/architecture/dependencies.md`;
7. current `develop` frontend implementation;
8. existing frontend tests for touched components.

Do not invent product behavior in visual code. If a requested visual treatment requires a new route, backend contract, business rule, or dependency, report that as a separate blocker instead of expanding #199.

## Branch and base

Branch:

```text
feature/199/establish-branded-operational-ui-foundation
```

The branch starts from `develop` at:

```text
42bf1e71bc723afb8c9021716f6096e6faffa443
```

## Current implementation observations

The current frontend provides a sound functional baseline but has several concrete visual inconsistencies that #199 owns:

- global Angular Material theming still uses the generated magenta/violet palettes rather than Céu de Lavanda colors;
- `index.html` loads Roboto and Material Icons from Google-hosted assets even though the production model is local-first;
- no current frontend template uses `mat-icon`, so the external Material Icons font is not required by existing functionality;
- the shell uses a plain Material toolbar and a fixed `240px` sidenav;
- shell content uses only fixed `24px` desktop / `16px` narrow padding and has no deliberate content-width or page-composition system;
- the sidenav renders an inert `Saídas` row even though no top-level output route exists; stock-output actions are item-scoped under the inventory flow;
- page-header layout and spacing rules are repeated across many feature-specific stylesheets;
- the dashboard and other operational pages rely on Material defaults with limited brand hierarchy;
- the repository currently contains only the generic `favicon.ico` asset;
- the final Céu de Lavanda/Lavanda Flow logo is not yet available.

These observations justify a small application-owned visual system; they do not justify a new component library or a route/information-architecture rewrite.

## Brand palette

Preserve the exact approved palette:

```text
#BD8C40  primary gold
#EDCC8A  light gold
#3B3836  dark neutral / primary ink
#EAC8A9  warm neutral
#FFF8EE  warm light canvas
#BEAAD4  primary lavender
#D9C9E8  light lavender
```

### Token strategy

Introduce application-owned CSS custom properties with an `--lf-` prefix. Raw palette values must be declared centrally and feature styles must consume semantic tokens instead of repeating new brand hex literals.

At minimum provide semantic roles equivalent to:

```text
--lf-color-canvas
--lf-color-surface
--lf-color-surface-muted
--lf-color-text
--lf-color-text-muted
--lf-color-primary
--lf-color-primary-emphasis
--lf-color-accent
--lf-color-accent-soft
--lf-color-border
--lf-color-focus
```

Exact semantic mappings may be adjusted during implementation only when required to meet WCAG AA contrast.

Also centralize a restrained spacing/radius/elevation vocabulary sufficient for shell and shared composition. Do not build a general-purpose design-system package.

A small Sass partial imported by `frontend/src/styles.scss` is acceptable when it keeps tokens/theme concerns isolated. Do not scatter the token declarations across feature stylesheets.

## Angular Material integration

Keep Angular Material/CDK as the component foundation.

The implementation must:

- replace the generated magenta/violet visual baseline with brand-aligned Material system colors;
- use Angular Material 22 theming/system-token mechanisms rather than broad unsupported internal-selector overrides;
- keep built-in component states and accessibility behavior intact;
- avoid per-component hard-coded brand hex values when a semantic token exists;
- preserve existing Material button, form-field, card, sidenav, list, toolbar, dialog, and focus behavior unless a scoped visual refinement is required.

Do not add another design system, CSS framework, or theme dependency.

## Typography

### Body and operational content

Use a local/system UI sans-serif stack for body text, labels, controls, tables, form fields, numeric values, and dense operational content.

The browser must not require a public font CDN for the production interface.

Remove the existing runtime dependency on Google-hosted Roboto. Also remove the Google-hosted Material Icons font unless implementation introduces a real existing requirement; #199 must not introduce an icon-font dependency simply for decoration.

### Display headings

Use the brand display stack:

```text
"IvyPresto Display", Georgia, "Times New Roman", serif
```

for primary page headings and selected low-density display contexts only.

`IvyPresto Display` is an optional first choice. If it is not installed/available on a client, the fallback serif must remain visually acceptable and fully usable.

Do not commit the supplied IvyPresto font binary until redistribution rights are confirmed.

### Script typography

`Classic Script MN` is not a general UI font. Reserve it for future logo/artwork or a very small brand accent only.

Do not use it for headings, body copy, navigation, buttons, tables, forms, codes, status text, or helper text.

Do not commit the supplied Classic Script MN font binary until redistribution rights are confirmed.

The final logo is expected to become an image/vector asset, so the shell must not depend on clients having Classic Script MN installed.

## Local-first asset rule

#199 must make the branded production UI self-contained with respect to required visual assets.

Required production rendering must not depend on:

- Google Fonts;
- Google Material Icons;
- another public font/icon CDN;
- a public logo URL;
- network-loaded theme assets.

Repository-owned placeholder assets and system-font fallbacks are acceptable.

## Application shell

Refine `ApplicationShell` without changing routing/authentication responsibilities.

### Desktop/tablet-landscape shell

Use a deliberate two-region layout:

- branded/navigation region on the left;
- operational content region on the right;
- operator identity/logout remain easy to find but visually secondary to primary navigation.

The final logo slot belongs in the shell/navigation brand area. Until the final asset exists, use a lightweight repository-safe placeholder/lockup that can be replaced without restructuring the shell.

The shell must not become a generic admin dashboard with unnecessary widgets, profile menus, breadcrumbs, notification bells, or speculative controls.

### Narrow/tablet behavior

Preserve the current responsive sidenav behavior using Angular Material/CDK.

On narrow widths:

- navigation may continue using overlay mode;
- the menu trigger must retain a clear accessible name;
- the brand must remain identifiable;
- touch targets must remain practical;
- opening/closing navigation must not affect route/session behavior.

## Navigation information architecture

Preserve all existing route contracts. #199 may improve grouping and labels only where it does not change destination semantics.

Use the following conceptual grouping for the existing destinations:

### Visão geral

- `Painel` -> `/dashboard`

### Estoque

- `Estoque` -> `/catalog`
- `Entradas` -> `/receipts`
- `Alertas` -> `/inventory/alerts`

### Produção

- `Produção` -> `/production/formulas`

### Cadastros

- `Fornecedores` -> `/suppliers`

The current inert `Saídas` list row must not remain as a fake top-level destination. Stock outputs/withdrawals remain item-scoped existing behavior reached through the inventory item operational flow. Do not create a new `/outputs` or equivalent route in #199.

#200 may later improve contextual guidance around how the operator reaches output actions.

Navigation must provide clear active, hover, focus-visible, and selected treatment without relying on color alone.

## Page composition and spacing

Establish one global operational composition baseline rather than solving spacing independently on every page.

### Shell content

Use responsive outer padding equivalent to a restrained clamp between approximately `20px` and `40px` on supported notebook/tablet widths, with a smaller narrow-screen value only where necessary.

Use a generous but bounded content width so pages do not feel glued to the shell edge or stretched without structure. A maximum content width around the current desktop operational viewport is acceptable; do not constrain dense operational tables/forms to a marketing-site width.

### Vertical rhythm

Use consistent semantic spacing for:

- page header -> first section;
- section -> section;
- heading -> supporting text;
- card/container internal padding;
- form groups;
- action rows.

Avoid both cramped edges and excessive whitespace that increases operational scrolling.

## Page heading pattern

Create a reusable visual pattern for existing semantic page headers containing:

- primary `h1`;
- optional supporting subtitle/context;
- optional action area.

Prefer a low-abstraction shared CSS contract/global utility pattern over creating a new wrapper component solely for styling.

A component is justified only if current templates require repeated behavior, not merely repeated CSS.

The pattern must:

- keep the `h1` semantically correct;
- use the display heading stack;
- remain readable when actions wrap below the heading on narrower widths;
- avoid marketing-scale hero typography;
- support the existing pt-BR page copy without rewriting feature behavior.

## Shared surfaces and primitives

Use brand/theme tokens to bring existing shared visual elements into the same system:

- outlined cards/surfaces;
- list/table containers;
- form group spacing;
- primary/secondary/destructive actions;
- loading state;
- empty state;
- error state;
- focus-visible treatment;
- selected/active navigation states.

Do not create new abstractions for every visual pattern. Prefer global Material/system tokens and existing shared components first.

## Dashboard treatment

The dashboard is the primary visual reference for the new foundation.

Preserve all five backend-provided metrics and links exactly as they function today.

Refine only presentation:

- stronger `Painel operacional` hierarchy;
- calmer supporting context;
- consistent metric-card surfaces and spacing;
- readable metric values;
- subtle brand accent rather than high-saturation SaaS KPI styling;
- hover/focus states for linked cards;
- responsive metric grid behavior.

Do not add charts, trends, percentages, icons, computed metrics, new API calls, or frontend-derived business indicators.

## Logo integration slot

The final Céu de Lavanda/Lavanda Flow logo is out of scope as artwork, but #199 must prepare the integration contract.

Use a shell brand structure that can later switch from the temporary placeholder to an image/vector asset without changing navigation layout.

Requirements:

- stable dimensions/placement;
- accessible product name remains available to assistive technology;
- placeholder contains no external URL;
- no base64-embedded large image;
- future static image integration should be compatible with `NgOptimizedImage` where applicable.

Do not attempt to recreate the final Céu de Lavanda logo in CSS.

## Favicon

Replace the generic favicon with a small repository-safe temporary Lavanda Flow branded mark.

Prefer a simple static SVG or equivalent lightweight local asset using the approved palette.

The temporary favicon must:

- contain no copyrighted third-party artwork;
- require no network fetch;
- be easy to replace with the final logo mark later;
- keep `index.html` favicon metadata correct.

Final logo artwork remains out of scope.

## Login page

The login page is outside `ApplicationShell` and must keep #179 authentication behavior unchanged.

Apply only enough brand foundation to avoid a visually disconnected login experience:

- same canvas/text/primary tokens;
- same body typeface;
- restrained brand identity/placeholder where appropriate;
- existing form, error, XSRF/session, and routing behavior unchanged.

Do not turn login into a marketing landing page.

## Accessibility

The implementation must preserve or improve WCAG AA behavior:

- text/background contrast;
- focus-visible contrast;
- keyboard navigation;
- semantic heading structure;
- accessible nav/menu names;
- non-color-only active state;
- touch-friendly controls on tablet;
- no overflow/clipping at supported widths;
- respect reduced-motion expectations for any existing transitions.

Do not introduce decorative animations that are unnecessary for operation.

## Expected implementation footprint

Likely touched areas include:

```text
frontend/src/styles.scss
frontend/src/index.html
frontend/public/*
frontend/src/app/core/layout/application-shell/*
frontend/src/app/features/dashboard/pages/dashboard-page/*
frontend/src/app/features/auth/pages/login-page/*
frontend/src/app/shared/*             # only existing shared states/primitives as justified
selected feature styles/templates     # only where global foundation cannot achieve consistency
```

A small global Sass token/theme partial is allowed if useful.

Do not edit backend Java, Flyway, Compose/runtime files, API contracts, or domain documentation for visual implementation.

## Testing strategy

Preserve existing behavior tests and add focused frontend coverage for structural changes.

At minimum test where applicable:

- shell still renders every existing navigable destination;
- navigation active/route behavior remains intact;
- inert `Saídas` fake destination is removed without adding a new route;
- operator username/logout behavior remains intact;
- handset menu still opens/closes and navigation remains accessible;
- brand/logo placeholder renders without altering authentication or route guards;
- dashboard still renders the same backend metrics/links;
- login behavior and visible auth controls remain unchanged;
- any shared-state markup changed for styling keeps loading/error/empty semantics and ARIA behavior.

Do not add brittle tests that assert exact CSS pixel values or implementation-specific Sass variables.

## Validation

Required:

```text
cd frontend
pnpm lint
pnpm test
pnpm build
```

Also review:

```text
git diff
git diff --check
git status --short
```

Manual visual validation must cover at least:

- supported notebook width;
- supported tablet/narrow layout;
- dashboard;
- catalog/list/detail/form examples;
- item operational page;
- receipt page;
- production page;
- login;
- sidenav open/closed behavior;
- keyboard focus visibility;
- favicon load;
- browser rendering with public internet unavailable or external Google font assets blocked.

The existing bundle-budget warning may remain non-blocking if unchanged. Record it; do not expand #199 into bundle optimization.

## Acceptance mapping

#199 is complete only when:

- approved palette is centrally tokenized and used coherently;
- Angular Material visual baseline is brand-aligned;
- required fonts/assets do not depend on public CDNs;
- page content has deliberate responsive outer spacing;
- primary headings have clear branded hierarchy;
- operational body/forms/tables remain highly legible;
- shell/navigation are visually improved while preserving route/auth behavior;
- the fake inert `Saídas` top-level row is removed rather than converted into invented functionality;
- dashboard and common surfaces align with the foundation;
- stable future-logo slot exists;
- temporary branded favicon exists;
- notebook/tablet behavior remains usable and accessible;
- no backend/domain/API/runtime behavior changes;
- no new dependency;
- `pnpm lint`, `pnpm test`, and `pnpm build` pass.

## Out of scope

Preserve the issue boundaries:

- contextual workflow help/microcopy beyond styling (#200);
- broader operational visibility of `essenceReference` / `productionTypeCode` (#201);
- final Céu de Lavanda logo artwork;
- new product functionality;
- new routes for stock outputs;
- backend/schema/API changes;
- dark mode;
- charts/analytics;
- animation-heavy UI;
- mobile-phone-first redesign;
- PWA/offline product behavior;
- a new design-system package or UI dependency.
