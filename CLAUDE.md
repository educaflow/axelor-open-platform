# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this repo is

Fork of **Axelor Open Platform** (v8.1.1) — a Java/React framework for business applications. This fork contains EduFlow-specific customizations layered on top of the upstream framework.

## Commands

### Backend (Gradle)
```bash
./install.sh                            # clean + build + publishToMavenLocal
```

## Module layout

```
axelor-common/     # Shared Java utilities
axelor-core/       # ORM, auth, metadata engine, actions, views
axelor-web/        # REST API layer, servlets, WebSockets
axelor-front/      # React/TypeScript SPA (built separately, embedded into axelor-web JAR)
axelor-gradle/     # Custom Gradle plugins
axelor-test/       # Test infrastructure
axelor-tomcat/     # Embedded Tomcat config
axelor-tools/      # Dev utilities
```

The frontend build output (`axelor-front/dist/`) is copied into `axelor-web` via `processResources`. The full build chain is: `./gradlew build` triggers `buildFront` (Node/pnpm) automatically.

## Frontend architecture

**Stack**: React 19, TypeScript (strict), Vite, Jotai (atoms), pnpm

Key source tree under `axelor-front/src/`:
- `view-containers/action/` — Action execution engine. Central to how buttons/events work.
- `views/form/` — Form view + all widget types
- `views/grid/` — Grid/list view
- `hooks/use-relation/` — Popup editor logic (use-editor.tsx is critical for o2m/m2m popups)
- `view-containers/view-popup/` — Popup dialog orchestration (ViewClosure, Footer, Header)
- `services/client/meta.ts` — TypeScript types for server action results (`ActionResult`)

## Action system (critical to understand)

Actions flow: XML button `onClick="action-name"` → `executor.ts` → Java (`ws/action`) → `executor.ts #handle()` → handler method.

**Built-in actions** (intercepted client-side, never sent to the server):
`save`, `validate`, `close`, `force-close`, `back`, `force-back`, `new`, `delete`, `delete-modal`, `save-modal`

### Adding a new built-in action

Every new built-in action requires changes in **7 places**:

| File | What to add |
|------|-------------|
| `axelor-front/src/view-containers/action/types.ts` | `methodName(): Promise<void>` + `setMethodNameHandler(h: () => Promise<void>): void` in `ActionHandler` interface |
| `axelor-front/src/view-containers/action/handler.ts` | No-op implementations in `DefaultActionHandler` |
| `axelor-front/src/views/form/builder/scope.ts` | `#methodNameHandler`, `setMethodNameHandler()`, `async methodName()` in `FormActionHandler` |
| `axelor-front/src/view-containers/action/executor.ts` | (3 spots) `#ensureLast`, `if (action === "name")`, `if (data.signal === "name")`, `if (data.methodName)` in `#handle()` |
| `axelor-front/src/services/client/meta.ts` | `methodName?: boolean` in `ActionResult` |
| `axelor-front/src/view-containers/view-popup/view-popup.tsx` | Register handler in `ViewClosure` via `setMethodNameHandler` (for popup-specific behavior) |
| `axelor-core/src/main/java/com/axelor/meta/schema/actions/ActionGroup.java` | Add `"action-name".equals(name)` in **both** keyword blocks (~line 189 and ~line 304) |

For actions that need to propagate through nested (editor) forms, also update:
`axelor-front/src/views/form/builder/form-editors.tsx` — delegate `setMethodNameHandler` to `parentHandler`.

### Popup close mechanics (`view-popup.tsx` → `ViewClosure`)

The popup has two distinct close paths registered on `PopupHandler`:
- `popup.close` = `handleClose` → calls `dialogs.confirmDirty` (asks user if dirty)
- `popup.directClose` = `doClose` → closes immediately, no confirmation

`setCloseHandler` (used by `close` action) registers `doClose` with the record.
`setForceCloseHandler` (used by `force-close` action) registers `doClose` immediately without record.

## Backend — metadata/action engine

Key Java packages in `axelor-core`:
- `com.axelor.meta.schema.actions` — Action classes (`ActionGroup`, `ActionMethod`, `ActionRecord`, …)
- `com.axelor.meta.schema.views` — View schema (XML → Java objects via JAXB)
- `com.axelor.meta.loader` — Loads XML view/action definitions at startup
- `com.axelor.rpc` — Handles `ws/action` REST endpoint

`ActionGroup.java` processes `<action-group>` XML. It runs each step sequentially; when it hits a built-in keyword it stops, returns `{"keyword": true, "pending": "remaining-steps"}` to the client, which then executes the built-in locally and resumes with `pending`.

## View XML schema

When adding new attributes to view XML elements (e.g., on `<panel-related>`), also update:
`axelor-core/src/main/resources/object-views.xsd` — attribute name must be exact kebab-case match.

## EduFlow-specific additions

### Servlet filter (backend)
`axelor-web/src/main/java/com/axelor/web/servlet/EduFlowIncludeFilter.java` — serves static HTML fragment files from the app's `includes/` directory:
- `head.start.include.html`, `head.end.include.html`
- `body.start.include.html`, `body.end.include.html`

### Frontend injector
`axelor-front/src/utils/eduFlowHTMLInjector.ts` — fetches those fragments and injects them into the DOM. Called from `App.tsx` on mount. Handles script execution and DOM insertion.
