package de.andre.jsfnavigation;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

public final class StackTraceNavigator {

    private static final Pattern STACK_TRACE =
            Pattern.compile(
                    "\\bat\\s+([A-Za-z_$][A-Za-z0-9_$.]*)"
                    + "\\.([A-Za-z_$<>][A-Za-z0-9_$<>]*)"
                    + "\\(([^():]+\\.java):(\\d+)\\)");

    private StackTraceNavigator() {
    }

    public static boolean looksNavigable(
            String line) {

        return parse(line) != null;
    }

    public static void open(
            final String line) {

        final StackFrame frame =
                parse(line);

        if (frame == null) {
            return;
        }

        Job job =
                new Job("Resolve stack trace source") {
                    @Override
                    protected IStatus run(
                            IProgressMonitor monitor) {

                        List<NavigationTarget> targets =
                                resolve(frame);

                        NavigationTarget selected =
                                MethodNavigationChooser.choose(
                                        "Open Stack Trace Source",
                                        "Select source for "
                                                + frame.className
                                                + ":"
                                                + frame.lineNumber,
                                        targets);

                        if (selected != null) {
                            selected.open();
                        } else {
                            WebSphereStatusLine.show(
                                    "No workspace source found for "
                                    + frame.className);
                        }

                        return Status.OK_STATUS;
                    }
                };

        job.setSystem(true);
        job.schedule();
    }

    private static List<NavigationTarget> resolve(
            StackFrame frame) {

        List<NavigationTarget> result =
                new ArrayList<NavigationTarget>();

        String outerType =
                outerTypeName(
                        frame.className);

        IProject[] projects =
                ResourcesPlugin.getWorkspace()
                        .getRoot()
                        .getProjects();

        for (IProject project : projects) {
            if (!project.isOpen()) {
                continue;
            }

            try {
                if (!project.hasNature(
                        JavaCore.NATURE_ID)) {

                    continue;
                }

            } catch (CoreException e) {
                continue;
            }

            IJavaProject javaProject =
                    JavaCore.create(project);

            try {
                IType type =
                        javaProject.findType(
                                outerType);

                if (type == null
                        || !type.exists()) {

                    continue;
                }

                ICompilationUnit unit =
                        type.getCompilationUnit();

                if (unit == null
                        || !(unit.getResource()
                                instanceof IFile)) {

                    continue;
                }

                IFile file =
                        (IFile) unit.getResource();

                int offset =
                        lineOffset(
                                unit,
                                frame.lineNumber);

                result.add(
                        new JavaSourceLineTarget(
                                file,
                                offset,
                                project.getName()
                                + " — "
                                + outerType
                                + ":"
                                + frame.lineNumber));

            } catch (JavaModelException e) {
                // Continue with other projects.
            }
        }

        return result;
    }

    private static int lineOffset(
            ICompilationUnit unit,
            int oneBasedLine)
            throws JavaModelException {

        IBuffer buffer =
                unit.getBuffer();

        if (buffer == null) {
            return 0;
        }

        String source =
                buffer.getContents();

        if (source == null
                || source.isEmpty()
                || oneBasedLine <= 1) {

            return 0;
        }

        int line = 1;

        for (int i = 0;
                i < source.length();
                i++) {

            char c =
                    source.charAt(i);

            if (c == '\n') {
                line++;

                if (line == oneBasedLine) {
                    return i + 1;
                }

            } else if (c == '\r') {
                if (i + 1 < source.length()
                        && source.charAt(i + 1)
                                == '\n') {

                    i++;
                }

                line++;

                if (line == oneBasedLine) {
                    return i + 1;
                }
            }
        }

        return 0;
    }

    private static StackFrame parse(
            String line) {

        if (line == null) {
            return null;
        }

        Matcher matcher =
                STACK_TRACE.matcher(line);

        if (!matcher.find()) {
            return null;
        }

        try {
            return new StackFrame(
                    matcher.group(1),
                    matcher.group(3),
                    Integer.parseInt(
                            matcher.group(4)));

        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String outerTypeName(
            String className) {

        int dollar =
                className.indexOf('$');

        return dollar >= 0
                ? className.substring(
                        0,
                        dollar)
                : className;
    }

    private static final class StackFrame {
        final String className;
        final String sourceFile;
        final int lineNumber;

        StackFrame(
                String className,
                String sourceFile,
                int lineNumber) {

            this.className = className;
            this.sourceFile = sourceFile;
            this.lineNumber = lineNumber;
        }
    }
}
