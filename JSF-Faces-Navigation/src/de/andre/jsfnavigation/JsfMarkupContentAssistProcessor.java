package de.andre.jsfnavigation;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.contentassist.CompletionProposal;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContentAssistProcessor;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.jface.text.contentassist.IContextInformationValidator;

public final class JsfMarkupContentAssistProcessor
        implements IContentAssistProcessor {

    @Override
    public ICompletionProposal[] computeCompletionProposals(
            ITextViewer viewer,
            int offset) {

        if (viewer == null
                || viewer.getDocument() == null) {

            return new ICompletionProposal[0];
        }

        IFile file =
                EditorContext.currentFile();

        if (!isXhtml(
                file)) {

            return new ICompletionProposal[0];
        }

        IDocument document =
                viewer.getDocument();

        JsfMarkupCompletionContext context =
                JsfMarkupCompletionContext
                        .detect(
                                document,
                                offset);

        if (context == null) {
            return new ICompletionProposal[0];
        }

        List<JsfComponentProposal> proposals;

        if (context.getKind()
                == JsfMarkupCompletionContext.TAG) {

            proposals =
                    JsfTaglibCatalogService
                            .tagProposals(
                                    file,
                                    document,
                                    context);

        } else {
            proposals =
                    JsfTaglibCatalogService
                            .attributeProposals(
                                    file,
                                    document,
                                    context);
        }

        if (proposals == null
                || proposals.isEmpty()) {

            return new ICompletionProposal[0];
        }

        List<ICompletionProposal> result =
                new ArrayList<ICompletionProposal>(
                        proposals.size());

        for (JsfComponentProposal proposal :
                proposals) {

            String replacement =
                    proposal.getInsertText();

            int cursor =
                    replacement.length();

            if (proposal.isAttribute()
                    && replacement.endsWith(
                            "=\"\"")) {

                /*
                 * Native Eclipse content assist will position the caret inside
                 * the generated attribute quotes, matching the old custom
                 * chooser behavior.
                 */
                cursor--;
            }

            result.add(
                    new CompletionProposal(
                            replacement,
                            context.getReplaceOffset(),
                            context.getReplaceLength(),
                            cursor,
                            null,
                            proposal.displayText(),
                            null,
                            proposal.getDetail()));
        }

        return result.toArray(
                new ICompletionProposal[
                        result.size()]);
    }

    @Override
    public IContextInformation[] computeContextInformation(
            ITextViewer viewer,
            int offset) {

        return new IContextInformation[0];
    }

    @Override
    public char[] getCompletionProposalAutoActivationCharacters() {
        /*
         * Keep this conservative. WTP itself owns automatic HTML/XML content
         * assist preferences. We participate in the normal popup but do not
         * introduce a new always-on keystroke listener.
         */
        return null;
    }

    @Override
    public char[] getContextInformationAutoActivationCharacters() {
        return null;
    }

    @Override
    public String getErrorMessage() {
        return null;
    }

    @Override
    public IContextInformationValidator getContextInformationValidator() {
        return null;
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
