package de.andre.jsfnavigation;

public final class FlowTransactionEvent {

    public static final String TX =
            "TX";

    public static final String WRITE =
            "WRITE";

    public static final String READ =
            "READ";

    public static final String QUERY =
            "QUERY";

    public static final String FLUSH =
            "FLUSH";

    public static final String PC_RESET =
            "PC RESET";

    public static final String PC =
            "PC";

    public static final String DB_CREATE =
            "DB CREATE";

    public static final String DB_DELETE =
            "DB DELETE";

    public static final String CLEANUP_TRACK =
            "CLEANUP TRACK";

    public static final String HEURISTIC =
            "HEURISTIC";

    private final String kind;
    private final String detail;
    private final int line;
    private final boolean heuristic;

    public FlowTransactionEvent(
            String kind,
            String detail,
            int line,
            boolean heuristic) {

        this.kind =
                kind == null
                        ? ""
                        : kind;

        this.detail =
                detail == null
                        ? ""
                        : detail;

        this.line =
                Math.max(
                        1,
                        line);

        this.heuristic =
                heuristic;
    }

    public String getKind() {
        return kind;
    }

    public String getDetail() {
        return detail;
    }

    public int getLine() {
        return line;
    }

    public boolean isHeuristic() {
        return heuristic;
    }
}
