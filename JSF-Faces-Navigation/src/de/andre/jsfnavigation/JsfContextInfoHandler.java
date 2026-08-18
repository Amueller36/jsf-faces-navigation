package de.andre.jsfnavigation;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jdt.core.IAnnotation;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.texteditor.ITextEditor;

public final class JsfContextInfoHandler
        extends AbstractHandler {

    private static final Pattern ATTRIBUTE =
            Pattern.compile(
                    "\\b(update|process|render|reRender|execute)\\s*=\\s*(['\"])([^'\"]*)\\2");

    @Override
    public Object execute(ExecutionEvent event)
            throws ExecutionException {

        ITextEditor editor =
                MethodContext.activeTextEditor();

        IFile file =
                EditorContext.currentFile();

        if (editor == null || file == null) {
            return null;
        }

        ISelection selection =
                editor.getSelectionProvider()
                        .getSelection();

        if (!(selection instanceof ITextSelection)) {
            return null;
        }

        int offset =
                ((ITextSelection) selection).getOffset();

        IDocument document =
                editor.getDocumentProvider()
                        .getDocument(
                                editor.getEditorInput());

        if (document == null) {
            return null;
        }

        String info =
                beanInfo(
                        document,
                        offset,
                        file);

        if (info == null) {
            info =
                    ajaxInfo(
                            document.get(),
                            offset);
        }

        if (info != null) {
            show(info);
        }

        return null;
    }

    private static String beanInfo(
            IDocument document,
            int offset,
            IFile file) {

        ELExpression expression =
                ELExpressionParser.find(
                        document,
                        offset);

        if (expression == null
                || expression.getParts().isEmpty()) {

            return null;
        }

        String beanName =
                expression.getParts().get(0);

        IType type =
                ElJavaResolver.resolveBean(
                        beanName,
                        file.getProject().getName());

        String aliasTarget = null;

        if (type == null) {
            aliasTarget =
                    JsfPageInspector.resolveUiParamAlias(
                            file,
                            beanName);

            if (aliasTarget != null) {
                type =
                        ElJavaResolver.resolveBean(
                                aliasTarget,
                                file.getProject().getName());
            }
        }

        boolean localVariable = false;

        if (type == null) {
            type =
                    JsfLocalVariableTypeResolver.resolve(
                            file,
                            beanName,
                            file.getProject().getName());

            localVariable = type != null;
        }

        if (type == null) {
            return "Bean/Facelets variable '#{"
                    + beanName
                    + "}' could not be resolved.";
        }

        StringBuilder out =
                new StringBuilder();

        out.append(beanName);

        if (aliasTarget != null) {
            out.append(" -> ")
                    .append(aliasTarget)
                    .append(" (ui:param)");
        }

        if (localVariable) {
            out.append(" (local component variable)");
        }

        out.append("\n\nClass: ")
                .append(
                        type.getFullyQualifiedName('.'));

        try {
            String scope =
                    scopeOf(type);

            if (scope != null) {
                out.append("\nScope: ")
                        .append(scope);
            }

        } catch (JavaModelException e) {
            // Optional metadata.
        }

        WebIndexService web =
                Activator.getWebIndexService();

        if (web != null) {
            List<BeanUsage> usages =
                    web.findBeanUsages(
                            beanName,
                            file.getProject().getName());

            java.util.Set<String> pages =
                    new java.util.LinkedHashSet<String>();

            for (BeanUsage usage : usages) {
                pages.add(
                        usage.getResourcePath());
            }

            out.append("\nPages using bean: ")
                    .append(pages.size());

            int shown = 0;

            for (String page : pages) {
                if (shown >= 8) {
                    out.append("\n  ...");
                    break;
                }

                out.append("\n  ")
                        .append(page);
                shown++;
            }
        }

        return out.toString();
    }

    private static String ajaxInfo(
            String source,
            int offset) {

        Matcher matcher =
                ATTRIBUTE.matcher(source);

        while (matcher.find()) {
            if (offset < matcher.start(3)
                    || offset >= matcher.end(3)) {

                continue;
            }

            String attr =
                    matcher.group(1);

            String value =
                    matcher.group(3);

            if ("process".equals(attr)
                    || "execute".equals(attr)) {

                return attr
                        + "=\""
                        + value
                        + "\"\n\n"
                        + "Controls which JSF components participate in the request processing lifecycle. "
                        + "PrimeFaces commonly uses 'process'; RichFaces commonly uses 'execute'.\n\n"
                        + "@this = only the triggering component\n"
                        + "@form = the enclosing form\n"
                        + "@all = the complete view\n"
                        + "@none = no additional component.";
            }

            return attr
                    + "=\""
                    + value
                    + "\"\n\n"
                    + "Controls which components are re-rendered after the Ajax request. "
                    + "PrimeFaces commonly uses 'update'; RichFaces uses 'render' and older code may use 'reRender'.\n\n"
                    + "@this = triggering component\n"
                    + "@form = enclosing form\n"
                    + "@all = complete view.";
        }

        return null;
    }

    private static String scopeOf(IType type)
            throws JavaModelException {

        for (IAnnotation annotation :
                type.getAnnotations()) {

            String name =
                    annotation.getElementName();

            if (name.endsWith("ViewScoped")
                    || name.endsWith("RequestScoped")
                    || name.endsWith("SessionScoped")
                    || name.endsWith("ApplicationScoped")
                    || name.endsWith("ConversationScoped")
                    || name.endsWith("Dependent")) {

                return "@"
                        + simpleName(name);
            }
        }

        return null;
    }

    private static String simpleName(
            String value) {

        int dot =
                value.lastIndexOf('.');

        return dot >= 0
                ? value.substring(dot + 1)
                : value;
    }

    private static void show(
            final String info) {

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
                                        "JSF Context",
                                        info);
                            }
                        });
    }
}
