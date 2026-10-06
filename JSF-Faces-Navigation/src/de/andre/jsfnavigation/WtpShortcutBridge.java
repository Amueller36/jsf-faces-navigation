package de.andre.jsfnavigation;

import org.eclipse.core.resources.IFile;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.IHandlerService;

public final class WtpShortcutBridge {

    private static final String HTML_EDITOR_ID =
            "org.eclipse.wst.html.core.htmlsource.source";

    private static final String COMPONENT_HELP_COMMAND =
            "de.andre.jsfnavigation.command.componentHelp";

    private static final String TOGGLE_COMMENT_COMMAND =
            "de.andre.jsfnavigation.command.toggleXhtmlComment";

    private Listener keyFilter;

    public void start() {
        if (!PlatformUI.isWorkbenchRunning()) {
            return;
        }

        final Display display =
                PlatformUI.getWorkbench()
                        .getDisplay();

        if (display == null
                || display.isDisposed()) {

            return;
        }

        keyFilter =
                new Listener() {
                    @Override
                    public void handleEvent(
                            Event event) {

                        if (event == null
                                || event.type
                                        != SWT.KeyDown
                                || !isSupportedEditor()) {

                            return;
                        }

                        boolean ctrl =
                                (event.stateMask
                                        & SWT.CTRL) != 0;

                        boolean alt =
                                (event.stateMask
                                        & SWT.ALT) != 0;

                        if (ctrl
                                && alt
                                && isKey(
                                        event,
                                        'h')) {

                            event.doit = false;
                            execute(
                                    COMPONENT_HELP_COMMAND);

                            return;
                        }

                        /*
                         * WTP/Eclipse key-binding conflicts can prevent Ctrl+/
                         * from reaching our command. Intercept only in XHTML/
                         * HTML/XML files, so Java/JDT keeps its own shortcut.
                         */
                        if (ctrl
                                && !alt
                                && isSlash(event)) {

                            event.doit = false;
                            execute(
                                    TOGGLE_COMMENT_COMMAND);
                        }
                    }
                };

        final Listener listener =
                keyFilter;

        if (Display.getCurrent() == display) {
            display.addFilter(
                    SWT.KeyDown,
                    listener);

        } else {
            display.asyncExec(
                    new Runnable() {
                        @Override
                        public void run() {

                            if (!display.isDisposed()
                                    && keyFilter
                                            == listener) {

                                display.addFilter(
                                        SWT.KeyDown,
                                        listener);
                            }
                        }
                    });
        }
    }

    public void stop() {
        final Listener listener =
                keyFilter;

        keyFilter = null;

        if (listener == null
                || !PlatformUI.isWorkbenchRunning()) {

            return;
        }

        Display display =
                PlatformUI.getWorkbench()
                        .getDisplay();

        if (display == null
                || display.isDisposed()) {

            return;
        }

        if (Display.getCurrent() == display) {
            display.removeFilter(
                    SWT.KeyDown,
                    listener);

        } else {
            display.syncExec(
                    new Runnable() {
                        @Override
                        public void run() {

                            Display current =
                                    PlatformUI
                                            .getWorkbench()
                                            .getDisplay();

                            if (current != null
                                    && !current.isDisposed()) {

                                current.removeFilter(
                                        SWT.KeyDown,
                                        listener);
                            }
                        }
                    });
        }
    }

    private static boolean isSupportedEditor() {
        IWorkbenchWindow window =
                PlatformUI.getWorkbench()
                        .getActiveWorkbenchWindow();

        if (window == null) {
            return false;
        }

        IWorkbenchPage page =
                window.getActivePage();

        if (page == null) {
            return false;
        }

        IEditorPart editor =
                page.getActiveEditor();

        if (editor == null
                || editor.getSite() == null
                || !HTML_EDITOR_ID.equals(
                        editor.getSite()
                                .getId())) {

            return false;
        }

        IFile file =
                EditorContext.currentFile();

        if (file == null) {
            return false;
        }

        String extension =
                file.getFileExtension();

        if (extension == null) {
            return false;
        }

        return "xhtml".equalsIgnoreCase(
                        extension)
                || "html".equalsIgnoreCase(
                        extension)
                || "htm".equalsIgnoreCase(
                        extension)
                || "xml".equalsIgnoreCase(
                        extension);
    }

    private static boolean isKey(
            Event event,
            char wanted) {

        char character =
                Character.toLowerCase(
                        event.character);

        int keyCode =
                Character.toLowerCase(
                        (char) event.keyCode);

        return character == wanted
                || keyCode == wanted;
    }

    private static boolean isSlash(
            Event event) {

        return event.character == '/'
                || event.keyCode == '/';
    }

    private static void execute(
            String commandId) {

        IWorkbenchWindow window =
                PlatformUI.getWorkbench()
                        .getActiveWorkbenchWindow();

        if (window == null) {
            return;
        }

        IHandlerService handlers =
                (IHandlerService)
                        window.getService(
                                IHandlerService.class);

        if (handlers == null) {
            return;
        }

        try {
            handlers.executeCommand(
                    commandId,
                    null);

        } catch (Exception e) {
            WebSphereStatusLine.show(
                    "Shortcut failed: "
                    + safeMessage(e));
        }
    }

    private static String safeMessage(
            Throwable error) {

        if (error == null) {
            return "unknown error";
        }

        String message =
                error.getMessage();

        return message == null
                || message.trim().isEmpty()
                        ? error.getClass()
                                .getSimpleName()
                        : message;
    }
}
