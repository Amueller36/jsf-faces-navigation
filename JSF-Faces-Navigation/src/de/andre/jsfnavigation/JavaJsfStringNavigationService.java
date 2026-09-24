package de.andre.jsfnavigation;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;

public final class JavaJsfStringNavigationService {

    private JavaJsfStringNavigationService() {
    }

    public static List<NavigationTarget> resolve(
            JavaStringReference reference,
            IFile javaFile) {

        List<NavigationTarget> result =
                new ArrayList<NavigationTarget>();

        if (reference == null || javaFile == null) {
            return result;
        }

        IProject project = javaFile.getProject();

        switch (reference.getKind()) {
        case JavaStringReference.COMPONENT_ID:
            JsfViewIndexService index =
                    Activator.getJsfViewIndexService();

            if (index != null) {
                for (ViewSymbol symbol :
                        index.find(
                                ViewSymbol.COMPONENT_ID,
                                reference.getValue(),
                                project.getName())) {

                    IFile target = symbol.getFile();

                    if (target.exists()) {
                        result.add(
                                new WebNavigationTarget(
                                        target,
                                        symbol.getOffset(),
                                        target.getProjectRelativePath()
                                            .toPortableString()
                                        + " — component "
                                        + reference.getValue()));
                    }
                }
            }
            break;

        case JavaStringReference.NAMED_QUERY:
            result.addAll(
                    NamedQueryNavigationSearch.findDefinitions(
                            project,
                            reference.getValue()));
            break;

        case JavaStringReference.ROLE:
            result.addAll(
                    ProjectTextSearch.find(
                            project,
                            reference.getValue(),
                            new String[] {
                                    ".java",
                                    ".xhtml",
                                    ".xml",
                                    ".properties"
                            },
                            "role "
                                    + reference.getValue()));
            break;

        case JavaStringReference.OUTCOME:
            result.addAll(
                    resolveOutcome(
                            project,
                            reference.getValue()));
            break;

        default:
            break;
        }

        return result;
    }

    private static List<NavigationTarget> resolveOutcome(
            IProject project,
            String outcome) {

        List<NavigationTarget> result =
                new ArrayList<NavigationTarget>();

        String logicalOutcome = outcome;

        int query = logicalOutcome.indexOf('?');
        if (query >= 0) {
            logicalOutcome =
                    logicalOutcome.substring(0, query);
        }

        String page =
                logicalOutcome.endsWith(".xhtml")
                        ? logicalOutcome
                        : logicalOutcome + ".xhtml";

        String simpleName = page;

        int slash = Math.max(
                simpleName.lastIndexOf('/'),
                simpleName.lastIndexOf('\\'));

        if (slash >= 0) {
            simpleName =
                    simpleName.substring(slash + 1);
        }

        result.addAll(
                ProjectTextSearch.findFilesByName(
                        project,
                        simpleName,
                        "navigation outcome "
                                + outcome));

        result.addAll(
                ProjectTextSearch.find(
                        project,
                        "<from-outcome>"
                                + outcome
                                + "</from-outcome>",
                        new String[] { ".xml" },
                        "faces-config outcome "
                                + outcome));

        return result;
    }
}
