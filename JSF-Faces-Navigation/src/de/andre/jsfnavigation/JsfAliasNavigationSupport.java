package de.andre.jsfnavigation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IFile;
import org.eclipse.jdt.core.IType;
import org.eclipse.jface.text.IDocument;

public final class JsfAliasNavigationSupport {

    private static final Pattern TAG =
            Pattern.compile(
                    "<\\s*[A-Za-z_][A-Za-z0-9_.-]*:[A-Za-z_][A-Za-z0-9_.-]*\\b([^<>]*?)>",
                    Pattern.DOTALL);

    private static final Pattern ATTRIBUTE =
            Pattern.compile(
                    "([A-Za-z_:][A-Za-z0-9_:.-]*)\\s*=\\s*(\"([^\"]*)\"|'([^']*)')",
                    Pattern.DOTALL);

    private static final Pattern EL_ROOT =
            Pattern.compile(
                    "[#$]\\{\\s*([A-Za-z_$][A-Za-z0-9_$]*)");

    private JsfAliasNavigationSupport() {
    }

    public static List<NavigationTarget> resolve(
            String alias,
            IFile currentFile,
            IDocument document) {

        List<NavigationTarget> result =
                new ArrayList<NavigationTarget>();

        if (alias == null
                || alias.trim().isEmpty()
                || currentFile == null
                || document == null) {

            return result;
        }

        String wanted =
                alias.trim();

        BeanIndexService index =
                Activator.getBeanIndexService();

        if (index == null) {
            return result;
        }

        Map<String, NavigationTarget> unique =
                new LinkedHashMap<String, NavigationTarget>();

        Matcher tag =
                TAG.matcher(
                        document.get());

        while (tag.find()) {
            String attributesText =
                    tag.group(1);

            Matcher attributes =
                    ATTRIBUTE.matcher(
                            attributesText);

            boolean declaresAlias =
                    false;

            List<String> values =
                    new ArrayList<String>();

            while (attributes.find()) {
                String attributeName =
                        attributes.group(1);

                String value =
                        attributes.group(3) != null
                                ? attributes.group(3)
                                : attributes.group(4);

                if (isAliasAttribute(
                        attributeName)
                        && wanted.equals(
                                value.trim())) {

                    declaresAlias = true;
                }

                values.add(
                        value);
            }

            if (!declaresAlias) {
                continue;
            }

            for (String value : values) {
                Matcher expression =
                        EL_ROOT.matcher(
                                value);

                while (expression.find()) {
                    String beanName =
                            expression.group(1);

                    IType type =
                            index.resolve(
                                    beanName,
                                    currentFile.getProject()
                                            .getName());

                    if (type == null
                            || !type.exists()) {

                        continue;
                    }

                    JavaTypeNavigationTarget target =
                            new JavaTypeNavigationTarget(
                                    type,
                                    "alias '"
                                    + wanted
                                    + "' -> "
                                    + type.getElementName());

                    unique.put(
                            target.getIdentity(),
                            target);
                }
            }
        }

        result.addAll(
                unique.values());

        return result;
    }

    private static boolean isAliasAttribute(
            String name) {

        if (name == null) {
            return false;
        }

        return "name".equals(name)
                || "alias".equals(name)
                || "beanName".equals(name)
                || "validatorName".equals(name)
                || "converterName".equals(name)
                || "handlerName".equals(name)
                || "validatorId".equals(name)
                || "converterId".equals(name);
    }
}
