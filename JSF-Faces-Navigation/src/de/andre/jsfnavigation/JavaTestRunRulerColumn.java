package de.andre.jsfnavigation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.jface.text.source.AbstractRulerColumn;
import org.eclipse.jface.text.source.CompositeRuler;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
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
    private boolean editorEligible;

    private IPreferenceStore preferenceStore;
    private IPropertyChangeListener preferenceListener;

    private boolean dirty = true;
    private int redrawGeneration;
    private final AtomicBoolean targetScanScheduled =
            new AtomicBoolean(false);

    @Override
    public Control createControl(
            CompositeRuler parentRuler,
            Composite parentControl) {

        attachEditor();

        /*
         * This ruler is contributed globally to the Java editor.  Production
         * Controller/Bean/Entity editors should pay zero per-keystroke cost.
         * Only conventional test files get a visible gutter and a document
         * listener.
         */
        delegate.setRulerWidth(editorEligible ? WIDTH : 0);

        final Control control =
                delegate.createControl(
                        parentRuler,
                        parentControl);

        installPreferenceListener();

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
        uninstallPreferenceListener();
        detachDocument();

        redrawGeneration++;

        methodsByLine =
                Collections.emptyMap();

        typesByLine =
                Collections.emptyMap();

        delegate.dispose();

        super.columnRemoved();
    }

    private void installPreferenceListener() {
        Activator plugin =
                Activator.getDefault();

        if (plugin == null
                || preferenceListener != null) {

            return;
        }

        preferenceStore =
                plugin.getPreferenceStore();

        preferenceListener =
                new IPropertyChangeListener() {
                    @Override
                    public void propertyChange(
                            PropertyChangeEvent event) {

                        if (!JavaTestRunSettings.SHOW_GUTTER
                                .equals(
                                        event.getProperty())) {

                            return;
                        }

                        final Control current =
                                delegate.getControl();

                        Runnable refresh =
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        refreshPreferenceState();
                                    }
                                };

                        if (current != null
                                && !current.isDisposed()) {

                            current.getDisplay()
                                    .asyncExec(
                                            refresh);

                        } else {
                            refresh.run();
                        }
                    }
                };

        preferenceStore.addPropertyChangeListener(
                preferenceListener);
    }

    private void uninstallPreferenceListener() {
        if (preferenceStore != null
                && preferenceListener != null) {

            preferenceStore.removePropertyChangeListener(
                    preferenceListener);
        }

        preferenceListener = null;
        preferenceStore = null;
    }

    private void refreshPreferenceState() {
        detachDocument();

        redrawGeneration++;

        methodsByLine =
                Collections.emptyMap();

        typesByLine =
                Collections.emptyMap();

        attachEditor();

        delegate.setRulerWidth(
                editorEligible
                        ? WIDTH
                        : 0);

        dirty = true;

        Control current =
                delegate.getControl();

        if (current != null
                && !current.isDisposed()) {

            current.getParent()
                    .layout(
                            true,
                            true);

            current.getParent()
                    .redraw();
        }

        delegate.redraw();
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

        if (!JavaTestRunSettings.isEnabled()) {
            editorEligible = false;
            document = null;
            return;
        }

        editorEligible =
                looksLikeConventionalTestFile(file);

        if (!editorEligible) {
            document = null;
            return;
        }

        document =
                editor.getDocumentProvider()
                        .getDocument(
                                editor.getEditorInput());

        if (document == null) {
            editorEligible = false;
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

                                                    refreshTargetsIfNeeded();
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
        editorEligible = false;
    }

    private void refreshTargetsIfNeeded() {
        if (!editorEligible || !dirty) {
            return;
        }

        dirty = false;

        /*
         * The ruler is registered for every Java editor. Most editors in the
         * application are production classes, so do a very cheap filename /
         * source-header preflight before touching JDT at all. This prevents a
         * background type/method scan from being started just because a normal
         * Controller/Entity/TO editor was painted after a tab switch.
         */
        if (!looksLikePotentialTestFile()) {
            methodsByLine = Collections.emptyMap();
            typesByLine = Collections.emptyMap();
            return;
        }

        scheduleTargetRefresh();
    }

    private boolean looksLikePotentialTestFile() {
        return editorEligible
                && looksLikeConventionalTestFile(file);
    }

    private static boolean looksLikeConventionalTestFile(
            IFile candidate) {

        if (candidate == null
                || !candidate.exists()
                || !"java".equalsIgnoreCase(
                        candidate.getFileExtension())) {

            return false;
        }

        String name =
                candidate.getName()
                        .toLowerCase();

        String path =
                candidate.getProjectRelativePath()
                        .toPortableString()
                        .toLowerCase();

        String project =
                candidate.getProject()
                        .getName()
                        .toLowerCase();

        return name.endsWith("test.java")
                || name.endsWith("tests.java")
                || name.endsWith("testcase.java")
                || name.endsWith("it.java")
                || path.indexOf("src/test") >= 0
                || path.indexOf("/test/") >= 0
                || path.indexOf("/tests/") >= 0
                || path.indexOf("src/integration") >= 0
                || project.indexOf("junit") >= 0
                || project.indexOf("test") >= 0;
    }

    private void scheduleTargetRefresh() {
        if (!targetScanScheduled.compareAndSet(
                false,
                true)) {

            return;
        }

        final IFile scanFile =
                file;

        final IDocument scanDocument =
                document;

        final int generation =
                redrawGeneration;

        final Control schedulingControl =
                delegate.getControl();

        final org.eclipse.swt.widgets.Display display =
                schedulingControl == null
                        || schedulingControl.isDisposed()
                                ? null
                                : schedulingControl.getDisplay();

        Job job =
                new Job(
                        "Index JUnit editor gutter") {
                    @Override
                    protected IStatus run(
                            IProgressMonitor monitor) {

                        final List<TargetOffset<IMethod>> methodOffsets =
                                new ArrayList<TargetOffset<IMethod>>();

                        final List<TargetOffset<IType>> typeOffsets =
                                new ArrayList<TargetOffset<IType>>();

                        try {
                            if (!monitor.isCanceled()
                                    && scanFile != null
                                    && scanFile.exists()
                                    && scanDocument != null
                                    && "java".equalsIgnoreCase(
                                            scanFile.getFileExtension())) {

                                ICompilationUnit unit =
                                        JavaCore.createCompilationUnitFrom(
                                                scanFile);

                                if (unit != null
                                        && unit.exists()) {

                                    for (IType type :
                                            unit.getAllTypes()) {

                                        if (monitor.isCanceled()) {
                                            break;
                                        }

                                        if (FlowTestClassifier
                                                .classify(
                                                        type)
                                                != FlowTestClassifier
                                                        .NOT_TEST) {

                                            ISourceRange range =
                                                    type.getNameRange();

                                            if (range != null
                                                    && range.getOffset()
                                                            >= 0) {

                                                typeOffsets.add(
                                                        new TargetOffset<IType>(
                                                                range.getOffset(),
                                                                type));
                                            }
                                        }

                                        for (IMethod method :
                                                type.getMethods()) {

                                            if (!FlowTestClassifier
                                                    .isJUnitTestMethod(
                                                            method)) {

                                                continue;
                                            }

                                            ISourceRange range =
                                                    method.getNameRange();

                                            if (range != null
                                                    && range.getOffset()
                                                            >= 0) {

                                                methodOffsets.add(
                                                        new TargetOffset<IMethod>(
                                                                range.getOffset(),
                                                                method));
                                            }
                                        }
                                    }
                                }
                            }

                        } catch (Exception e) {
                            methodOffsets.clear();
                            typeOffsets.clear();
                        }

                        if (display != null) {
                            try {
                                display.asyncExec(
                                        new Runnable() {
                                            @Override
                                            public void run() {

                                                try {
                                                    Control current =
                                                            delegate.getControl();

                                                    if (current == null
                                                            || current.isDisposed()) {

                                                        return;
                                                    }

                                                    if (generation
                                                            == redrawGeneration
                                                            && scanFile
                                                                    == file
                                                            && scanDocument
                                                                    == document) {

                                                        methodsByLine =
                                                                toLineMap(
                                                                        scanDocument,
                                                                        methodOffsets);

                                                        typesByLine =
                                                                toLineMap(
                                                                        scanDocument,
                                                                        typeOffsets);

                                                        delegate.redraw();
                                                    }

                                                } finally {
                                                    targetScanScheduled.set(false);

                                                    if (dirty) {
                                                        refreshTargetsIfNeeded();
                                                    }
                                                }
                                            }
                                        });

                            } catch (RuntimeException e) {
                                targetScanScheduled.set(false);
                            }

                        } else {
                            targetScanScheduled.set(false);
                        }

                        return monitor.isCanceled()
                                ? Status.CANCEL_STATUS
                                : Status.OK_STATUS;
                    }
                };

        job.setSystem(true);
        job.setPriority(Job.DECORATE);
        job.setRule(PluginBackgroundSchedulingRule.INSTANCE);
        job.schedule();
    }

    private static <T> Map<Integer, T> toLineMap(
            IDocument document,
            List<TargetOffset<T>> offsets) {

        if (document == null
                || offsets == null
                || offsets.isEmpty()) {

            return Collections.emptyMap();
        }

        Map<Integer, T> result =
                new LinkedHashMap<Integer, T>();

        for (TargetOffset<T> target :
                offsets) {

            try {
                int line =
                        document.getLineOfOffset(
                                Math.min(
                                        target.offset,
                                        document.getLength()));

                result.put(
                        Integer.valueOf(line),
                        target.value);

            } catch (Exception e) {
                // Document changed after the background JDT scan.
            }
        }

        return result;
    }

    private final class TestRunRuler
            extends AbstractRulerColumn {

        TestRunRuler() {
            setWidth(
                    WIDTH);
        }

        /**
         * Exposes the protected AbstractRulerColumn#setWidth(int) only to the
         * owning contributed ruler. This keeps production editors at width 0
         * without calling the protected API from the outer class.
         */
        void setRulerWidth(int width) {
            setWidth(width);
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

            Control rulerControl =
                    getControl();

            boolean darkBackground =
                    rulerControl != null
                    && !rulerControl.isDisposed()
                    && luminance(
                            rulerControl
                                    .getBackground())
                            < 128.0d;

            gc.setBackground(
                    getControl()
                            .getDisplay()
                            .getSystemColor(
                                    darkBackground
                                            ? SWT.COLOR_GREEN
                                            : SWT.COLOR_DARK_GREEN));

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


    private static double luminance(
            org.eclipse.swt.graphics.Color color) {

        if (color == null) {
            return 0.0d;
        }

        return 0.2126d
                * color.getRed()
                + 0.7152d
                        * color.getGreen()
                + 0.0722d
                        * color.getBlue();
    }

    private static final class TargetOffset<T> {

        final int offset;
        final T value;

        TargetOffset(
                int offset,
                T value) {

            this.offset = offset;
            this.value = value;
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
