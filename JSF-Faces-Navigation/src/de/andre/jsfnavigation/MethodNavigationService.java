package de.andre.jsfnavigation;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IMethod;

public final class MethodNavigationService {

    public static final int GO_CALLER = 1;
    public static final int GO_CALLEE = 2;
    public static final int FOLLOW_CALLER_CHAIN = 3;
    public static final int FOLLOW_CALLEE_CHAIN = 4;

    private MethodNavigationService() {
    }

    public static void execute(
            final IMethod method,
            final int mode) {

        if (method == null) {
            return;
        }

        Job job =
                new Job("Resolve method navigation") {
                    @Override
                    protected IStatus run(
                            IProgressMonitor monitor) {

                        switch (mode) {
                        case GO_CALLER:
                            goCaller(method);
                            break;

                        case GO_CALLEE:
                            goCallee(method);
                            break;

                        case FOLLOW_CALLER_CHAIN:
                            followCallerChain(method);
                            break;

                        case FOLLOW_CALLEE_CHAIN:
                            followCalleeChain(method);
                            break;

                        default:
                            break;
                        }

                        return Status.OK_STATUS;
                    }
                };

        job.setSystem(true);
        job.schedule();
    }

    private static void goCaller(IMethod method) {
        List<NavigationTarget> callers =
                CallerSearch.findDirectCallers(method);

        NavigationTarget selected =
                MethodNavigationChooser.choose(
                        "Go to Caller",
                        callers.isEmpty()
                                ? "No direct caller was found."
                                : "Select a direct caller of "
                                        + method.getElementName()
                                        + "(...):",
                        callers);

        if (selected != null) {
            selected.open();
        }
    }

    private static void goCallee(IMethod method) {
        List<NavigationTarget> callees =
                CalleeSearch.findDirectCallees(method);

        NavigationTarget selected =
                MethodNavigationChooser.choose(
                        "Go to Callee",
                        callees.isEmpty()
                                ? "No project callee was found."
                                : "Select a direct project callee of "
                                        + method.getElementName()
                                        + "(...):",
                        callees);

        if (selected != null) {
            selected.open();
        }
    }

    private static void followCallerChain(
            IMethod start) {

        IMethod current = start;
        boolean moved = false;

        Set<String> visited =
                new HashSet<String>();

        while (current != null
                && visited.add(
                        current.getHandleIdentifier())) {

            List<NavigationTarget> callers =
                    CallerSearch.findDirectCallers(
                            current);

            if (callers.size() == 1) {
                NavigationTarget only =
                        callers.get(0);

                if (only instanceof JavaNavigationTarget) {
                    current =
                            ((JavaNavigationTarget) only)
                                    .getMethod();

                    moved = true;
                    continue;
                }

                /*
                 * XHTML is a natural terminal caller/entry point.
                 */
                only.open();
                return;
            }

            if (!moved && callers.size() > 1) {
                NavigationTarget selected =
                        MethodNavigationChooser.choose(
                                "Follow Single Caller Chain",
                                "The first method has multiple callers. Select one:",
                                callers);

                if (selected != null) {
                    selected.open();
                }

                return;
            }

            break;
        }

        if (moved && current != null) {
            JavaEditorOpener.open(current);
        }
    }

    private static void followCalleeChain(
            IMethod start) {

        IMethod current = start;
        boolean moved = false;

        Set<String> visited =
                new HashSet<String>();

        while (current != null
                && visited.add(
                        current.getHandleIdentifier())) {

            List<NavigationTarget> callees =
                    CalleeSearch.findDirectCallees(
                            current);

            if (callees.size() == 1
                    && callees.get(0)
                            instanceof JavaNavigationTarget) {

                current =
                        ((JavaNavigationTarget)
                                callees.get(0))
                                .getMethod();

                moved = true;
                continue;
            }

            if (!moved && callees.size() > 1) {
                NavigationTarget selected =
                        MethodNavigationChooser.choose(
                                "Follow Single Callee Chain",
                                "The first method has multiple project callees. Select one:",
                                callees);

                if (selected != null) {
                    selected.open();
                }

                return;
            }

            break;
        }

        if (moved && current != null) {
            JavaEditorOpener.open(current);
        }
    }
}
