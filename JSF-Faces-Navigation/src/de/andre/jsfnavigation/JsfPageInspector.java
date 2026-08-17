package de.andre.jsfnavigation;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IFile;
import org.eclipse.jdt.core.IType;

public final class JsfPageInspector {

    private static final Pattern EL_ROOT =
            Pattern.compile(
                    "[#\\$]\\{[^}]*?\\b([A-Za-z_$][A-Za-z0-9_$]*)\\s*(?=\\.|\\[|\\}|\\s)");

    private static final Pattern LOCAL_VAR =
            Pattern.compile(
                    "<\\s*(?:ui:param|c:set|ui:repeat|a4j:repeat|h:dataTable|p:dataTable|rich:dataTable|f:selectItems)\\b[^>]*\\b(?:name|var)\\s*=\\s*(['\"])([A-Za-z_$][A-Za-z0-9_$]*)\\1",
                    Pattern.DOTALL);

    private JsfPageInspector() {
    }

    public static List<String> beanNames(IFile file) {
        String source = read(file);

        Set<String> unique =
                new LinkedHashSet<String>();

        if (source == null) {
            return new ArrayList<String>();
        }

        Matcher matcher =
                EL_ROOT.matcher(source);

        while (matcher.find()) {
            String name =
                    matcher.group(1);

            if (!isElKeyword(name)) {
                unique.add(name);
            }
        }

        return new ArrayList<String>(unique);
    }

    public static List<NavigationTarget> beanTargets(
            IFile file) {

        List<NavigationTarget> result =
                new ArrayList<NavigationTarget>();

        if (file == null) {
            return result;
        }

        BeanIndexService index =
                Activator.getBeanIndexService();

        if (index == null) {
            return result;
        }

        String project =
                file.getProject().getName();

        for (String beanName : beanNames(file)) {
            IType type =
                    index.resolve(
                            beanName,
                            project);

            if (type != null) {
                result.add(
                        new JavaTypeNavigationTarget(
                                type,
                                beanName
                                + " — "
                                + type.getFullyQualifiedName('.')));
            }
        }

        return result;
    }



    public static String resolveUiParamAlias(
            IFile file,
            String variableName) {

        String source = read(file);

        if (source == null || variableName == null) {
            return null;
        }

        Matcher matcher =
                UI_PARAM_ALIAS.matcher(source);

        while (matcher.find()) {
            if (variableName.equals(
                    matcher.group(2))) {

                return matcher.group(4);
            }
        }

        return null;
    }

    public static Set<String> localVariables(IFile file) {
        String source = read(file);

        Set<String> result =
                new LinkedHashSet<String>();

        if (source == null) {
            return result;
        }

        Matcher matcher =
                LOCAL_VAR.matcher(source);

        while (matcher.find()) {
            result.add(matcher.group(2));
        }

        return result;
    }

    private static boolean isElKeyword(String value) {
        return "true".equals(value)
                || "false".equals(value)
                || "null".equals(value)
                || "empty".equals(value)
                || "and".equals(value)
                || "or".equals(value)
                || "not".equals(value)
                || "eq".equals(value)
                || "ne".equals(value)
                || "lt".equals(value)
                || "gt".equals(value)
                || "le".equals(value)
                || "ge".equals(value)
                || "div".equals(value)
                || "mod".equals(value);
    }

    public static String read(IFile file) {
        if (file == null || !file.exists()) {
            return null;
        }

        InputStream in = null;

        try {
            in = file.getContents();

            ByteArrayOutputStream out =
                    new ByteArrayOutputStream();

            byte[] buffer =
                    new byte[8192];

            int count;

            while ((count = in.read(buffer)) >= 0) {
                out.write(buffer, 0, count);
            }

            return new String(
                    out.toByteArray(),
                    Charset.forName(
                            file.getCharset()));

        } catch (Exception e) {
            return null;

        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception ignored) {
                }
            }
        }
    }
}
