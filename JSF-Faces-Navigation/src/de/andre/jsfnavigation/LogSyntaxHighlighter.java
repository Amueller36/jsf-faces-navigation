package de.andre.jsfnavigation;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Display;

public final class LogSyntaxHighlighter {

    private static final Pattern IBM_WARNING =
            Pattern.compile("\\b[A-Z0-9]{6,}W:");

    private static final Pattern IBM_INFO =
            Pattern.compile("\\b[A-Z0-9]{6,}I:");

    private LogSyntaxHighlighter() {
    }

    public static void apply(
            StyledText text) {

        if (text == null
                || text.isDisposed()) {

            return;
        }

        String content = text.getText();

        if (content.isEmpty()) {
            text.setStyleRanges(
                    new StyleRange[0]);

            return;
        }

        Display display =
                text.getDisplay();

        Color error =
                display.getSystemColor(
                        SWT.COLOR_RED);

        Color warning =
                display.getSystemColor(
                        SWT.COLOR_DARK_YELLOW);

        Color info =
                display.getSystemColor(
                        SWT.COLOR_DARK_GREEN);

        Color stack =
                display.getSystemColor(
                        SWT.COLOR_BLUE);

        List<StyleRange> styles =
                new ArrayList<StyleRange>();

        int lineCount =
                text.getLineCount();

        for (int lineIndex = 0;
                lineIndex < lineCount;
                lineIndex++) {

            int offset =
                    text.getOffsetAtLine(
                            lineIndex);

            String line =
                    text.getLine(lineIndex);

            if (line == null) {
                line = "";
            }

            if (StackTraceNavigator
                    .looksNavigable(line)) {

                StyleRange style =
                        new StyleRange(
                                offset,
                                line.length(),
                                stack,
                                null);

                style.underline = true;
                styles.add(style);

            } else if (isError(line)) {
                StyleRange style =
                        new StyleRange(
                                offset,
                                line.length(),
                                error,
                                null,
                                SWT.BOLD);

                styles.add(style);

            } else if (isWarning(line)) {
                styles.add(
                        new StyleRange(
                                offset,
                                line.length(),
                                warning,
                                null));

            } else if (isInfo(line)) {
                styles.add(
                        new StyleRange(
                                offset,
                                line.length(),
                                info,
                                null));
            }

        }

        text.setStyleRanges(
                styles.toArray(
                        new StyleRange[
                                styles.size()]));
    }

    private static boolean isError(
            String line) {

        String upper =
                line.toUpperCase();

        return upper.contains(" ERROR")
                || upper.contains("[ERROR")
                || upper.contains("SEVERE")
                || upper.contains("EXCEPTION")
                || upper.contains("CAUSED BY:")
                || upper.contains(" FFDC");
    }

    private static boolean isWarning(
            String line) {

        String upper =
                line.toUpperCase();

        return upper.contains(" WARN")
                || upper.contains("[WARN")
                || upper.contains("WARNING")
                || IBM_WARNING.matcher(upper)
                        .find();
    }

    private static boolean isInfo(
            String line) {

        String upper =
                line.toUpperCase();

        return upper.contains(" INFO")
                || upper.contains("[INFO")
                || IBM_INFO.matcher(upper)
                        .find();
    }
}
