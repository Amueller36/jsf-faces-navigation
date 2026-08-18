# JSF / Java Navigation Plug-in 1.10 — Full Cheat Sheet

## Hotkeys

| Action | Windows / Linux | macOS |
|---|---|---|
| Go to direct caller | `Ctrl+Alt+Page Up` | `Cmd+Option+Page Up` |
| Go to direct callee | `Ctrl+Alt+Page Down` | `Cmd+Option+Page Down` |
| Follow single-caller chain | `Ctrl+Shift+Alt+Page Up` | `Cmd+Shift+Option+Page Up` |
| Follow single-callee chain | `Ctrl+Shift+Alt+Page Down` | `Cmd+Shift+Option+Page Down` |
| JSF EL completion | `Ctrl+Alt+Space` | `Cmd+Option+Space` |
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
