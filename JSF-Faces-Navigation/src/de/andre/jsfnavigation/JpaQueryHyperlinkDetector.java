package de.andre.jsfnavigation;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.hyperlink.IHyperlink;
import org.eclipse.jface.text.hyperlink.IHyperlinkDetector;

public final class JpaQueryHyperlinkDetector implements IHyperlinkDetector {

    @Override
    public IHyperlink[] detectHyperlinks(
            ITextViewer textViewer,
            IRegion region,
            boolean canShowMultipleHyperlinks) {

        IDocument document = textViewer.getDocument();
        JpaQueryReference reference = JpaQuerySupport.detect(
                document,
                region.getOffset());

        if (reference == null) {
            return null;
        }

        IFile javaFile = EditorContext.currentFile();
        JpaResolvedReference resolved = JpaQueryResolver.resolve(
                document,
                javaFile,
                reference);

        if (resolved == null) {
            return null;
        }

        return new IHyperlink[] {
                new JpaQueryHyperlink(
                        reference.getSelectedRegion(),
                        resolved)
        };
    }
}
