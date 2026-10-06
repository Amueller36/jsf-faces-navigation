package de.andre.jsfnavigation;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;

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

            if (line == null
                    || line.isEmpty()) {

                continue;
            }

            if (StackTraceNavigator
                    .looksNavigable(line)) {

                /*
                 * Keep the theme's normal foreground color. Only underline
                 * navigable stack frames so they remain readable on both
                 * dark and light Eclipse themes.
                 */
                StyleRange style =
                        new StyleRange();

                style.start = offset;
                style.length = line.length();
                style.underline = true;

                styles.add(style);

            } else if (isError(line)) {
                /*
                 * Do not force bright red on the complete line. The normal
                 * theme foreground stays intact; bold is enough to make
                 * errors stand out without destroying readability.
                 */
                StyleRange style =
                        new StyleRange();

                style.start = offset;
                style.length = line.length();
                style.fontStyle = SWT.BOLD;

                styles.add(style);

            } else if (isWarning(line)) {
                StyleRange style =
                        new StyleRange();

                style.start = offset;
                style.length = line.length();
                style.fontStyle = SWT.BOLD;

                styles.add(style);

            } else if (isInfo(line)) {
                /*
                 * Intentionally leave INFO lines unstyled. INFO usually makes
                 * up most of SystemOut.log and coloring every line creates a
                 * visually noisy "wall of green" on dark themes.
                 */
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
