# JSF / Java Navigation Plug-in 1.12.7 — Full Cheat Sheet

## Hotkeys

| Action | Windows / Linux | macOS |
|---|---|---|
| Go to direct caller | `Ctrl+Alt+Page Up` | `Cmd+Option+Page Up` |
| Go to direct callee | `Ctrl+Alt+Page Down` | `Cmd+Option+Page Down` |
| Follow single-caller chain | `Ctrl+Shift+Alt+Page Up` | `Cmd+Shift+Option+Page Up` |
| Follow single-callee chain | `Ctrl+Shift+Alt+Page Down` | `Cmd+Shift+Option+Page Down` |
| JSF EL completion | `Ctrl+Alt+Space` | `Cmd+Option+Space` |
| JSF component / attribute help | `Ctrl+Alt+H` | `Cmd+Option+H` |
| Toggle XHTML comment | `Ctrl+/` | `Cmd+/` |
| Open backing bean from XHTML | `Ctrl+Alt+B` | `Cmd+Option+B` |
| Open XHTML pages using controller | `Ctrl+Alt+P` | `Cmd+Option+P` |
| Find JSF references to `id`/`widgetVar` | `Ctrl+Alt+R` | `Cmd+Option+R` |
| Show JSF page/controller graph | `Ctrl+Alt+G` | `Cmd+Option+G` |
| Show JSF context / Ajax info | `Ctrl+Alt+I` | `Cmd+Option+I` |
| Sync current web resource to WebSphere | `Ctrl+Alt+S` | `Cmd+Option+S` |
| Open WebSphere deployed copy | `Ctrl+Alt+D` | `Cmd+Option+D` |
| Toggle WebSphere logs | `Ctrl+Alt+L` | `Cmd+Option+L` |
| Add current file to active Flow | `Ctrl+Alt+F` | `Cmd+Option+F` |
| Show JSF Flow Explorer | `Ctrl+Shift+Alt+F` | `Cmd+Shift+Option+F` |
| Create new Development Flow | `Ctrl+Shift+Alt+N` | `Cmd+Shift+Option+N` |

All key bindings are ordinary Eclipse commands. Change them under:

`Window -> Preferences -> General -> Keys`

Search for category:

`JSF / Java Navigation`

---

## Ctrl+Click: JSF / Facelets / PrimeFaces / RichFaces

### EL to Java

```xhtml
#{aumiAntragDetail.save}
#{bean.user.address.street}
```

Ctrl+Click navigates through the bean and Java property/method chain.

Supported:

- managed beans / CDI named beans
- inherited properties
- getter/field resolution
- method calls in richer EL expressions
- `ui:param` aliases

Example:

```xhtml
<ui:param name="controller" value="#{aumiAntragDetail}" />
#{controller.save}
```

`controller` is resolved through `aumiAntragDetail`.

### PrimeFaces / RichFaces component IDs

Ctrl+Click IDs in:

```xhtml
update="resultPanel"
process="@this resultPanel"
render="resultPanel"
reRender="resultPanel"
execute="@this resultPanel"
for="username"
```

to the matching:

```xhtml
id="resultPanel"
id="username"
```

Same-file matches are preferred before project-wide matches.

### widgetVar

```javascript
PF('myDialog')
```

Ctrl+Click `myDialog` to:

```xhtml
widgetVar="myDialog"
```

### Facelets files

Ctrl+Click:

```xhtml
<ui:include src="/pages/foo.xhtml" />
<ui:composition template="/templates/main.xhtml" />
<ui:decorate template="/templates/popup.xhtml" />
```

### Static navigation outcomes

Ctrl+Click static outcomes where resolvable:

```xhtml
outcome="details"
action="details"
```

### Composite components

```xhtml
<app:address value="#{bean.address}" />
```

Ctrl+Click the custom component to:

```text
/resources/app/address.xhtml
```

Ctrl+Click a composite attribute such as `value` to the matching:

```xhtml
<cc:attribute name="value" />
```

### Resource bundles

```xhtml
#{msg['user.name']}
```

Ctrl+Click `user.name` to the matching `.properties` key.

Supports resource bundles from:

- `<f:loadBundle ...>`
- `faces-config.xml`

### Security roles

Role strings such as:

```xhtml
#{request.isUserInRole('ADMIN')}
```

can navigate to project role usages/definitions.

---

## Ctrl+Click: Java

### PrimeFaces / RichFaces UI updates

```java
PrimeFaces.current().ajax().update("form:resultPanel");
```

Ctrl+Click the component ID to the matching XHTML component.

Common RichFaces server-side partial-update APIs are supported as well.

### Java -> JavaScript

```java
PrimeFaces.current().executeScript("refreshPage()");
```

and legacy RichFaces/PrimeFaces-style script execution can navigate to associated
JavaScript definitions, preferring pages that use the current controller.

### JPQL / JPA

For:

```java
"SELECT user FROM " + UserEntity.class.getName()
    + " user WHERE user.organisationseinheit = :bezirk"
```

Ctrl+Click:

```text
user
```

to the entity, or:

```text
organisationseinheit
```

to the mapped Java field/getter.

Hover can show:

- Java type
- field/getter declaration
- JPA annotations
- `@Column`
- `@Enumerated`
- relation annotations
- source/Javadoc comment

### Named queries

```java
em.createNamedQuery("User.findActive");
```

Ctrl+Click to matching:

```java
@NamedQuery(name = "User.findActive", ...)
```

or XML named-query declaration.

### Role strings

Role strings in common Java security checks/annotations can navigate to matching
project usages/definitions.

---

## Caller / Callee navigation

Put the caret anywhere inside a Java method.

### Go to Caller

`Ctrl+Alt+Page Up`

- one caller -> jump immediately
- multiple callers -> chooser
- includes Java references
- includes JSF EL callers where resolvable

### Go to Callee

`Ctrl+Alt+Page Down`

- one project callee -> jump immediately
- multiple project callees -> chooser
- library/JDK calls are ignored for project-callee navigation

### Follow Single Caller Chain

`Ctrl+Shift+Alt+Page Up`

Example:

```text
Repository.method()
    ↑
Service.method()
    ↑
Controller.method()
    ↑
XHTML #{controller.method}
```

The plug-in keeps moving upward while exactly one caller exists.

### Follow Single Callee Chain

`Ctrl+Shift+Alt+Page Down`

Example:

```text
Controller.load()
    ↓
Bean.load()
    ↓
ISP.load()
    ↓
Repository.load()
```

The plug-in keeps moving downward while exactly one project callee exists and
stops safely on branching, no destination, library code, or recursion.

---

## JSF EL Completion

Inside:

```xhtml
#{aumiAntragDetail.rep}
```

press:

`Ctrl+Alt+Space`

The plug-in resolves the current bean/property type and shows matching fields,
getter properties and methods.

It also follows chains:

```xhtml
#{bean.user.addr}
```

and understands simple `ui:param` aliases.

---

## JSF diagnostics

Background diagnostics create ordinary Eclipse Problems markers for common
mistakes, including:

- unresolved `update/process/render/reRender/execute/for` IDs
- missing `widgetVar` for `PF(...)`
- missing Facelets include/template
- straightforward unresolved EL bean/property chains
- obvious callback signature mismatches
- missing resource-bundle variables/keys

The validator ignores common JSF implicit objects and local Facelets variables
to reduce false positives.

---

## Backing bean / page navigation

### Open Backing Bean

From XHTML:

`Ctrl+Alt+B`

Shows Java beans used by the current page.

### Open Controller Pages

From a JSF/CDI controller:

`Ctrl+Alt+P`

Shows XHTML pages that use the controller bean.

### Find JSF References

Put the caret inside:

```xhtml
id="resultPanel"
```

or:

```xhtml
widgetVar="dialog"
```

then press:

`Ctrl+Alt+R`

Shows references from XHTML and supported Java PrimeFaces/RichFaces code.

### Page / Controller Graph

`Ctrl+Alt+G`

Shows a quick summary of:

- beans
- includes
- templates
- widgetVars
- component IDs

### JSF Context Info

`Ctrl+Alt+I`

On `#{bean.property}` it shows:

- resolved class
- scope such as `@ViewScoped`
- `ui:param` alias target
- pages using the bean

On Ajax attributes such as:

```xhtml
process="@this"
update="@form"
execute="@this"
render="panel"
```

it explains request-processing vs re-rendering semantics and common JSF search
keywords.

---

## JSF Flow Explorer

Open it with:

`Ctrl+Shift+Alt+F`

or:

`Window -> Show View -> Other -> JSF / Java Navigation -> JSF Flow Explorer`

The Flow Explorer is a persistent task-focused view containing only files you
want associated with the current development task.

### Named flows

Examples:

```text
Antrag Detail
Postbuch
Login
User Administration
```

Use `+ Flow` in the view or:

`Ctrl+Shift+Alt+N`

to create a new flow.

The selected flow is persisted across Eclipse restarts.

### Add files manually

`Ctrl+Alt+F`

adds the current editor file to the active flow.

Or click:

`+ File`

### Automatic capture

The `Auto` checkbox controls automatic capture.

When enabled, workspace files opened/activated while navigating are
automatically added to the current flow. This means normal Ctrl+Click,
caller/callee navigation, backing-bean navigation, etc. naturally build the
task context as you work.

Turn `Auto` off when you want a strictly hand-picked list.

### Automatic categories

Files are grouped into:

```text
View
Controller
Bean
ISP
Service
Persistence
Resources
Tests
Other
```

Classification uses file type and common class-name conventions.

### Open All

`Open All` opens every existing workspace file in the current flow.

Useful after switching back to a task from yesterday.

### Focus Tabs

`Focus Tabs` closes workspace editor tabs that are not part of the current
flow.

Eclipse's normal save behavior is retained: unsaved editors still receive the
usual save prompt.

This does not delete files or alter projects. It only reduces editor-tab
clutter.

### Remove

Select a file and click `Remove` to unpin it from the current flow.

Deleting a flow only removes the saved flow definition; it does not delete
project files.

---

## WebSphere Hot Sync

Configure under:

`Window -> Preferences -> JSF / Java Navigation -> WebSphere Hot Sync`

Recommended fields:

- WebSphere profile directory
- exact exploded deployed web-module root
- project-relative web root such as `WebContent` or `src/main/webapp`
- enabled resource extensions
- automatic sync on/off

### Manual sync

`Ctrl+Alt+S`

Copies the current enabled web resource to its corresponding path in the
exploded WebSphere web module.

Example:

```text
WebContent/faces/detail.xhtml
```

to:

```text
<profile>/installedApps/<cell>/<app>.ear/<module>.war/faces/detail.xhtml
```

### Open deployed copy

`Ctrl+Alt+D`

Opens the actual deployed filesystem copy Eclipse/WebSphere is using.

Very useful when a browser still appears to show an older XHTML version.

### Automatic save sync

When enabled in Preferences, saving supported files can automatically sync:

- XHTML / HTML
- JavaScript
- CSS
- `.properties` if explicitly enabled

Not automatically hot-copied:

- `.class`
- JARs
- `web.xml`
- `persistence.xml`
- EJB metadata
- deployment descriptors

Use normal WebSphere deployment/reload procedures for those until Java
class-hot-reload behavior has been verified for the application.

---

## Persistent indexes / performance

The plug-in persists lightweight indexes below the Eclipse workspace state
directory, normally:

```text
<workspace>/.metadata/.plugins/de.andre.jsfnavigation/
```

Persistent state includes:

- bean index
- XHTML/JavaScript bean-usage index
- JSF view-symbol index
- Flow Explorer definitions

Java caller searches use Eclipse JDT's own Java index.

Expensive work is scheduled in Eclipse background Jobs where practical, and
resource listeners ignore marker-only changes to avoid validation/index loops.

---

## Useful configuration locations

### Change shortcuts

`Window -> Preferences -> General -> Keys`

Search:

`JSF / Java Navigation`

### WebSphere Hot Sync

`Window -> Preferences -> JSF / Java Navigation -> WebSphere Hot Sync`

### Open Flow Explorer manually

`Window -> Show View -> Other -> JSF / Java Navigation -> JSF Flow Explorer`

### JPQL hover

If the custom JPQL/JPA hover is disabled in the Eclipse installation:

`Window -> Preferences -> Java -> Editor -> Hovers`

and enable:

`JPA / JPQL Mapping`

---

## WebSphere Logs

Press:

`Ctrl+Alt+L`

to **toggle** the WebSphere Logs view.

- If the view is closed, the shortcut opens it.
- If the view is visible, the same shortcut closes it.
- The view contains separate tabs for `SystemOut.log` and `SystemErr.log`.
- With **Auto refresh** enabled, the tail of both files is refreshed every
  second.
- Log file reading happens in an Eclipse background Job so large logs do not
  block the editor UI.
- **Clear View** only clears the text currently displayed in Eclipse. It never
  truncates or modifies the real WebSphere log files.

The view reads only the tail of each log (256 KiB by default), so a multi-GB
server log is not loaded into memory.

### Custom WebSphere path

The plug-in does not assume that WebSphere is installed under IBM's default
directory.

Configure:

`Window -> Preferences -> JSF / Java Navigation -> WebSphere Hot Sync`

Set **WebSphere profile directory** to the real profile you use, for example:

```text
D:\CompanyTools\WebSphere\AppServer\profiles\AppSrv01
```

or any other custom location.

For logs, the plug-in then looks under:

```text
<profile>\logs\<server-name>\SystemOut.log
<profile>\logs\<server-name>\SystemErr.log
```

Set **WebSphere server name** if you know it, for example:

```text
server1
```

If multiple server log directories exist and no server is configured, opening
the log view presents a chooser and remembers the selected directory.

You can also bypass discovery completely using **Log directory override**, for
example:

```text
D:\CustomWAS\profiles\AppSrv01\logs\server1
```

For XHTML hot sync, the **Deployed web module root** remains independently
configurable, for example:

```text
D:\CustomWAS\profiles\AppSrv01\installedApps\MyCell\
    VatRefundOnline.ear\VatRefundOnlineWeb.war
```

The important path for hot sync/logs is normally the **profile path**, not the
WebSphere installation (`AppServer`) root itself.


---

## Smart WebSphere Deploy (opt-in)

Configure:

`Window -> Preferences -> JSF / Java Navigation -> WebSphere Hot Sync`

Enable:

`Enable Smart Java/Class Deploy after Eclipse builds (opt-in)`

This feature is **off by default**.

When enabled, the plug-in listens for Eclipse Java build output changes and
learns mappings between Eclipse output folders and deployed WebSphere modules.

### WAR classes

For classes deployed as:

```text
SomeApp.ear/
  SomeWeb.war/
    WEB-INF/classes/com/company/Foo.class
```

the plug-in copies the changed Eclipse-generated `.class` files directly into
the matching exploded `WEB-INF/classes` directory.

It also handles compiler-generated inner/anonymous classes:

```text
Foo.class
Foo$1.class
Foo$2.class
Foo$Inner.class
```

and removes stale deployed `$...class` files when the local compiler output no
longer contains them.

### JAR / EJB / EAR library classes

For a compiled class that already exists inside a top-level JAR or an EAR
`lib/*.jar`, the plug-in does **not** rewrite the JAR itself.

Instead it starts `wsadmin` once for the batch and uses WebSphere
`AdminApp.update(..., 'file', ...)` single-file application updates with a
`contenturi` pointing into the JAR.

That lets a class such as:

```text
com/company/service/FooBean.class
```

be updated inside:

```text
VATrefund-Online.ear/vr-antragsteller-core.jar
```

without rebuilding the complete EAR.

New generated inner classes use the WebSphere file `add` operation; changed
classes use `update`; stale inner classes use `delete`.

### Multiple applications

Discovery scans all exploded EARs below:

```text
<profile>/installedApps/<cell>/
```

so separate projects can map to different applications automatically.

Examples:

```text
VATrefundWeb output
  -> VATrefund-Online.ear / VATrefundWeb.war

Batch output
  -> VATrefund-Batch.ear / ...

Kaba output
  -> VATrefund-Kaba.ear / ...
```

If exactly one deployed module contains the class, the plug-in learns the
mapping automatically.

If several modules contain the same class, a chooser appears once and the
selected mapping is persisted.

Use **Forget learned Smart Deploy mappings** in the WebSphere preference page
if a project/module mapping changes.

### Smart web-resource recognition

With Smart Deploy enabled, XHTML/JS/CSS hot sync can also discover the correct
WAR across multiple EARs by matching the resource's relative web path.

This is useful for an EAR containing several web modules such as:

```text
VATrefund-Online.ear/
  VATrefundWeb.war/
  vatrefund-was-antragsteller.war/
  vatrefund-was-postbuch.war/
  vatrefund-was-textbausteine.war/
```

Learned mappings are persisted so subsequent saves do not rescan every module.

### wsadmin

For JAR/archive updates, the plug-in resolves:

```text
<profile>/bin/wsadmin.bat
```

or:

```text
<profile>/bin/wsadmin.sh
```

automatically.

You can override it using:

`wsadmin executable override`

and supply normal additional wsadmin command-line options in:

`wsadmin extra arguments`

For example, environments that require connection/authentication options can
supply them there.

Smart Java deploy requires Eclipse to actually compile the saved Java source.
Normally that means **Project -> Build Automatically** must be enabled.


### Search inside logs

With focus in `SystemOut.log`, `SystemErr.log`, or the Smart Deploy tab:

`Ctrl+F`

opens the built-in search bar.

- typing searches immediately
- Enter / Next moves forward
- Previous moves backward
- Escape closes the search bar
- search remains usable while auto-refresh is running

### Log highlighting

The log view styles common:

- errors / exceptions
- warnings
- informational messages
- Java stack-trace frames

Stack-trace source frames are underlined and clickable.

Example:

```text
at de.zivit.ustv.vatrefund.guiclient.controller.DetailsichtAntraegeController.save(DetailsichtAntraegeController.java:418)
```

Clicking the frame searches the Eclipse Java projects for the matching class
and opens the source directly at the reported line. If the same type exists in
multiple workspace projects, a chooser is shown.

The view also has a **Smart Deploy** tab that shows wsadmin output/errors from
archive-class updates.

### Smart Deploy progress

While Smart Deploy is running, the WebSphere Logs view shows a progress bar and
phase, for example:

```text
5%  — Detected changed classes
15% — Resolving deployed module mappings
30% — Preparing JAR/application updates
50% — Running wsadmin single-file update
80% — Waiting for WebSphere reload/log output
100% — Smart Deploy finished; watching WebSphere logs
```

The deploy also appears in Eclipse's normal **Progress** view.

For about 12 seconds after the deploy reaches the WebSphere reload phase, the
log reader temporarily refreshes every ~250 ms instead of every second. This
does not make WebSphere itself reload faster, but it makes new `SystemOut.log`
and `SystemErr.log` lines appear in Eclipse as soon as the files change.

### Log filtering

Click **Filter** or press:

`Ctrl+Shift+F`

The active filter is applied line-by-line to all three log tabs. Only lines
containing the filter text are shown; the original log data remains untouched.

The filter bar shows how many matching lines exist in the active tab. Clear the
filter to immediately restore the complete log tail.

`Ctrl+F` remains the normal find/search function *inside the currently visible
(filtered) text*.

### Log font zoom

Use the toolbar buttons:

- `A-` — smaller
- `A+` — larger
- `A` — reset

or:

- `Ctrl+-`
- `Ctrl++`
- `Ctrl+0`
- `Ctrl+Mouse Wheel`

The log viewer uses Eclipse's text font as its base and changes only the log
view's font.

### Scroll-aware auto-scroll

The log viewer follows new lines only while you are at the bottom.

If you manually scroll upward:

```text
new log refresh
    -> current position is preserved
    -> no jump back to the bottom
```

When you scroll all the way back to the bottom, automatic following turns back
on automatically.

This applies independently to `SystemOut.log`, `SystemErr.log`, and the Smart
Deploy tab.

### Resizable Page / Controller Graph

`Ctrl+Alt+G` now opens the JSF Page / Controller Graph in a resizable,
maximizable dialog instead of a fixed-size information popup.

### Responsive Flow Explorer

The Flow Explorer toolbar is split into multiple compact rows so all actions
remain available when the view is docked narrowly.

Select a file and press:

`Delete`

to remove it from the current Flow. This only removes the Flow entry; the
workspace file itself is untouched.

### v1.10.5 fixes

**Smart XHTML sync:** `Ctrl+Alt+S` no longer opens the generic list of every WAR
when Smart Deploy is enabled. The current XHTML/JS/CSS relative path is matched
against deployed WARs first; only genuine ambiguous matches produce a chooser.

**PrimeFaces autocomplete variables:** `p:autoComplete var="user"` is a local
component variable, not a CDI/JSF bean. Diagnostics no longer report it as an
unresolved bean. When `completeMethod` returns a typed collection such as
`List<UserEntity>`, Ctrl+Click/completion/context info can resolve the local
variable as `UserEntity`.

**Log filter:** the Filter text box is always visible. `Ctrl+Shift+F` simply
focuses it. `Esc` returns focus to the log without hiding the filter.

**Safe log font zoom:** `A-`, `A+`, `A`, `Ctrl+-`, `Ctrl++`, `Ctrl+0` and
`Ctrl+Mouse Wheel` update all log tabs using one replacement SWT Font. The old
font is disposed only after every StyledText has switched to the new font.
Ctrl+Mouse Wheel also cancels the widget's normal scrolling during zoom.

---

## XHTML / PrimeFaces / RichFaces completion

The same shortcut used for EL completion is context-sensitive:

`Ctrl+Alt+Space`

### Component/tag completion

Example:

```xhtml
<p:au|
```

Press `Ctrl+Alt+Space` and the plug-in offers matching PrimeFaces components,
for example `autoComplete`.

It supports the namespace prefixes declared in the XHTML file and common
libraries including:

- `p:` — PrimeFaces
- `rich:` — RichFaces
- `a4j:` — Ajax4jsf/RichFaces Ajax
- `h:` — JSF HTML
- `f:` — JSF Core
- `ui:` — Facelets

### Attribute completion

Example:

```xhtml
<p:autoComplete co|>
```

`Ctrl+Alt+Space` offers attributes valid for that component, such as
`completeMethod` or `converter`.

Selecting an attribute inserts:

```xhtml
completeMethod=""
```

with the caret placed between the quotes.

Attributes already present on the current tag are omitted from the list.

### Version-aware metadata

The plug-in scans Facelets tag-library metadata from likely JSF/PrimeFaces/
RichFaces JARs on the current project's resolved Java classpath and caches the
result in memory.

That means an older enterprise project gets suggestions from its installed
component-library version when metadata is available. Built-in definitions are
used only as fallbacks for common tags/attributes.

The existing behavior remains unchanged inside EL:

```xhtml
#{aumiAntragDetail.ver|}
```

`Ctrl+Alt+Space` still completes Java bean properties/methods there.

---

## JSF / PrimeFaces / RichFaces context help

Put the caret on a component or attribute and press:

`Ctrl+Alt+H`

### Attribute help

Example:

```xhtml
<p:autoComplete forceSelection="true" />
                    ^
```

The help window shows:

- library and component
- attribute name
- practical explanation
- project taglib description when available
- Java/type or method-signature metadata
- whether the taglib marks the attribute required
- whether the information came from project metadata or fallback help
- a small usage example
- related attributes from the same component

Example output conceptually:

```text
PrimeFaces <p:autoComplete>
Attribute: forceSelection

What it does
Restricts the value to an item selected from the suggestion list instead of
accepting arbitrary free-form text.

Type: boolean
Required: no / not marked required
Metadata: project taglib metadata

Example
<p:autoComplete forceSelection="true" />
```

The plug-in has curated explanations for common JSF/PrimeFaces/RichFaces
attributes such as `process`, `execute`, `update`, `render`, `reRender`,
`completeMethod`, `itemLabel`, `itemValue`, `converter`, `forceSelection`,
`widgetVar`, `immediate`, `rowKey`, `selection`, `lazy`, `sortBy`, `filterBy`,
and common Ajax callbacks.

For attributes without a curated explanation, project taglib metadata is shown
when available; otherwise the plug-in still provides structural/generic help
and a generated example.

### Component help

Put the caret on the tag name:

```xhtml
<p:autoComplete ... />
   ^
```

and press `Ctrl+Alt+H`.

The window shows the component description from the project's taglib metadata,
an example, and an attribute summary.

The window is resizable/maximizable.

---

## XHTML comment shortcut

In a Facelets/XHTML editor:

`Ctrl+/`

toggles an XML comment around the selected lines or the current line.

Single line:

```xhtml
<p:panel />
```

becomes:

```xhtml
<!-- <p:panel /> -->
```

A multi-line selection becomes:

```xhtml
<!--
<p:panel>
    ...
</p:panel>
-->
```

Press `Ctrl+/` again to restore the original block.

The command refuses to wrap a block that already contains an inner
`<!-- ... -->` comment because XML comments cannot be nested.

The binding is scoped to the Facelets/XHTML editor context, so Java keeps
Eclipse/JDT's existing `Ctrl+/` behavior.

---

## 1.12.1 fixes

The XHTML-only shortcuts are bound to the WTP HTML source-editor context shown
by Eclipse Plug-in Selection Spy:

`org.eclipse.wst.html.core.htmlsource.source.EditorContext`

So in the normal Facelets/XHTML `StructuredTextEditor`:

- `Ctrl+Alt+H` opens component/attribute help.
- `Ctrl+/` toggles an XHTML/XML comment.

Component-reference diagnostics now split both whitespace and commas. For
example:

```xhtml
process=":antragForm,@form,noValidation"
```

is interpreted as three separate expressions:

```text
:antragForm
@form
noValidation
```

`@form` is treated as a PrimeFaces/JSF search expression and is not validated as
a component ID. The ordinary component IDs are validated independently.

The JSF view-symbol persistent cache is automatically rebuilt once after the
upgrade so an old combined warning such as
`antragForm,@form,noValidation` cannot remain cached.

---

## 1.12.2 shortcut reliability + warning cleanup

`Ctrl+Alt+H` and `Ctrl+/` are now intercepted directly while the active editor
is the WTP HTML/Facelets source editor:

```text
org.eclipse.wst.html.core.htmlsource.source
```

This avoids conflicts with Eclipse's existing global shortcuts such as
**Open Call Hierarchy** on `Ctrl+Alt+H`.

The bridge is restricted to `.xhtml`, `.html`, `.htm`, and `.xml` files, so
Java/JDT keyboard behavior is left alone.

On plug-in startup, old persistent JSF warning markers are removed and only
the files that had those markers are revalidated. This is specifically meant
to clear stale warnings left by older component-reference tokenization, for
example an old combined warning for:

```xhtml
update=":antragForm,@form"
```

The current parser treats the comma as a separator and ignores `@form` as a
search expression.

---

## 1.12.3 automatic XHTML hot-sync fix

Web-resource hot sync is independent from **Smart Java/Class Deploy**.

When **Enable WebSphere hot sync** and **Automatically sync supported web
resources after save** are enabled, saving an XHTML/JS/CSS file now resolves
the target in this order:

1. Explicit **Deployed web module root** preference, when configured.
2. Remembered source-web-root -> WAR mapping.
3. Exact relative-resource-path match in exploded WARs under
   `<profile>/installedApps/...`. One match is selected automatically; multiple
   matches open a one-time chooser and remember the selection.
4. If the profile contains only one exploded WAR, use that as a final fallback.

`Ctrl+Alt+S` and **Open WebSphere Deployed Copy** use the same resolver and no
longer invoke the old generic "pick any WAR" guard first.

---

## 1.12.4 Flow Explorer improvements

### Single-click navigation

Clicking a **file entry once** opens/activates that file in the editor.

Clicking a category header such as `Bean (4)` or `TO (2)` does not open
anything.

### Active editor highlighting

When the active editor changes, the Flow Explorer automatically selects and
reveals the matching file entry if that file belongs to the current flow.

This works even when **Auto** capture is disabled. Auto capture only controls
whether newly visited files are added to the flow.

### Flow Explorer text zoom

Hover the mouse over the Flow Explorer file tree and use:

`Ctrl+MouseWheel`

to increase/decrease the tree text size.

The zoom uses a stable copy of the original Eclipse tree font and safely
replaces/disposes only the custom zoom font.

### TO category

The category order is now:

```text
View
Controller
Bean
TO
ISP
Service
Persistence
Resources
Tests
Other
```

Examples classified as `TO`:

```text
UserTO.java
UserDTO.java
UserDto.java
UserTransferObject.java
```

Entities, repositories and DAOs remain under `Persistence`.

Existing persisted flow entries are reclassified when the plug-in starts, so
old TO files previously shown under `Other` migrate automatically.

---

## 1.12.5 Flow errors + impacted tests

### Eclipse errors in the Flow Explorer

Any flow file that currently has an Eclipse **error** problem marker is shown
in red and with:

```text
[ERROR]  SomeClass.java — src/main/java/...
```

Category headers also show the number of affected files, for example:

```text
Controller (3)  [1 error]
```

The view listens for Eclipse marker changes and refreshes automatically.

### Auto tests

The Flow Explorer now has an independent **Auto tests** checkbox.

When enabled, the plug-in remembers JDT method changes while you edit and waits
until the Java file is actually saved. It then follows Java callers upward,
similar to repeatedly looking at the call hierarchy, up to 5 levels deep.

If a JUnit test class is found anywhere in that caller chain, the test file is
added to the current flow under `Tests`.

This is independent from normal **Auto** capture. You can disable automatic
file capture while still keeping automatic impacted-test discovery.

### Run Unit Tests

The **Run Unit Tests** button runs the eligible JUnit classes currently present
under `Tests` in the active flow.

For safety, the bulk button deliberately skips:

- Arquillian integration tests (`org.jboss.arquillian`, `Arquillian.class`)
- generic integration tests (`*IntegrationTest`, `*IT`, integration source folders/tags)
- JPA/persistence tests (JPA API / `EntityManager` / persistence-context usage,
  or clearly JPA/persistence-named test classes)

Skipped test counts are reported in the Eclipse status line.

Tests are launched through Eclipse's normal JUnit launcher, one safe test class
at a time. Temporary launch configurations are deleted after each run.

This conservative bulk-run policy is intentional: impacted Arquillian/JPA tests
can still appear in the Flow Explorer so you know they exist, but they are not
accidentally executed by **Run Unit Tests**.

---

## 1.12.6 Impact distance in Tests

Impacted tests are now shown nearest-first with a clear caller-distance badge:

```text
Tests (4)  [nearest first]

[DIRECT]         AumiAntragBeanTest.java
[2 calls away]   AntragServiceTest.java
[3 calls away]   AntragControllerTest.java
[5 calls away]   SomeWorkflowTest.java
```

`[DIRECT]` means the test calls the edited method directly.

`[2 calls away]` means there is one intermediate production method between the
test and the edited method, and so on.

The automatic caller search now has a hard ceiling of **5 levels**. If the same
test is reached through multiple caller paths, the shortest discovered distance
is kept.

Tests that were added manually and have no impact-distance information are
still shown normally, after the impacted tests.

The distance is stored in Flow Explorer state format v3. Existing v1/v2 flows
remain compatible; their existing entries simply start with distance `0`
(no impact badge).

---

## 1.12.7 Impacted tests grouped by changed code

The `Tests` category is no longer one flat impacted-test list. Automatically
found tests are grouped first by the **changed production file**, then by the
**exact changed method**:

```text
Tests (5)  [grouped by changed file]

▼ Impacted by AumiAntragBean.java  (2 methods, 3 tests)
    ▼ save(Antrag)  (2 tests)
        [DIRECT]        AumiAntragBeanTest.java
        [2 calls away]  AntragServiceTest.java
    ▼ load(Long)  (1 test)
        [3 calls away]  NavigationTest.java

▼ Impacted by DetailsichtAntraegeController.java  (1 method, 1 test)
    ▼ speichernUndNavigieren()  (1 test)
        [2 calls away]  NavigationTest.java

▼ Other tests  (1)
    ManuallyAddedTest.java
```

The same test can appear in multiple groups on purpose. That means multiple
changed methods/files independently lead to that test. The underlying flow
still stores the test file only once, so **Run Unit Tests** does not run it
multiple times.

Navigation is also contextual:

- single-click a changed-file group → open that source file
- single-click a changed-method group → open that exact method
- single-click an impacted test → open the test file

Each changed-method → test relationship stores its own shortest caller distance
(up to the existing 5-level ceiling).

Flow state format v4 persists these relationships and remains backward
compatible with v1-v3 state files.
