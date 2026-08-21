package de.andre.jsfnavigation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.jface.text.source.AbstractRulerColumn;
import org.eclipse.jface.text.source.CompositeRuler;
import org.eclipse.jface.text.source.IAnnotationModel;
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
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
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

    private Map<Integer, IType> typesByLine =
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

        final Control control =
                delegate.createControl(
                        parentRuler,
                        parentControl);

        control.setToolTipText(
                "JUnit run/debug gutter");

        control.addMouseListener(
                new MouseAdapter() {
                    @Override
                    public void mouseUp(
                            MouseEvent e) {

                        if (e.button != 1) {
                            return;
                        }

                        LaunchTarget target =
                                targetAtY(
                                        e.y);

                        if (target == null) {
                            return;
                        }

                        showRunDebugMenu(
                                control,
                                e.x,
                                e.y,
                                target);
                    }
                });

        control.addMouseMoveListener(
                new MouseMoveListener() {
                    @Override
                    public void mouseMove(
                            MouseEvent e) {

                        LaunchTarget target =
                                targetAtY(
                                        e.y);

                        boolean runnable =
                                target != null;

                        Control current =
                                delegate.getControl();

                        if (current == null
                                || current.isDisposed()) {

                            return;
                        }

                        current.setCursor(
                                runnable
                                        ? current.getDisplay()
                                                .getSystemCursor(
                                                        SWT.CURSOR_HAND)
                                        : null);

                        current.setToolTipText(
                                target == null
                                        ? "JUnit run/debug gutter"
                                        : target.isMethod()
                                                ? "Run or debug this JUnit test method"
                                                : "Run or debug this JUnit test class");
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

        typesByLine =
                Collections.emptyMap();

        delegate.dispose();

        super.columnRemoved();
    }

    private void showRunDebugMenu(
            final Control control,
            int x,
            int y,
            final LaunchTarget target) {

        final Menu menu =
                new Menu(
                        control);

        MenuItem run =
                new MenuItem(
                        menu,
                        SWT.PUSH);

        run.setText(
                "Run "
                + target.getDisplayName());

        run.addListener(
                SWT.Selection,
                new Listener() {
                    @Override
                    public void handleEvent(
                            Event event) {

                        launch(
                                target,
                                ILaunchManager.RUN_MODE);
                    }
                });

        MenuItem debug =
                new MenuItem(
                        menu,
                        SWT.PUSH);

        debug.setText(
                "Debug "
                + target.getDisplayName());

        debug.addListener(
                SWT.Selection,
                new Listener() {
                    @Override
                    public void handleEvent(
                            Event event) {

                        launch(
                                target,
                                ILaunchManager.DEBUG_MODE);
                    }
                });

        menu.addListener(
                SWT.Hide,
                new Listener() {
                    @Override
                    public void handleEvent(
                            Event event) {

                        control.getDisplay()
                                .asyncExec(
                                        new Runnable() {
                                            @Override
                                            public void run() {

                                                if (!menu.isDisposed()) {
                                                    menu.dispose();
                                                }
                                            }
                                        });
                    }
                });

        Point location =
                control.toDisplay(
                        x,
                        y);

        menu.setLocation(
                location);

        menu.setVisible(
                true);
    }

    private void launch(
            LaunchTarget target,
            String launchMode) {

        if (target == null
                || file == null
                || !file.exists()) {

            return;
        }

        if (target.isMethod()) {
            FlowJUnitRunner
                    .runExplicitTestMethod(
                            file,
                            target.method,
                            launchMode);

        } else {
            FlowJUnitRunner
                    .runExplicitTestClass(
                            file,
                            target.type,
                            launchMode);
        }
    }

    private LaunchTarget targetAtY(
            int y) {

        int line =
                delegate
                        .toDocumentLineNumber(
                                y);

        if (line < 0) {
            return null;
        }

        refreshTargetsIfNeeded();

        IMethod method =
                methodsByLine.get(
                        Integer.valueOf(
                                line));

        if (method != null) {
            return LaunchTarget
                    .forMethod(
                            method);
        }

        IType type =
                typesByLine.get(
                        Integer.valueOf(
                                line));

        return type == null
                ? null
                : LaunchTarget.forType(
                        type);
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
                                delegate.getControl();

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
                                                        delegate.getControl();

                                                if (current != null
                                                        && !current.isDisposed()) {

                                                    delegate.redraw();
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

    private void refreshTargetsIfNeeded() {
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

            typesByLine =
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

            typesByLine =
                    Collections.emptyMap();

            return;
        }

        Map<Integer, IMethod> methods =
                new LinkedHashMap<Integer, IMethod>();

        Map<Integer, IType> types =
                new LinkedHashMap<Integer, IType>();

        try {
            for (IType type :
                    unit.getAllTypes()) {

                if (FlowTestClassifier
                        .classify(
                                type)
                        != FlowTestClassifier.NOT_TEST) {

                    ISourceRange typeNameRange =
                            type.getNameRange();

                    int line =
                            lineOf(
                                    typeNameRange);

                    if (line >= 0) {
                        types.put(
                                Integer.valueOf(
                                        line),
                                type);
                    }
                }

                for (IMethod method :
                        type.getMethods()) {

                    if (!FlowTestClassifier
                            .isJUnitTestMethod(
                                    method)) {

                        continue;
                    }

                    int line =
                            lineOf(
                                    method.getNameRange());

                    if (line >= 0) {
                        methods.put(
                                Integer.valueOf(
                                        line),
                                method);
                    }
                }
            }

        } catch (Exception e) {
            methods.clear();
            types.clear();
        }

        methodsByLine =
                methods;

        typesByLine =
                types;
    }

    private int lineOf(
            ISourceRange range) {

        if (range == null
                || range.getOffset() < 0
                || document == null) {

            return -1;
        }

        try {
            return document.getLineOfOffset(
                    Math.min(
                            range.getOffset(),
                            document.getLength()));

        } catch (Exception e) {
            return -1;
        }
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

            refreshTargetsIfNeeded();

            boolean runnable =
                    methodsByLine.containsKey(
                            Integer.valueOf(
                                    modelLine))
                    || typesByLine.containsKey(
                            Integer.valueOf(
                                    modelLine));

            if (!runnable) {
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

    private static final class LaunchTarget {

        final IMethod method;
        final IType type;

        private LaunchTarget(
                IMethod method,
                IType type) {

            this.method = method;
            this.type = type;
        }

        static LaunchTarget forMethod(
                IMethod method) {

            return new LaunchTarget(
                    method,
                    method == null
                            ? null
                            : method.getDeclaringType());
        }

        static LaunchTarget forType(
                IType type) {

            return new LaunchTarget(
                    null,
                    type);
        }

        boolean isMethod() {
            return method != null;
        }

        String getDisplayName() {
            if (isMethod()) {
                return type.getElementName()
                        + "."
                        + method.getElementName();
            }

            return type == null
                    ? "JUnit test"
                    : type.getElementName();
        }
    }
}
