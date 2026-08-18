# JSF EL Navigation 1.9.0

Version 1.9 adds a built-in **WebSphere Logs** view. `Ctrl+Alt+L` toggles
`SystemOut.log` / `SystemErr.log` inside Eclipse with background auto-refresh.
Custom WebSphere profile paths and an explicit log-directory override are
supported.


Version 1.8 adds a persistent **JSF Flow Explorer** for layered Java EE
development while retaining everything from 1.7.

The Flow Explorer supports named task flows, automatic/manual capture,
View/Controller/Bean/ISP/Service/Persistence grouping, Open All, and Focus Tabs.

Default Flow shortcuts:

- `Ctrl+Alt+F` — Add Current File to Flow
- `Ctrl+Shift+Alt+F` — Show JSF Flow Explorer
- `Ctrl+Shift+Alt+N` — New Development Flow

The complete feature and shortcut reference is in `CHEAT-SHEET.md`.


Version 1.7 keeps all navigation, persistent indexing, diagnostics, PrimeFaces,
RichFaces, Facelets, JPA and caller/callee features from 1.6 and adds
development-only WebSphere hot sync.

## WebSphere Hot Sync

The plug-in can copy supported source web resources directly into the exploded
WebSphere deployment after a normal Eclipse save.

Supported resource types are configurable:

- `.xhtml`, `.html`, `.htm`
- `.js`
- `.css`
- `.properties` (disabled by default)

It intentionally does **not** copy Java classes, JARs, `web.xml`,
`persistence.xml`, EJB metadata, or deployment descriptors.

### Configure

Open:

`Window -> Preferences -> JSF / Java Navigation -> WebSphere Hot Sync`

Recommended configuration:

- Enable WebSphere hot sync
- Enable automatic sync after save if desired
- WebSphere profile directory, for example:
  `F:\IBM\WebSphere\AppServer\profiles\AppSrv01`
- Deployed web module root, for example:
  `...\installedApps\<cell>\<application>.ear\<module>.war`
- Project-relative source web root, for example:
  `WebContent`
  or
  `src/main/webapp`

The deployed web-module root is the safest configuration because it removes
ambiguity. If it is left empty, the manual sync command can scan the configured
profile's `installedApps` tree for exploded web-module directories and ask you
to choose one.

### Manual commands

- **Ctrl+Alt+S** — Sync Current Resource to WebSphere
- **Ctrl+Alt+D** — Open WebSphere Deployed Copy

Manual sync works even if automatic sync is disabled.

### Automatic save sync

When both **Enable WebSphere hot sync** and **Automatically sync after save**
are enabled, a POST_CHANGE content delta for a supported file schedules a small
background copy job.

Example:

```text
Project:
WebContent/faces/antrag/detail.xhtml

deployed web root:
...\installedApps\MyCell\MyApp.ear\MyWeb.war

target:
...\installedApps\MyCell\MyApp.ear\MyWeb.war\faces\antrag\detail.xhtml
```

The source file is always the authoritative file. Hot sync only overwrites the
deployed copy. Removing a source file does not automatically delete the
deployed copy.

### Safety

This feature is intended for a local/development WebSphere profile only.
It deliberately syncs view/static resources, not Java bytecode or application
configuration. Use the normal application deployment for changes that require
classloading, metadata, persistence, EJB or descriptor updates.

If a copied XHTML file does not immediately appear in the browser, the
WebSphere/JSF runtime may still be configured to cache Facelets/JSP resources.
The plug-in only performs the filesystem sync; it does not change server
reload/cache settings.

## Existing productivity features

The release still includes:

- JSF EL Ctrl+Click and `ui:param` aliases
- PrimeFaces/RichFaces component-ID navigation
- `widgetVar` / `PF(...)` navigation
- Facelets include/template/composite navigation
- resource-bundle navigation/diagnostics
- Java -> XHTML PrimeFaces/RichFaces update navigation
- Java `executeScript(...)` -> controller-associated JavaScript definitions
- JPQL/JPA field navigation and mapping hover
- named-query, role and navigation-outcome navigation
- persistent bean/web/view indexes with incremental updates
- JSF diagnostics and EL completion
- backing-bean/page graph/controller-page navigation
- Go to Caller/Callee and single-call-chain traversal

## Shortcuts

- Ctrl+Alt+Space — JSF EL Complete
- Ctrl+Alt+B — Open Backing Bean
- Ctrl+Alt+P — Open Controller Pages
- Ctrl+Alt+R — Find JSF References
- Ctrl+Alt+G — Show JSF Page Graph
- Ctrl+Alt+I — Show JSF Context Info
- Ctrl+Alt+S — Sync Current Resource to WebSphere
- Ctrl+Alt+D — Open WebSphere Deployed Copy
- Ctrl+Alt+Page Up — Go to Caller
- Ctrl+Alt+Page Down — Go to Callee
- Ctrl+Shift+Alt+Page Up — Follow Single Caller Chain
- Ctrl+Shift+Alt+Page Down — Follow Single Callee Chain

All shortcuts can be changed under:

`Window -> Preferences -> General -> Keys -> JSF / Java Navigation`
