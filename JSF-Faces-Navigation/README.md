# JSF EL Navigation 1.3.0

Eclipse WTP plug-in for fast Ctrl+Click navigation from JSF/Facelets EL to Java.

## Performance design

Version 1.3.0 uses two cache layers:

- **Persistent bean index on disk** in the Eclipse plug-in state area.
- **In-memory JDT member/type caches** for getters, fields, methods, type
  hierarchies and return types.

The persistent index stores only lightweight identifiers (bean name, project,
qualified Java type, workspace resource path and source modification stamp).
It does not serialize JDT objects.

Java source changes are handled incrementally through Eclipse resource deltas:
only changed/added/removed `.java` compilation units are re-indexed.

If a source file changed while Eclipse was closed, the persisted modification
stamp makes the old entry stale. It is discarded and resolved/rebuilt rather
than trusted.

Explicit bean names such as:

```java
@ManagedBean(name = "foo")
public class CompletelyDifferentController { ... }
```

cannot be derived from `foo`. On the first unresolved lookup in a session the
plug-in performs one background full-source index build. The resulting index
is persisted, so normal future Eclipse sessions use the disk cache directly.

Navigation lookup itself is executed in an Eclipse background `Job`, so even a
cold rebuild does not block the UI thread.

## WTP editor target

The hyperlink detector is registered once for:

`org.eclipse.wst.html.core.htmlsource`

There is intentionally no duplicate Default Text Editor registration.

## Supported common expressions

```xhtml
#{userConverter}
#{aumiAntragDetail.completeUser}
#{aumiAntragDetail.antrag.currentRevision.bezirk}
```

## Current scope

This version resolves `@ManagedBean`, CDI `@Named`, explicit bean names,
JavaBean getters, inherited members, fields, direct action/listener-style
methods, and ordinary chained property return types.

Dynamic EL resolvers, CDI producer methods/fields, collection indexing and
arbitrary method-call expressions are outside the current resolver.
