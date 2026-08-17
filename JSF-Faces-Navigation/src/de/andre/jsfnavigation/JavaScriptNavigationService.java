package de.andre.jsfnavigation;

import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.text.IDocument;

public final class JavaScriptNavigationService {

    private JavaScriptNavigationService() {
    }

    public static void navigateFromXhtml(
            final JavaScriptCall call,
            final IDocument currentDocument,
            final IFile currentFile) {

        if (currentDocument != null) {
            List<Integer> local = JavaScriptDefinitionFinder.findOffsets(
                    currentDocument.get(),
                    call.getName());

            if (!local.isEmpty()) {
                WebEditorOpener.revealInCurrentEditor(local.get(0).intValue());
                return;
            }
        }

        final String projectName = currentFile == null
                ? null
                : currentFile.getProject().getName();

        navigateFunctionInBackground(call.getName(), projectName, null);
    }

    public static void navigateFromJava(
            final String functionName,
            final String projectName,
            final String controllerBeanName) {

        navigateFunctionInBackground(functionName, projectName, controllerBeanName);
    }

    private static void navigateFunctionInBackground(
            final String functionName,
            final String projectName,
            final String controllerBeanName) {

        Job job = new Job("Resolve JavaScript definition") {
            @Override
            protected IStatus run(IProgressMonitor monitor) {
                WebIndexService index = Activator.getWebIndexService();
                if (index == null) {
                    return Status.OK_STATUS;
                }

                List<JavaScriptDefinition> definitions =
                        index.findFunctions(functionName, projectName);

                JavaScriptDefinition selected =
                        NavigationChooser.chooseFunction(functionName, definitions);

                if (selected != null) {
                    WebEditorOpener.open(selected.getFile(), selected.getOffset());
                    return Status.OK_STATUS;
                }

                if (controllerBeanName != null && !controllerBeanName.isEmpty()) {
                    List<BeanUsage> pages = index.findBeanUsages(controllerBeanName, projectName);
                    BeanUsage page = NavigationChooser.choosePage(controllerBeanName, pages);
                    if (page != null) {
                        WebEditorOpener.open(page.getFile(), page.getOffset());
                    }
                }

                return Status.OK_STATUS;
            }
        };

        job.setSystem(true);
        job.schedule();
    }
}
