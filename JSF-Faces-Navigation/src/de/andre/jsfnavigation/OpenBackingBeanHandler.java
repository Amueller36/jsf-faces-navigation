package de.andre.jsfnavigation;

import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;

public final class OpenBackingBeanHandler
        extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event)
            throws ExecutionException {

        IFile file =
                EditorContext.currentFile();

        List<NavigationTarget> targets =
                JsfPageInspector.beanTargets(file);

        NavigationTarget selected =
                MethodNavigationChooser.choose(
                        "Open Backing Bean",
                        "Select a bean used by this JSF page:",
                        targets);

        if (selected != null) {
            selected.open();
        }

        return null;
    }
}
