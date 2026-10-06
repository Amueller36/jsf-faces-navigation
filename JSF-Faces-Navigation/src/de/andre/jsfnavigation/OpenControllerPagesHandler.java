package de.andre.jsfnavigation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

public final class OpenControllerPagesHandler
        extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event)
            throws ExecutionException {

        IFile file = EditorContext.currentFile();

        if (file == null
                || !"java".equalsIgnoreCase(
                        file.getFileExtension())) {

            return null;
        }

        ICompilationUnit unit =
                JavaCore.createCompilationUnitFrom(file);

        if (unit == null || !unit.exists()) {
            return null;
        }

        try {
            IType[] types = unit.getTypes();

            if (types.length == 0) {
                return null;
            }

            IType type = types[0];
            String beanName =
                    BeanIntrospector.beanNameOf(type);

            if (beanName == null) {
                return null;
            }

            WebIndexService index =
                    Activator.getWebIndexService();

            if (index == null) {
                return null;
            }

            List<BeanUsage> usages =
                    index.findBeanUsages(
                            beanName,
                            file.getProject().getName());

            Map<String, NavigationTarget> unique =
                    new LinkedHashMap<String, NavigationTarget>();

            for (BeanUsage usage : usages) {
                IFile page = usage.getFile();

                if (page != null && page.exists()) {
                    NavigationTarget target =
                            new WebNavigationTarget(
                                    page,
                                    usage.getOffset(),
                                    page.getProjectRelativePath()
                                        .toPortableString()
                                    + " — #{"
                                    + beanName
                                    + "...}");

                    unique.put(
                            target.getIdentity(),
                            target);
                }
            }

            List<NavigationTarget> targets =
                    new ArrayList<NavigationTarget>(
                            unique.values());

            NavigationTarget selected =
                    MethodNavigationChooser.choose(
                            "Open Controller Page",
                            "Pages using bean '"
                                    + beanName
                                    + "':",
                            targets);

            if (selected != null) {
                selected.open();
            }

        } catch (JavaModelException e) {
            // Keep navigation non-disruptive while JDT rebuilds.
        }

        return null;
    }
}
