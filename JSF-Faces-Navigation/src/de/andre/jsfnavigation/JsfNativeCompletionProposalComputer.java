package de.andre.jsfnavigation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.wst.sse.ui.contentassist.CompletionProposalInvocationContext;
import org.eclipse.wst.sse.ui.contentassist.ICompletionProposalComputer;

public final class JsfNativeCompletionProposalComputer
        implements ICompletionProposalComputer {

    @Override
    public List<ICompletionProposal> computeCompletionProposals(
            CompletionProposalInvocationContext context,
            IProgressMonitor monitor) {

        if (context == null
                || context.getViewer() == null
                || context.getDocument() == null) {

            return Collections.emptyList();
        }

        IFile file =
                EditorContext.currentFile();

        if (!isXhtml(
                file)) {

            return Collections.emptyList();
        }

        int offset =
                context.getInvocationOffset();

        /*
         * EL gets first priority. This is the exact case where the old
         * Ctrl+Alt+Space command used a modal ElementListSelectionDialog.
         */
        List<ICompletionProposal> el =
                ElCompletionHandler
                        .nativeElProposals(
                                context.getDocument(),
                                offset,
                                file);

        if (!el.isEmpty()) {
            return el;
        }

        /*
         * Outside EL, contribute our JSF/PrimeFaces/RichFaces tag/attribute
         * proposals to the same WTP default proposal page.
         */
        JsfMarkupContentAssistProcessor markup =
                new JsfMarkupContentAssistProcessor();

        ICompletionProposal[] proposals =
                markup.computeCompletionProposals(
                        context.getViewer(),
                        offset);

        if (proposals == null
                || proposals.length == 0) {

            return Collections.emptyList();
        }

        return new ArrayList<ICompletionProposal>(
                Arrays.asList(
                        proposals));
    }

    @Override
    public List<IContextInformation> computeContextInformation(
            CompletionProposalInvocationContext context,
            IProgressMonitor monitor) {

        return Collections.emptyList();
    }

    @Override
    public String getErrorMessage() {
        return null;
    }

    @Override
    public void sessionStarted() {
    }

    @Override
    public void sessionEnded() {
    }

    private static boolean isXhtml(
            IFile file) {

        if (file == null
                || !file.exists()) {

            return false;
        }

        String extension =
                file.getFileExtension();

        return extension != null
                && "xhtml".equalsIgnoreCase(
                        extension);
    }
}
