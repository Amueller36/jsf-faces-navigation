# JSF EL Navigation 1.12.7

Version 1.12.7 groups automatically discovered impacted tests by the exact
production code that caused them to be discovered.

The `Tests` tree is now hierarchical:

```text
Tests
  Impacted by AumiAntragBean.java
    save(...)
      [DIRECT] AumiAntragBeanTest.java
      [2 calls away] AntragServiceTest.java
    load(...)
      [3 calls away] NavigationTest.java
  Impacted by DetailsichtAntraegeController.java
    speichernUndNavigieren(...)
      [2 calls away] NavigationTest.java
  Other tests
    ManuallyAddedTest.java
```

A test may intentionally appear under more than one changed method/file when
multiple edits affect it. Each relationship keeps its own shortest caller
distance. Clicking a changed-file group opens that source file, clicking a
method group opens that exact Java method, and clicking a test opens the test.

Impact relationships are persisted in Flow Explorer state format v4. Existing
v1-v3 flow state remains readable; older impact-depth-only entries appear under
`Other tests` until they are rediscovered with source/method information.

Version 1.12.6 makes impacted tests visually easier to understand in the
Flow Explorer.

- impacted-test caller search now stops at 5 caller levels instead of 8
- each impacted test stores its shortest discovered caller distance
- direct callers are labeled `[DIRECT]`
- indirect callers are labeled `[2 calls away]`, `[3 calls away]`, etc.
- impacted tests are sorted nearest-first inside the `Tests` category
- the Tests header shows `[nearest first]` whenever impact-distance data exists
- caller distance is persisted in flow state format v3; existing v1/v2 flows
  remain readable and simply start with no impact-distance metadata

Version 1.12.5 adds test-impact assistance and Eclipse problem visibility to
the JSF Flow Explorer.

- Flow entries with an Eclipse error are shown in red and prefixed with
  `[ERROR]`; category headers also show the number of files with errors.
- `Auto tests` watches methods edited in saved Java files and follows the Java
  caller hierarchy (up to 8 levels) to find JUnit tests that directly or
  indirectly call those methods. Newly found test files are added to the
  current flow automatically.
- `Run Unit Tests` runs the safe JUnit test classes currently in the flow.
  Arquillian/generic integration tests and JPA/persistence tests are
  deliberately excluded from this bulk action.
- Auto-test discovery is enabled by default and can be toggled independently
  from normal Auto file capture.

Version 1.12.4 improves the JSF Flow Explorer:

- a single click on a file opens/activates it in the editor
- `Ctrl+MouseWheel` over the flow tree changes its text size
- the file belonging to the currently active editor is automatically selected
  and revealed when it is part of the current flow
- a dedicated `TO` category was added for `*TO`, `*DTO`, `*Dto` and
  `*TransferObject` Java classes
- persisted flow entries are reclassified on startup so existing TO files move
  out of `Other` automatically

Version 1.12.3 fixes automatic web-resource hot sync so XHTML/JS/CSS WAR
resolution no longer depends on the opt-in Smart Java/Class Deploy checkbox or
a manually configured deployed web-module root. The configured root is now an
optional override; otherwise the plug-in matches the current resource path
against exploded WARs under the configured WebSphere profile, remembers the
source-root -> WAR mapping, and reuses it on later saves.

Version 1.12.2 makes the XHTML shortcuts independent of Eclipse/WTP key-binding
conflicts. A small UI-thread shortcut bridge intercepts `Ctrl+Alt+H` and
`Ctrl+/` only while the WTP HTML/Facelets `StructuredTextEditor` is active,
then executes the plug-in commands directly.

It also refreshes existing persistent JSF diagnostic markers once at startup.
This removes stale combined-reference warnings created by older parser versions,
then revalidates only the XHTML files that previously had plug-in warnings.

Version 1.12.1 fixes the two new XHTML hotkeys for the actual WTP HTML/Facelets
`StructuredTextEditor` context and corrects comma-separated JSF/PrimeFaces
component-reference parsing such as `process=":form,@form,panel"`.

The JSF view-symbol cache was bumped to v2 so stale component-reference warnings
created by the old tokenizer are discarded automatically after upgrading.

Version 1.12 adds context help for PrimeFaces/RichFaces/JSF components and
attributes plus an XHTML-aware `Ctrl+/` toggle-comment command.

`Ctrl+Alt+H` opens a resizable help window for the tag/attribute at the caret.
The help combines the project's own taglib metadata (type, required flag and
library description where available) with practical explanations and generated
usage examples.

`Ctrl+/` is scoped to the Facelets/XHTML editor context and toggles XML comments
for the selected lines/current line without stealing the Java editor's comment
shortcut.

Version 1.11 adds XHTML component and attribute completion to the existing
`Ctrl+Alt+Space` command. It completes PrimeFaces, RichFaces/A4J, standard JSF
`h:`/`f:`, and Facelets `ui:` tags. Where available, the plug-in reads the
actual Facelets tag-library metadata from the project's installed JARs, so the
suggestions follow the PrimeFaces/RichFaces version used by the project rather
than assuming a current library version.

Version 1.10.5 fixes smart XHTML manual sync, PrimeFaces autocomplete local
variables, and log-view zoom/filter behavior. Ctrl+Alt+S now lets the smart WAR
resolver run before any legacy deployment chooser. `p:autoComplete var` values
are treated as local variables and their type is inferred from `completeMethod`
when possible. The log filter row is permanently visible, and font zoom now
replaces SWT fonts safely without disposing a font still used by StyledText.

Version 1.10.4 makes the JSF Flow Explorer responsive when docked in a
narrow Eclipse view. Flow selection, automatic capture and actions are split
across compact rows so buttons no longer disappear off the right edge. The
selected Flow entry can also be removed with the `Delete` key.

Version 1.10.3 improves the WebSphere Logs view with line filtering, font zoom
controls and scroll-aware auto-scroll. Scrolling upward pauses auto-follow so
refreshes keep the same place; scrolling back to the bottom automatically
reenables following. The JSF Page / Controller Graph is now a normal resizable,
maximizable dialog.

Version 1.10.2 adds visible Smart Deploy progress and faster log pickup after a deploy.
The WebSphere Logs view now shows the current deploy phase and percentage, while
the same deploy also appears in Eclipse's standard Progress UI. During and just
after deployment, log refresh temporarily accelerates so WebSphere reload/startup
messages appear with less delay.

Version 1.10.1 changes the WebSphere log styling to be theme-friendly:
normal/INFO text uses the Eclipse theme foreground, errors and warnings use
font emphasis instead of neon line colors, and clickable stack traces use
underline without forcing bright blue text.

Version 1.10 adds opt-in **Smart WebSphere Deploy** across multiple installed
EARs. It recognizes exploded WAR classes, top-level/EAR-lib JAR classes, and
web-resource module mappings. JAR-backed classes use WebSphere single-file
`AdminApp.update` operations through `wsadmin` rather than rebuilding the
entire EAR.

The WebSphere Logs view now has `Ctrl+F` search, severity/stack-trace
highlighting, clickable Java stack frames, and a Smart Deploy output tab.

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
