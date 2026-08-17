package de.andre.jsfnavigation;

public interface NavigationTarget {

    String getLabel();

    String getIdentity();

    void open();
}
