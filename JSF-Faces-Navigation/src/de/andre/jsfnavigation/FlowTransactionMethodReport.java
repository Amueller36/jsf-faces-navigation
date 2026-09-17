package de.andre.jsfnavigation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class FlowTransactionMethodReport {

    private final String methodLabel;
    private final int line;
    private final List<FlowTransactionEvent> events;
    private final List<String> hints;

    public FlowTransactionMethodReport(
            String methodLabel,
            int line,
            List<FlowTransactionEvent> events,
            List<String> hints) {

        this.methodLabel =
                methodLabel == null
                        ? "test(...)"
                        : methodLabel;

        this.line =
                Math.max(
                        1,
                        line);

        this.events =
                Collections.unmodifiableList(
                        new ArrayList<FlowTransactionEvent>(
                                events));

        this.hints =
                Collections.unmodifiableList(
                        new ArrayList<String>(
                                hints));
    }

    public String getMethodLabel() {
        return methodLabel;
    }

    public int getLine() {
        return line;
    }

    public List<FlowTransactionEvent> getEvents() {
        return events;
    }

    public List<String> getHints() {
        return hints;
    }
}
