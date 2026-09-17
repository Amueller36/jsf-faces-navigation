package de.andre.jsfnavigation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.ITextOperationTarget;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.text.contentassist.CompletionProposal;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.window.Window;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.ElementListSelectionDialog;
import org.eclipse.ui.texteditor.ITextEditor;

public final class ElCompletionHandler
        extends AbstractHandler {

    @Override
    public Object execute(
            ExecutionEvent event)
            throws ExecutionException {

        ITextEditor editor =
                MethodContext.activeTextEditor();

        if (editor == null) {
            return null;
        }

        ISelection selection =
                editor.getSelectionProvider()
                        .getSelection();

        if (!(selection
                instanceof ITextSelection)) {

            return null;
        }

        int offset =
                ((ITextSelection) selection)
                        .getOffset();

        IDocument document =
                editor.getDocumentProvider()
                        .getDocument(
                                editor.getEditorInput());

        if (document == null) {
            return null;
        }

        CompletionContext context =
                context(
                        document,
                        offset);

        JsfMarkupCompletionContext markupContext =
                JsfMarkupCompletionContext
                        .detect(
                                document,
                                offset);

        if (context != null
                || markupContext != null) {

            if (invokeNativeContentAssist(
                    editor)) {

                return null;
            }
        }

        /*
         * Compatibility fallback for unusual/older WTP editor
         * configurations that do not expose the standard content-assist
         * operation.
         */
        if (context != null) {
            completeEl(
                    editor,
                    document,
                    offset,
                    context);

            return null;
        }

        completeMarkup(
                editor,
                document,
                offset);

        return null;
    }


    static boolean invokeNativeContentAssist(
            ITextEditor editor) {

        if (editor == null) {
            return false;
        }

        ITextOperationTarget operation =
                (ITextOperationTarget)
                        editor.getAdapter(
                                ITextOperationTarget.class);

        if (operation == null
                || !operation.canDoOperation(
                        ISourceViewer.CONTENTASSIST_PROPOSALS)) {

            return false;
        }

        operation.doOperation(
                ISourceViewer.CONTENTASSIST_PROPOSALS);

        return true;
    }

    static List<ICompletionProposal> nativeElProposals(
            IDocument document,
            int offset,
            IFile file) {

        CompletionContext context =
                context(
                        document,
                        offset);

        if (context == null) {
            return Collections.emptyList();
        }

        String project =
                file == null
                        ? null
                        : file.getProject()
                                .getName();

        IType type =
                ElJavaResolver.resolveBean(
                        context.beanName,
                        project);

        if (type == null
                && file != null) {

            String alias =
                    JsfPageInspector
                            .resolveUiParamAlias(
                                    file,
                                    context.beanName);

            if (alias != null) {
                type =
                        ElJavaResolver.resolveBean(
                                alias,
                                project);
            }
        }

        if (type == null
                && file != null) {

            type =
                    JsfLocalVariableTypeResolver
                            .resolve(
                                    file,
                                    context.beanName,
                                    project);
        }

        if (type == null) {
            return Collections.emptyList();
        }

        try {
            for (String property :
                    context.resolvedProperties) {

                JavaMemberTarget target =
                        JavaPropertyResolver
                                .resolve(
                                        type,
                                        property);

                if (target == null) {
                    return Collections.emptyList();
                }

                type =
                        JavaReturnTypeResolver
                                .resolve(
                                        type,
                                        target);

                if (type == null) {
                    return Collections.emptyList();
                }
            }

            List<String> values =
                    proposals(
                            type);

            List<ICompletionProposal> result =
                    new ArrayList<ICompletionProposal>();

            for (String proposal :
                    values) {

                if (!context.prefix
                        .isEmpty()
                        && !proposal
                                .toLowerCase()
                                .startsWith(
                                        context.prefix
                                                .toLowerCase())) {

                    continue;
                }

                result.add(
                        new CompletionProposal(
                                proposal,
                                context.prefixOffset,
                                context.prefix.length(),
                                proposal.length(),
                                null,
                                proposal,
                                null,
                                "JSF EL member of "
                                        + type.getFullyQualifiedName()));
            }

            return result;

        } catch (JavaModelException e) {
            return Collections.emptyList();
        }
    }

    private static void completeEl(
            ITextEditor editor,
            IDocument document,
            int offset,
            CompletionContext context) {

        IFile file =
                EditorContext.currentFile();

        String project =
                file == null
                        ? null
                        : file.getProject()
                                .getName();

        IType type =
                ElJavaResolver.resolveBean(
                        context.beanName,
                        project);

        if (type == null
                && file != null) {

            String alias =
                    JsfPageInspector
                            .resolveUiParamAlias(
                                    file,
                                    context.beanName);

            if (alias != null) {
                type =
                        ElJavaResolver.resolveBean(
                                alias,
                                project);
            }
        }

        if (type == null
                && file != null) {

            type =
                    JsfLocalVariableTypeResolver
                            .resolve(
                                    file,
                                    context.beanName,
                                    project);
        }

        if (type == null) {
            return;
        }

        try {
            for (String property :
                    context.resolvedProperties) {

                JavaMemberTarget target =
                        JavaPropertyResolver
                                .resolve(
                                        type,
                                        property);

                if (target == null) {
                    return;
                }

                type =
                        JavaReturnTypeResolver
                                .resolve(
                                        type,
                                        target);

                if (type == null) {
                    return;
                }
            }

            List<String> proposals =
                    proposals(type);

            if (!context.prefix
                    .isEmpty()) {

                List<String> filtered =
                        new ArrayList<String>();

                for (String proposal :
                        proposals) {

                    if (proposal
                            .toLowerCase()
                            .startsWith(
                                    context.prefix
                                            .toLowerCase())) {

                        filtered.add(
                                proposal);
                    }
                }

                proposals = filtered;
            }

            String selected =
                    chooseEl(
                            proposals);

            if (selected != null) {
                document.replace(
                        context.prefixOffset,
                        context.prefix.length(),
                        selected);

                editor.selectAndReveal(
                        context.prefixOffset
                                + selected.length(),
                        0);
            }

        } catch (JavaModelException e) {
            // Keep completion non-disruptive if JDT is rebuilding.

        } catch (BadLocationException e) {
            // Document changed while the chooser was open.
        }
    }

    private static void completeMarkup(
            ITextEditor editor,
            IDocument document,
            int offset) {

        JsfMarkupCompletionContext context =
                JsfMarkupCompletionContext
                        .detect(
                                document,
                                offset);

        if (context == null) {
            return;
        }

        IFile file =
                EditorContext.currentFile();

        List<JsfComponentProposal> proposals;

        if (context.getKind()
                == JsfMarkupCompletionContext.TAG) {

            proposals =
                    JsfTaglibCatalogService
                            .tagProposals(
                                    file,
                                    document,
                                    context);

        } else {
            proposals =
                    JsfTaglibCatalogService
                            .attributeProposals(
                                    file,
                                    document,
                                    context);
        }

        JsfComponentProposal selected =
                chooseMarkup(
                        proposals,
                        context.getKind());

        if (selected == null) {
            return;
        }

        try {
            document.replace(
                    context.getReplaceOffset(),
                    context.getReplaceLength(),
                    selected.getInsertText());

            int caret =
                    context.getReplaceOffset()
                    + selected.getInsertText()
                            .length();

            if (selected.isAttribute()
                    && selected.getInsertText()
                            .endsWith("=\"\"")) {

                /*
                 * Put the caret between the newly inserted quotes.
                 */
                caret--;
            }

            editor.selectAndReveal(
                    caret,
                    0);

        } catch (BadLocationException e) {
            // Document changed while the chooser was open.
        }
    }

    private static CompletionContext context(
            IDocument document,
            int offset) {

        String text =
                document.get();

        int hash =
                text.lastIndexOf(
                        "#{",
                        offset);

        int dollar =
                text.lastIndexOf(
                        "${",
                        offset);

        int start =
                Math.max(
                        hash,
                        dollar);

        if (start < 0) {
            return null;
        }

        int close =
                text.indexOf(
                        '}',
                        start + 2);

        if (close >= 0
                && close < offset) {

            return null;
        }

        String before =
                text.substring(
                        start + 2,
                        offset)
                        .trim();

        if (before.indexOf(' ') >= 0
                || before.indexOf('(') >= 0
                || before.indexOf('[') >= 0) {

            return null;
        }

        int dot =
                before.lastIndexOf('.');

        if (dot < 0) {
            return null;
        }

        String chain =
                before.substring(
                        0,
                        dot);

        String prefix =
                before.substring(
                        dot + 1);

        List<String> parts =
                ElJavaResolver
                        .splitSimpleChain(
                                chain);

        if (parts.isEmpty()) {
            return null;
        }

        String bean =
                parts.remove(0);

        return new CompletionContext(
                bean,
                parts,
                prefix,
                offset - prefix.length());
    }

    private static List<String> proposals(
            IType type)
            throws JavaModelException {

        Map<String, String> unique =
                new LinkedHashMap<String, String>();

        collect(
                type,
                unique);

        ITypeHierarchy hierarchy =
                type.newSupertypeHierarchy(
                        null);

        for (IType superType :
                hierarchy.getAllSupertypes(
                        type)) {

            collect(
                    superType,
                    unique);
        }

        List<String> result =
                new ArrayList<String>(
                        unique.values());

        Collections.sort(
                result,
                new Comparator<String>() {
                    @Override
                    public int compare(
                            String left,
                            String right) {

                        return left
                                .compareToIgnoreCase(
                                        right);
                    }
                });

        return result;
    }

    private static void collect(
            IType type,
            Map<String, String> unique)
            throws JavaModelException {

        for (IMethod method :
                type.getMethods()) {

            String name =
                    method.getElementName();

            if (method.getNumberOfParameters()
                    == 0
                    && name.startsWith("get")
                    && name.length() > 3
                    && !"getClass".equals(
                            name)) {

                String property =
                        decapitalize(
                                name.substring(3));

                unique.put(
                        property,
                        property);

            } else if (method
                    .getNumberOfParameters()
                    == 0
                    && name.startsWith("is")
                    && name.length() > 2) {

                String property =
                        decapitalize(
                                name.substring(2));

                unique.put(
                        property,
                        property);

            } else {
                unique.put(
                        name,
                        name);
            }
        }

        for (IField field :
                type.getFields()) {

            unique.put(
                    field.getElementName(),
                    field.getElementName());
        }
    }

    private static String decapitalize(
            String value) {

        if (value.length() > 1
                && Character.isUpperCase(
                        value.charAt(0))
                && Character.isUpperCase(
                        value.charAt(1))) {

            return value;
        }

        return Character.toLowerCase(
                value.charAt(0))
                + value.substring(1);
    }

    private static String chooseEl(
            final List<String> proposals) {

        if (proposals == null
                || proposals.isEmpty()) {

            return null;
        }

        if (proposals.size() == 1) {
            return proposals.get(0);
        }

        final String[] selected =
                new String[1];

        PlatformUI.getWorkbench()
                .getDisplay()
                .syncExec(
                        new Runnable() {
                            @Override
                            public void run() {

                                Shell shell =
                                        PlatformUI
                                                .getWorkbench()
                                                .getActiveWorkbenchWindow()
                                                .getShell();

                                ElementListSelectionDialog dialog =
                                        new ElementListSelectionDialog(
                                                shell,
                                                new LabelProvider());

                                dialog.setTitle(
                                        "JSF EL Completion");

                                dialog.setMessage(
                                        "Select a bean property or method:");

                                dialog.setElements(
                                        proposals.toArray());

                                if (dialog.open()
                                        == Window.OK) {

                                    selected[0] =
                                            (String)
                                                    dialog.getFirstResult();
                                }
                            }
                        });

        return selected[0];
    }

    private static JsfComponentProposal chooseMarkup(
            final List<JsfComponentProposal> proposals,
            final int kind) {

        if (proposals == null
                || proposals.isEmpty()) {

            return null;
        }

        if (proposals.size() == 1) {
            return proposals.get(0);
        }

        final JsfComponentProposal[] selected =
                new JsfComponentProposal[1];

        PlatformUI.getWorkbench()
                .getDisplay()
                .syncExec(
                        new Runnable() {
                            @Override
                            public void run() {

                                Shell shell =
                                        PlatformUI
                                                .getWorkbench()
                                                .getActiveWorkbenchWindow()
                                                .getShell();

                                ElementListSelectionDialog dialog =
                                        new ElementListSelectionDialog(
                                                shell,
                                                new LabelProvider() {
                                                    @Override
                                                    public String getText(
                                                            Object element) {

                                                        return ((JsfComponentProposal)
                                                                element)
                                                                .displayText();
                                                    }
                                                });

                                dialog.setTitle(
                                        kind
                                                == JsfMarkupCompletionContext.TAG
                                                        ? "JSF / PrimeFaces Component Completion"
                                                        : "JSF / PrimeFaces Attribute Completion");

                                dialog.setMessage(
                                        kind
                                                == JsfMarkupCompletionContext.TAG
                                                        ? "Select a component/tag:"
                                                        : "Select an attribute:");

                                dialog.setElements(
                                        proposals.toArray());

                                if (dialog.open()
                                        == Window.OK) {

                                    selected[0] =
                                            (JsfComponentProposal)
                                                    dialog.getFirstResult();
                                }
                            }
                        });

        return selected[0];
    }

    private static final class CompletionContext {

        final String beanName;
        final List<String> resolvedProperties;
        final String prefix;
        final int prefixOffset;

        CompletionContext(
                String beanName,
                List<String> resolvedProperties,
                String prefix,
                int prefixOffset) {

            this.beanName = beanName;
            this.resolvedProperties =
                    resolvedProperties;
            this.prefix = prefix;
            this.prefixOffset = prefixOffset;
        }
    }
}
