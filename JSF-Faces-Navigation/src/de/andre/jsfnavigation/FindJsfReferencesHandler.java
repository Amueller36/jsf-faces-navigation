package de.andre.jsfnavigation;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.texteditor.ITextEditor;

public final class FindJsfReferencesHandler
        extends AbstractHandler {

    private static final Pattern DEFINITION =
            Pattern.compile(
                    "\\b(id|widgetVar)\\s*=\\s*(['\"])([^'\"]+)\\2");

    private static final int LOCAL_WINDOW = 8192;

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

        final Definition definition =
                find(document, offset);

        if (definition == null) {
            return null;
        }

        Job job =
                new Job(
                        "Find JSF references to "
                        + definition.name) {
                    @Override
                    protected IStatus run(
                            IProgressMonitor monitor) {

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

                        if (monitor.isCanceled()) {
                            return Status.CANCEL_STATUS;
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

                        return Status.OK_STATUS;
                    }
                };

        job.setUser(true);
        job.schedule();

        return null;
    }

    private static Definition find(
            IDocument document,
            int offset) {

        if (document == null
                || offset < 0
                || offset > document.getLength()) {

            return null;
        }

        int start =
                Math.max(0, offset - LOCAL_WINDOW);

        int end =
                Math.min(document.getLength(), offset + LOCAL_WINDOW);

        String source;

        try {
            source = document.get(start, end - start);
        } catch (BadLocationException e) {
            return null;
        }

        int localOffset = offset - start;

        Matcher matcher =
                DEFINITION.matcher(source);

        while (matcher.find()) {
            if (localOffset >= matcher.start(3)
                    && localOffset < matcher.end(3)) {

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
