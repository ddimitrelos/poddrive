---
project_name: 'BMAD Test'
user_name: 'Dimitrios.dimitrelos'
date: '2026-03-19'
sections_completed: ['technology_stack', 'language_rules', 'framework_rules', 'testing_rules', 'quality_rules', 'workflow_rules', 'anti_patterns']
status: 'complete'
rule_count: 38
optimized_for_llm: true
---

# Project Context for AI Agents

_This file contains critical rules and patterns that AI agents must follow when implementing code in this project. Focus on unobvious details that agents might otherwise miss._

---

## Technology Stack & Versions

- **Next.js** `16.2.0` — App Router, Turbopack dev server. Ships breaking changes vs prior versions — always consult `node_modules/next/dist/docs/` before writing code.
- **React / React DOM** `19.2.4`
- **TypeScript** `^5` — strict mode enabled
- **Tailwind CSS** `^4` — via `@tailwindcss/postcss`
- **ESLint** `^9` — `eslint-config-next` core-web-vitals + typescript presets
- **Node.js types** `^20`
- **No** testing framework, icon library, state management library, or HTTP client installed

> ⚠️ `cacheComponents` is NOT set in `next.config.ts` → the **old fetch-level caching model** is active. Do NOT use the new `use cache` directive.
> ⚠️ Tailwind v4 has **no `tailwind.config.js`** — all configuration lives in CSS via `@theme inline`.

---

## Critical Implementation Rules

### Language-Specific Rules

- **Strict TypeScript** — `strict: true`; no implicit `any`; use `import type { Foo }` for type-only imports (required by `isolatedModules: true`)
- **Path alias** — always use `@/*` for imports from `src/*` (e.g. `import Foo from "@/app/Foo"`)
- **`isolatedModules: true`** — every `.ts`/`.tsx` file must contain at least one `import` or `export`
- **`target: ES2017`** — do not use syntax that does not transpile to ES2017
- **`moduleResolution: "bundler"`** — do not use `require()` or CommonJS patterns
- **Default exports** for page and layout components; named exports for utilities and helpers
- **Error handling** — wrap all external `fetch` calls in `try/catch`; return empty/fallback values on failure; empty `catch {}` is allowed only for non-critical parsing failures and must include an explanatory comment
- **No new npm packages** without explicit user approval — zero extra runtime dependencies is intentional

### Framework-Specific Rules

**Next.js App Router:**
- All components are **Server Components by default** — only add `"use client"` when using hooks, browser APIs, or event handlers; it must be the **first line** of the file
- Pages live at `src/app/<route>/page.tsx`; do NOT use `getServerSideProps`/`getStaticProps` (Pages Router patterns)
- Use `export const metadata: Metadata` for SEO — never set `<title>` or `<meta>` in JSX manually
- External image domains must be added to `next.config.ts` `images.remotePatterns` (currently only `images.unsplash.com`)
- Fetch caching: pass `next: { revalidate: N }` per `fetch()` call — do NOT use `export const revalidate` at route level or the `"use cache"` directive

**React:**
- `Math.random()` / `Date.now()` in Server Component render output causes **hydration mismatch** — initialise inside `useState(() => ...)` in Client Components only
- Hooks only in `"use client"` components
- `key` props must be stable and unique — do not use array index alone when items can reorder
- Sub-components may be co-located in the same file when small (see `movies/page.tsx`)

**File & Naming Conventions:**
- PascalCase filenames for components (`SeaViewer.tsx`); lowercase/kebab-case for route segments (`movies/`)
- Fonts loaded via `next/font/google` in `layout.tsx` as CSS variables — reference via `var(--font-geist-sans)`, not hardcoded names

### Testing Rules

- **No testing framework is installed** — do NOT generate `*.test.ts` / `*.spec.tsx` files as part of feature implementation unless explicitly asked
- Do not assume Jest, Vitest, or `@testing-library/react` are available — they are not
- If tests are requested, confirm the framework choice and install it before writing any test code
- No coverage thresholds or CI test gates are configured

### Code Quality & Style Rules

**ESLint:**
- Flat config format (`eslint.config.mjs`) — do NOT create legacy `.eslintrc.*` files
- Presets: `eslint-config-next/core-web-vitals` + `eslint-config-next/typescript`
- Run via `npm run lint` — no auto-fix flag set by default

**Tailwind CSS v4:**
- Entry point is `@import "tailwindcss"` only — do NOT use `@tailwind base/components/utilities` directives
- Custom design tokens go inside `@theme inline { }` in `globals.css` — not in any config file
- Established dark-theme palette: `bg-zinc-950` (page), `bg-zinc-900` (card), `bg-zinc-800` (card hover), `text-white` (headings), `text-zinc-300` (body), `text-zinc-500` (muted), `rounded-2xl` (containers), `shadow-2xl` (elevation)
- Standard responsive grid: `grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4`

**Code Organisation:**
- No `/components` directory exists — co-locate small sub-components in the same file as their page; do not create a `/components` folder unless explicitly directed
- Section dividers: `// ─── Label ───────` style (as in `movies/page.tsx`)
- Fonts (`--font-geist-sans`, `--font-geist-mono`) are already applied on `<html>` in root layout — do not re-apply in child components

### Development Workflow Rules

- **Dev server**: `npm run dev` on port **3000** — config in `.claude/launch.json`; always start via `preview_start` tool, never raw `bash`
- **Build**: `npm run build` then `npm start` for production; `npm run lint` for linting
- **Do not hardcode ports** other than 3000 in application code
- **`next.config.ts` is TypeScript** — keep it as `.ts`, never rename to `.js`
- **No `.env` files** exist or are required currently — do not create them speculatively
- **BMAD system files** (`_bmad/`, `.claude/skills/`) must not be modified by implementation agents
- **Planning artifacts** go to `_bmad-output/`; project knowledge base is `docs/` (currently empty)
- **Windows/Git Bash**: host is Windows; shell scripts use Unix paths (`/c/Users/...`)

### Critical Don't-Miss Rules

**Anti-Patterns — Never Do These:**
- ❌ `Math.random()` / `Date.now()` in Server Component render output → **hydration mismatch**; always initialise inside `useState(() => ...)` in a Client Component
- ❌ `"use cache"` directive or `export const revalidate` at route level → invalid with current config; use `next: { revalidate: N }` per `fetch()` call
- ❌ Creating `tailwind.config.js` → Tailwind v4 uses CSS-only config; a config file will break the build
- ❌ `@tailwind base` / `@tailwind utilities` directives → v3 syntax; not valid in v4
- ❌ Installing npm packages without explicit user approval → zero-dep policy is intentional
- ❌ Creating `.eslintrc.*` files → project uses flat config `eslint.config.mjs`
- ❌ `getServerSideProps` / `getStaticProps` → Pages Router only; not valid in App Router

**Edge Cases:**
- External fetch responses over **2MB** are silently skipped by Next.js fetch cache — log a warning but do not treat as an error
- YouTube `listType=search` embed is non-functional (deprecated) — always use explicit video IDs
- `next/image` remote domains must be in `next.config.ts` `images.remotePatterns` — omission causes build errors in production

**Security:**
- External HTML scraping is server-side only — never forward raw third-party HTML to the client

**Performance:**
- Use `Promise.all` for parallel fan-out fetches — never `await` in a sequential loop over multiple URLs

---

## Usage Guidelines

**For AI Agents:**
- Read this file before implementing any code
- Follow ALL rules exactly as documented
- When in doubt, prefer the more restrictive option
- Update this file if new patterns emerge

**For Humans:**
- Keep this file lean and focused on agent needs
- Update when technology stack changes
- Review quarterly for outdated rules
- Remove rules that become obvious over time

_Last Updated: 2026-03-19_
