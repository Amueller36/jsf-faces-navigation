package de.andre.jsfnavigation;

public final class FlowTestCaseResult {

    public static final int PASS = 1;
    public static final int FAILURE = 2;
    public static final int ERROR = 3;
    public static final int SKIPPED = 4;

    private final String testFilePath;
    private final String className;
    private final String methodName;
    private final int status;
    private final String stackTrace;
    private final String expected;
    private final String actual;
    private final double elapsedSeconds;

    public FlowTestCaseResult(
            String testFilePath,
            String className,
            String methodName,
            int status,
            String stackTrace,
            String expected,
            String actual,
            double elapsedSeconds) {

        this.testFilePath =
                safe(testFilePath);

        this.className =
                safe(className);

        this.methodName =
                safe(methodName);

        this.status = status;
        this.stackTrace =
                safe(stackTrace);

        this.expected =
                safe(expected);

        this.actual =
                safe(actual);

        this.elapsedSeconds =
                Double.isNaN(elapsedSeconds)
                        ? 0.0d
                        : Math.max(
                                0.0d,
                                elapsedSeconds);
    }

    public String getTestFilePath() {
        return testFilePath;
    }

    public String getClassName() {
        return className;
    }

    public String getMethodName() {
        return methodName;
    }

    public int getStatus() {
        return status;
    }

    public String getStackTrace() {
        return stackTrace;
    }

    public String getExpected() {
        return expected;
    }

    public String getActual() {
        return actual;
    }

    public double getElapsedSeconds() {
        return elapsedSeconds;
    }

    public boolean isFailed() {
        return status == FAILURE
                || status == ERROR;
    }

    public boolean isSkipped() {
        return status == SKIPPED;
    }

    public String getSimpleClassName() {
        if (className.isEmpty()) {
            return "Test";
        }

        int dot =
                className.lastIndexOf('.');

        return dot >= 0
                ? className.substring(
                        dot + 1)
                : className;
    }

    public String getFirstTraceLine() {
        if (stackTrace.isEmpty()) {
            return "";
        }

        int newline =
                stackTrace.indexOf('\n');

        String line =
                newline >= 0
                        ? stackTrace.substring(
                                0,
                                newline)
                        : stackTrace;

        if (line.endsWith("\r")) {
            line =
                    line.substring(
                            0,
                            line.length() - 1);
        }

        return line.trim();
    }

    private static String safe(
            String value) {

        return value == null
                ? ""
                : value;
    }
}
