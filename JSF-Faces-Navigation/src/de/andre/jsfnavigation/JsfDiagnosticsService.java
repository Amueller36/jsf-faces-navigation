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
    }

    public void stop() {
        if (listener != null) {
            ResourcesPlugin.getWorkspace()
                    .removeResourceChangeListener(listener);
            listener = null;
        }
    }

    public void validate(IFile file) {
        if (file == null
                || !file.exists()
                || !isView(file.getName())) {

            return;
        }

        String source = read(file);

        if (source == null) {
            return;
        }

        try {
            file.deleteMarkers(
                    MARKER_TYPE,
                    false,
                    IResource.DEPTH_ZERO);
        } catch (CoreException e) {
            return;
        }

        validateIndexedReferences(file, source);
        validateEl(file, source);
        validateMethodAttributes(file, source);
    }

    private void validateIndexedReferences(
            IFile file,
            String source) {

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

                marker(
                        file,
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

                marker(
                        file,
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

                marker(
                        file,
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

                marker(
                        file,
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
                marker(
                        file,
                        symbol.getOffset(),
                        symbol.getName().length(),
                        IMarker.SEVERITY_WARNING,
                        "Resource bundle variable '"
                                + symbol.getAttributeName()
                                + "' could not be resolved.");
            } else if (JsfNavigationSupport.findPropertyKeyOffset(
                    properties,
                    symbol.getName()) < 0) {

                marker(
                        file,
                        symbol.getOffset(),
                        symbol.getName().length(),
                        IMarker.SEVERITY_WARNING,
                        "Resource bundle key '"
                                + symbol.getName()
                                + "' was not found.");
            }
        }
    }

    private void validateEl(
            IFile file,
            String source) {

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

                IType bean =
                        ElJavaResolver.resolveBean(
                                beanName,
                                project);

                int expressionOffset =
                        el.start(1)
                        + chain.start();

                if (bean == null) {
                    marker(
                            file,
                            expressionOffset,
                            beanName.length(),
                            IMarker.SEVERITY_WARNING,
                            "Unresolved JSF/CDI bean '#{"
                                    + beanName
                                    + "...}'.");
                    continue;
                }

                try {
                    JavaMemberTarget target =
                            ElJavaResolver.resolveChain(
                                    bean,
                                    parts);

                    if (target == null) {
                        String last =
                                parts.get(
                                        parts.size() - 1);

                        marker(
                                file,
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
                    // Avoid noisy diagnostics while JDT is rebuilding.
                }
            }
        }
    }

    private void validateMethodAttributes(
            IFile file,
            String source) {

        Matcher matcher =
                METHOD_ATTRIBUTE.matcher(source);

        String project =
                file.getProject().getName();

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
                    ElJavaResolver.resolveBean(
                            beanName,
                            project);

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
                    marker(
                            file,
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
                // Skip during transient model changes.
            }
        }
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
        job.schedule(500L);
    }

    private static void marker(
            IFile file,
            int start,
            int length,
            int severity,
            String message) {

        try {
            IMarker marker =
                    file.createMarker(
                            MARKER_TYPE);

            marker.setAttribute(
                    IMarker.MESSAGE,
                    message);

            marker.setAttribute(
                    IMarker.SEVERITY,
                    severity);

            marker.setAttribute(
                    IMarker.CHAR_START,
                    Math.max(0, start));

            marker.setAttribute(
                    IMarker.CHAR_END,
                    Math.max(
                            start,
                            start + length));

        } catch (CoreException e) {
            // Diagnostics must never interfere with editing.
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
