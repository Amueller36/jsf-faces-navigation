package de.andre.jsfnavigation;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.resource.JFaceResources;
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
import org.eclipse.swt.events.MouseWheelListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.ScrollBar;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.part.ViewPart;

public final class WebSphereLogsView
        extends ViewPart {

    public static final String VIEW_ID =
            "de.andre.jsfnavigation.views.webSphereLogs";

    private static volatile WebSphereLogsView instance;

    private static final StringBuilder DEPLOY_STATUS =
            new StringBuilder();

    private static volatile int LAST_DEPLOY_PERCENT;
    private static volatile String LAST_DEPLOY_PHASE =
            "Idle";
    private static volatile boolean LAST_DEPLOY_ACTIVE;

    private Label pathLabel;
    private Button autoRefreshButton;
    private CTabFolder tabs;

    private Composite deployProgressRow;
    private ProgressBar deployProgressBar;
    private Label deployProgressLabel;

    private long fastRefreshUntil;

    private StyledText systemOutText;
    private StyledText systemErrText;
    private StyledText deployText;

    private Composite searchRow;
    private Text searchField;
    private Label searchResultLabel;

    private Composite filterRow;
    private Text filterField;
    private Label filterResultLabel;

    private String rawSystemOut = "";
    private String rawSystemErr = "";
    private String rawDeploy = "";

    private boolean autoScrollOut = true;
    private boolean autoScrollErr = true;
    private boolean autoScrollDeploy = true;

    private int logFontDelta;
    private Font logFont;

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
        createDeployProgress(parent);
        createSearchBar(parent);
        createFilterBar(parent);
        createTabs(parent);

        synchronized (DEPLOY_STATUS) {
            if (DEPLOY_STATUS.length() > 0) {
                rawDeploy =
                        DEPLOY_STATUS.toString();

                replaceFromRaw(
                        deployText,
                        rawDeploy);
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
                new GridLayout(8, false);

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

        Button filter =
                new Button(
                        row,
                        SWT.PUSH);

        filter.setText("Filter");

        filter.setToolTipText(
                "Filter visible log lines. Shortcut: Ctrl+Shift+F");

        filter.addSelectionListener(
                new SelectionAdapter() {
                    @Override
                    public void widgetSelected(
                            SelectionEvent e) {

                        showFilter();
                    }
                });

        Button smaller =
                new Button(
                        row,
                        SWT.PUSH);

        smaller.setText("A-");
        smaller.setToolTipText(
                "Decrease log font size (Ctrl+-)");

        smaller.addSelectionListener(
                new SelectionAdapter() {
                    @Override
                    public void widgetSelected(
                            SelectionEvent e) {

                        changeLogFont(-1);
                    }
                });

        Button larger =
                new Button(
                        row,
                        SWT.PUSH);

        larger.setText("A+");
        larger.setToolTipText(
                "Increase log font size (Ctrl++)");

        larger.addSelectionListener(
                new SelectionAdapter() {
                    @Override
                    public void widgetSelected(
                            SelectionEvent e) {

                        changeLogFont(1);
                    }
                });

        Button resetFont =
                new Button(
                        row,
                        SWT.PUSH);

        resetFont.setText("A");
        resetFont.setToolTipText(
                "Reset log font size (Ctrl+0)");

        resetFont.addSelectionListener(
                new SelectionAdapter() {
                    @Override
                    public void widgetSelected(
                            SelectionEvent e) {

                        resetLogFont();
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


    private void createDeployProgress(
            Composite parent) {

        deployProgressRow =
                new Composite(
                        parent,
                        SWT.NONE);

        deployProgressRow.setLayoutData(
                new GridData(
                        SWT.FILL,
                        SWT.TOP,
                        true,
                        false));

        GridLayout layout =
                new GridLayout(2, false);

        layout.marginWidth = 0;
        layout.marginHeight = 0;

        deployProgressRow.setLayout(layout);

        deployProgressBar =
                new ProgressBar(
                        deployProgressRow,
                        SWT.HORIZONTAL);

        deployProgressBar.setMinimum(0);
        deployProgressBar.setMaximum(100);

        deployProgressBar.setLayoutData(
                new GridData(
                        SWT.FILL,
                        SWT.CENTER,
                        true,
                        false));

        deployProgressLabel =
                new Label(
                        deployProgressRow,
                        SWT.NONE);

        deployProgressLabel.setText("Idle");

        applyDeployProgress(
                LAST_DEPLOY_PERCENT,
                LAST_DEPLOY_PHASE,
                LAST_DEPLOY_ACTIVE);
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


    private void createFilterBar(
            Composite parent) {

        filterRow =
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
        filterRow.setLayoutData(data);
        filterRow.setVisible(false);

        GridLayout layout =
                new GridLayout(4, false);

        layout.marginWidth = 0;
        layout.marginHeight = 0;

        filterRow.setLayout(layout);

        new Label(
                filterRow,
                SWT.NONE)
                .setText("Filter:");

        filterField =
                new Text(
                        filterRow,
                        SWT.SEARCH
                        | SWT.ICON_CANCEL);

        filterField.setMessage(
                "Show only lines containing this text");

        filterField.setLayoutData(
                new GridData(
                        SWT.FILL,
                        SWT.CENTER,
                        true,
                        false));

        filterField.addModifyListener(
                new ModifyListener() {
                    @Override
                    public void modifyText(
                            ModifyEvent e) {

                        applyFilterToAll();
                    }
                });

        filterField.addKeyListener(
                new KeyAdapter() {
                    @Override
                    public void keyPressed(
                            KeyEvent e) {

                        if (e.keyCode == SWT.ESC) {
                            hideFilter();
                            e.doit = false;
                        }
                    }
                });

        filterResultLabel =
                new Label(
                        filterRow,
                        SWT.NONE);

        filterResultLabel.setText("");

        Button clearFilter =
                new Button(
                        filterRow,
                        SWT.PUSH);

        clearFilter.setText("Clear");

        clearFilter.addSelectionListener(
                new SelectionAdapter() {
                    @Override
                    public void widgetSelected(
                            SelectionEvent e) {

                        filterField.setText("");
                        hideFilter();
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

                        updateFilterCount();
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

        text.setMargins(6, 4, 6, 4);

        applyLogFont(text);

        ScrollBar vertical =
                text.getVerticalBar();

        if (vertical != null) {
            vertical.addSelectionListener(
                    new SelectionAdapter() {
                        @Override
                        public void widgetSelected(
                                SelectionEvent e) {

                            updateAutoScrollState(
                                    text);
                        }
                    });
        }

        text.addMouseWheelListener(
                new MouseWheelListener() {
                    @Override
                    public void mouseScrolled(
                            MouseEvent e) {

                        if ((e.stateMask
                                & SWT.MOD1) != 0) {

                            changeLogFont(
                                    e.count > 0
                                            ? 1
                                            : -1);

                        } else {
                            text.getDisplay()
                                    .asyncExec(
                                            new Runnable() {
                                                @Override
                                                public void run() {
                                                    updateAutoScrollState(
                                                            text);
                                                }
                                            });
                        }
                    }
                });

        text.addKeyListener(
                new KeyAdapter() {
                    @Override
                    public void keyPressed(
                            KeyEvent e) {

                        if ((e.stateMask
                                & SWT.MOD1) != 0
                                && (e.stateMask
                                        & SWT.SHIFT) != 0
                                && (e.keyCode == 'f'
                                        || e.keyCode == 'F')) {

                            showFilter();
                            e.doit = false;

                        } else if ((e.stateMask
                                & SWT.MOD1) != 0
                                && (e.keyCode == 'f'
                                        || e.keyCode == 'F')) {

                            showSearch();
                            e.doit = false;

                        } else if ((e.stateMask
                                & SWT.MOD1) != 0
                                && (e.keyCode == '+'
                                        || e.keyCode == '=')) {

                            changeLogFont(1);
                            e.doit = false;

                        } else if ((e.stateMask
                                & SWT.MOD1) != 0
                                && e.keyCode == '-') {

                            changeLogFont(-1);
                            e.doit = false;

                        } else if ((e.stateMask
                                & SWT.MOD1) != 0
                                && e.keyCode == '0') {

                            resetLogFont();
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


    private void showFilter() {
        GridData data =
                (GridData)
                        filterRow.getLayoutData();

        data.exclude = false;
        filterRow.setVisible(true);

        filterRow.getParent()
                .layout(
                        true,
                        true);

        filterField.setFocus();
        filterField.selectAll();
    }

    private void hideFilter() {
        GridData data =
                (GridData)
                        filterRow.getLayoutData();

        data.exclude = true;
        filterRow.setVisible(false);

        filterRow.getParent()
                .layout(
                        true,
                        true);

        activeLogText().setFocus();
    }

    private void applyFilterToAll() {
        replaceFromRaw(
                systemOutText,
                rawSystemOut);

        replaceFromRaw(
                systemErrText,
                rawSystemErr);

        replaceFromRaw(
                deployText,
                rawDeploy);

        updateFilterCount();
        updateSearchCount();
    }

    private void replaceFromRaw(
            StyledText target,
            String raw) {

        if (target == null
                || target.isDisposed()) {

            return;
        }

        String replacement =
                filtered(raw);

        replacePreservingScroll(
                target,
                replacement);

        LogSyntaxHighlighter.apply(
                target);
    }

    private String filtered(
            String raw) {

        String filter =
                filterField == null
                        ? ""
                        : filterField.getText();

        if (filter == null
                || filter.trim().isEmpty()) {

            return raw == null
                    ? ""
                    : raw;
        }

        String needle =
                filter.toLowerCase();

        String source =
                raw == null
                        ? ""
                        : raw;

        StringBuilder out =
                new StringBuilder();

        String[] lines =
                source.split(
                        "(?<=\\n)",
                        -1);

        for (String line : lines) {
            if (line.toLowerCase()
                    .contains(needle)) {

                out.append(line);
            }
        }

        return out.toString();
    }

    private void updateFilterCount() {
        if (filterResultLabel == null
                || filterResultLabel.isDisposed()) {

            return;
        }

        String filter =
                filterField == null
                        ? ""
                        : filterField.getText();

        if (filter == null
                || filter.trim().isEmpty()) {

            filterResultLabel.setText("");
            return;
        }

        String raw =
                rawFor(
                        activeLogText());

        String needle =
                filter.toLowerCase();

        int matches = 0;

        String[] lines =
                (raw == null
                        ? ""
                        : raw)
                        .split("\\r?\\n");

        for (String line : lines) {
            if (line.toLowerCase()
                    .contains(needle)) {

                matches++;
            }
        }

        filterResultLabel.setText(
                matches == 1
                        ? "1 line"
                        : matches + " lines");

        filterRow.layout(
                true,
                true);
    }

    private String rawFor(
            StyledText text) {

        if (text == systemErrText) {
            return rawSystemErr;
        }

        if (text == deployText) {
            return rawDeploy;
        }

        return rawSystemOut;
    }

    private boolean autoScrollFor(
            StyledText text) {

        if (text == systemErrText) {
            return autoScrollErr;
        }

        if (text == deployText) {
            return autoScrollDeploy;
        }

        return autoScrollOut;
    }

    private void setAutoScrollFor(
            StyledText text,
            boolean enabled) {

        if (text == systemErrText) {
            autoScrollErr = enabled;

        } else if (text == deployText) {
            autoScrollDeploy = enabled;

        } else {
            autoScrollOut = enabled;
        }
    }

    private void updateAutoScrollState(
            StyledText text) {

        if (text == null
                || text.isDisposed()) {

            return;
        }

        ScrollBar bar =
                text.getVerticalBar();

        if (bar == null) {
            return;
        }

        int bottom =
                bar.getMaximum()
                - bar.getThumb();

        boolean atBottom =
                bar.getSelection()
                        >= Math.max(
                                0,
                                bottom - 2);

        setAutoScrollFor(
                text,
                atBottom);
    }

    private void replacePreservingScroll(
            StyledText target,
            String replacement) {

        boolean autoScroll =
                autoScrollFor(target);

        int oldTop =
                target.getTopIndex();

        Point oldSelection =
                target.getSelectionRange();

        target.setText(
                replacement == null
                        ? ""
                        : replacement);

        if (autoScroll) {
            target.setTopIndex(
                    Math.max(
                            0,
                            target.getLineCount() - 1));

            target.setSelection(
                    target.getCharCount());

            setAutoScrollFor(
                    target,
                    true);

        } else {
            target.setTopIndex(
                    Math.min(
                            oldTop,
                            Math.max(
                                    0,
                                    target.getLineCount() - 1)));

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

            setAutoScrollFor(
                    target,
                    false);
        }
    }

    private void applyLogFont(
            StyledText text) {

        if (text == null
                || text.isDisposed()) {

            return;
        }

        if (logFont == null
                || logFont.isDisposed()) {

            Font baseFont =
                    JFaceResources.getTextFont();

            FontData[] data =
                    baseFont.getFontData();

            for (FontData fontData : data) {
                fontData.setHeight(
                        Math.max(
                                6,
                                fontData.getHeight()
                                        + logFontDelta));
            }

            logFont =
                    new Font(
                            text.getDisplay(),
                            data);
        }

        text.setFont(logFont);
    }

    private void changeLogFont(
            int delta) {

        logFontDelta =
                Math.max(
                        -6,
                        Math.min(
                                20,
                                logFontDelta + delta));

        recreateLogFont();
    }

    private void resetLogFont() {
        logFontDelta = 0;
        recreateLogFont();
    }

    private void recreateLogFont() {
        if (logFont != null
                && !logFont.isDisposed()) {

            logFont.dispose();
            logFont = null;
        }

        applyLogFont(systemOutText);
        applyLogFont(systemErrText);
        applyLogFont(deployText);
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

                                                        rawSystemOut =
                                                                outText;

                                                        rawSystemErr =
                                                                errText;

                                                        replaceFromRaw(
                                                                systemOutText,
                                                                rawSystemOut);

                                                        replaceFromRaw(
                                                                systemErrText,
                                                                rawSystemErr);

                                                        updateFilterCount();
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

        int delay =
                System.currentTimeMillis()
                        < fastRefreshUntil
                                ? 250
                                : 1000;

        getSite().getShell()
                .getDisplay()
                .timerExec(
                        delay,
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


    private void applyDeployProgress(
            int percent,
            String phase,
            boolean active) {

        if (deployProgressBar == null
                || deployProgressBar.isDisposed()
                || deployProgressLabel == null
                || deployProgressLabel.isDisposed()) {

            return;
        }

        int safePercent =
                Math.max(
                        0,
                        Math.min(
                                100,
                                percent));

        deployProgressBar.setSelection(
                safePercent);

        String text =
                phase == null
                        || phase.trim().isEmpty()
                                ? "Smart Deploy"
                                : phase;

        deployProgressLabel.setText(
                active
                        ? safePercent + "% — " + text
                        : text);

        deployProgressRow.layout(
                true,
                true);
    }

    private void enableFastRefreshBurst() {
        fastRefreshUntil =
                System.currentTimeMillis()
                + 12000L;

        requestRefresh();
    }

    public static void deployProgress(
            final int percent,
            final String phase) {

        LAST_DEPLOY_PERCENT =
                Math.max(
                        0,
                        Math.min(
                                100,
                                percent));

        LAST_DEPLOY_PHASE =
                phase == null
                        ? "Smart Deploy"
                        : phase;

        LAST_DEPLOY_ACTIVE =
                percent < 100;

        final WebSphereLogsView current =
                instance;

        if (current == null
                || current.disposed
                || current.getSite() == null
                || current.getSite().getShell() == null
                || current.getSite().getShell().isDisposed()) {

            return;
        }

        current.getSite()
                .getShell()
                .getDisplay()
                .asyncExec(
                        new Runnable() {
                            @Override
                            public void run() {

                                if (current.disposed) {
                                    return;
                                }

                                current.applyDeployProgress(
                                        LAST_DEPLOY_PERCENT,
                                        LAST_DEPLOY_PHASE,
                                        LAST_DEPLOY_ACTIVE);

                                if (percent >= 80) {
                                    current.enableFastRefreshBurst();
                                }
                            }
                        });
    }

    public static void deployFinished(
            String phase) {

        LAST_DEPLOY_PERCENT = 100;
        LAST_DEPLOY_PHASE =
                phase == null
                        ? "Smart Deploy finished"
                        : phase;
        LAST_DEPLOY_ACTIVE = false;

        deployProgress(
                100,
                LAST_DEPLOY_PHASE);

        final WebSphereLogsView current =
                instance;

        if (current != null
                && !current.disposed) {

            current.enableFastRefreshBurst();
        }
    }

    public static void deployFailed(
            String message) {

        LAST_DEPLOY_PERCENT = 100;
        LAST_DEPLOY_PHASE =
                "Failed"
                + (message == null
                        || message.trim().isEmpty()
                                ? ""
                                : " — " + message);
        LAST_DEPLOY_ACTIVE = false;

        deployProgress(
                100,
                LAST_DEPLOY_PHASE);
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

        if (logFont != null
                && !logFont.isDisposed()) {

            logFont.dispose();
            logFont = null;
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

                                synchronized (DEPLOY_STATUS) {
                                    current.rawDeploy =
                                            DEPLOY_STATUS.toString();
                                }

                                current.replaceFromRaw(
                                        current.deployText,
                                        current.rawDeploy);

                                current.updateFilterCount();
                            }
                        });
    }
}
