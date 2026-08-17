package de.andre.jsfnavigation;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextHover;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Region;
import org.eclipse.jdt.ui.text.java.hover.IJavaEditorTextHover;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;

public final class JpaQueryHover implements IJavaEditorTextHover, ITextHover {

    private IEditorPart editor;

    @Override
    public void setEditor(IEditorPart editor) {
        this.editor = editor;
    }

    @Override
    public IRegion getHoverRegion(ITextViewer textViewer, int offset) {
        JpaQueryReference reference = JpaQuerySupport.detect(
                textViewer.getDocument(),
                offset);

        return reference == null
                ? new Region(offset, 0)
                : reference.getSelectedRegion();
    }

    @Override
    public String getHoverInfo(ITextViewer textViewer, IRegion hoverRegion) {
        if (textViewer == null || hoverRegion == null) {
            return null;
        }

        JpaQueryReference reference = JpaQuerySupport.detect(
                textViewer.getDocument(),
                hoverRegion.getOffset());

        if (reference == null) {
            return null;
        }

        IFile file = currentFile();
        JpaResolvedReference resolved = JpaQueryResolver.resolve(
                textViewer.getDocument(),
                file,
                reference);

        return JpaMappingInfo.describe(resolved);
    }

    private IFile currentFile() {
        if (editor != null && editor.getEditorInput() instanceof IFileEditorInput) {
            return ((IFileEditorInput) editor.getEditorInput()).getFile();
        }

        return EditorContext.currentFile();
    }
}
