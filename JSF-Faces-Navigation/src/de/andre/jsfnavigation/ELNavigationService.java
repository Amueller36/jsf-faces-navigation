package de.andre.jsfnavigation;

import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;

public final class ELNavigationService {

    private ELNavigationService() {
    }

    public static void navigate(
            final ELSelection selection) {

        Job job = new Job(
                "Resolve JSF EL declaration") {

            @Override
            protected IStatus run(
                    IProgressMonitor monitor) {

                resolveAndOpen(selection);

                return Status.OK_STATUS;
            }
        };

        job.setSystem(true);
        job.schedule();
    }

    private static void resolveAndOpen(
            ELSelection selection) {

        try {
            List<String> parts =
                    selection.getExpression()
                            .getParts();

            if (parts.isEmpty()) {
                return;
            }

            BeanIndexService index =
                    Activator.getBeanIndexService();

            if (index == null) {
                return;
            }

            IType currentType =
                    index.resolve(
                            parts.get(0),
                            selection
                                    .getPreferredProjectName());

            if (currentType == null) {
                return;
            }

            if (selection.getPartIndex() == 0) {
                JavaEditorOpener.open(currentType);
                return;
            }

            for (int i = 1;
                    i <= selection.getPartIndex();
                    i++) {

                JavaMemberTarget target =
                        JavaPropertyResolver.resolve(
                                currentType,
                                parts.get(i));

                if (target == null) {
                    return;
                }

                if (i == selection.getPartIndex()) {
                    JavaEditorOpener.open(target);
                    return;
                }

                IType nextType =
                        JavaReturnTypeResolver.resolve(
                                currentType,
                                target);

                if (nextType == null) {
                    return;
                }

                currentType = nextType;
            }

        } catch (JavaModelException e) {
            e.printStackTrace();
        }
    }
}
