package de.andre.jsfnavigation;

import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.hyperlink.IHyperlink;

public final class JavaExecuteScriptHyperlink implements IHyperlink {

    private final IRegion region;
    private final String functionName;
    private final String projectName;
    private final String controllerBeanName;

    public JavaExecuteScriptHyperlink(
            IRegion region,
            String functionName,
            String projectName,
            String controllerBeanName) {

        this.region = region;
        this.functionName = functionName;
        this.projectName = projectName;
        this.controllerBeanName = controllerBeanName;
    }

    @Override
    public IRegion getHyperlinkRegion() {
        return region;
    }

    @Override
    public String getTypeLabel() {
        return "JavaScript definition";
    }

    @Override
    public String getHyperlinkText() {
        return "Open JavaScript definition of " + functionName;
    }

    @Override
    public void open() {
        JavaScriptNavigationService.navigateFromJava(
                functionName,
                projectName,
                controllerBeanName);
    }
}
