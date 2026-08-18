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
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
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

    private Label pathLabel;
    private Button autoRefreshButton;
    private Text systemOutText;
    private Text systemErrText;
    private CTabFolder tabs;

    private boolean disposed;
    private final AtomicBoolean refreshScheduled =
            new AtomicBoolean(false);

    @Override
    public void createPartControl(
            Composite parent) {

        parent.setLayout(
                new GridLayout(1, false));

        createToolbar(parent);
        createTabs(parent);

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
                "Clear the text shown in Eclipse. This does not modify the actual WebSphere log files.");

        clear.addSelectionListener(
                new SelectionAdapter() {
                    @Override
                    public void widgetSelected(
                            SelectionEvent e) {

                        systemOutText.setText("");
                        systemErrText.setText("");
                    }
                });
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

        outItem.setText("SystemOut.log");

        systemOutText =
                createLogText(tabs);

        outItem.setControl(
                systemOutText);

        CTabItem errItem =
                new CTabItem(
                        tabs,
                        SWT.NONE);

        errItem.setText("SystemErr.log");

        systemErrText =
                createLogText(tabs);

        errItem.setControl(
                systemErrText);

        tabs.setSelection(0);
    }

    private Text createLogText(
            Composite parent) {

        Text text =
                new Text(
                        parent,
                        SWT.MULTI
                        | SWT.READ_ONLY
                        | SWT.H_SCROLL
                        | SWT.V_SCROLL);

        return text;
    }

    private void requestRefresh() {
        if (disposed
                || !refreshScheduled.compareAndSet(
                        false,
                        true)) {

            return;
        }

        Job job =
                new Job("Read WebSphere logs") {
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
                                && !getSite().getShell().isDisposed()) {

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

                                                        systemOutText.setText(
                                                                outText);

                                                        systemErrText.setText(
                                                                errText);

                                                        systemOutText.setSelection(
                                                                systemOutText.getCharCount());

                                                        systemErrText.setSelection(
                                                                systemErrText.getCharCount());

                                                    } finally {
                                                        refreshScheduled.set(false);
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

            tabs.setFocus();
        }
    }

    @Override
    public void dispose() {
        disposed = true;
        super.dispose();
    }
}
