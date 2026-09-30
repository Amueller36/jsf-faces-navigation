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

        /*
         * Cheap naming conventions first. In 1.16.5 semantic/JDT inspection
         * happened before these checks, so merely capturing a Controller/TO
         * after an editor switch could wake JDT unnecessarily.
         */

        /*
         * A fair amount of the legacy application uses controller hierarchy
         * names such as SpecializedOrderController.java or
         * ExternalOrderController.java. They are controllers
         * even though the concrete variant is appended after "Controller".
         */
        if (name.endsWith("Controller.java")
                || name.indexOf("Controller") >= 0) {
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

        /*
         * Only ambiguous Java files reach the semantic fallback. One cached JDT
         * inspection can then identify annotation-driven architectural roles.
         */
        if (FlowJavaSemantics.isJaxb(file)) {
            return JAXB;
        }

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

        return OTHER;
    }
}
