package de.andre.jsfnavigation;

import org.eclipse.core.resources.IFile;

public final class FlowCategoryClassifier {

    public static final String VIEW = "View";
    public static final String CONTROLLER = "Controller";
    public static final String BEAN = "Bean";
    public static final String TO = "TO";
    public static final String ISP = "ISP";
    public static final String DSP = "DSP";
    public static final String SERVICE = "Service";
    public static final String PERSISTENCE = "Persistence";
    public static final String JAXB = "JAXB";
    public static final String SCHEMA = "Schema";
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

        if (lower.endsWith(".xsd")) {
            return SCHEMA;
        }

        if (lower.endsWith(".properties")
                || lower.endsWith(".xml")
                || lower.endsWith(".css")
                || lower.endsWith(".js")) {

            return RESOURCE;
        }

        String path =
                file.getProjectRelativePath()
                        .toPortableString()
                        .toLowerCase();

        if (lower.contains("test")
                || path.indexOf("src/test") >= 0
                || path.indexOf("/test/") >= 0
                || path.indexOf("/tests/") >= 0
                || path.indexOf("src/integration") >= 0) {

            return TEST;
        }

        if (FlowJavaSemantics.isJaxb(file)) {
            return JAXB;
        }

        if (name.endsWith("Controller.java")) {
            return CONTROLLER;
        }

        if (name.endsWith("Bean.java")) {
            return BEAN;
        }

        if (name.endsWith("TO.java")
                || name.endsWith("Dto.java")
                || name.endsWith("DTO.java")
                || name.endsWith("TransferObject.java")) {

            return TO;
        }

        /*
         * Older parts of the application do not always follow modern naming
         * conventions. Let JDT annotations identify the architectural role as
         * well, so e.g. @Entity class Antrag is still Persistence even though
         * the class name does not end in "Entity".
         */
        if (FlowJavaSemantics.isEntity(file)
                || FlowJavaSemantics.isRepository(file)) {

            return PERSISTENCE;
        }

        if (FlowJavaSemantics.isService(file)) {
            return SERVICE;
        }

        if (FlowJavaSemantics.isManagedBean(file)) {
            return BEAN;
        }

        if (name.endsWith("ISP.java")
                || name.contains("ISP")) {

            return ISP;
        }

        if (name.endsWith("DSP.java")
                || name.contains("DSP")) {

            return DSP;
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
