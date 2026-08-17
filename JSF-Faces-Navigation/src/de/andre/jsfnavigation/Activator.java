package de.andre.jsfnavigation;

import java.io.File;

import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

public final class Activator extends AbstractUIPlugin {

    public static final String PLUGIN_ID = "de.andre.jsfnavigation";

    private static Activator instance;
    private BeanIndexService beanIndexService;

    @Override
    public void start(BundleContext context) throws Exception {
        super.start(context);
        instance = this;

        File indexFile = getStateLocation()
                .append("bean-index-v1.bin")
                .toFile();

        beanIndexService = new BeanIndexService(indexFile);
        beanIndexService.start();
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        try {
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
}
