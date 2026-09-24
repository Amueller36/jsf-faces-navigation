package de.andre.jsfnavigation;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.hyperlink.IHyperlink;

public final class JavaScriptHyperlink implements IHyperlink {

    private final IRegion region;
    private final JavaScriptCall call;
    private final IDocument currentDocument;
    private final IFile currentFile;

    public JavaScriptHyperlink(
            IRegion region,
            JavaScriptCall call,
            IDocument currentDocument,
            IFile currentFile) {

        this.region = region;
        this.call = call;
        this.currentDocument = currentDocument;
        this.currentFile = currentFile;
    }

    @Override
    public IRegion getHyperlinkRegion() {
        return region;
    }

    @Override
    public String getTypeLabel() {
        return "JavaScript definition";
    }

    @Override
    public String getHyperlinkText() {
        return "Open JavaScript definition of " + call.getName();
    }

    @Override
    public void open() {
        JavaScriptNavigationService.navigateFromXhtml(call, currentDocument, currentFile);
    }
}
