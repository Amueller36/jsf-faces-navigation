package de.andre.jsfnavigation;

public final class JsfComponentProposal {

    private final String name;
    private final String insertText;
    private final String detail;
    private final boolean attribute;

    public JsfComponentProposal(
            String name,
            String insertText,
            String detail,
            boolean attribute) {

        this.name = name;
        this.insertText = insertText;
        this.detail = detail;
        this.attribute = attribute;
    }

    public String getName() {
        return name;
    }

    public String getInsertText() {
        return insertText;
    }

    public String getDetail() {
        return detail;
    }

    public boolean isAttribute() {
        return attribute;
    }

    public String displayText() {
        if (detail == null
                || detail.trim().isEmpty()) {

            return name;
        }

        return name
                + " — "
                + oneLine(detail);
    }

    private static String oneLine(
            String value) {

        String text =
                value.replace('\r', ' ')
                        .replace('\n', ' ')
                        .replace('\t', ' ')
                        .trim();

        while (text.contains("  ")) {
            text =
                    text.replace(
                            "  ",
                            " ");
        }

        return text.length() > 120
                ? text.substring(0, 117)
                        + "..."
                : text;
    }
}
