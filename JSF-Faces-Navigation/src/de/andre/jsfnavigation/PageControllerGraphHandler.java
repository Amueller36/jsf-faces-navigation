package de.andre.jsfnavigation;

import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;

public final class PageControllerGraphHandler
        extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event)
            throws ExecutionException {

        final IFile file =
                EditorContext.currentFile();

        if (file == null) {
            return null;
        }

        final String text =
                buildGraph(file);

        PlatformUI.getWorkbench()
                .getDisplay()
                .asyncExec(
                        new Runnable() {
                            @Override
                            public void run() {
                                Shell shell =
                                        PlatformUI.getWorkbench()
                                                .getActiveWorkbenchWindow()
                                                .getShell();

                                MessageDialog.openInformation(
                                        shell,
                                        "JSF Page / Controller Graph",
                                        text);
                            }
                        });

        return null;
    }

    private static String buildGraph(IFile file) {
        StringBuilder out =
                new StringBuilder();

        out.append(
                file.getProjectRelativePath()
                        .toPortableString())
                .append('\n');

        List<String> beans =
                JsfPageInspector.beanNames(file);

        out.append("\nBeans:\n");

        if (beans.isEmpty()) {
            out.append("  (none)\n");
        } else {
            for (String bean : beans) {
                out.append("  - ")
                        .append(bean)
                        .append('\n');
            }
        }

        JsfViewIndexService index =
                Activator.getJsfViewIndexService();

        if (index != null) {
            appendSymbols(
                    out,
                    "Includes",
                    index.symbolsInFile(
                            file,
                            ViewSymbol.INCLUDE));

            appendSymbols(
                    out,
                    "Templates",
                    index.symbolsInFile(
                            file,
                            ViewSymbol.TEMPLATE));

            appendSymbols(
                    out,
                    "widgetVars",
                    index.symbolsInFile(
                            file,
                            ViewSymbol.WIDGET_VAR));

            appendSymbols(
                    out,
                    "Component IDs",
                    index.symbolsInFile(
                            file,
                            ViewSymbol.COMPONENT_ID));
        }

        return out.toString();
    }

    private static void appendSymbols(
            StringBuilder out,
            String title,
            List<ViewSymbol> symbols) {

        out.append('\n')
                .append(title)
                .append(":\n");

        if (symbols.isEmpty()) {
            out.append("  (none)\n");
            return;
        }

        for (ViewSymbol symbol : symbols) {
            out.append("  - ")
                    .append(symbol.getName());

            if (symbol.getExtra() != null) {
                out.append("  [")
                        .append(symbol.getExtra())
                        .append(']');
            }

            out.append('\n');
        }
    }
}
