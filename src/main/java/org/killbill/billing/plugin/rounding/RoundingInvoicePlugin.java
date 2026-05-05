package org.killbill.billing.plugin.rounding;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.invoice.plugin.api.AdditionalItemsResult;
import org.killbill.billing.invoice.plugin.api.InvoiceContext;
import org.killbill.billing.osgi.libs.killbill.OSGIConfigPropertiesService;
import org.killbill.billing.osgi.libs.killbill.OSGIKillbillAPI;
import org.killbill.billing.osgi.libs.killbill.OSGIKillbillClock;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.plugin.api.invoice.PluginAdditionalItemsResult;
import org.killbill.billing.plugin.api.invoice.PluginInvoicePluginApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RoundingInvoicePlugin extends PluginInvoicePluginApi {

    private static final Logger logger = LoggerFactory.getLogger(RoundingInvoicePlugin.class);

    private static final String ROUNDING_DESCRIPTION_KEY = "org.killbill.billing.plugin.rounding.description";
    private static final String ROUNDING_ENABLED_KEY     = "org.killbill.billing.plugin.rounding.enabled";

    private static final String DEFAULT_DESCRIPTION = "Rounding adjustment";
    private static final String DEFAULT_ENABLED     = "true";

    private final OSGIConfigPropertiesService configProperties;

    public RoundingInvoicePlugin(final OSGIKillbillAPI killbillAPI,
                                 final OSGIConfigPropertiesService configProperties,
                                 final OSGIKillbillClock clock) {
        super(killbillAPI, configProperties, clock.getClock());
        this.configProperties = configProperties;
    }

    @Override
    public AdditionalItemsResult getAdditionalInvoiceItems(final Invoice invoice,
                                                           final boolean isDryRun,
                                                           final Iterable<PluginProperty> pluginProperties,
                                                           final InvoiceContext context) {
        final List<InvoiceItem> additionalItems = new ArrayList<>();

        if (isDryRun) {
            return new PluginAdditionalItemsResult(additionalItems, null);
        }

        final String enabled = getConfigValue(ROUNDING_ENABLED_KEY, DEFAULT_ENABLED);
        if (!"true".equalsIgnoreCase(enabled)) {
            logger.debug("Rounding plugin is disabled, skipping invoice {}", invoice.getId());
            return new PluginAdditionalItemsResult(additionalItems, null);
        }

        final BigDecimal currentTotal = calculateInvoiceTotal(invoice);

        if (currentTotal == null || currentTotal.compareTo(BigDecimal.ZERO) <= 0) {
            return new PluginAdditionalItemsResult(additionalItems, null);
        }

        final BigDecimal ceilingTotal = currentTotal.setScale(0, RoundingMode.CEILING);
        final BigDecimal roundingDiff = ceilingTotal.subtract(currentTotal);

        if (roundingDiff.compareTo(BigDecimal.ZERO) <= 0) {
            logger.debug("Invoice {} total {} is already a whole number, no rounding needed",
                    invoice.getId(), currentTotal);
            return new PluginAdditionalItemsResult(additionalItems, null);
        }

        logger.info("Invoice {} total={} ceiling={} adding rounding adjustment={}",
                invoice.getId(), currentTotal, ceilingTotal, roundingDiff);

        final String description = getConfigValue(ROUNDING_DESCRIPTION_KEY, DEFAULT_DESCRIPTION);

        final InvoiceItem roundingItem = new RoundingInvoiceItem(
                UUID.randomUUID(),
                invoice.getId(),
                invoice.getAccountId(),
                invoice.getInvoiceDate(),
                roundingDiff,
                invoice.getCurrency(),
                description
        );

        additionalItems.add(roundingItem);
        return new PluginAdditionalItemsResult(additionalItems, null);
    }

    private BigDecimal calculateInvoiceTotal(final Invoice invoice) {
        BigDecimal total = BigDecimal.ZERO;
        final String roundingDescription = getConfigValue(ROUNDING_DESCRIPTION_KEY, DEFAULT_DESCRIPTION);

        for (final InvoiceItem item : invoice.getInvoiceItems()) {
            if (InvoiceItemType.EXTERNAL_CHARGE.equals(item.getInvoiceItemType())
                    && roundingDescription.equals(item.getDescription())) {
                continue;
            }
            if (item.getAmount() == null) {
                continue;
            }
            total = total.add(item.getAmount());
        }

        return total;
    }

    private String getConfigValue(final String key, final String defaultValue) {
        final String value = configProperties.getString(key);
        return (value != null && !value.isEmpty()) ? value : defaultValue;
    }
}
