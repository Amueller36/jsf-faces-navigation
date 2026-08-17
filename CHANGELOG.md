# Changelog

## 1.1.0

- Added Eclipse WTP HTML/XHTML editor hyperlink registration.
- Targets `org.eclipse.wst.html.core.htmlsource`, matching the WTP HTML source configuration used by `StructuredTextEditor`.
- Keeps `org.eclipse.ui.DefaultTextEditor` as a fallback.
- Adds `org.eclipse.wst.sse.ui` as a required Eclipse bundle.
- Confirmed configuration against the user's Plug-in Spy information:
  - `StructuredTextEditor`
  - `org.eclipse.wst.sse.ui`
  - `org.eclipse.wst.html.core.htmlsource.source`
