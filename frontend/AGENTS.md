# Frontend agent instructions

Apply the root `AGENTS.md` first. This file contains Angular-specific guidance only.

## Stack and architecture

- Use Angular 22, TypeScript, Angular Material/CDK, Angular Router, native `HttpClient`, Signals, RxJS when justified, and Vitest.
- Use standalone architecture. Do not set `standalone: true` or `ChangeDetectionStrategy.OnPush` explicitly: both are Angular 22 defaults.
- Feature routes may use lazy loading.
- Keep HTTP and data-access logic outside presentation components. Business and inventory rules remain backend-authoritative.
- Keep components small and focused. Prefer `input()`, `output()`, `model()`, and `inject()` over the corresponding decorators or constructor injection.
- Prefer inline templates for small components and use `linkedSignal()` only when multiple reactive sources must stay synchronized.
- Use `host` metadata instead of `@HostBinding` or `@HostListener`.
- Use `NgOptimizedImage` for static images when applicable; it does not support inline base64 images.

## TypeScript, templates, and state

- Use strict typing, infer obvious types, and prefer `unknown` to `any`.
- Use Signals for appropriate local or derived state and `computed()` for derived values. Keep transformations pure; use `set()` or `update()`, never `mutate()`.
- Use RxJS for asynchronous composition when justified, and the async pipe for observable templates.
- Do not duplicate backend-authoritative inventory logic in frontend state or apply incorrect optimistic state to stock-changing operations.
- Prefer native control flow (`@if`, `@for`, `@switch`), `class` bindings over `ngClass`, and `style` bindings over `ngStyle`.
- Keep templates simple. Do not assume globals such as `new Date()` are available in templates.
- For external templates or styles, use paths relative to the component TypeScript file.
- Keep services single-purpose. For new singleton services, use `providedIn: 'root'` and prefer `@Service` over `@Injectable({ providedIn: 'root' })`.

## Forms

- Reactive Forms are the project default.
- Do not introduce Signal Forms unless the current issue explicitly approves or adopts them.
- Do not opportunistically migrate existing forms.
- Do not use Template-driven Forms unless there is a concrete approved reason.

## UX, accessibility, and language

- Make loading, error, and empty states explicit.
- Meet WCAG AA and AXE requirements, including focus management, contrast, and appropriate ARIA.
- All operator-facing UI copy, accessibility text, user-visible metadata, and frontend errors are pt-BR.
- Code, identifiers, routes, DTOs, API contracts, wire values, comments, and test descriptions remain English.

## Dependencies and validation

- Do not add dependencies that duplicate Angular capabilities. In particular, do not add NgRx, Axios, external forms or routing libraries, Tailwind alongside Angular Material, or `@angular/animations` for new code. Prefer CSS and `animate.enter` / `animate.leave` for new animations.
- Do not force `pnpm test -- --run`; use the configured `pnpm test` script.
- Run `pnpm lint`, `pnpm test`, and `pnpm build` for frontend changes.
