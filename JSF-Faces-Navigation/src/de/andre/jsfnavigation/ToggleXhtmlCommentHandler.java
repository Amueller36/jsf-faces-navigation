package de.andre.jsfnavigation;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.texteditor.ITextEditor;

public final class ToggleXhtmlCommentHandler
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

        IFile file =
                EditorContext.currentFile();

        if (!isMarkupFile(file)) {
            return null;
        }

        ISelection selection =
                editor.getSelectionProvider()
                        .getSelection();

        if (!(selection
                instanceof ITextSelection)) {

            return null;
        }

        IDocument document =
                editor.getDocumentProvider()
                        .getDocument(
                                editor.getEditorInput());

        if (document == null) {
            return null;
        }

        ITextSelection textSelection =
                (ITextSelection) selection;

        try {
            Region region =
                    selectedLineRegion(
                            document,
                            textSelection);

            String original =
                    document.get(
                            region.offset,
                            region.length);

            String replacement =
                    XhtmlCommentSupport.toggle(
                            original,
                            lineDelimiter(
                                    document,
                                    region.startLine));

            if (replacement == null) {
                WebSphereStatusLine.show(
                        "Cannot create nested XML comments. Remove/comment the inner <!-- ... --> block first.");

                return null;
            }

            document.replace(
                    region.offset,
                    region.length,
                    replacement);

            editor.selectAndReveal(
                    region.offset,
                    replacement.length());

        } catch (BadLocationException e) {
            WebSphereStatusLine.show(
                    "Could not toggle XHTML comment: "
                    + e.getMessage());
        }

        return null;
    }

    private static boolean isMarkupFile(
            IFile file) {

        if (file == null) {
            return false;
        }

        String extension =
                file.getFileExtension();

        if (extension == null) {
            return false;
        }

        return "xhtml".equalsIgnoreCase(
                        extension)
                || "html".equalsIgnoreCase(
                        extension)
                || "htm".equalsIgnoreCase(
                        extension)
                || "xml".equalsIgnoreCase(
                        extension);
    }

    private static Region selectedLineRegion(
            IDocument document,
            ITextSelection selection)
            throws BadLocationException {

        int startLine =
                selection.getStartLine();

        int endLine =
                selection.getEndLine();

        if (selection.getLength() > 0
                && endLine > startLine) {

            int selectionEnd =
                    selection.getOffset()
                    + selection.getLength();

            int endLineOffset =
                    document.getLineOffset(
                            endLine);

            /*
             * Eclipse selections that end exactly at the beginning of the
             * following line should not unexpectedly comment that extra line.
             */
            if (selectionEnd
                    == endLineOffset) {

                endLine--;
            }
        }

        IRegion first =
                document.getLineInformation(
                        startLine);

        IRegion last =
                document.getLineInformation(
                        endLine);

        int start =
                first.getOffset();

        int end =
                last.getOffset()
                + last.getLength();

        return new Region(
                start,
                end - start,
                startLine);
    }

    private static String lineDelimiter(
            IDocument document,
            int line) {

        try {
            String delimiter =
                    document.getLineDelimiter(
                            line);

            if (delimiter != null) {
                return delimiter;
            }

        } catch (BadLocationException e) {
            // Fall through.
        }

        return System.lineSeparator();
    }

    private static final class Region {

        final int offset;
        final int length;
        final int startLine;

        Region(
                int offset,
                int length,
                int startLine) {

            this.offset = offset;
            this.length = length;
            this.startLine = startLine;
        }
    }
}
