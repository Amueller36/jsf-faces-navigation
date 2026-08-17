package de.andre.jsfnavigation;

import org.eclipse.core.resources.IFile;

public final class FlowCategoryClassifier {

    public static final String VIEW = "View";
    public static final String CONTROLLER = "Controller";
    public static final String BEAN = "Bean";
    public static final String ISP = "ISP";
    public static final String SERVICE = "Service";
    public static final String PERSISTENCE = "Persistence";
    public static final String RESOURCE = "Resources";
    public static final String TEST = "Tests";
    public static final String OTHER = "Other";

    private FlowCategoryClassifier() {
    }

    public static String classify(IFile file) {
        if (file == null) {
            return OTHER;
        }

        String name =
                file.getName();

        String lower =
                name.toLowerCase();

        if (lower.endsWith(".xhtml")
                || lower.endsWith(".html")
                || lower.endsWith(".htm")) {

            return VIEW;
        }

        if (lower.endsWith(".properties")
                || lower.endsWith(".xml")
                || lower.endsWith(".css")
                || lower.endsWith(".js")) {

            return RESOURCE;
        }

        if (lower.contains("test")) {
            return TEST;
        }

        if (name.endsWith("Controller.java")) {
            return CONTROLLER;
        }

        if (name.endsWith("Bean.java")) {
            return BEAN;
        }

        if (name.endsWith("ISP.java")
                || name.contains("ISP")) {

            return ISP;
        }

        if (name.endsWith("Service.java")) {
            return SERVICE;
        }

        if (name.endsWith("Entity.java")
                || name.endsWith("Repository.java")
                || name.endsWith("DAO.java")
                || name.endsWith("Dao.java")) {

            return PERSISTENCE;
        }

        return OTHER;
    }
}
