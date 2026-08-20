package de.andre.jsfnavigation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.jface.text.source.AbstractRulerColumn;
import org.eclipse.jface.text.source.CompositeRuler;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.jface.text.source.IVerticalRulerColumn;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseMoveListener;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.ui.texteditor.rulers.AbstractContributedRulerColumn;

public final class JavaTestRunRulerColumn
        extends AbstractContributedRulerColumn {

    private static final int WIDTH = 18;
    private static final int REFRESH_DELAY_MS = 280;

    private final TestRunRuler delegate =
            new TestRunRuler();

    private Map<Integer, IMethod> methodsByLine =
            Collections.emptyMap();

    private IFile file;
    private IDocument document;
    private IDocumentListener documentListener;

    private boolean dirty = true;
    private int redrawGeneration;

    @Override
    public Control createControl(
            CompositeRuler parentRuler,
            Composite parentControl) {

        attachEditor();

        Control control =
                delegate.createControl(
                        parentRuler,
                        parentControl);

        control.setToolTipText(
                "Run JUnit test");

        control.addMouseListener(
                new MouseAdapter() {
                    @Override
                    public void mouseUp(
                            MouseEvent e) {

                        if (e.button != 1) {
                            return;
                        }

                        int line =
                                delegate
                                        .toDocumentLineNumber(
                                                e.y);

                        IMethod method =
                                methodAtLine(
                                        line);

                        if (method == null
                                || file == null
                                || !file.exists()) {

                            return;
                        }

                        FlowJUnitRunner
                                .runExplicitTestMethod(
                                        file,
                                        method);
                    }
                });

        control.addMouseMoveListener(
                new MouseMoveListener() {
                    @Override
                    public void mouseMove(
                            MouseEvent e) {

                        int line =
                                delegate
                                        .toDocumentLineNumber(
                                                e.y);

                        boolean runnable =
                                methodAtLine(
                                        line)
                                        != null;

                        Control current =
                                delegate
                                        .getControl();

                        if (current == null
                                || current.isDisposed()) {

                            return;
                        }

                        current.setCursor(
                                runnable
                                        ? current
                                                .getDisplay()
                                                .getSystemCursor(
                                                        SWT.CURSOR_HAND)
                                        : null);

                        current.setToolTipText(
                                runnable
                                        ? "Run this JUnit test method"
                                        : "JUnit test run gutter");
                    }
                });

        return control;
    }

    @Override
    public Control getControl() {
        return delegate.getControl();
    }

    @Override
    public int getWidth() {
        return delegate.getWidth();
    }

    @Override
    public void redraw() {
        delegate.redraw();
    }

    @Override
    public void setFont(
            Font font) {

        delegate.setFont(
                font);
    }

    @Override
    public void setModel(
            IAnnotationModel model) {

        delegate.setModel(
                model);
    }

    @Override
    public void columnRemoved() {
        detachDocument();

        redrawGeneration++;
        methodsByLine =
                Collections.emptyMap();

        delegate.dispose();

        super.columnRemoved();
    }

    private void attachEditor() {
        ITextEditor editor =
                getEditor();

        if (editor == null
                || !(editor.getEditorInput()
                        instanceof IFileEditorInput)) {

            return;
        }

        file =
                ((IFileEditorInput)
                        editor.getEditorInput())
                        .getFile();

        document =
                editor.getDocumentProvider()
                        .getDocument(
                                editor.getEditorInput());

        if (document == null) {
            return;
        }

        documentListener =
                new IDocumentListener() {
                    @Override
                    public void documentAboutToBeChanged(
                            DocumentEvent event) {
                    }

                    @Override
                    public void documentChanged(
                            DocumentEvent event) {

                        dirty = true;

                        final int generation =
                                ++redrawGeneration;

                        final Control control =
                                delegate
                                        .getControl();

                        if (control == null
                                || control.isDisposed()) {

                            return;
                        }

                        control.getDisplay()
                                .timerExec(
                                        REFRESH_DELAY_MS,
                                        new Runnable() {
                                            @Override
                                            public void run() {

                                                if (generation
                                                        != redrawGeneration) {

                                                    return;
                                                }

                                                Control current =
                                                        delegate
                                                                .getControl();

                                                if (current != null
                                                        && !current
                                                                .isDisposed()) {

                                                    delegate
                                                            .redraw();
                                                }
                                            }
                                        });
                    }
                };

        document.addDocumentListener(
                documentListener);

        dirty = true;
    }

    private void detachDocument() {
        if (document != null
                && documentListener != null) {

            document.removeDocumentListener(
                    documentListener);
        }

        documentListener = null;
        document = null;
        file = null;
    }

    private IMethod methodAtLine(
            int zeroBasedLine) {

        if (zeroBasedLine < 0) {
            return null;
        }

        refreshMethodsIfNeeded();

        return methodsByLine.get(
                Integer.valueOf(
                        zeroBasedLine));
    }

    private void refreshMethodsIfNeeded() {
        if (!dirty) {
            return;
        }

        dirty = false;

        if (file == null
                || !file.exists()
                || document == null
                || !"java".equalsIgnoreCase(
                        file.getFileExtension())) {

            methodsByLine =
                    Collections.emptyMap();
            return;
        }

        ICompilationUnit unit =
                JavaCore.createCompilationUnitFrom(
                        file);

        if (unit == null
                || !unit.exists()) {

            methodsByLine =
                    Collections.emptyMap();
            return;
        }

        Map<Integer, IMethod> result =
                new LinkedHashMap<Integer, IMethod>();

        try {
            for (IType type :
                    unit.getAllTypes()) {

                for (IMethod method :
                        type.getMethods()) {

                    if (!FlowTestClassifier
                            .isJUnitTestMethod(
                                    method)) {

                        continue;
                    }

                    ISourceRange nameRange =
                            method.getNameRange();

                    if (nameRange == null
                            || nameRange.getOffset()
                                    < 0) {

                        continue;
                    }

                    int line =
                            document.getLineOfOffset(
                                    Math.min(
                                            nameRange.getOffset(),
                                            document.getLength()));

                    result.put(
                            Integer.valueOf(
                                    line),
                            method);
                }
            }

        } catch (Exception e) {
            result.clear();
        }

        methodsByLine =
                result;
    }

    private final class TestRunRuler
            extends AbstractRulerColumn {

        TestRunRuler() {
            setWidth(
                    WIDTH);
        }

        @Override
        protected void paintLine(
                GC gc,
                int modelLine,
                int widgetLine,
                int linePixel,
                int lineHeight) {

            super.paintLine(
                    gc,
                    modelLine,
                    widgetLine,
                    linePixel,
                    lineHeight);

            IMethod method =
                    methodAtLine(
                            modelLine);

            if (method == null) {
                return;
            }

            int centerY =
                    linePixel
                    + Math.max(
                            1,
                            lineHeight / 2);

            int half =
                    Math.max(
                            4,
                            Math.min(
                                    6,
                                    lineHeight / 3));

            int left = 4;
            int right =
                    Math.max(
                            left + 5,
                            getWidth() - 4);

            gc.setBackground(
                    getControl()
                            .getDisplay()
                            .getSystemColor(
                                    SWT.COLOR_DARK_GREEN));

            gc.fillPolygon(
                    new int[] {
                            left,
                            centerY - half,
                            left,
                            centerY + half,
                            right,
                            centerY
                    });
        }
    }
}
