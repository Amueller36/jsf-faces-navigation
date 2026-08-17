package de.andre.jsfnavigation;

import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.hyperlink.IHyperlink;
import org.eclipse.jface.text.hyperlink.IHyperlinkDetector;

public final class ELHyperlinkDetector
        implements IHyperlinkDetector {

    @Override
    public IHyperlink[] detectHyperlinks(
            ITextViewer textViewer,
            IRegion region,
            boolean canShowMultipleHyperlinks) {

        IDocument document =
                textViewer.getDocument();

        int offset = region.getOffset();

        ELExpression expression =
                ELExpressionParser.find(
                        document,
                        offset);

        if (expression == null) {
            return null;
        }

        String projectName =
                EditorContext.currentProjectName();

        ELSelection selection =
                ELSelectionResolver.resolve(
                        offset,
                        expression,
                        projectName);

        if (selection == null) {
            return null;
        }

        int partStart =
                findPartOffset(
                        expression,
                        selection.getPartIndex());

        IRegion hyperlinkRegion =
                new Region(
                        partStart,
                        selection.getSelectedPart()
                                .length());

        return new IHyperlink[] {
                new ELHyperlink(
                        hyperlinkRegion,
                        selection)
        };
    }

    private int findPartOffset(
            ELExpression expression,
            int partIndex) {

        int offset =
                expression.getExpressionStart() + 2;

        for (int i = 0; i < partIndex; i++) {
            offset += expression.getParts()
                    .get(i)
                    .length() + 1;
        }

        return offset;
    }
}
