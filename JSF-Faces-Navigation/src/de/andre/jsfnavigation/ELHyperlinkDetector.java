package de.andre.jsfnavigation;

import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.hyperlink.IHyperlink;
import org.eclipse.jface.text.hyperlink.IHyperlinkDetector;

public final class ELHyperlinkDetector implements IHyperlinkDetector {

    @Override
    public IHyperlink[] detectHyperlinks(
            ITextViewer textViewer,
            IRegion region,
            boolean canShowMultipleHyperlinks) {

        IDocument document = textViewer.getDocument();
        int offset = region.getOffset();

        ELExpression expression = ELExpressionParser.find(document, offset);

        if (expression != null) {
            ELSelection selection = ELSelectionResolver.resolve(
                    offset,
                    expression,
                    EditorContext.currentProjectName());

            if (selection != null) {
                IRegion hyperlinkRegion = new Region(
                        expression.getPartOffset(selection.getPartIndex()),
                        selection.getSelectedPart().length());

                return new IHyperlink[] {
                        new ELHyperlink(hyperlinkRegion, selection)
                };
            }
        }

        JavaScriptCall call = JavaScriptCallDetector.find(document, offset);

        if (call != null) {
            return new IHyperlink[] {
                    new JavaScriptHyperlink(
                            new Region(call.getOffset(), call.getName().length()),
                            call,
                            document,
                            EditorContext.currentFile())
            };
        }

        return null;
    }
}
