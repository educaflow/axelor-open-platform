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
`save`, `validate`, `close`, `back`, `force-back`, `new`, `delete`, `delete-modal`, `save-modal`

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

## Customizations in this fork

### Built-in actions added

These actions are intercepted client-side (never reach Java), except when used inside `<action-group>` XML where Java routes them back via keyword detection in `ActionGroup.java`.

| Action | Description |
|--------|-------------|
| `back` | Navigate back to previous view (grid). Asks user to confirm if form is dirty. |
| `force-back` | Same as `back` but skips the dirty confirmation. |
| `close` | Close the current popup. |
| `delete` | Delete the current record (same mechanics as the toolbar delete button). |
| `delete-modal` | Delete the record inside a `<panel-related>` popup and close it. Defined in `one-to-many.tsx`. |
| `save-modal` | Save the record inside a `<panel-related>` popup and close it. Runs `onValidate` if defined. Defined in `use-editor.tsx`. |

`delete` is intentionally **not** `#ensureLast` — it can appear anywhere in an action chain.

### New attributes on `<form>`

| Attribute | Type | Description |
|-----------|------|-------------|
| `canBack` | expression | Show/hide the Back button (expression evaluated against record). |
| `canCancel` | expression | Show/hide the Cancel button. |
| `canMore` | expression | Show/hide the More menu. |
| `canBackOnSave` | expression | When `true`, navigates back to the previous view after a successful save. |
| `related` | JSON string | Extra relational fields to fetch alongside the record. Format: `{"field": ["subfield1", "subfield2"]}`. Merged with the view's own `related` map. |
| `onValidate` | action name | Action executed when saving a popup (via `save-modal`). Runs before closing. |
| `validateSignal` | string | Signal name passed to `onValidate` action. |

### New attributes on `<grid>`

| Attribute | Type | Description |
|-----------|------|-------------|
| `newButtonTitle` | string | Replace the `+` icon with a text label on the New button. |
| `canRefresh` | boolean | Show/hide the Refresh button (default `true`). |
| `canAdvanceSearch` | boolean | Show/hide the advanced search bar (default `true`). |
| `allowSearchFields` | boolean | Show/hide the per-column search fields below header row (default `true`). |
| `canViewOnClick` | boolean | Open the detail view on single click (instead of double-click). |
| `canEditOnClick` | boolean | Open the detail view in edit mode on single click. |
| `action` | action name | Action to execute on row double-click (instead of default view). |
| `actionSignal` | string | Signal name sent with the `action` on double-click (defaults to view name). |

`selector="checkbox"` is now required for checkboxes to appear in popup grids (previously always shown).

Grid view param `<view-param name="reload-grid" value="true"/>` forces the grid to reload data each time it is activated.

### New attributes on `<panel-related>`

| Attribute | Type | Description |
|-----------|------|-------------|
| `showFooter` | boolean | Show/hide the Close/OK footer in the popup modal (default `true`). |
| `forceEdit` | boolean | Always open the popup editor in edit mode (default `false`). |
| `newButtonTitle` | string | Replace the `+` icon with a text label on the New button. |

### New attributes on `<button>`

| Attribute | Type | Description |
|-----------|------|-------------|
| `outline` | boolean | Render button with outline style. |
| `size` | string | Button size: `btn-lg` or `btn-sm`. |
| `css` | string | Extra CSS class, e.g. `btn-link`. |

### New attributes on `<dashboard>`

| Attribute | Type | Description |
|-----------|------|-------------|
| `searchModel` | string | Model passed as `_model` context when calling `onInit`. Required when `onInit` uses `<action-record>`. |

Dashboard layout changed from drag-and-drop grid to responsive CSS grid (`auto-fit`/`minmax`). A single dashlet expands to fill the full height.

### View params (on `<action-view>`)

| Param | Description |
|-------|-------------|
| `show-toolbar-form` | Hide the toolbar when displaying the form view of this action (default `true`). |
| `show-toolbar-grid` | Hide the toolbar when displaying the grid view of this action (default `true`). |

### Domain model XML extensions

`extra-imports-model` and `extra-code-model` elements can be added inside a `<entity>` to inject raw Java code into the generated model class.

Domain XML files can now be placed in subdirectories under `src/main/resources/domains/` (or `src/main/java/`).

### Service layer

`com.axelor.db.modelservice.ModelService<T>` — new interface between `Resource` and JPA. `DefaultModelService` delegates to the entity's `Repository` **without running Bean Validation** on insert/update/remove. Override by creating a `@Singleton` class implementing `ModelService<YourEntity>` and registering it in a Guice module.

### Behaviour changes from upstream

- **`back` action**: Now asks for confirmation if the form is dirty (upstream did not).
- **Error dialogs**: Size forced to `xl` for all error/alert dialogs.
- **Default error title**: Changed from `"Error"` to `"Validation Error"`.
- **`many-to-one` widget**: View button and magnifier are hidden when `form-view`/`grid-view` attributes are empty strings.
- **Cards view**: Uses CSS Grid (`auto-fit minmax`) instead of fixed-width floats. `width` attribute now means minimum card width in px.
- **Tree view**: Empty cells show `""` instead of `"---"`.
- **`input-config` priority**: Multiple `data-init` folders are executed ordered by `priority` XML attribute (higher = first).
- **File upload**: `target` attribute on `<binary>` field can be a subclass of `MetaFile`; downloads use `inline` content-disposition with the file's MIME type.
- **JS actions**: `executeJs`/`payload` are read from both `data` and `data.values` (allows returning them nested inside `values` from Java).
- **Copyright text**: Rendered as HTML (supports links/markup) in About screen and login page.
- **App header**: Favourites and Messages icons are hidden (`showFav = false`, `showMessages = false` hardcoded in nav-header).
- **Context path `/`**: Tomcat context path set to `""` when configured as `/` to avoid Tomcat warning.
- **`--contextPath` Gradle arg**: `./gradlew run --port 8080 --contextPath /myapp` now supported.

## Files modified by this fork

All EduFlow customizations relative to upstream Axelor. These are the files most likely to need changes when adding new features.

### Action system
- `axelor-front/src/view-containers/action/executor.ts` — built-in action interception + `#handle()` for Java responses
- `axelor-front/src/view-containers/action/handler.ts` — `DefaultActionHandler` base class
- `axelor-front/src/view-containers/action/types.ts` — `ActionHandler` interface
- `axelor-front/src/views/form/builder/scope.ts` — `FormActionHandler` with all custom handlers
- `axelor-front/src/services/client/meta.ts` — `ActionResult` type (add new `boolean` fields here)
- `axelor-core/src/main/java/com/axelor/meta/schema/actions/ActionGroup.java` — built-in keyword detection

### Popup / modal
- `axelor-front/src/view-containers/view-popup/view-popup.tsx` — `ViewClosure`, close mechanics, footer
- `axelor-front/src/view-containers/view-popup/handler.ts` — `PopupHandler` type (`close`, `directClose`)
- `axelor-front/src/views/form/builder/form-editors.tsx` — nested form handler delegation
- `axelor-front/src/hooks/use-relation/use-editor.tsx` — `useEditor()`, `SaveModalHandler`, `DeleteModalHandler`, popup validation (`onValidate`/`validateSignal`)

### Form view
- `axelor-front/src/views/form/form.tsx` — form toolbar, `back`/`forceBack` handlers, `canBackOnSave`, `show-toolbar-form`, `related` attribute
- `axelor-front/src/views/form/builder/form-widget.tsx` — `canViewOnClick`, `canEditOnClick` on widgets
- `axelor-front/src/views/form/builder/types.ts` — schema type extensions
- `axelor-front/src/views/form/widgets/button/button.tsx` — `outline`, `size`, `css` button attributes
- `axelor-front/src/views/form/widgets/many-to-one/many-to-one.tsx` — hide icon/link when no `form-view`/`grid-view`
- `axelor-front/src/views/form/widgets/one-to-many/one-to-many.tsx` — `panel-related` (`showFooter`, `forceEdit`, `newButtonTitle`) + checkbox visibility in modal grids

### Grid view
- `axelor-front/src/views/grid/grid.tsx` — `reload-grid` param, checkbox visibility, `show-toolbar-grid`
- `axelor-front/src/views/grid/builder/grid.tsx` — `canViewOnClick`, `action` on double-click, `canAdvanceSearch`, `allowSearchFields`

### Other views
- `axelor-front/src/views/cards/cards.tsx`, `card.tsx` — CSS Grid layout, no border
- `axelor-front/src/views/dashboard/dashboard.tsx` — responsive layout, `searchModel` support

### Java view schema (always update XSD alongside Java)
- `axelor-core/src/main/java/com/axelor/meta/schema/views/FormView.java` — `canBack`, `canCancel`, `canMore`, `canBackOnSave`, `related`, `onValidate`/`validateSignal` attributes
- `axelor-core/src/main/java/com/axelor/meta/schema/views/GridView.java` — `newButtonTitle`, `canRefresh`, `canAdvanceSearch`, `canViewOnClick`, `action`, `allowSearchFields`
- `axelor-core/src/main/java/com/axelor/meta/schema/views/PanelRelated.java` — `showFooter`, `forceEdit`, `newButtonTitle`
- `axelor-core/src/main/java/com/axelor/meta/schema/views/Button.java` — `outline`, `size`, `css`
- `axelor-core/src/main/resources/object-views.xsd` — XSD for all of the above

### Frontend meta types (view schema → TypeScript)
- `axelor-front/src/services/client/meta.types.ts` — mirrors Java view schema attributes in TypeScript

### Service layer (Java)
- `axelor-core/src/main/java/com/axelor/db/modelservice/ModelService.java` — interface
- `axelor-core/src/main/java/com/axelor/db/modelservice/DefaultModelService.java` — implementation (no validation on insert/update/remove)
- `axelor-core/src/main/java/com/axelor/db/modelservice/ModelServiceFactory.java` — factory
- `axelor-core/src/main/java/com/axelor/rpc/Resource.java` — REST resource (uses ModelService)
- `axelor-web/src/main/java/com/axelor/web/service/RestService.java` — REST endpoints

### Infrastructure / misc
- `axelor-front/src/App.tsx` — mounts EduFlow HTML injector
- `axelor-front/src/hooks/use-tabs/use-tabs.ts` — `show-toolbar-form`/`show-toolbar-grid` tab params
- `axelor-front/src/hooks/use-app-theme/themes/default.json`, `dark.json` — EduFlow colour theme
- `axelor-core/src/main/java/com/axelor/meta/loader/ViewLoader.java` — logs XML filename on load error
