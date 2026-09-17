package de.andre.jsfnavigation;

public final class JsfComponentHelp {

    private final String title;
    private final String text;

    public JsfComponentHelp(
            String title,
            String text) {

        this.title = title;
        this.text = text;
    }

    public String getTitle() {
        return title;
    }

    public String getText() {
        return text;
    }
}
