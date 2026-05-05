package org.killbill.billing.plugin.rounding;

import java.util.Hashtable;

import org.killbill.billing.invoice.plugin.api.InvoicePluginApi;
import org.killbill.billing.osgi.api.OSGIPluginProperties;
import org.killbill.billing.osgi.libs.killbill.KillbillActivatorBase;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RoundingActivator extends KillbillActivatorBase {

    private static final Logger logger = LoggerFactory.getLogger(RoundingActivator.class);

    public static final String PLUGIN_NAME = "killbill-rounding-plugin";

    @Override
    public void start(final BundleContext context) throws Exception {
        super.start(context);

        logger.info("Starting Kill Bill Rounding Plugin");

        final RoundingInvoicePlugin invoicePlugin = new RoundingInvoicePlugin(killbillAPI, configProperties, clock);

        final Hashtable<String, String> props = new Hashtable<>();
        props.put(OSGIPluginProperties.PLUGIN_NAME_PROP, PLUGIN_NAME);

        registrar.registerService(context, InvoicePluginApi.class, invoicePlugin, props);

        logger.info("Kill Bill Rounding Plugin started successfully");
    }

    @Override
    public void stop(final BundleContext context) throws Exception {
        super.stop(context);
        logger.info("Kill Bill Rounding Plugin stopped");
    }
}
