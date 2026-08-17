package de.andre.jsfnavigation;

import java.util.List;

import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.ElementListSelectionDialog;

public final class NavigationChooser {

    private NavigationChooser() {
    }

    public static JavaScriptDefinition chooseFunction(
            final String functionName,
            final List<JavaScriptDefinition> choices) {

        if (choices == null || choices.isEmpty()) {
            return null;
        }

        if (choices.size() == 1) {
            return choices.get(0);
        }

        final JavaScriptDefinition[] selected = new JavaScriptDefinition[1];

        PlatformUI.getWorkbench().getDisplay().syncExec(new Runnable() {
            @Override
            public void run() {
                Shell shell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
                ElementListSelectionDialog dialog = new ElementListSelectionDialog(
                        shell,
                        new LabelProvider() {
                            @Override
                            public String getText(Object element) {
                                JavaScriptDefinition definition = (JavaScriptDefinition) element;
                                return definition.getResourcePath() + "  [" + definition.getFunctionName() + "]";
                            }
                        });

                dialog.setTitle("Select JavaScript definition");
                dialog.setMessage("Multiple definitions of '" + functionName + "' were found:");
                dialog.setElements(choices.toArray());

                if (dialog.open() == Window.OK) {
                    selected[0] = (JavaScriptDefinition) dialog.getFirstResult();
                }
            }
        });

        return selected[0];
    }

    public static BeanUsage choosePage(
            final String beanName,
            final List<BeanUsage> choices) {

        if (choices == null || choices.isEmpty()) {
            return null;
        }

        if (choices.size() == 1) {
            return choices.get(0);
        }

        final BeanUsage[] selected = new BeanUsage[1];

        PlatformUI.getWorkbench().getDisplay().syncExec(new Runnable() {
            @Override
            public void run() {
                Shell shell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
                ElementListSelectionDialog dialog = new ElementListSelectionDialog(
                        shell,
                        new LabelProvider() {
                            @Override
                            public String getText(Object element) {
                                BeanUsage usage = (BeanUsage) element;
                                return usage.getResourcePath();
                            }
                        });

                dialog.setTitle("Select JSF page");
                dialog.setMessage("The controller '" + beanName + "' is used by multiple pages:");
                dialog.setElements(uniquePages(choices).toArray());

                if (dialog.open() == Window.OK) {
                    selected[0] = (BeanUsage) dialog.getFirstResult();
                }
            }
        });

        return selected[0];
    }

    private static java.util.List<BeanUsage> uniquePages(List<BeanUsage> choices) {
        java.util.Map<String, BeanUsage> unique = new java.util.LinkedHashMap<String, BeanUsage>();
        for (BeanUsage usage : choices) {
            if (!unique.containsKey(usage.getResourcePath())) {
                unique.put(usage.getResourcePath(), usage);
            }
        }
        return new java.util.ArrayList<BeanUsage>(unique.values());
    }
}
