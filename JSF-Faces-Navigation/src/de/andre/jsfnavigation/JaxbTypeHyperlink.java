package de.andre.jsfnavigation;

import java.util.List;

import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.hyperlink.IHyperlink;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.window.Window;
import org.eclipse.jdt.core.IType;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.ElementListSelectionDialog;

public final class JaxbTypeHyperlink
        implements IHyperlink {

    private final IRegion region;
    private final List<IType> types;

    public JaxbTypeHyperlink(
            IRegion region,
            List<IType> types) {

        this.region = region;
        this.types = types;
    }

    @Override
    public IRegion getHyperlinkRegion() {
        return region;
    }

    @Override
    public String getTypeLabel() {
        return "JAXB Java type";
    }

    @Override
    public String getHyperlinkText() {
        return types.size() == 1
                ? "Open JAXB type "
                        + types.get(0)
                                .getElementName()
                : "Choose JAXB Java type";
    }

    @Override
    public void open() {
        if (types == null
                || types.isEmpty()) {

            return;
        }

        IType selected =
                types.size() == 1
                        ? types.get(0)
                        : choose();

        if (selected != null) {
            JavaEditorOpener.open(
                    selected);
        }
    }

    private IType choose() {
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

                                if (!(element
                                        instanceof IType)) {

                                    return super
                                            .getText(
                                                    element);
                                }

                                IType type =
                                        (IType)
                                                element;

                                return type.getFullyQualifiedName()
                                        + "  ["
                                        + type.getJavaProject()
                                                .getElementName()
                                        + "]";
                            }
                        });

        dialog.setTitle(
                "Choose JAXB Type");

        dialog.setMessage(
                "Multiple JAXB types match this schema declaration.");

        dialog.setElements(
                types.toArray());

        return dialog.open()
                == Window.OK
                && dialog.getFirstResult()
                        instanceof IType
                                ? (IType)
                                        dialog.getFirstResult()
                                : null;
    }
}
