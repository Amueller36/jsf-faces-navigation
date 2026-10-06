package de.andre.jsfnavigation;

import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.hyperlink.IHyperlink;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.ElementListSelectionDialog;

public final class XsdDefinitionHyperlink
        implements IHyperlink {

    private final IRegion region;
    private final List<XsdDefinition> definitions;

    public XsdDefinitionHyperlink(
            IRegion region,
            List<XsdDefinition> definitions) {

        this.region = region;
        this.definitions = definitions;
    }

    @Override
    public IRegion getHyperlinkRegion() {
        return region;
    }

    @Override
    public String getTypeLabel() {
        return "XSD declaration";
    }

    @Override
    public String getHyperlinkText() {
        return definitions.size() == 1
                ? "Open "
                        + definitions.get(0)
                                .getKind()
                        + " "
                        + definitions.get(0)
                                .getName()
                : "Choose XSD declaration";
    }

    @Override
    public void open() {
        if (definitions == null
                || definitions.isEmpty()) {

            return;
        }

        XsdDefinition selected =
                definitions.size() == 1
                        ? definitions.get(0)
                        : choose();

        if (selected == null) {
            return;
        }

        XsdIndexService service =
                Activator.getXsdIndexService();

        IFile file =
                service == null
                        ? null
                        : service.fileFor(
                                selected);

        if (file != null) {
            WebEditorOpener.open(
                    file,
                    selected.getOffset());
        }
    }

    private XsdDefinition choose() {
        if (!PlatformUI.isWorkbenchRunning()) {
            return null;
        }

        Shell shell =
                PlatformUI.getWorkbench()
                        .getActiveWorkbenchWindow()
                        .getShell();

        ElementListSelectionDialog dialog =
                new ElementListSelectionDialog(
                        shell,
                        new LabelProvider() {
                            @Override
                            public String getText(
                                    Object element) {

                                return element
                                        instanceof XsdDefinition
                                                ? ((XsdDefinition)
                                                        element)
                                                        .getLabel()
                                                : super
                                                        .getText(
                                                                element);
                            }
                        });

        dialog.setTitle(
                "Choose XSD Declaration");

        dialog.setMessage(
                "Multiple schema declarations match this QName.");

        dialog.setElements(
                definitions.toArray());

        return dialog.open()
                == Window.OK
                && dialog.getFirstResult()
                        instanceof XsdDefinition
                                ? (XsdDefinition)
                                        dialog.getFirstResult()
                                : null;
    }
}
