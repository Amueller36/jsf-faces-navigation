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

    private static List<JavaScriptDefinition> filterDefinitionsToBeanPages(
            List<JavaScriptDefinition> definitions,
            List<BeanUsage> pages) {

        java.util.Set<String> paths = new java.util.HashSet<String>();

        if (pages != null) {
            for (BeanUsage usage : pages) {
                paths.add(usage.getResourcePath());
            }
        }

        java.util.List<JavaScriptDefinition> filtered =
                new java.util.ArrayList<JavaScriptDefinition>();

        if (definitions != null) {
            for (JavaScriptDefinition definition : definitions) {
                if (paths.contains(definition.getResourcePath())) {
                    filtered.add(definition);
                }
            }
        }

        return filtered;
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

                List<BeanUsage> pages = null;

                if (controllerBeanName != null && !controllerBeanName.isEmpty()) {
                    pages = index.findBeanUsages(controllerBeanName, projectName);

                    /*
                     * Prefer JavaScript definitions in XHTML files that actually
                     * reference the current controller bean. This avoids showing
                     * identically named helper functions from unrelated screens.
                     */
                    List<JavaScriptDefinition> controllerDefinitions =
                            filterDefinitionsToBeanPages(definitions, pages);

                    if (!controllerDefinitions.isEmpty()) {
                        definitions = controllerDefinitions;
                    }
                }

                JavaScriptDefinition selected =
                        NavigationChooser.chooseFunction(functionName, definitions);

                if (selected != null) {
                    WebEditorOpener.open(selected.getFile(), selected.getOffset());
                    return Status.OK_STATUS;
                }

                if (pages != null && !pages.isEmpty()) {
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
