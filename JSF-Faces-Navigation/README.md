# JSF EL Navigation 1.15.4

Version 1.15.4 groups Feature Test Audit results by architecture layer
and lets the developer reverse the direction.

### Architektur-Reihenfolge

The Feature Test Audit now contains:

```text
Architektur-Reihenfolge:
[ Controller → Bean → DSP → ISP ▼ ]
```

with the alternative:

```text
ISP → DSP → Bean → Controller
```

The tree is grouped rather than merely globally sorted:

```text
Controller (3 Klassen, 4/18 Methoden offen)
    PostbuchController
    PostbuchDetailController
    ...

Bean (2 Klassen, 1/11 Methoden offen)
    PostbuchBean
    ...

DSP (1 Klasse, 2/5 Methoden offen)
    PostbuchDSP

ISP (2 Klassen, 3/13 Methoden offen)
    PostbuchISP
    ...
```

Within each architecture group, the most actionable classes are kept first:
classes without any test class, then classes with more missing methods, then
package/name order.

The German `Jira-Übersicht kopieren` export follows the exact same selected
direction and adds section headings such as:

```text
=== Controller ===
...
=== Bean ===
...
=== DSP ===
...
=== ISP ===
...
```

or the reverse order.

The grouping is purely in-memory over the already computed Feature Test Audit
report and triggers no new JDT/workspace search.

Version 1.15.3 makes the Jira clipboard export unambiguous when the
workspace contains duplicate simple class names in different packages.

### Packages in the Jira export

Every production class in `Jira-Übersicht kopieren` now includes its package:

```text
[TEILWEISE] PostbuchISP [ISP]
- Package: de.itzbund.ustv.vr4.postbuch
- Methoden: 6/8 referenziert, 2 offen (75 %)
```

Every listed existing test class also includes its package:

```text
- Vorhandene Testklassen:
  - PostbuchISPImplTest [JPA]
    — Package: de.itzbund.ustv.vr4.postbuch.jpa
    — VAT-Refund-TestJPA
```

This is especially useful when the same simple class/test name exists in
multiple modules or packages.

The change is formatting-only: copying the overview still performs no new
workspace search or JDT analysis.

Version 1.15.2 adds a German Jira-friendly clipboard export to
Feature Test Audit.

### Jira-Übersicht kopieren

The Feature Test Audit dialog now contains:

```text
Jira-Übersicht kopieren
```

It copies a plain-text German overview that can be pasted directly into a Jira
ticket, Teams message, review comment, etc.

Example:

```text
Feature-Testübersicht: Postbuch

Zusammenfassung
- Relevante Produktionsklassen: 7
- Testbare Methoden: 42
- Bereits durch Testcode referenziert: 31
- Noch nicht durch Testcode referenziert: 11
- Statische Methoden-Referenzabdeckung: 74 %
- Klassen ohne gefundene Testklasse: 2

Hinweis: Die Abdeckung basiert auf statisch aufgelösten Methodenaufrufen im
Testcode und ist keine JaCoCo-Laufzeit-Coverage.

[KEINE TESTKLASSE] PostbuchDSP [DSP]
- Methoden: 0/5 referenziert, 5 offen (0 %)
- Vorhandene Testklassen: keine
- Noch nicht durch Testcode referenzierte Methoden:
  - speichern(...)
  - laden(...)

[TEILWEISE] PostbuchISP [ISP]
- Methoden: 6/8 referenziert, 2 offen (75 %)
- Vorhandene Testklassen:
  - PostbuchISPImplTest [JPA] — VAT-Refund-TestJPA
- Noch nicht durch Testcode referenzierte Methoden:
  - löschen(...)
  - neuBerechnen(...)
```

By default the clipboard export mirrors the audit's focus and omits fully
covered classes / already referenced methods.

If `Show already referenced/tested methods` is enabled before copying, the
clipboard export also includes the tested methods and the first concrete test
reference, including `[via ImplementationType]` information when relevant.

Clipboard generation is purely in-memory and adds no extra workspace/JDT
search.

Version 1.15.1 extends Feature Test Audit across production
implementations/subtypes.

### Implementation/subtype tests count toward the audited production method

A common project layout is:

```text
PostbuchISP.java
PostbuchISPImpl.java
PostbuchISPImplTest.java
```

The existing-test finder already recognizes `PostbuchISPImplTest` as a
candidate for `PostbuchISP` because of the shared name prefix. 1.15.1 now also
maps the method calls inside that test correctly when JDT resolves them against
`PostbuchISPImpl` rather than `PostbuchISP`.

During the on-demand Feature Test Audit, the plug-in builds the JDT type
hierarchy for the production class and accepts method bindings declared on:

```text
the audited production type
its supertypes/interfaces
its implementations/subclasses
```

A call such as:

```java
postbuchISPImpl.loadPostbuch(id);
```

inside `PostbuchISPImplTest` can therefore satisfy the matching
`PostbuchISP.loadPostbuch(...)` audit entry when the signature matches.

When already-tested methods are shown, the audit makes this visible:

```text
✓ loadPostbuch(Long)
  ← PostbuchISPImplTest.shouldLoadPostbuch(...) [via PostbuchISPImpl]
```

### Test discovery for differently named implementations

The common case (`PostbuchISPImplTest`) needs no extra workspace search because
the normal `PostbuchISP*` indexed lookup already finds it.

For differently prefixed implementations such as:

```text
DefaultPostbuchISPImpl
DefaultPostbuchISPImplTest
```

Feature Test Audit performs a small bounded extra test lookup for workspace
subtypes.

Performance bounds added for this extension:

```text
related subtypes accepted in hierarchy: 64
extra subtype-specific test lookups:      8
```

These checks only run inside the explicitly requested Feature Test Audit. No
new hierarchy work is added to normal typing, editor navigation, Flow refreshes,
or ordinary `Auto tests` discovery.

Version 1.15.0 adds an on-demand **Feature Test Audit** for exactly the
workflow where a feature spans many Controller/Bean/ISP/DSP classes and the
developer must find what is still missing tests.

### Feature Test Audit

Open the JSF Flow Explorer and click:

```text
Feature Tests…
```

Enter a class-name fragment such as:

```text
Postbuch
```

The scan uses Eclipse/JDT's workspace type index, rather than recursively
walking the filesystem. It selects concrete production types whose class names
contain the feature term and whose architectural role is one of:

```text
Controller
Bean
ISP
DSP
```

It excludes:

```text
interfaces (including IPostbuch...)
Entities / JPA persistence types
TO / DTO classes
JAXB classes
existing tests
```

The result is a tree such as:

```text
[NO TEST CLASS] PostbuchDSP — DSP — 5/5 methods untested — 0% static method-reference coverage
    ✗ savePostbuch(...) [NOT REFERENCED BY TEST]
    ✗ loadPostbuch(...) [NOT REFERENCED BY TEST]

[PARTIAL] PostbuchController — Controller — 2/8 methods untested — 75% static method-reference coverage
    TEST: PostbuchControllerTest [VAT-Refund-JUnit] UNIT
    ✗ deleteEntry(...) [NOT REFERENCED BY TEST]
    ✗ recalculate(...) [NOT REFERENCED BY TEST]

[OK] PostbuchBean — Bean — 0/6 methods untested — 100% static method-reference coverage
    TEST: PostbuchBeanTest [VAT-Refund-JUnit] UNIT
```

Already referenced methods are hidden by default so the audit immediately
focuses on missing work. `Show already referenced/tested methods` reveals the
full method inventory.

The dialog provides:

```text
Open Source
Open Test
Generate Helper
Create Test Class…
Add Audit to Flow
```

`Generate Helper` reuses the existing Mockito/JPA test-helper generator for the
selected missing production method.

`Create Test Class…` ranks real Java source roots in the workspace, including
separate unit/JPA test projects, applies the existing `de.zivit.*` ->
`de.itzbund.*` migration rule for new test packages, creates a minimal
compilable test class and opens it.

### Static coverage semantics

The feature audit deliberately calls its metric **static method-reference
coverage**. A production method is considered covered when a matching existing
test class contains a JDT-resolved invocation of that production method (also
recognizing calls through a production supertype/interface).

This is much faster and more useful for inventory work than launching every test
suite, but it is not equivalent to JaCoCo runtime line/branch coverage. A test
can reference a method and still have weak assertions; conversely reflection or
dynamic invocation can be missed.

The scan is on-demand and bounded:

```text
max production classes:       250
max testable methods/class:   160
max parsed test source CUs:    16 per production class
```

No audit work runs while typing/editing.

### Related tests automatically join the Flow

With `Auto tests` enabled, opening/activating a production Java file that is in
the current Flow now performs a small JDT-index lookup for conventionally named
existing test classes. For example:

```text
PostbuchISP.java
     ↓ open
PostbuchISPTest.java
     ↓
automatically added to Tests in the current Flow
```

The lookup is debounced and only runs for production files that actually belong
to the current Flow.

`FlowExplorerService.addFile(...)` now also reclassifies an already-present
entry. Therefore old persisted entries such as `PostbuchISPTest.java` that were
previously stored under `Other` are moved to `Tests` the next time they are
added/opened.

### DSP category

`DSP` is now a first-class Flow category, alongside Controller, Bean, ISP,
Service, Persistence, etc., and is included in Feature Test Audit.

Version 1.14.3 completes the Java-editor JUnit gutter behavior.

### Test class + test method gutter actions

The JUnit ruler now shows a green play triangle on both:

```text
▶  public class AuslandsantragISPImplTest extends JPATestClient {
       ...
   }

   @Test
▶  public void shouldLoadSomething() {
       ...
   }
```

A class-line click targets the whole JUnit class. A method-line click targets
that exact JUnit test method.

Clicking a triangle no longer launches immediately. It opens a tiny SWT context
menu at the gutter with:

```text
Run AuslandsantragISPImplTest
Debug AuslandsantragISPImplTest
```

or, for a method:

```text
Run AuslandsantragISPImplTest.shouldLoadSomething
Debug AuslandsantragISPImplTest.shouldLoadSomething
```

Debug uses Eclipse's normal JUnit debug launch mode, so normal breakpoints and
the Debug perspective/session behavior apply.

Explicit gutter launches intentionally work for JPA/integration test classes as
well; the conservative exclusions still apply only to the bulk Flow
`Run Unit Tests` action.

### JUnit lifecycle

Both class and exact-method actions still use Eclipse's normal JUnit launch
configuration. The plug-in does **not** invoke the Java test method directly.

Therefore normal lifecycle behavior remains owned by JUnit:

- JUnit 4 `@Before` runs before each selected test method, including inherited
  superclass `@Before` methods according to JUnit's normal rules.
- JUnit 4 `@BeforeClass` runs for the class launch lifecycle.
- JUnit 5 `@BeforeEach` / `@BeforeAll` work through the JUnit 5 loader.
- JUnit 3 `setUp()` runs through the JUnit 3 loader.

Selecting a single test method only limits which test case JUnit executes; it
does not bypass its setup/teardown lifecycle.

The gutter stays lightweight: class/method source-line targets are refreshed
only when the open document changes and use the existing 280 ms debounce.

Version 1.14.2 fixes a JAXB/XSD hyperlink-detector instantiation error.

`JaxbJavaHyperlinkDetector` was registered as an Eclipse extension but had a
private no-argument constructor. Eclipse creates extension classes
reflectively and therefore could not instantiate the detector when a Java
editor was opened/activated. Opening a file from Flow Explorer still succeeded
because file opening and hyperlink-detector initialization are separate editor
steps. The constructor is now public, as required by the Eclipse registry.

Version 1.14.1 fixes the native XHTML content-assist integration shown
by the real WTP/Facelets editor.

### Native popup now includes EL proposals too

1.14.0 moved markup/tag/attribute completion toward WTP's content assistant,
but `Ctrl+Alt+Space` still took the old modal path whenever the caret was inside
an EL expression such as:

```xhtml
listener="#{aumiAntragdetail.|}"
```

That is why the separate `JSF EL Completion` dialog could still appear, while
Eclipse's own content-assist window showed `No Default Proposals`.

1.14.1 fixes both sides:

- `Ctrl+Alt+Space` delegates to the editor's normal content-assist operation for
  both JSF EL and XHTML markup.
- A modern `org.eclipse.wst.sse.ui.completionProposal` proposal computer is
  registered on WTP's default proposal page for the HTML/XML/default partitions
  used by the Structured Text Editor.
- The proposal computer contributes the plug-in's existing bean member
  resolution (`@Named`/managed beans, aliases, inherited members, local JSF
  variables) directly as standard Eclipse `ICompletionProposal` entries.
- The same proposal computer contributes PrimeFaces/RichFaces/JSF
  tag/attribute proposals.
- The old modal EL/markup chooser remains only as a fallback if a custom editor
  does not expose Eclipse's standard content-assist operation.

Expected behavior:

```text
#{aumiAntragdetail.|
                     ↓ Ctrl+Alt+Space / Ctrl+Space
         ┌───────────────────────────────┐
         │ action                        │
         │ anlagen                       │
         │ antrag                        │
         │ antragId                      │
         │ ...                           │
         └───────────────────────────────┘
```

The proposal window is the normal WTP/Eclipse popup at the caret, rather than a
separate modal dialog.

This change adds no background search. Bean/member resolution runs only when
content assist is requested and continues to use the plug-in's existing indexes
and JDT caches.

Version 1.14.0 adds workspace-aware existing-test targeting and the first
XSD/JAXB navigation layer.


### New-test package migration: `de.zivit` -> `de.itzbund`

Existing production classes can still live in the historical `de.zivit.*`
namespace, but **newly suggested test files** now use the current organization
package automatically.

Example:

```text
production:
de.zivit.ustv.vatrefund.foo.AuslandsantragISP

suggested new test:
de.itzbund.ustv.vatrefund.foo.AuslandsantragISPTest
```

The rule only affects suggested new test locations. Existing tests/classes are
not renamed or moved.

### XHTML completion now uses Eclipse's native popup

JSF/PrimeFaces/RichFaces tag and attribute proposals now participate in WTP's
normal Structured Text Editor content-assist UI.

`Ctrl+Alt+Space` still invokes the plug-in completion command, but when the
caret is in XHTML tag/attribute markup it delegates to Eclipse's own content
assistant. This gives the standard caret-anchored proposal popup instead of the
old modal selection dialog, and the plug-in proposals can coexist with WTP's
built-in proposals.

Because the processor is registered with WTP's normal HTML/XML/default
partitions, ordinary Eclipse content assist (`Ctrl+Space`) can also include the
same JSF markup proposals in `.xhtml` files.

The old modal chooser remains only as a compatibility fallback if a custom/old
WTP editor configuration does not expose the normal content-assist operation.

No extra background indexing is added for this UI change: proposal computation
reuses the existing per-project taglib catalog cache and only runs when content
assist is invoked.

### Existing test discovery + insertion

`Generate Test Helper...` no longer assumes tests live in the production
project.

The generator queries Eclipse JDT's existing workspace type index and ranks
matching test classes across all Java projects. This is designed for workspaces
with dedicated test projects such as:

```text
VAT-Refund-JUnit
VAT-Refund-TestJPA
VAT-Refund-TestEJB
VAT-Refund-Regression
```

For a production type such as `AuslandsantragISP`, candidates like
`AuslandsantragISPTest`, `AuslandsantragISPJPATest`, integration tests, etc.
are ranked using:

```text
production-class name match
package similarity
JUnit/JPA/integration classification
test-project name hints (*JUnit, *TestJPA, *TestEJB, *Regression)
```

The generator dialog now contains:

```text
Existing test: [candidate combo]

Open Test
Insert...
Copy
```

Switching generator mode automatically prefers the matching test kind:

```text
Mockito helper / unit scaffold -> UNIT / *JUnit candidate
JPA scaffold                  -> JPA / *TestJPA candidate
```

The test search runs only when the generator is explicitly requested and uses
JDT's indexed type-name search rather than walking every Java source file.

`Insert...` is explicit and inserts the currently previewed snippet before the
chosen test class' closing brace. It refuses to modify a target with unsaved
changes and removes generated `@Mock` / `@InjectMocks` fields when the same
field name already exists. A duplicate generated `_shouldTODO` method name gets
a numeric suffix. The target is opened afterward for review.

### XSD / JAXB navigation

The plug-in now maintains a lightweight XSD definition index for `.xsd` files.
It recognizes named:

```text
complexType
simpleType
element
attribute
group
attributeGroup
```

with arbitrary XML Schema prefixes, not only `xs:` / `xsd:`.

In XSD source, Ctrl+Click navigation supports:

```xml
type="tns:ApplicationType"
base="tns:BaseApplicationType"
ref="tns:application"
schemaLocation="../common/common.xsd"
```

`type` / `base` prefer XSD type declarations and `ref` prefers
element/attribute/group declarations. Built-in `xs:*` types are deliberately
left to Eclipse instead of falling back to unrelated project declarations.

`schemaLocation` supports relative workspace paths, workspace-absolute paths
and `platform:/resource/...`.

Ctrl+Click a named XSD declaration such as:

```xml
<xs:complexType name="ApplicationType">
```

to jump to a matching JAXB Java type when one can be resolved from the JDT
index.

The reverse also works from JAXB annotation names:

```java
@XmlType(name = "ApplicationType")
@XmlRootElement(name = "application")
@XmlElement(name = "country")
```

Ctrl+Click the `name` string to jump back to matching XSD declarations.

When several XSD/JAXB candidates match, a chooser is shown instead of silently
picking an arbitrary one.

### Flow Explorer: JAXB + Schema

Two new Flow categories are available:

```text
JAXB
Schema
```

JAXB Java files are detected semantically from annotations such as
`@XmlType`, `@XmlRootElement`, `@XmlAccessorType`, `@XmlEnum` and
`@XmlRegistry`.

Architecture Focus can now extend a related JAXB class to its matching XSD file
when both are already present in the active Flow:

```text
Controller
  [FOCUS] DetailsichtController.java

JAXB
  • ApplicationType.java

Schema
  • application.xsd
```

### XSD performance design

The XSD layer is intentionally separate from Java AST/call-hierarchy work.

```text
persistent snapshot: xsd-index-v1.bin
startup: snapshot available immediately
validation: background Job after startup
updates: incremental POST_CHANGE only for .xsd files
parsing: lightweight bounded text/regex scan, no DOM/XSD model build
per XSD file limit: 8 MiB
XSD -> JAXB: exact JDT indexed type search, on-demand only
JAXB search cache: 128 entries / 30-second TTL
Flow JAXB -> XSD: O(1)-style XSD index lookup after Java relation is known
```

The existing-test finder is similarly on-demand and uses JDT's workspace type
index, so neither feature adds a workspace-wide Java source scan to ordinary
typing, Flow refreshes or editor navigation.

Version 1.13.3 adds an on-demand test-helper generator directly to Java
method context menus.

### Generate Test Helper...

Right-click inside a Java method and choose:

```text
Generate Test Helper...
```

The command also understands an `IMethod` selected in Eclipse Outline/Java
structured selections.

The generator analyzes the selected method with JDT bindings in a background
Job. It follows internal helper methods declared in the same class and
superclasses (bounded depth 4 / 30 methods / 12 source compilation units) so a
dependency used by a private/inherited helper is still visible to the generator.

It detects field collaborators actually called on the selected method path and
builds three copy/paste profiles:

```text
Mockito mock helper method
Mockito unit test scaffold
JPA test scaffold
```

For normal unit-test code the default is the mock-helper profile. Example:

```java
@Mock
private UserService userService;

private void mockLoadDependencies() {
    when(userService.findUser(anyString()))
        .thenReturn(/* TODO User */ null);
}
```

The full Mockito scaffold additionally generates:

```text
@Mock collaborator fields
@InjectMocks subject
@Test method
Arrange parameter placeholders
when(...) stubs for non-void collaborator calls
subject.method(...)
basic assertion placeholder
verify(...) interaction suggestions
```

Primitive/String argument matchers are made more specific (`anyInt()`,
`anyString()`, etc.) to reduce overload ambiguity. Behavior-specific return
values and assertions deliberately remain TODOs rather than being invented.

When JPA API usage is detected in the selected method/helper path, the dialog
defaults to the JPA scaffold instead. It reminds the developer to use the
project's cleanup-tracked persistence helper and, for real DB round-trip
assertions, to flush and clear/renew the persistence context at the intended
boundary.

The result is shown in a resizable code dialog with a mode selector and a
`Copy` button. The generator never edits the Java source automatically.

This feature is deterministic/offline: it uses the project's JDT source model
and bindings and does not call an external AI service.

Version 1.13.2 improves test-data cleanup analysis and adds fast
text/regex filtering to the JSF Flow Explorer.

### Tx Lens now follows helper implementations and superclasses

The entity leak-risk analysis no longer stops at the visible test method.

For every test method, the on-demand cleanup scan now follows workspace-source
helper calls with a bounded recursive traversal. It therefore detects cases
such as:

```java
@Test
public void testSomething() {
    persistEntity(antrag);       // helper in same class OR inherited
}
```

where `persistEntity` is implemented:

- in the same test class,
- in a superclass / base JPA test class,
- or in another workspace helper reached from that method.

It additionally inspects cleanup lifecycle methods in the test class and its
superclasses:

```text
@After
@AfterEach
@AfterAll
@AfterMethod
@AfterTest
tearDown()
```

and recursively follows helper calls from those lifecycle methods too.

The report explicitly says how many helper/lifecycle methods were inspected and
whether same-class or inherited helpers were encountered.

Leak warnings remain conservative. If an inherited `@After` cleanup exists but
a directly persisted entity is not visibly registered with that cleanup path,
the lens still reports a possible leak rather than assuming the superclass can
magically identify the row.

Performance guards for this deeper scan:

```text
on-demand Tx Lens only
max helper depth:        5
max inspected methods:  60
max parsed source units: 18
cycle prevention by JDT method handle
cancellable background Eclipse Job
```

So normal editing, Flow selection, saves and JUnit gutter rendering do not pay
for superclass/helper cleanup analysis.

### Flow Explorer filtering

The Flow Explorer header now has:

```text
Filter…
Clear Filter
```

and `Ctrl+F` while the Flow tree is focused opens the same filter dialog.

Normal input is a case-insensitive text filter:

```text
antrag
Controller
postbuch
```

It matches category, filename and workspace/project path.

Regex is supported in either form:

```text
re:.*Antrag.*(Bean|Controller)
```

or:

```text
/.*Antrag.*(Bean|Controller)/
```

Invalid regexes are rejected without replacing the current filter.

Filtering is purely in-memory over entries already present in the active Flow;
it performs no workspace search, Java search or AST parsing. Test impact groups
are rebuilt from the filtered test entries, and the summary line shows the
active filter.

Version 1.13.1 moves the JUnit play action to the place it was actually
intended: the Java editor gutter beside each runnable test method.

### Java editor test gutter

The plug-in contributes a dedicated ruler column to Eclipse's normal Java
compilation-unit editor. Every detected JUnit test method gets a small green
play triangle on the same source line:

```java
    ▶  @Test
       public void shouldLoadAntrag() {
           ...
       }
```

(Visually the triangle is in Eclipse's left editor gutter, aligned with the
test method line, like IntelliJ's gutter actions.)

Clicking the triangle launches **that exact test method**, not the complete Flow
and not the whole test file. It uses Eclipse's normal JUnit launch
configuration, so the standard JUnit view still works.

Unlike automatic/bulk Flow test execution, this is an explicit user action.
Therefore the gutter is available for JUnit test methods even when the class is
a JPA/integration-style test; the bulk `Run Unit Tests` safety exclusions remain
unchanged.

If the test file belongs to the current Flow, its gutter-run result is also
captured by the existing Flow last-run/stack-trace summary. Running a test from
an unrelated file does not overwrite the active Flow's stored summary.

The gutter implementation is intentionally lightweight: it uses JDT's method
model rather than an AST binding walk, caches the discovered source-line map,
and debounces refreshes after document edits by 280 ms.

The temporary `▶` labels introduced inside the Flow Explorer in 1.13.0 were
removed; the play control now lives only where requested: beside test methods in
the Java source editor.

### Possible leaked test entities

`Tx Lens` now also distinguishes **persistence-context cleanup** from
**database-row cleanup**.

This matters because:

```java
entityManager.persist(entity);
entityManager.clear();
```

does *not* clean the database. `clear()` only detaches managed objects.

The lens now labels direct operations such as:

```text
[DB CREATE]  EntityManager.persist(...)
[DB DELETE]  EntityManager.remove(...)
[PC RESET]   EntityManager.clear(...)
```

and can report:

```text
POSSIBLE DB LEAK:
this test appears to create/persist an entity, but no visible cleanup
registration or delete/remove is associated with it.
```

It also recognizes common cleanup-tracking helper names such as
`persistEntity`, `registerEntity`, `trackEntity`, `addEntityForCleanup`, and
`addTestEntity`. These are deliberately marked as heuristics because static
analysis cannot prove what a custom helper really does.

Entity-aware helper calls such as `createX(Entity)` / `saveX(Entity)` /
`erstelleX(Entity)` can also be flagged as possible DB creates when JDT can
resolve an `@Entity` type in their parameter or return types.

`EntityManager.merge(...)` is called out separately because it may update an
existing row or insert a new one.

This is a **static leak-risk detector**, not a runtime database assertion.
Inherited `@After`/teardown logic or a custom test superclass may clean rows
outside the visible method, so the lens phrases these as warnings/hints rather
than hard errors. Exact runtime leak proof would require project-specific DB
instrumentation or a before/after database snapshot.

Version 1.13.0 adds inline safe JUnit play actions and an on-demand
transaction / persistence-context lens for tests.

### Inline test play actions

Safe unit-test rows in the Flow Explorer now start with a clickable play glyph:

```text
Tests
  Impacted by DetailsichtController.java
    speichern(...)
      ▶  [DIRECT] DetailsichtControllerTest.java
```

The same play action is shown on failed/skipped test-class nodes from the last
Flow test summary.

Clicking the `▶` hot area runs only the selected safe JUnit test file/class
through Eclipse's normal JUnit launcher and feeds its result back into the
existing persisted Flow test summary/stack-trace view.

The play glyph is intentionally **not** shown for Arquillian, generic
integration, or JPA/persistence tests. The existing `Run Unit Tests` safety
policy therefore remains unchanged. Test classification for painting/clicking
is cached by file modification stamp (LRU, 384 files), so normal Flow refreshes
do not repeatedly re-read unchanged test sources.

### Transaction / Persistence Context Lens

A new `Tx Lens` action analyzes the selected Flow test (or the active Java test
editor) only when requested. It runs in a background Eclipse Job and does not
add work to normal typing, saving, Flow selection, or test execution.

The lens statically illustrates visible events such as:

```text
testFindAntrag(...)  (line 40)
   42  [WRITE]     EntityManager.persist(...)
   43  [FLUSH]     EntityManager.flush(...)
   44  [PC RESET]  EntityManager.clear(...)
   47  [READ]      EntityManager.find(...)
```

It recognizes `EntityManager` write/read/query/flush/clear operations,
`EntityTransaction` / `UserTransaction` begin/commit/rollback calls, and common
transaction annotations such as `@Transactional` and
`@TransactionAttribute`.

Because older test frameworks often wrap these operations in helpers, obvious
helper names such as `renewPersistenceContext`, `flushAndClear`,
`beginTransaction`, `commitTransaction`, etc. are also shown, but explicitly
marked with `?` as heuristics rather than asserted facts.

The lens gives guidance for common JPA-test mistakes, for example:

- a write followed by a read/query in the same persistence context can be
  satisfied by the managed object / first-level cache and therefore fail to
  prove a true DB round-trip;
- a visible PC reset after a write without an explicit flush deserves checking
  because the framework may or may not flush at the intended boundary;
- a manual transaction begin without a visible commit/rollback is called out;
- when no explicit transaction boundary is visible, the lens explains that it
  may be inherited or framework/superclass managed.

These are deliberately **hints**, not Eclipse error markers, because static
source analysis cannot prove container-managed transaction boundaries.

Double-clicking an event in the lens jumps to the corresponding line in the
test source.

Version 1.12.9 adds semantic Entity detection and a performant architecture
focus slice to the JSF Flow Explorer.

### Semantic legacy-Entity detection

Flow categorization is no longer only filename based. JDT source annotations
are cached by file modification stamp and recognize:

- `@Entity`, `@Embeddable`, `@MappedSuperclass` -> `Persistence`
- `@Named`, `@ManagedBean` -> `Bean` when a stronger filename category does
  not already apply
- `@Service` -> `Service`
- `@Repository` -> `Persistence`

This means an old entity named simply `Antrag.java` is correctly categorized
as Persistence even when it is passed directly into Bean/Controller layers
instead of going through a TO. Persistence entries that are actual JPA entity
types also display an `[ENTITY]` badge.

### Architecture Focus

Single-clicking a Java architecture file in the Flow Explorer now makes it the
focus root. For a controller this produces a visual slice such as:

```text
Controller (3)  [1 related]
  [FOCUS] DetailsichtController.java
  OtherController.java                         <- dimmed

Bean (4)  [2 related]
  • AntragBean.java
  • ValidationBean.java
  UserBean.java                                <- dimmed

TO (3)  [1 related]
  • AntragTO.java
  UserTO.java                                  <- dimmed

Persistence (5)  [2 related]
  • [ENTITY] Antrag.java
  • AntragRepository.java
  User.java                                    <- dimmed
```

Unrelated entries remain visible for context but are theme-aware dimmed.
Eclipse errors keep their existing red priority. Relevant XHTML pages found
through the existing JSF bean-usage index are included in the same focus slice.

`Clear Focus` removes the slice and returns the complete Flow to normal
rendering.

### Performance design

Architecture focus does **not** execute a workspace-wide call hierarchy search
on every click. It runs in a cancellable background Eclipse Job with a small
click debounce, lazily walks only dependencies reachable from the selected
Flow file, and only considers files already present in the active Flow.

Java dependency results are held in an in-memory LRU cache keyed by Eclipse
resource modification stamp. Unchanged classes therefore do not get reparsed
when switching between controllers. The traversal is bounded to 7 dependency
edges and at most 150 related Flow files.

Dependencies are derived from JDT bindings for declared types, method
calls/return/parameter types, constructors and inheritance. This catches old
architectural paths where an Entity itself crosses into higher layers.

Version 1.12.8 adds an aggregate, persistent Flow JUnit result overview.

`Run Unit Tests` still runs all safe unit-test classes sequentially, so one
failed class does not prevent the remaining classes from running. The plug-in
now listens to Eclipse's JUnit model and copies the case-level result of each
launch into one combined Flow summary.

The `Tests` tree starts with the latest result for the active flow:

```text
Tests
  Last run: FAILED — 20 Aug 12:34  (38 passed, 2 failed, 1 skipped, 6 classes)
    Failed tests (2)
      AumiAntragBeanTest (1 case)
        ✗ shouldCreateAntrag() — java.lang.AssertionError: ...
          Stack trace… (click to open)
      UserServiceTest (1 case)
        ! shouldRejectInvalidUser() — java.lang.NullPointerException
          Stack trace… (click to open)
    Skipped tests (1)
      SomeTest (1 case)
        ○ ignoredForNow()
  Impacted by ...
```

The overview intentionally keeps successful cases collapsed into the aggregate
count, while failed/error and skipped cases remain inspectable. Clicking a test
class opens its file; clicking a test case jumps to the test method when it can
be resolved.

Selecting `Stack trace…` opens a resizable, copyable full-trace dialog.
Double-clicking a Java stack frame such as `FooTest.java:42` resolves the Java
type in the workspace and jumps to that source line.

The latest result is stored separately per Flow in
`flow-test-results-v1.bin`, including failure traces and expected/actual values,
and survives closing the Flow Explorer or restarting Eclipse. `Clear Results`
removes the saved result for the current Flow. Flow rename/delete operations
also rename/delete their saved result.

Arquillian, generic integration, and JPA/persistence tests remain excluded from
the bulk runner and their excluded-class count is visible in the last-run
summary.

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
