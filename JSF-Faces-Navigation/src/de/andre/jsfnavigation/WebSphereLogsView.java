package de.andre.jsfnavigation;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseMoveListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.part.ViewPart;

public final class WebSphereLogsView
        extends ViewPart {

    public static final String VIEW_ID =
            "de.andre.jsfnavigation.views.webSphereLogs";

    private static volatile WebSphereLogsView instance;

    private static final StringBuilder DEPLOY_STATUS =
            new StringBuilder();

    private Label pathLabel;
    private Button autoRefreshButton;
    private CTabFolder tabs;

    private StyledText systemOutText;
    private StyledText systemErrText;
    private StyledText deployText;

    private Composite searchRow;
    private Text searchField;
    private Label searchResultLabel;

    private boolean disposed;

    private final AtomicBoolean refreshScheduled =
            new AtomicBoolean(false);

    @Override
    public void createPartControl(
            Composite parent) {

        instance = this;

        parent.setLayout(
                new GridLayout(1, false));

        createToolbar(parent);
        createSearchBar(parent);
        createTabs(parent);

        synchronized (DEPLOY_STATUS) {
            if (DEPLOY_STATUS.length() > 0) {
                deployText.setText(
                        DEPLOY_STATUS.toString());
            }
        }

        requestRefresh();
        scheduleRefresh();
    }

    private void createToolbar(
            Composite parent) {

        Composite row =
                new Composite(
                        parent,
                        SWT.NONE);

        row.setLayoutData(
                new GridData(
                        SWT.FILL,
                        SWT.TOP,
                        true,
                        false));

        GridLayout layout =
                new GridLayout(4, false);

        layout.marginWidth = 0;
        layout.marginHeight = 0;

        row.setLayout(layout);

        pathLabel =
                new Label(
                        row,
                        SWT.NONE);

        pathLabel.setLayoutData(
                new GridData(
                        SWT.FILL,
                        SWT.CENTER,
                        true,
                        false));

        autoRefreshButton =
                new Button(
                        row,
                        SWT.CHECK);

        autoRefreshButton.setText(
                "Auto refresh");

        IPreferenceStore store =
                WebSphereHotSyncSettings.store();

        autoRefreshButton.setSelection(
                store != null
                && store.getBoolean(
                        WebSphereLogSettings.AUTO_REFRESH));

        autoRefreshButton.addSelectionListener(
                new SelectionAdapter() {
                    @Override
                    public void widgetSelected(
                            SelectionEvent e) {

                        IPreferenceStore store =
                                WebSphereHotSyncSettings.store();

                        if (store != null) {
                            store.setValue(
                                    WebSphereLogSettings.AUTO_REFRESH,
                                    autoRefreshButton
                                            .getSelection());
                        }
                    }
                });

        Button refresh =
                new Button(
                        row,
                        SWT.PUSH);

        refresh.setText("Refresh");

        refresh.addSelectionListener(
                new SelectionAdapter() {
                    @Override
                    public void widgetSelected(
                            SelectionEvent e) {

                        requestRefresh();
                    }
                });

        Button clear =
                new Button(
                        row,
                        SWT.PUSH);

        clear.setText("Clear View");

        clear.setToolTipText(
                "Clear only the text shown in Eclipse. The real WebSphere log files are never modified.");

        clear.addSelectionListener(
                new SelectionAdapter() {
                    @Override
                    public void widgetSelected(
                            SelectionEvent e) {

                        activeLogText().setText("");
                    }
                });
    }

    private void createSearchBar(
            Composite parent) {

        searchRow =
                new Composite(
                        parent,
                        SWT.NONE);

        GridData data =
                new GridData(
                        SWT.FILL,
                        SWT.TOP,
                        true,
                        false);

        data.exclude = true;

        searchRow.setLayoutData(data);
        searchRow.setVisible(false);

        GridLayout layout =
                new GridLayout(5, false);

        layout.marginWidth = 0;
        layout.marginHeight = 0;

        searchRow.setLayout(layout);

        new Label(
                searchRow,
                SWT.NONE)
                .setText("Find:");

        searchField =
                new Text(
                        searchRow,
                        SWT.SEARCH
                        | SWT.ICON_CANCEL);

        searchField.setLayoutData(
                new GridData(
                        SWT.FILL,
                        SWT.CENTER,
                        true,
                        false));

        searchField.addModifyListener(
                new ModifyListener() {
                    @Override
                    public void modifyText(
                            ModifyEvent e) {

                        find(
                                true,
                                true);
                    }
                });

        searchField.addKeyListener(
                new KeyAdapter() {
                    @Override
                    public void keyPressed(
                            KeyEvent e) {

                        if (e.keyCode == SWT.ESC) {
                            hideSearch();
                            e.doit = false;

                        } else if (e.keyCode
                                == SWT.CR) {

                            find(
                                    true,
                                    false);

                            e.doit = false;
                        }
                    }
                });

        Button previous =
                new Button(
                        searchRow,
                        SWT.PUSH);

        previous.setText("Previous");

        previous.addSelectionListener(
                new SelectionAdapter() {
                    @Override
                    public void widgetSelected(
                            SelectionEvent e) {

                        find(
                                false,
                                false);
                    }
                });

        Button next =
                new Button(
                        searchRow,
                        SWT.PUSH);

        next.setText("Next");

        next.addSelectionListener(
                new SelectionAdapter() {
                    @Override
                    public void widgetSelected(
                            SelectionEvent e) {

                        find(
                                true,
                                false);
                    }
                });

        searchResultLabel =
                new Label(
                        searchRow,
                        SWT.NONE);

        searchResultLabel.setText("");
    }

    private void createTabs(
            Composite parent) {

        tabs =
                new CTabFolder(
                        parent,
                        SWT.BORDER);

        tabs.setSimple(false);

        tabs.setLayoutData(
                new GridData(
                        SWT.FILL,
                        SWT.FILL,
                        true,
                        true));

        CTabItem outItem =
                new CTabItem(
                        tabs,
                        SWT.NONE);

        outItem.setText(
                "SystemOut.log");

        systemOutText =
                createLogText(tabs);

        outItem.setControl(
                systemOutText);

        CTabItem errItem =
                new CTabItem(
                        tabs,
                        SWT.NONE);

        errItem.setText(
                "SystemErr.log");

        systemErrText =
                createLogText(tabs);

        errItem.setControl(
                systemErrText);

        CTabItem deployItem =
                new CTabItem(
                        tabs,
                        SWT.NONE);

        deployItem.setText(
                "Smart Deploy");

        deployText =
                createLogText(tabs);

        deployItem.setControl(
                deployText);

        tabs.setSelection(0);

        tabs.addSelectionListener(
                new SelectionAdapter() {
                    @Override
                    public void widgetSelected(
                            SelectionEvent e) {

                        updateSearchCount();
                    }
                });
    }

    private StyledText createLogText(
            Composite parent) {

        final StyledText text =
                new StyledText(
                        parent,
                        SWT.MULTI
                        | SWT.READ_ONLY
                        | SWT.H_SCROLL
                        | SWT.V_SCROLL);

        text.addKeyListener(
                new KeyAdapter() {
                    @Override
                    public void keyPressed(
                            KeyEvent e) {

                        if ((e.stateMask
                                & SWT.MOD1) != 0
                                && (e.keyCode == 'f'
                                        || e.keyCode == 'F')) {

                            showSearch();
                            e.doit = false;
                        }
                    }
                });

        text.addMouseMoveListener(
                new MouseMoveListener() {
                    @Override
                    public void mouseMove(
                            MouseEvent e) {

                        String line =
                                lineAt(
                                        text,
                                        e.x,
                                        e.y);

                        text.setCursor(
                                StackTraceNavigator
                                        .looksNavigable(line)
                                                ? text.getDisplay()
                                                        .getSystemCursor(
                                                                SWT.CURSOR_HAND)
                                                : null);
                    }
                });

        text.addMouseListener(
                new MouseAdapter() {
                    @Override
                    public void mouseUp(
                            MouseEvent e) {

                        if (e.button != 1) {
                            return;
                        }

                        String line =
                                lineAt(
                                        text,
                                        e.x,
                                        e.y);

                        if (StackTraceNavigator
                                .looksNavigable(line)) {

                            StackTraceNavigator.open(
                                    line);
                        }
                    }
                });

        return text;
    }

    private String lineAt(
            StyledText text,
            int x,
            int y) {

        if (text == null
                || text.isDisposed()) {

            return null;
        }

        try {
            int offset =
                    text.getOffsetAtLocation(
                            new Point(x, y));

            int line =
                    text.getLineAtOffset(
                            offset);

            return text.getLine(line);

        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void showSearch() {
        if (searchRow == null
                || searchRow.isDisposed()) {

            return;
        }

        GridData data =
                (GridData)
                        searchRow.getLayoutData();

        data.exclude = false;

        searchRow.setVisible(true);

        searchRow.getParent()
                .layout(
                        true,
                        true);

        String selection =
                activeLogText()
                        .getSelectionText();

        if (selection != null
                && !selection.trim()
                        .isEmpty()
                && selection.indexOf('\n') < 0
                && selection.indexOf('\r') < 0) {

            searchField.setText(selection);
        }

        searchField.setFocus();
        searchField.selectAll();
        updateSearchCount();
    }

    private void hideSearch() {
        GridData data =
                (GridData)
                        searchRow.getLayoutData();

        data.exclude = true;

        searchRow.setVisible(false);

        searchRow.getParent()
                .layout(
                        true,
                        true);

        activeLogText().setFocus();
    }

    private void find(
            boolean forward,
            boolean fromStartWhenModified) {

        StyledText text =
                activeLogText();

        String needle =
                searchField.getText();

        if (needle == null
                || needle.isEmpty()
                || text.getCharCount() == 0) {

            updateSearchCount();
            return;
        }

        String haystack =
                text.getText();

        String lowerHaystack =
                haystack.toLowerCase();

        String lowerNeedle =
                needle.toLowerCase();

        int index;

        if (forward) {
            int start =
                    fromStartWhenModified
                            ? 0
                            : text.getSelectionRange().x
                                    + Math.max(
                                            1,
                                            text.getSelectionRange().y);

            index =
                    lowerHaystack.indexOf(
                            lowerNeedle,
                            Math.min(
                                    start,
                                    lowerHaystack.length()));

            if (index < 0
                    && !fromStartWhenModified) {

                index =
                        lowerHaystack.indexOf(
                                lowerNeedle);
            }

        } else {
            int start =
                    Math.max(
                            0,
                            text.getSelectionRange().x
                                    - 1);

            index =
                    lowerHaystack.lastIndexOf(
                            lowerNeedle,
                            start);

            if (index < 0) {
                index =
                        lowerHaystack.lastIndexOf(
                                lowerNeedle);
            }
        }

        if (index >= 0) {
            text.setSelection(
                    index,
                    index + needle.length());

            text.showSelection();
        }

        updateSearchCount();
    }

    private void updateSearchCount() {
        if (searchResultLabel == null
                || searchResultLabel.isDisposed()) {

            return;
        }

        String needle =
                searchField == null
                        ? ""
                        : searchField.getText();

        if (needle == null
                || needle.isEmpty()) {

            searchResultLabel.setText("");
            return;
        }

        String haystack =
                activeLogText()
                        .getText()
                        .toLowerCase();

        String lowerNeedle =
                needle.toLowerCase();

        int count = 0;
        int from = 0;

        while (from <= haystack.length()
                - lowerNeedle.length()) {

            int index =
                    haystack.indexOf(
                            lowerNeedle,
                            from);

            if (index < 0) {
                break;
            }

            count++;
            from =
                    index
                    + Math.max(
                            1,
                            lowerNeedle.length());
        }

        searchResultLabel.setText(
                count == 1
                        ? "1 match"
                        : count + " matches");

        searchRow.layout(
                true,
                true);
    }

    private StyledText activeLogText() {
        int selection =
                tabs == null
                        ? 0
                        : tabs.getSelectionIndex();

        if (selection == 1) {
            return systemErrText;
        }

        if (selection == 2) {
            return deployText;
        }

        return systemOutText;
    }

    private void requestRefresh() {
        if (disposed
                || !refreshScheduled
                        .compareAndSet(
                                false,
                                true)) {

            return;
        }

        Job job =
                new Job(
                        "Read WebSphere logs") {
                    @Override
                    protected IStatus run(
                            IProgressMonitor monitor) {

                        final File directory =
                                WebSphereLogPaths
                                        .resolveLogDirectory();

                        IPreferenceStore store =
                                WebSphereHotSyncSettings.store();

                        int maxBytes =
                                store == null
                                        ? 262144
                                        : store.getInt(
                                                WebSphereLogSettings.TAIL_BYTES);

                        final String outText;
                        final String errText;

                        if (directory == null) {
                            outText =
                                    "WebSphere log directory is not configured.\n\n"
                                    + "Configure Window -> Preferences -> "
                                    + "JSF / Java Navigation -> WebSphere Hot Sync.";

                            errText = outText;

                        } else {
                            outText =
                                    read(
                                            new File(
                                                    directory,
                                                    "SystemOut.log"),
                                            maxBytes);

                            errText =
                                    read(
                                            new File(
                                                    directory,
                                                    "SystemErr.log"),
                                            maxBytes);
                        }

                        if (!disposed
                                && getSite() != null
                                && getSite().getShell() != null
                                && !getSite().getShell()
                                        .isDisposed()) {

                            getSite().getShell()
                                    .getDisplay()
                                    .asyncExec(
                                            new Runnable() {
                                                @Override
                                                public void run() {
                                                    try {
                                                        if (disposed
                                                                || systemOutText == null
                                                                || systemOutText.isDisposed()) {

                                                            return;
                                                        }

                                                        pathLabel.setText(
                                                                directory == null
                                                                        ? "WebSphere log directory is not configured."
                                                                        : directory.getAbsolutePath());

                                                        replacePreservingSelection(
                                                                systemOutText,
                                                                outText);

                                                        replacePreservingSelection(
                                                                systemErrText,
                                                                errText);

                                                        LogSyntaxHighlighter.apply(
                                                                systemOutText);

                                                        LogSyntaxHighlighter.apply(
                                                                systemErrText);

                                                        updateSearchCount();

                                                    } finally {
                                                        refreshScheduled
                                                                .set(false);
                                                    }
                                                }
                                            });

                        } else {
                            refreshScheduled.set(false);
                        }

                        return Status.OK_STATUS;
                    }
                };

        job.setSystem(true);
        job.schedule();
    }

    private void replacePreservingSelection(
            StyledText target,
            String replacement) {

        Point oldSelection =
                target.getSelectionRange();

        boolean atEnd =
                oldSelection.x
                        + oldSelection.y
                        >= target.getCharCount();

        target.setText(replacement);

        if (atEnd) {
            target.setSelection(
                    target.getCharCount());

        } else {
            int start =
                    Math.min(
                            oldSelection.x,
                            target.getCharCount());

            int end =
                    Math.min(
                            start + oldSelection.y,
                            target.getCharCount());

            target.setSelection(
                    start,
                    end);
        }
    }

    private String read(
            File file,
            int maxBytes) {

        try {
            return WebSphereLogTailReader
                    .readTail(
                            file,
                            maxBytes);

        } catch (IOException e) {
            return "Could not read "
                    + file.getAbsolutePath()
                    + "\n\n"
                    + e.getMessage();
        }
    }

    private void scheduleRefresh() {
        if (disposed
                || getSite() == null
                || getSite().getShell() == null
                || getSite().getShell()
                        .getDisplay()
                        .isDisposed()) {

            return;
        }

        getSite().getShell()
                .getDisplay()
                .timerExec(
                        1000,
                        new Runnable() {
                            @Override
                            public void run() {

                                if (disposed) {
                                    return;
                                }

                                IPreferenceStore store =
                                        WebSphereHotSyncSettings.store();

                                if (store != null
                                        && store.getBoolean(
                                                WebSphereLogSettings.AUTO_REFRESH)) {

                                    requestRefresh();
                                }

                                scheduleRefresh();
                            }
                        });
    }

    @Override
    public void setFocus() {
        if (tabs != null
                && !tabs.isDisposed()) {

            activeLogText().setFocus();
        }
    }

    @Override
    public void dispose() {
        disposed = true;

        if (instance == this) {
            instance = null;
        }

        super.dispose();
    }

    public static void appendDeployStatus(
            final String text) {

        if (text == null
                || text.trim().isEmpty()) {

            return;
        }

        synchronized (DEPLOY_STATUS) {
            DEPLOY_STATUS.append(
                    text.endsWith("\n")
                            ? text
                            : text + "\n");
        }

        final WebSphereLogsView current =
                instance;

        if (current == null
                || current.disposed) {

            return;
        }

        current.getSite()
                .getShell()
                .getDisplay()
                .asyncExec(
                        new Runnable() {
                            @Override
                            public void run() {

                                if (current.disposed
                                        || current.deployText == null
                                        || current.deployText.isDisposed()) {

                                    return;
                                }

                                current.deployText.append(
                                        text.endsWith("\n")
                                                ? text
                                                : text + "\n");

                                current.deployText.setSelection(
                                        current.deployText
                                                .getCharCount());

                                LogSyntaxHighlighter.apply(
                                        current.deployText);
                            }
                        });
    }
}
