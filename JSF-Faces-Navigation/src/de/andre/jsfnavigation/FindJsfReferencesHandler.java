package de.andre.jsfnavigation;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.texteditor.ITextEditor;

public final class FindJsfReferencesHandler
        extends AbstractHandler {

    private static final Pattern DEFINITION =
            Pattern.compile(
                    "\\b(id|widgetVar)\\s*=\\s*(['\"])([^'\"]+)\\2");

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
                editor.getSelectionProvider().getSelection();

        if (!(selection instanceof ITextSelection)) {
            return null;
        }

        int offset =
                ((ITextSelection) selection).getOffset();

        IDocument document =
                editor.getDocumentProvider()
                        .getDocument(
                                editor.getEditorInput());

        Definition definition =
                find(document.get(), offset);

        if (definition == null) {
            return null;
        }

        List<NavigationTarget> refs;

        if ("widgetVar".equals(
                definition.attribute)) {

            refs =
                    JsfNavigationSupport
                            .reverseWidgetReferences(
                                    definition.name,
                                    file);
        } else {
            refs =
                    JsfNavigationSupport
                            .reverseComponentReferences(
                                    definition.name,
                                    file);
        }

        NavigationTarget selected =
                MethodNavigationChooser.choose(
                        "Find JSF References",
                        "References to "
                                + definition.attribute
                                + "='"
                                + definition.name
                                + "':",
                        refs);

        if (selected != null) {
            selected.open();
        }

        return null;
    }

    private static Definition find(
            String source,
            int offset) {

        Matcher matcher =
                DEFINITION.matcher(source);

        while (matcher.find()) {
            if (offset >= matcher.start(3)
                    && offset < matcher.end(3)) {

                return new Definition(
                        matcher.group(1),
                        matcher.group(3));
            }
        }

        return null;
    }

    private static final class Definition {
        final String attribute;
        final String name;

        Definition(
                String attribute,
                String name) {

            this.attribute = attribute;
            this.name = name;
        }
    }
}
