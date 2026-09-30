package de.andre.jsfnavigation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class FeatureTestAuditGroupNode {

    private final String role;
    private final List<FeatureTestClassStatus> classes;

    public FeatureTestAuditGroupNode(
            String role,
            List<FeatureTestClassStatus> classes) {

        this.role =
                role == null
                        ? ""
                        : role;

        this.classes =
                Collections.unmodifiableList(
                        new ArrayList<FeatureTestClassStatus>(
                                classes));
    }

    public String getRole() {
        return role;
    }

    public List<FeatureTestClassStatus> getClasses() {
        return classes;
    }

    public int getUntestedMethodCount() {
        int count = 0;

        for (FeatureTestClassStatus clazz :
                classes) {

            count += clazz
                    .getUntestedCount();
        }

        return count;
    }

    public int getMethodCount() {
        int count = 0;

        for (FeatureTestClassStatus clazz :
                classes) {

            count += clazz
                    .getMethods()
                    .size();
        }

        return count;
    }

    public String getLabel() {
        return role
                + " ("
                + classes.size()
                + (classes.size() == 1
                        ? " Klasse"
                        : " Klassen")
                + ", "
                + getUntestedMethodCount()
                + "/"
                + getMethodCount()
                + " Methoden offen)";
    }
}
