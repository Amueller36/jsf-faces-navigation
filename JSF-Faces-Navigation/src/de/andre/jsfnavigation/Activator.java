package de.andre.jsfnavigation;

import java.io.File;

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

    @Override
    public void start(BundleContext context) throws Exception {
        super.start(context);
        instance = this;

        WebSphereHotSyncSettings.initializeDefaults(
                getPreferenceStore());

        File beanIndexFile = getStateLocation()
                .append("bean-index-v1.bin")
                .toFile();

        File webIndexFile = getStateLocation()
                .append("web-index-v1.bin")
                .toFile();

        File jsfViewIndexFile = getStateLocation()
                .append("jsf-view-index-v1.bin")
                .toFile();

        beanIndexService = new BeanIndexService(beanIndexFile);
        beanIndexService.start();

        webIndexService = new WebIndexService(webIndexFile);
        webIndexService.start();

        jsfViewIndexService = new JsfViewIndexService(jsfViewIndexFile);
        jsfViewIndexService.start();

        jsfDiagnosticsService = new JsfDiagnosticsService();
        jsfDiagnosticsService.start();

        webSphereHotSyncService = new WebSphereHotSyncService();
        webSphereHotSyncService.start();
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        try {
            if (webSphereHotSyncService != null) {
                webSphereHotSyncService.stop();
                webSphereHotSyncService = null;
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
}
