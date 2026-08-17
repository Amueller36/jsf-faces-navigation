# JSF EL Navigation for Eclipse

Small Eclipse PDE plug-in that adds Ctrl+Click navigation for common JSF/EL expressions.

Examples:

```xhtml
converter="#{userConverter}"
completeMethod="#{aumiAntragDetail.completeUser}"
value="#{aumiAntragDetail.antrag.currentRevision.bezirk}"
```

The plug-in can navigate to:

- `@ManagedBean` and `@Named` beans
- explicit bean names such as `@Named("foo")`
- JavaBean getters (`getX()` / `isX()`)
- inherited getters and methods
- fields
- direct methods such as PrimeFaces `completeMethod`, `action`, and listeners
- chained EL properties by following Java return types

## Import into Eclipse

1. Make sure your Eclipse has PDE / Plug-in Development Environment.
2. `File -> Import -> Existing Projects into Workspace`.
3. Select this repository directory.
4. Eclipse should recognize it as a Plug-in Project.

If you do not have **Run As -> Eclipse Application**, PDE is probably missing.

## Test locally

Right-click the project:

`Run As -> Eclipse Application`

A second Eclipse instance starts with the plug-in enabled.

Open an XHTML file and try Ctrl+Click on the EL identifier.

## Build/export a JAR

Use:

`File -> Export -> Plug-in Development -> Deployable plug-ins and fragments`

Select `de.andre.jsfnavigation` and export to a directory.

The result should contain something like:

`plugins/de.andre.jsfnavigation_1.1.0.jar`

## Install locally without Marketplace

The cleanest PDE route is:

`File -> Export -> Plug-in Development -> Deployable plug-ins and fragments -> Install into host`

Alternatively, if your company setup permits copying plug-ins, use the exported JAR with your Eclipse installation's supported dropins/extensions mechanism and restart Eclipse. On first restart, `eclipse.exe -clean` can help refresh bundle state.

Verify under:

`Help -> About Eclipse IDE -> Installation Details -> Plug-ins`

and search for:

`de.andre.jsfnavigation`

## XHTML / WTP editor support

This repository is configured for the Eclipse WTP HTML editor discovered with Plug-in Spy:

- editor class: `StructuredTextEditor`
- contributing plug-in: `org.eclipse.wst.sse.ui`
- active editor identifier: `org.eclipse.wst.html.core.htmlsource.source`

WTP uses the HTML source hyperlink target `org.eclipse.wst.html.core.htmlsource`, so the plug-in registers the EL hyperlink detector for that target.

A second registration for `org.eclipse.ui.DefaultTextEditor` is kept as a fallback, so you can also test with:

`Open With -> Text Editor`

### Quick verification

Run the plug-in with:

`Run As -> Eclipse Application`

In the second Eclipse instance, open an XHTML file using **HTML Editor**, then hold Ctrl over an EL identifier such as:

```xhtml
converter="#{userConverter}"
completeMethod="#{aumiAntragDetail.completeUser}"
```

The identifier should underline / become clickable. Ctrl+Click should navigate into the corresponding Java bean, method, getter, or field.

## Java compatibility

Source level/runtime is Java 8 compatible. No Maven Central dependencies or external libraries are required; the plug-in uses Eclipse/JDT APIs already present in a Java-capable Eclipse installation.

## Current limitations

This deliberately focuses on common JSF/PrimeFaces/RichFaces EL usage. It does not yet fully resolve:

- `faces-config.xml` bean declarations
- CDI producer methods/fields
- dynamic EL resolvers
- collection/map indexing such as `users[0].name`
- arbitrary method-call expressions with parameters
- overloaded method selection based on component attribute semantics

Those can be added later without changing the core design.
