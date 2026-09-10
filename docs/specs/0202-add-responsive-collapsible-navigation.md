# Issue #202 — Add responsive collapsible navigation

## Status

Approved implementation specification for issue #202.

The GitHub issue remains the source of truth for Objective, Context, Scope, Acceptance Criteria, Constraints, and Out of Scope. This specification fixes the concrete shell interaction decisions needed to implement the smallest complete responsive collapsible-navigation behavior on top of the branded foundation delivered by #199.

## Branch and baseline

- Branch: `feature/202/add-responsive-collapsible-navigation`
- Base: `develop`
- Baseline commit: `f9dddec300c30302b11ebf1b59694af23e064e04`
- #199 branded operational UI foundation: merged.
- #200 contextual guidance: merged.
- #201 operational reference visibility: merged.

## Objective

Reduce permanent horizontal navigation space on supported notebook/desktop layouts while preserving predictable touch behavior on tablet-oriented layouts.

The shell must provide:

- a compact persistent navigation rail for pointer-capable notebook/desktop use;
- intentional expansion for pointer hover and equivalent keyboard focus;
- an explicit overlay drawer for touch-oriented/tablet use;
- stable route, authentication, session, accessibility, and business behavior.

This issue is an `ApplicationShell` presentation/interaction refinement only.

## Source of truth

Apply, in order:

1. GitHub issue #202;
2. root `AGENTS.md`;
3. `frontend/AGENTS.md`;
4. `docs/specs/0199-establish-branded-operational-ui-foundation.md`;
5. current `develop` `ApplicationShell` implementation and tests.

Do not change routes, backend contracts, authentication/session behavior, business workflows, or existing information architecture.

## Current-state findings

The current shell already provides a stable base:

- `mat-sidenav` uses `side` outside `Breakpoints.Handset` and `over` on handset;
- desktop navigation remains permanently open at `17rem`;
- narrow navigation is closed by default and opened by the existing explicit menu trigger;
- `aria-expanded` is already bound to the actual sidenav open state;
- route selection on narrow layouts already closes the sidenav;
- navigation groups and destinations already match #199;
- navigation destinations currently rely on visible text only;
- no `mat-icon`/Material icon font dependency is present;
- repository-owned `lavanda-flow-logo.svg` and `favicon-lf.svg` assets are already available locally.

The main implementation gap is therefore interaction and responsive presentation, not routing or application behavior.

## Responsive mode decision

`Breakpoints.Handset` alone is not sufficient for #202 because the issue distinguishes pointer-capable notebook/desktop interaction from touch-oriented tablet interaction.

Use a focused shell-level capability query through existing Angular CDK `BreakpointObserver` support.

Persistent compact rail mode requires both:

- enough viewport width for persistent navigation; and
- hover/fine-pointer capability suitable for intentional hover expansion.

A media query equivalent to the following intent is appropriate:

```text
(min-width: 960px) and (hover: hover) and (pointer: fine)
```

The exact threshold may be adjusted only if the existing supported shell layout requires it, but the semantic rule must remain:

```text
persistent rail = sufficient width AND hover-capable fine pointer
```

All other supported contexts use overlay navigation.

Do not use width alone to classify touch/tablet layouts.

Expose one focused derived shell state, conceptually:

```text
usesOverlayNavigation
```

or its inverse.

Do not introduce a general responsive-layout service.

## Persistent notebook/desktop navigation

### Resting state

In persistent mode:

- the sidenav remains logically open;
- the visual resting state is a compact rail;
- the compact width should be sufficient for touch-safe/focusable destination controls and a compact brand mark, approximately `4.5rem` unless implementation proves another nearby value is required;
- item names are not the only visual affordance in the compact state;
- selected route remains identifiable;
- navigation groups remain structurally present.

Do not persist expanded/collapsed preference between sessions.

### Expansion behavior

Prefer CSS interaction rather than Angular state for purely visual desktop expansion.

Use the equivalent of:

```css
:hover
:focus-within
```

on the persistent navigation region so:

- pointer hover expands labels;
- keyboard focus entering the navigation provides equivalent label/context exposure;
- focus remains in place while the navigation is expanded;
- leaving hover collapses only when keyboard focus is not still inside;
- tabbing out collapses naturally without forcing focus movement.

Do not add `mouseenter`/`mouseleave` Signals unless CSS cannot satisfy a real acceptance criterion.

### Flicker avoidance

Do not implement delayed timers, hover debouncing services, or pointer-tracking state unless a real defect remains after using the rail as one stable hover/focus region.

The expanded surface must not collapse merely because the pointer moves between child links inside the sidenav.

### Main-content stability

Routine pointer expansion must not make the main content repeatedly jump horizontally.

The shell should reserve the compact rail footprint as the persistent layout width and allow the expanded navigation surface to occupy the additional visual width without repeatedly reflowing the operational content.

Implement this using supported `mat-sidenav`/host layout composition and application-owned CSS only.

Do not target unsupported Angular Material internal selectors.

No acceptance test should assert exact pixel widths.

## Compact destination identity

The current destinations are text-only, so compact mode requires a stable visual identifier for each route.

Do not add an icon dependency or external icon/font asset.

Use small repository-owned or inline SVG markup in the shell for destination glyphs.

Requirements:

- SVGs are decorative and `aria-hidden="true"` when the link already has an accessible name;
- each compact destination retains a clear accessible name independent of visible label state;
- the visible label remains in the DOM and is revealed in expanded mode;
- the icon/glyph is not the sole accessible identity;
- icon meaning must not rely on color alone;
- icons remain visually secondary to expanded text labels.

Do not introduce `MatIconModule`, Google Material Icons, an icon package, or a new public asset dependency solely for #202.

## Navigation destinations and groups

Preserve exactly the current information architecture:

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

Do not add `/outputs` or any other new destination.

In compact persistent mode:

- group headings may be visually hidden;
- their semantic/grouping purpose must not be destroyed;
- expanded mode reveals the existing headings again.

Selected route treatment must remain clear in compact and expanded states through more than color alone, for example background plus weight/shape/border treatment consistent with #199.

## Brand treatment

Reuse existing local assets only.

Persistent expanded mode:

```text
/lavanda-flow-logo.svg
```

Persistent compact mode:

```text
/favicon-lf.svg
```

Both represent the same existing dashboard brand link and retain the same accessible product/destination name.

Do not create another logo or external asset in #202.

Overlay/tablet mode may keep the full existing brand treatment if it remains practical.

## Overlay tablet/touch navigation

In overlay mode:

- `mat-sidenav` uses `over`;
- navigation is closed by default;
- the explicit existing menu control is visible;
- the menu control opens/closes the actual sidenav;
- `aria-expanded` reflects `sidenav.opened` rather than a duplicated local Boolean;
- selecting a navigation destination closes the drawer;
- Angular Material backdrop/outside interaction closes the drawer normally;
- hover is not required for any necessary label or action;
- touch targets remain practical;
- navigation remains scrollable if viewport height is constrained.

Do not add custom backdrop click handling unless Angular Material's supported behavior proves insufficient.

Do not carry desktop compact/expanded presentation semantics into overlay mode.

## Accessibility and keyboard behavior

The implementation must preserve WCAG AA and current project accessibility requirements.

Required behavior:

- every navigation destination remains keyboard-focusable;
- entering the compact persistent navigation with keyboard focus exposes full destination context via `:focus-within` or equivalent;
- no focus trap is introduced;
- collapse never programmatically moves focused elements;
- compact destinations have explicit accessible names;
- selected state remains perceivable without color alone;
- visible focus treatment from #199 remains intact;
- menu trigger `aria-expanded` remains synchronized with actual drawer state;
- no hover-only information is required for operation;
- existing `aria-label` values for primary navigation and brand remain valid.

Avoid adding tooltips as the only mechanism for destination naming. Tooltips are not required for #202.

## Motion

Use CSS transitions only where they materially improve the expansion/collapse interaction.

Preferred timing range:

```text
140ms–180ms
```

Do not add `@angular/animations` or another animation dependency.

Any transition introduced by #202 must be disabled or reduced under:

```css
@media (prefers-reduced-motion: reduce)
```

Do not animate the main operational content position during routine hover expansion.

## Angular implementation guidance

Keep state local to `ApplicationShell`.

Prefer:

- `BreakpointObserver` for capability/mode detection;
- `toSignal()` for the responsive mode;
- template binding to actual `mat-sidenav` state;
- CSS `:hover` / `:focus-within` for persistent visual expansion;
- application-owned classes and semantic markup.

Do not introduce:

- a responsive/navigation service;
- RxJS state beyond the existing breakpoint observation unless required;
- persisted preferences;
- host listeners for hover where CSS is sufficient;
- business logic;
- new route configuration.

Follow `frontend/AGENTS.md`: use host metadata instead of `@HostListener` if host behavior is truly needed.

## Expected implementation footprint

The implementation should remain centered on:

```text
frontend/src/app/core/layout/application-shell/application-shell.ts
frontend/src/app/core/layout/application-shell/application-shell.html
frontend/src/app/core/layout/application-shell/application-shell.scss
frontend/src/app/core/layout/application-shell/application-shell.spec.ts
```

A small local SVG helper/template fragment is acceptable only if it materially reduces duplicated shell markup. Do not create a general icon system.

No feature page should require modification for #202.

Do not edit backend Java, API contracts, DTOs, Flyway, database, Compose/runtime, auth/session logic, or dependencies.

## Testing strategy

Extend `ApplicationShell` tests with behavior-oriented assertions.

### Responsive mode

Cover:

- pointer-capable/sufficient-width state uses persistent navigation;
- touch-oriented/narrow state uses overlay navigation;
- tests drive `BreakpointObserver` state rather than browser-global viewport hacks where possible.

### Persistent navigation

Cover:

- sidenav remains open in persistent mode;
- compact rail class/state is present;
- all six destinations remain rendered;
- each destination has an accessible name that does not depend on visually revealed text;
- selected route/link behavior remains intact where practical to assert;
- group/destination inventory remains exactly the #199 list.

Do not attempt to test browser CSS `:hover` pixel expansion in unit tests. Instead test the structural classes/markup that make the CSS behavior possible.

### Keyboard-equivalent context

Cover structure required for keyboard expansion:

- destination labels remain associated with the links;
- links remain focusable;
- no `tabindex=-1` or focus-management workaround is introduced;
- navigation region supports the `:focus-within` CSS strategy.

Do not add brittle synthetic focus choreography merely to assert CSS rendering that jsdom cannot compute reliably.

### Overlay/tablet navigation

Cover:

- overlay drawer is closed by default;
- explicit menu trigger is rendered;
- menu trigger toggles the actual sidenav;
- `aria-expanded` transitions with actual open state;
- selecting a destination closes the overlay drawer;
- route destination remains unchanged.

Angular Material owns backdrop-close mechanics; unit tests should preserve/configure the supported backdrop rather than reimplement it. Manual validation must confirm backdrop dismissal.

### Preserved behavior

Keep existing tests proving:

- exact route list;
- no `/outputs` destination;
- brand link remains `/dashboard`;
- username remains visible;
- logout success navigates to `/login`;
- logout failure retains authenticated shell/error behavior.

Do not weaken existing auth/session assertions.

## Manual validation

After automated validation, manually inspect at minimum:

### Notebook / pointer

- supported notebook width around 1280px;
- compact rail at rest;
- pointer hover reveals labels;
- moving among links does not flicker;
- leaving navigation collapses it;
- content does not visibly jump horizontally on each hover;
- selected route remains clear in compact state;
- full logo/compact brand swap behaves correctly;
- long page content remains usable while rail expands.

### Keyboard

- Tab into navigation;
- labels/context become available without mouse;
- navigate all links;
- focus is never trapped;
- Tab out and confirm natural collapse;
- visible focus remains clear.

### Tablet/touch-oriented

- overlay drawer starts closed;
- Menu opens it;
- no hover behavior is required;
- tapping a destination closes it;
- tapping the backdrop closes it;
- `aria-expanded` matches real drawer state;
- touch targets are practical.

### Motion

- normal transition is subtle;
- reduced-motion mode removes/reduces the introduced transition.

## Validation

From `frontend/`:

```bash
pnpm lint
pnpm test
pnpm build
```

Then:

```bash
git diff --check
git diff
git status --short
```

The existing initial bundle-budget warning remains non-blocking if materially unchanged. Record it; do not expand #202 into bundle optimization.

## Acceptance mapping

#202 is complete when:

- pointer-capable notebook/desktop layouts rest in a compact persistent rail;
- pointer hover intentionally expands labels;
- keyboard focus provides equivalent label/context exposure;
- leaving hover/focus returns the rail to compact state without focus loss;
- normal movement inside the rail does not flicker;
- routine expansion does not repeatedly shift main content;
- selected route remains identifiable in both states;
- existing groups and exactly six destinations remain unchanged;
- no `/outputs` route is introduced;
- touch-oriented/tablet layouts use explicit overlay navigation rather than hover;
- overlay drawer starts closed, opens from Menu, closes on selection, and supports Material backdrop dismissal;
- menu ARIA state reflects the real sidenav state;
- compact destinations retain accessible names;
- focus, contrast, and touch usability remain intact;
- motion respects reduced-motion preferences;
- no auth/session/backend/API/DTO/database/runtime/business behavior changes;
- no new frontend dependency;
- focused shell tests pass;
- `pnpm lint`, `pnpm test`, and `pnpm build` pass.

## Out of scope

Preserve issue boundaries:

- #199 theme/brand redesign;
- #200 workflow guidance;
- #201 reference-code visibility;
- new routes or navigation destinations;
- nested/role-based navigation;
- user-persisted navigation preference;
- general responsive-layout framework;
- mobile-phone-first redesign;
- PWA/offline behavior;
- backend/authentication changes;
- new icon or animation dependencies.