package de.andre.jsfnavigation;

import java.io.File;

import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

public final class Activator extends AbstractUIPlugin {

    public static final String PLUGIN_ID = "de.andre.jsfnavigation";

    private static Activator instance;
    private BeanIndexService beanIndexService;
    private WebIndexService webIndexService;
    private JsfViewIndexService jsfViewIndexService;
    private JsfDiagnosticsService jsfDiagnosticsService;
    private WebSphereHotSyncService webSphereHotSyncService;
    private FlowExplorerService flowExplorerService;
    private FlowExplorerWorkbenchListener flowWorkbenchListener;
    private FlowTestImpactService flowTestImpactService;
    private FlowTestResultStore flowTestResultStore;
    private FlowDependencyIndexService flowDependencyIndexService;
    private XsdIndexService xsdIndexService;
    private SmartDeployMappingStore smartDeployMappingStore;
    private SmartDeployService smartDeployService;
    private WtpShortcutBridge wtpShortcutBridge;

    @Override
    public void start(BundleContext context) throws Exception {
        super.start(context);
        instance = this;

        WebSphereHotSyncSettings.initializeDefaults(
                getPreferenceStore());

        WebSphereLogSettings.initializeDefaults(
                getPreferenceStore());

        SmartDeploySettings.initializeDefaults(
                getPreferenceStore());

        File beanIndexFile = getStateLocation()
                .append("bean-index-v1.bin")
                .toFile();

        File webIndexFile = getStateLocation()
                .append("web-index-v1.bin")
                .toFile();

        File jsfViewIndexFile = getStateLocation()
                .append("jsf-view-index-v2.bin")
                .toFile();

        File flowStateFile = getStateLocation()
                .append("flow-explorer-v1.bin")
                .toFile();

        File smartDeployMappingFile = getStateLocation()
                .append("smart-deploy-mappings-v1.bin")
                .toFile();

        File flowTestResultsFile = getStateLocation()
                .append("flow-test-results-v1.bin")
                .toFile();

        File xsdIndexFile = getStateLocation()
                .append("xsd-index-v1.bin")
                .toFile();

        beanIndexService = new BeanIndexService(beanIndexFile);
        beanIndexService.start();

        webIndexService = new WebIndexService(webIndexFile);
        webIndexService.start();

        jsfViewIndexService = new JsfViewIndexService(jsfViewIndexFile);
        jsfViewIndexService.start();

        jsfDiagnosticsService = new JsfDiagnosticsService();
        jsfDiagnosticsService.start();

        wtpShortcutBridge = new WtpShortcutBridge();
        wtpShortcutBridge.start();

        webSphereHotSyncService = new WebSphereHotSyncService();
        webSphereHotSyncService.start();

        smartDeployMappingStore =
                new SmartDeployMappingStore(
                        smartDeployMappingFile);
        smartDeployMappingStore.start();

        smartDeployService =
                new SmartDeployService(
                        smartDeployMappingStore);
        smartDeployService.start();

        flowExplorerService = new FlowExplorerService(flowStateFile);
        flowExplorerService.start();

        flowTestResultStore =
                new FlowTestResultStore(
                        flowTestResultsFile);
        flowTestResultStore.start();

        flowDependencyIndexService =
                new FlowDependencyIndexService();
        flowDependencyIndexService.start();

        xsdIndexService =
                new XsdIndexService(
                        xsdIndexFile);
        xsdIndexService.start();

        flowTestImpactService =
                new FlowTestImpactService();
        flowTestImpactService.start();

        if (PlatformUI.isWorkbenchRunning()) {
            flowWorkbenchListener = new FlowExplorerWorkbenchListener();
            flowWorkbenchListener.start();
        }
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        try {
            if (flowWorkbenchListener != null) {
                flowWorkbenchListener.stop();
                flowWorkbenchListener = null;
            }

            if (flowTestImpactService != null) {
                flowTestImpactService.stop();
                flowTestImpactService = null;
            }

            if (flowTestResultStore != null) {
                flowTestResultStore.stop();
                flowTestResultStore = null;
            }

            if (flowDependencyIndexService != null) {
                flowDependencyIndexService.stop();
                flowDependencyIndexService = null;
            }

            if (xsdIndexService != null) {
                xsdIndexService.stop();
                xsdIndexService = null;
            }

            if (flowExplorerService != null) {
                flowExplorerService.stop();
                flowExplorerService = null;
            }

            if (smartDeployService != null) {
                smartDeployService.stop();
                smartDeployService = null;
            }

            if (smartDeployMappingStore != null) {
                smartDeployMappingStore.stop();
                smartDeployMappingStore = null;
            }

            if (webSphereHotSyncService != null) {
                webSphereHotSyncService.stop();
                webSphereHotSyncService = null;
            }

            if (wtpShortcutBridge != null) {
                wtpShortcutBridge.stop();
                wtpShortcutBridge = null;
            }

            if (jsfDiagnosticsService != null) {
                jsfDiagnosticsService.stop();
                jsfDiagnosticsService = null;
            }

            if (jsfViewIndexService != null) {
                jsfViewIndexService.stop();
                jsfViewIndexService = null;
            }

            if (webIndexService != null) {
                webIndexService.stop();
                webIndexService = null;
            }

            if (beanIndexService != null) {
                beanIndexService.stop();
                beanIndexService = null;
            }

            JavaPropertyResolver.clearCache();
            JavaReturnTypeResolver.clearCache();
            FlowJavaSemantics.clear();
            FlowTestLaunchSupport.clear();
            JaxbTypeResolver.clearCache();
        } finally {
            instance = null;
            super.stop(context);
        }
    }

    public static Activator getDefault() {
        return instance;
    }

    public static BeanIndexService getBeanIndexService() {
        Activator plugin = instance;
        return plugin == null ? null : plugin.beanIndexService;
    }

    public static WebIndexService getWebIndexService() {
        Activator plugin = instance;
        return plugin == null ? null : plugin.webIndexService;
    }

    public static JsfViewIndexService getJsfViewIndexService() {
        Activator plugin = instance;
        return plugin == null ? null : plugin.jsfViewIndexService;
    }

    public static WebSphereHotSyncService getWebSphereHotSyncService() {
        Activator plugin = instance;
        return plugin == null ? null : plugin.webSphereHotSyncService;
    }

    public static FlowExplorerService getFlowExplorerService() {
        Activator plugin = instance;
        return plugin == null ? null : plugin.flowExplorerService;
    }


    public static XsdIndexService getXsdIndexService() {
        Activator plugin = instance;
        return plugin == null ? null : plugin.xsdIndexService;
    }

    public static FlowDependencyIndexService getFlowDependencyIndexService() {
        Activator plugin = instance;
        return plugin == null ? null : plugin.flowDependencyIndexService;
    }

    public static FlowTestResultStore getFlowTestResultStore() {
        Activator plugin = instance;
        return plugin == null ? null : plugin.flowTestResultStore;
    }

    public static SmartDeployMappingStore getSmartDeployMappingStore() {
        Activator plugin = instance;
        return plugin == null ? null : plugin.smartDeployMappingStore;
    }
}
