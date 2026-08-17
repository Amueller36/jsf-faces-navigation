# JSF EL Navigation 1.4.0

Eclipse WTP plug-in for fast Ctrl+Click navigation between JSF/Facelets XHTML,
Java controllers and JavaScript.

## 1.4.0

### Richer EL navigation

The detector no longer requires the whole EL expression to be a simple
`bean.property` chain. It resolves the chain under the cursor inside expressions
such as:

```xhtml
#{!aumiAntragDetail.bescheidButtonVisible or aumiAntragDetail.modusLesen}
#{aumiAntragDetail.save()}
```

Operators, negation, comparisons and method parentheses no longer disable the
hyperlink detector.

### XHTML JavaScript navigation

Ctrl+Click JavaScript calls in an XHTML file, for example:

```xhtml
oncomplete="preventBVsDeletion(); triggerDL(args);"
```

The current unsaved XHTML document is searched first. If the definition is not
in the current document, the persistent project/workspace web index is queried.
Supported definition styles include:

```javascript
function triggerDL(args) { }
const triggerDL = function(args) { };
const triggerDL = (args) => { };
```

### Java / PrimeFaces -> JavaScript

A second hyperlink detector is registered for the JDT Java editor target
`org.eclipse.jdt.ui.javaCode`.

Calls inside common PrimeFaces/RequestContext script execution strings can be
Ctrl+Clicked, for example:

```java
PrimeFaces.current().executeScript("refreshHistorieTab()");
RequestContext.getCurrent().execute("refreshHistorieTab()");
```

If one JavaScript definition is found it is opened. If several are found, a
selection dialog shows the candidate XHTML/JS files. If no JavaScript definition
is found and the Java class is a `@ManagedBean`/`@Named` controller, the plug-in
shows XHTML pages that reference that controller.

## Persistent indexes

The plug-in keeps two persistent indexes in Eclipse's plug-in state directory:

- `bean-index-v1.bin`: EL bean name -> Java type
- `web-index-v1.bin`: JavaScript definitions and XHTML controller usages

They normally live below:

`.metadata/.plugins/de.andre.jsfnavigation/`

Both indexes are updated incrementally while Eclipse is running. Changed Java
files update the bean index; changed `.xhtml`/`.js` files update only their web
index entries. Stored modification stamps prevent stale file entries from being
blindly trusted after an Eclipse restart.

JDT member/type-hierarchy/return-type caches remain in memory because recreating
those handles from JDT is cheap and safer than serializing JDT objects.

## WTP editor target

XHTML detector:

`org.eclipse.wst.html.core.htmlsource`

Java detector:

`org.eclipse.jdt.ui.javaCode`

The old duplicate Default Text Editor registration remains removed.
