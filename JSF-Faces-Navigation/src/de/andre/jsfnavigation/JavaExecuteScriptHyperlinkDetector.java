package de.andre.jsfnavigation;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.hyperlink.IHyperlink;
import org.eclipse.jface.text.hyperlink.IHyperlinkDetector;

public final class JavaExecuteScriptHyperlinkDetector implements IHyperlinkDetector {

    @Override
    public IHyperlink[] detectHyperlinks(
            ITextViewer textViewer,
            IRegion region,
            boolean canShowMultipleHyperlinks) {

        IDocument document = textViewer.getDocument();
        int offset = region.getOffset();

        JavaScriptCall call = JavaScriptCallDetector.find(document, offset);
        if (call == null) {
            return null;
        }

        if (!isInsideExecuteScriptCall(document.get(), call.getOffset())) {
            return null;
        }

        IFile javaFile = EditorContext.currentFile();
        String projectName = javaFile == null ? null : javaFile.getProject().getName();
        String beanName = ControllerContext.beanNameAt(javaFile, offset);

        return new IHyperlink[] {
                new JavaExecuteScriptHyperlink(
                        new Region(call.getOffset(), call.getName().length()),
                        call.getName(),
                        projectName,
                        beanName)
        };
    }

    private boolean isInsideExecuteScriptCall(String source, int functionOffset) {
        int quoteStart = findOpeningQuote(source, functionOffset);
        if (quoteStart < 0) {
            return false;
        }

        int prefixStart = Math.max(0, quoteStart - 300);
        String prefix = source.substring(prefixStart, quoteStart);

        int executeScript = prefix.lastIndexOf("executeScript");
        int execute = prefix.lastIndexOf(".execute");
        int candidate = Math.max(executeScript, execute);

        if (candidate < 0) {
            return false;
        }

        int openParen = prefix.indexOf('(', candidate);
        return openParen >= 0;
    }

    private int findOpeningQuote(String source, int offset) {
        int doubleQuote = source.lastIndexOf('"', offset);
        int singleQuote = source.lastIndexOf('\'', offset);
        int start = Math.max(doubleQuote, singleQuote);

        if (start < 0) {
            return -1;
        }

        char quote = source.charAt(start);
        int close = source.indexOf(quote, start + 1);

        return close >= offset ? start : -1;
    }
}
