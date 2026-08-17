# JSF / Java Navigation Plug-in 1.8 — Full Cheat Sheet

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
