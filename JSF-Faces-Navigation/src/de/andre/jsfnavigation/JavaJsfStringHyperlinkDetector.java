package de.andre.jsfnavigation;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.hyperlink.IHyperlink;
import org.eclipse.jface.text.hyperlink.IHyperlinkDetector;

public final class JavaJsfStringHyperlinkDetector
        implements IHyperlinkDetector {

    @Override
    public IHyperlink[] detectHyperlinks(
            ITextViewer textViewer,
            IRegion region,
            boolean canShowMultipleHyperlinks) {

        JavaStringReference reference =
                JavaStringReferenceDetector.find(
                        textViewer.getDocument(),
                        region.getOffset());

        if (reference == null) {
            return null;
        }

        IFile file =
                EditorContext.currentFile();

        if (file == null) {
            return null;
        }

        return new IHyperlink[] {
                new JavaJsfStringHyperlink(
                        new Region(
                                reference.getOffset(),
                                reference.getLength()),
                        reference,
                        file)
        };
    }
}
