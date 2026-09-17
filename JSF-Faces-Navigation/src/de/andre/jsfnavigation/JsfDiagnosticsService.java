package de.andre.jsfnavigation;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;

public final class JsfDiagnosticsService {

    public static final String MARKER_TYPE =
            "de.andre.jsfnavigation.problem";

    private static final String UNRESOLVED_BEAN_PREFIX =
            "Unresolved JSF/CDI bean";

    private static final Pattern EL =
            Pattern.compile("[#\\$]\\{([^}]*)\\}");

    private static final Pattern SIMPLE_CHAIN =
            Pattern.compile(
                    "\\b([A-Za-z_$][A-Za-z0-9_$]*)"
                    + "(\\.[A-Za-z_$][A-Za-z0-9_$]*(?:\\([^)]*\\))?)+");

    private static final Set<String> IMPLICIT_EL_OBJECTS =
            new HashSet<String>(Arrays.asList(
                    "application",
                    "applicationScope",
                    "cc",
                    "component",
                    "cookie",
                    "facesContext",
                    "flash",
                    "header",
                    "headerValues",
                    "initParam",
                    "param",
                    "paramValues",
                    "request",
                    "requestScope",
                    "resource",
                    "session",
                    "sessionScope",
                    "view",
                    "viewScope"));

    private static final Pattern METHOD_ATTRIBUTE =
            Pattern.compile(
                    "\\b(completeMethod|action|actionListener|listener|valueChangeListener)"
                    + "\\s*=\\s*(['\"])\\s*[#\\$]\\{\\s*"
                    + "([A-Za-z_$][A-Za-z0-9_$]*)"
                    + "\\.([A-Za-z_$][A-Za-z0-9_$]*)"
                    + "(\\([^}]*\\))?\\s*\\}\\2");

    private final ConcurrentHashMap<String, Boolean> pending =
            new ConcurrentHashMap<String, Boolean>();

    private final AtomicBoolean scheduled =
            new AtomicBoolean(false);

    private final AtomicBoolean beanRevalidationScheduled =
            new AtomicBoolean(false);

    private final ConcurrentHashMap<String, Boolean> waitingForBeanIndex =
            new ConcurrentHashMap<String, Boolean>();

    /*
     * Files that currently have at least one unresolved-bean diagnostic.
     * Keeping this small set avoids a workspace-wide marker scan every time
     * the incremental bean index changes.
     */
    private final ConcurrentHashMap<String, Boolean> unresolvedBeanFiles =
            new ConcurrentHashMap<String, Boolean>();

    /*
     * Bean navigation and the incremental bean index can settle a fraction of
     * a second after an XHTML edit. Requiring a second stable miss prevents a
     * transient index race from leaving a bogus yellow "bean not found" marker
     * even though Ctrl+Click can resolve the bean immediately afterwards.
     */
    private final ConcurrentHashMap<String, Long> unresolvedBeanFirstMiss =
            new ConcurrentHashMap<String, Long>();

    private static final long UNRESOLVED_BEAN_GRACE_MS = 900L;

    private IResourceChangeListener listener;

    public void start() {
        listener =
                new IResourceChangeListener() {
                    @Override
                    public void resourceChanged(
                            IResourceChangeEvent event) {

                        collect(event.getDelta());
                    }
                };

        ResourcesPlugin.getWorkspace()
                .addResourceChangeListener(
                        listener,
                        IResourceChangeEvent.POST_CHANGE);

        scheduleInitialMarkerRefresh();
    }


    private void scheduleInitialMarkerRefresh() {
        Job job =
                new Job(
                        "Refresh JSF diagnostics") {

                    @Override
                    protected IStatus run(
                            IProgressMonitor monitor) {

                        try {
                            IMarker[] markers =
                                    ResourcesPlugin
                                            .getWorkspace()
                                            .getRoot()
                                            .findMarkers(
                                                    MARKER_TYPE,
                                                    false,
                                                    IResource.DEPTH_INFINITE);

                            Set<IFile> files =
                                    new HashSet<IFile>();

                            for (IMarker marker : markers) {
                                IResource resource =
                                        marker.getResource();

                                if (resource instanceof IFile) {
                                    IFile file = (IFile) resource;

                                    if (file.exists()
                                            && isView(file.getName())) {

                                        files.add(file);

                                        String message =
                                                marker.getAttribute(
                                                        IMarker.MESSAGE,
                                                        "");

                                        if (message.startsWith(
                                                UNRESOLVED_BEAN_PREFIX)) {

                                            unresolvedBeanFiles.put(
                                                    file.getFullPath()
                                                            .toPortableString(),
                                                    Boolean.TRUE);
                                        }
                                    }
                                }
                            }

                            /*
                             * Do not delete markers before validation. The old
                             * implementation briefly removed every warning and
                             * then recreated it. Eclipse rendered that as warning
                             * triangles blinking on/off whenever background bean
                             * index updates caused a revalidation.
                             */
                            for (IFile file : files) {
                                if (monitor.isCanceled()) {
                                    break;
                                }

                                validate(file);
                            }

                        } catch (CoreException e) {
                            // Diagnostics refresh is best effort only.
                        }

                        return Status.OK_STATUS;
                    }
                };

        job.setSystem(true);
        job.setPriority(Job.DECORATE);
        job.setRule(PluginBackgroundSchedulingRule.INSTANCE);
        job.schedule(1000L);
    }


    public void stop() {
        if (listener != null) {
            ResourcesPlugin.getWorkspace()
                    .removeResourceChangeListener(listener);
            listener = null;
        }

        unresolvedBeanFirstMiss.clear();
        waitingForBeanIndex.clear();
        unresolvedBeanFiles.clear();
    }

    public void beanIndexChanged() {
        /*
         * Keep unresolvedBeanFirstMiss intact here. Clearing it on every
         * incremental Java-index update restarted the grace timer and made an
         * existing unresolved-bean marker disappear before being recreated.
         */
        if (!beanRevalidationScheduled.compareAndSet(
                false,
                true)) {

            return;
        }

        Job job = new Job("Revalidate JSF bean diagnostics") {
            @Override
            protected IStatus run(IProgressMonitor monitor) {
                try {
                    for (String path :
                            new ArrayList<String>(
                                    waitingForBeanIndex.keySet())) {

                        waitingForBeanIndex.remove(path);
                        pending.put(path, Boolean.TRUE);
                    }

                    /*
                     * Revalidate only XHTML files that actually have an
                     * unresolved-bean marker. The old implementation scanned all
                     * plugin markers in the entire workspace after every bean
                     * index change.
                     */
                    for (String path :
                            new ArrayList<String>(
                                    unresolvedBeanFiles.keySet())) {

                        if (monitor.isCanceled()) {
                            break;
                        }

                        pending.put(path, Boolean.TRUE);
                    }

                } finally {
                    beanRevalidationScheduled.set(false);
                }

                if (!pending.isEmpty()) {
                    schedule(300L);
                }

                return monitor.isCanceled()
                        ? Status.CANCEL_STATUS
                        : Status.OK_STATUS;
            }
        };

        job.setSystem(true);
        job.setPriority(Job.DECORATE);
        job.setRule(PluginBackgroundSchedulingRule.INSTANCE);
        job.schedule(250L);
    }

    public void validate(IFile file) {
        if (file == null
                || !file.exists()
                || !isView(file.getName())) {

            return;
        }

        String filePath =
                file.getFullPath()
                        .toPortableString();

        BeanIndexService beanIndex =
                Activator.getBeanIndexService();

        /*
         * A validation pass is committed only against a stable bean index.
         * Keeping the current markers while the index is updating prevents
         * transient background work from making the editor annotations blink.
         */
        if (beanIndex == null
                || !beanIndex.isReadyForDiagnostics()) {

            waitingForBeanIndex.put(
                    filePath,
                    Boolean.TRUE);
            return;
        }

        waitingForBeanIndex.remove(filePath);

        String source = read(file);

        if (source == null) {
            return;
        }

        List<Diagnostic> diagnostics =
                new ArrayList<Diagnostic>();

        validateIndexedReferences(
                file,
                source,
                diagnostics);

        if (!validateEl(
                file,
                source,
                beanIndex,
                diagnostics)) {

            return;
        }

        if (!validateMethodAttributes(
                file,
                source,
                beanIndex,
                diagnostics)) {

            return;
        }

        /*
         * The index may have started an incremental update while this file was
         * being checked. In that case discard the in-memory result and leave the
         * old markers untouched; beanIndexChanged() will queue a fresh pass.
         */
        if (!beanIndex.isReadyForDiagnostics()) {
            waitingForBeanIndex.put(
                    filePath,
                    Boolean.TRUE);
            return;
        }

        reconcileMarkers(
                file,
                diagnostics);
    }

    private void validateIndexedReferences(
            IFile file,
            String source,
            List<Diagnostic> diagnostics) {

        JsfViewIndexService index =
                Activator.getJsfViewIndexService();

        if (index == null) {
            return;
        }

        String project =
                file.getProject().getName();

        for (ViewSymbol ref :
                index.symbolsInFile(
                        file,
                        ViewSymbol.COMPONENT_REFERENCE)) {

            if (index.find(
                    ViewSymbol.COMPONENT_ID,
                    ref.getName(),
                    project).isEmpty()) {

                diagnostic(
                        diagnostics,
                        ref.getOffset(),
                        ref.getName().length(),
                        IMarker.SEVERITY_WARNING,
                        "Unresolved JSF component reference '"
                                + ref.getName()
                                + "' in "
                                + ref.getAttributeName()
                                + ".");
            }
        }

        for (ViewSymbol ref :
                index.symbolsInFile(
                        file,
                        ViewSymbol.WIDGET_REFERENCE)) {

            if (index.find(
                    ViewSymbol.WIDGET_VAR,
                    ref.getName(),
                    project).isEmpty()) {

                diagnostic(
                        diagnostics,
                        ref.getOffset(),
                        ref.getName().length(),
                        IMarker.SEVERITY_WARNING,
                        "No widgetVar='"
                                + ref.getName()
                                + "' was found for PF('"
                                + ref.getName()
                                + "').");
            }
        }

        for (ViewSymbol symbol :
                index.symbolsInFile(
                        file,
                        ViewSymbol.INCLUDE)) {

            if (JsfNavigationSupport.resolveViewPath(
                    file,
                    symbol.getName()) == null) {

                diagnostic(
                        diagnostics,
                        symbol.getOffset(),
                        symbol.getName().length(),
                        IMarker.SEVERITY_WARNING,
                        "Included Facelets file '"
                                + symbol.getName()
                                + "' could not be resolved.");
            }
        }

        for (ViewSymbol symbol :
                index.symbolsInFile(
                        file,
                        ViewSymbol.TEMPLATE)) {

            if (JsfNavigationSupport.resolveViewPath(
                    file,
                    symbol.getName()) == null) {

                diagnostic(
                        diagnostics,
                        symbol.getOffset(),
                        symbol.getName().length(),
                        IMarker.SEVERITY_WARNING,
                        "Facelets template '"
                                + symbol.getName()
                                + "' could not be resolved.");
            }
        }

        for (ViewSymbol symbol :
                index.symbolsInFile(
                        file,
                        ViewSymbol.BUNDLE_KEY)) {

            IFile properties =
                    JsfNavigationSupport.resolveBundleProperties(
                            file,
                            symbol.getAttributeName());

            if (properties == null) {
                diagnostic(
                        diagnostics,
                        symbol.getOffset(),
                        symbol.getName().length(),
                        IMarker.SEVERITY_WARNING,
                        "Resource bundle variable '"
                                + symbol.getAttributeName()
                                + "' could not be resolved.");
            } else if (JsfNavigationSupport.findPropertyKeyOffset(
                    properties,
                    symbol.getName()) < 0) {

                diagnostic(
                        diagnostics,
                        symbol.getOffset(),
                        symbol.getName().length(),
                        IMarker.SEVERITY_WARNING,
                        "Resource bundle key '"
                                + symbol.getName()
                                + "' was not found.");
            }
        }
    }

    private boolean validateEl(
            IFile file,
            String source,
            BeanIndexService beanIndex,
            List<Diagnostic> diagnostics) {

        String project =
                file.getProject().getName();

        Set<String> localVariables =
                JsfPageInspector.localVariables(file);

        Matcher el = EL.matcher(source);

        Set<String> already =
                new HashSet<String>();

        while (el.find()) {
            String body = el.group(1);

            Matcher chain =
                    SIMPLE_CHAIN.matcher(body);

            while (chain.find()) {
                String full =
                        chain.group();

                int paren =
                        full.indexOf('(');

                String beforeParen =
                        paren >= 0
                                ? full.substring(
                                        0,
                                        paren)
                                : full;

                List<String> parts =
                        ElJavaResolver.splitSimpleChain(
                                beforeParen);

                if (parts.size() < 2) {
                    continue;
                }

                String beanName =
                        parts.remove(0);

                if (IMPLICIT_EL_OBJECTS.contains(beanName)
                        || localVariables.contains(beanName)) {

                    continue;
                }

                String key =
                        beanName
                        + ":"
                        + beforeParen;

                if (!already.add(key)) {
                    continue;
                }

                String filePath =
                        file.getFullPath()
                                .toPortableString();

                /*
                 * Diagnostics are passive. Never make XHTML validation trigger
                 * a full workspace bean-index rebuild while Eclipse is opening
                 * or reconciling an editor. The index will notify us when ready.
                 */
                if (!beanIndex.isReadyForDiagnostics()) {
                    waitingForBeanIndex.put(
                            filePath,
                            Boolean.TRUE);
                    return false;
                }

                IType bean =
                        beanIndex.resolveForDiagnostics(
                                beanName,
                                project);

                int expressionOffset =
                        el.start(1)
                        + chain.start();

                if (bean == null) {
                    String unresolvedKey =
                            filePath
                            + "#"
                            + beanName;

                    long now =
                            System.currentTimeMillis();

                    Long firstMiss =
                            unresolvedBeanFirstMiss
                                    .putIfAbsent(
                                            unresolvedKey,
                                            Long.valueOf(now));

                    if (firstMiss == null
                            || now
                                    - firstMiss.longValue()
                                    < UNRESOLVED_BEAN_GRACE_MS) {

                        /*
                         * Verify once more after the index/JDT has had a quiet
                         * period. This mirrors the successful Ctrl+Click path
                         * instead of turning a transient miss into a marker.
                         */
                        pending.put(
                                filePath,
                                Boolean.TRUE);
                        schedule(
                                UNRESOLVED_BEAN_GRACE_MS);
                        return false;
                    }

                    diagnostic(
                            diagnostics,
                            expressionOffset,
                            beanName.length(),
                            IMarker.SEVERITY_WARNING,
                            UNRESOLVED_BEAN_PREFIX
                                    + " '#{"
                                    + beanName
                                    + "...}'.");
                    continue;
                }

                unresolvedBeanFirstMiss.remove(
                        file.getFullPath()
                                .toPortableString()
                        + "#"
                        + beanName);

                try {
                    JavaMemberTarget target =
                            ElJavaResolver.resolveChain(
                                    bean,
                                    parts);

                    if (target == null) {
                        String last =
                                parts.get(
                                        parts.size() - 1);

                        diagnostic(
                                diagnostics,
                                expressionOffset
                                        + beforeParen.lastIndexOf(last),
                                last.length(),
                                IMarker.SEVERITY_WARNING,
                                "Unresolved property/method '"
                                        + last
                                        + "' on "
                                        + bean.getElementName()
                                        + ".");
                    }

                } catch (JavaModelException e) {
                    // Keep the previous marker set while JDT is rebuilding.
                    return false;
                }
            }
        }

        return true;
    }

    private boolean validateMethodAttributes(
            IFile file,
            String source,
            BeanIndexService beanIndex,
            List<Diagnostic> diagnostics) {

        Matcher matcher =
                METHOD_ATTRIBUTE.matcher(source);

        String project =
                file.getProject().getName();

        if (!beanIndex.isReadyForDiagnostics()) {

            waitingForBeanIndex.put(
                    file.getFullPath()
                            .toPortableString(),
                    Boolean.TRUE);
            return false;
        }

        while (matcher.find()) {
            String attr =
                    matcher.group(1);

            String beanName =
                    matcher.group(3);

            String methodName =
                    matcher.group(4);

            String explicitArguments =
                    matcher.group(5);

            IType bean =
                    beanIndex != null
                            ? beanIndex.resolveForDiagnostics(
                                    beanName,
                                    project)
                            : null;

            if (bean == null) {
                continue;
            }

            try {
                IMethod method =
                        ElJavaResolver.findMethod(
                                bean,
                                methodName);

                if (method == null) {
                    continue;
                }

                int params =
                        method.getNumberOfParameters();

                boolean valid = true;

                if (explicitArguments != null) {
                    // EL method expressions with explicit arguments are
                    // intentionally left to the EL runtime/JDT navigation.
                    // Framework callback signature rules below apply only
                    // when JSF/PrimeFaces supplies the arguments.
                    continue;
                }

                if ("completeMethod".equals(attr)) {
                    valid = params == 1;
                } else if ("action".equals(attr)) {
                    valid = params == 0;
                } else if ("actionListener".equals(attr)
                        || "listener".equals(attr)
                        || "valueChangeListener".equals(attr)) {

                    valid = params <= 1;
                }

                if (!valid) {
                    diagnostic(
                            diagnostics,
                            matcher.start(4),
                            methodName.length(),
                            IMarker.SEVERITY_WARNING,
                            "Method signature of "
                                    + beanName
                                    + "."
                                    + methodName
                                    + "(...) looks incompatible with JSF attribute '"
                                    + attr
                                    + "'.");
                }

            } catch (JavaModelException e) {
                // Keep the previous marker set during transient model changes.
                return false;
            }
        }

        return true;
    }

    private void collect(IResourceDelta delta) {
        if (delta == null) {
            return;
        }

        try {
            delta.accept(
                    new IResourceDeltaVisitor() {
                        @Override
                        public boolean visit(
                                IResourceDelta child)
                                throws CoreException {

                            IResource resource =
                                    child.getResource();

                            if (resource.getType()
                                    == IResource.FILE
                                    && isView(
                                            resource.getName())
                                    && child.getKind()
                                            != IResourceDelta.REMOVED
                                    && isContentChange(child)) {

                                pending.put(
                                        resource.getFullPath()
                                                .toPortableString(),
                                        Boolean.TRUE);

                                return false;
                            }

                            return true;
                        }
                    });

        } catch (CoreException e) {
            return;
        }

        schedule();
    }

    private void schedule() {
        schedule(500L);
    }

    private void schedule(
            long delayMillis) {

        if (pending.isEmpty()
                || !scheduled.compareAndSet(
                        false,
                        true)) {

            return;
        }

        Job job =
                new Job("Validate JSF references") {
                    @Override
                    protected IStatus run(
                            IProgressMonitor monitor) {

                        try {
                            List<String> batch =
                                    new ArrayList<String>(
                                            pending.keySet());

                            for (String path : batch) {
                                if (pending.remove(path) != null) {
                                    IFile file =
                                            ResourcesPlugin
                                                    .getWorkspace()
                                                    .getRoot()
                                                    .getFile(
                                                            new org.eclipse.core.runtime.Path(
                                                                    path));

                                    if (file.exists()) {
                                        validate(file);
                                    }
                                }
                            }

                        } finally {
                            scheduled.set(false);

                            if (!pending.isEmpty()) {
                                schedule();
                            }
                        }

                        return Status.OK_STATUS;
                    }
                };

        job.setSystem(true);
        job.setPriority(Job.DECORATE);
        job.setRule(PluginBackgroundSchedulingRule.INSTANCE);
        job.schedule(
                Math.max(0L, delayMillis));
    }

    private static void diagnostic(
            List<Diagnostic> diagnostics,
            int start,
            int length,
            int severity,
            String message) {

        diagnostics.add(
                new Diagnostic(
                        Math.max(0, start),
                        Math.max(0, start + length),
                        severity,
                        message));
    }

    private void reconcileMarkers(
            IFile file,
            List<Diagnostic> diagnostics) {

        try {
            IMarker[] existing =
                    file.findMarkers(
                            MARKER_TYPE,
                            false,
                            IResource.DEPTH_ZERO);

            boolean[] used =
                    new boolean[existing.length];

            /*
             * Keep identical markers in place. This is the important part for
             * editor stability: a warning that is still valid never disappears
             * between two validation passes, so Eclipse has nothing to blink.
             */
            for (Diagnostic wanted : diagnostics) {
                int match =
                        findMatchingMarker(
                                existing,
                                used,
                                wanted);

                if (match >= 0) {
                    used[match] = true;
                    continue;
                }

                IMarker marker =
                        file.createMarker(
                                MARKER_TYPE);

                marker.setAttributes(
                        new String[] {
                                IMarker.MESSAGE,
                                IMarker.SEVERITY,
                                IMarker.CHAR_START,
                                IMarker.CHAR_END
                        },
                        new Object[] {
                                wanted.message,
                                Integer.valueOf(wanted.severity),
                                Integer.valueOf(wanted.start),
                                Integer.valueOf(wanted.end)
                        });
            }

            /*
             * Create new markers first and remove stale ones last. Even when a
             * diagnostic moves because text was edited, there is no intermediate
             * state in which all warnings vanish.
             */
            for (int i = 0; i < existing.length; i++) {
                if (!used[i]) {
                    existing[i].delete();
                }
            }

            String filePath =
                    file.getFullPath()
                            .toPortableString();

            boolean hasUnresolvedBean = false;

            for (Diagnostic diagnostic : diagnostics) {
                if (diagnostic.message.startsWith(
                        UNRESOLVED_BEAN_PREFIX)) {

                    hasUnresolvedBean = true;
                    break;
                }
            }

            if (hasUnresolvedBean) {
                unresolvedBeanFiles.put(
                        filePath,
                        Boolean.TRUE);
            } else {
                unresolvedBeanFiles.remove(
                        filePath);
            }

        } catch (CoreException e) {
            // Diagnostics must never interfere with editing.
        }
    }

    private static int findMatchingMarker(
            IMarker[] existing,
            boolean[] used,
            Diagnostic wanted)
            throws CoreException {

        for (int i = 0; i < existing.length; i++) {
            if (used[i]) {
                continue;
            }

            IMarker marker = existing[i];

            if (wanted.start
                            == marker.getAttribute(
                                    IMarker.CHAR_START,
                                    -1)
                    && wanted.end
                            == marker.getAttribute(
                                    IMarker.CHAR_END,
                                    -1)
                    && wanted.severity
                            == marker.getAttribute(
                                    IMarker.SEVERITY,
                                    -1)
                    && wanted.message.equals(
                            marker.getAttribute(
                                    IMarker.MESSAGE,
                                    ""))) {

                return i;
            }
        }

        return -1;
    }

    private static final class Diagnostic {
        private final int start;
        private final int end;
        private final int severity;
        private final String message;

        private Diagnostic(
                int start,
                int end,
                int severity,
                String message) {

            this.start = start;
            this.end = end;
            this.severity = severity;
            this.message = message;
        }
    }


    private static boolean isContentChange(
            IResourceDelta delta) {

        if (delta.getKind() == IResourceDelta.ADDED) {
            return true;
        }

        int flags = delta.getFlags();

        return (flags & IResourceDelta.CONTENT) != 0
                || (flags & IResourceDelta.REPLACED) != 0;
    }

    private static boolean isView(String name) {
        String lower =
                name.toLowerCase();

        return lower.endsWith(".xhtml")
                || lower.endsWith(".html")
                || lower.endsWith(".htm");
    }

    private static String read(IFile file) {
        InputStream in = null;

        try {
            in = file.getContents();

            ByteArrayOutputStream out =
                    new ByteArrayOutputStream();

            byte[] buffer =
                    new byte[8192];

            int read;

            while ((read = in.read(buffer)) >= 0) {
                out.write(buffer, 0, read);
            }

            return new String(
                    out.toByteArray(),
                    Charset.forName(
                            file.getCharset()));

        } catch (Exception e) {
            return null;

        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception ignored) {
                }
            }
        }
    }
}
