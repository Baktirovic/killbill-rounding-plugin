package org.killbill.billing.plugin.rounding;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.killbill.billing.catalog.api.Currency;
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

    private static final String ROUNDING_ENABLED_KEY     = "org.killbill.billing.plugin.rounding.enabled";
    private static final String ROUNDING_DESCRIPTION_KEY = "org.killbill.billing.plugin.rounding.description";
    // Comma-separated list of CURRENCY=increment pairs, e.g. "SEK=1,EUR=0.05,NOK=1,ISK=1"
    private static final String ROUNDING_INCREMENTS_KEY  = "org.killbill.billing.plugin.rounding.increments";

    private static final String DEFAULT_ENABLED     = "true";
    private static final String DEFAULT_DESCRIPTION = "Rounding adjustment";
    private static final String DEFAULT_INCREMENTS  = "SEK=1,EUR=0.05,NOK=1,ISK=1";

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

        final Currency currency = invoice.getCurrency();
        final Map<Currency, BigDecimal> increments = parseIncrements(getConfigValue(ROUNDING_INCREMENTS_KEY, DEFAULT_INCREMENTS));
        final BigDecimal increment = increments.get(currency);
        if (increment == null) {
            logger.debug("Currency {} not configured for rounding, skipping invoice {}", currency, invoice.getId());
            return new PluginAdditionalItemsResult(additionalItems, null);
        }

        final BigDecimal currentTotal = calculateInvoiceTotal(invoice);
        if (currentTotal == null || currentTotal.compareTo(BigDecimal.ZERO) <= 0) {
            return new PluginAdditionalItemsResult(additionalItems, null);
        }

        final BigDecimal roundedTotal = roundToIncrement(currentTotal, increment);
        final BigDecimal roundingDiff = roundedTotal.subtract(currentTotal);

        if (roundingDiff.compareTo(BigDecimal.ZERO) == 0) {
            logger.debug("Invoice {} total {} is already at a rounding boundary, no rounding needed",
                    invoice.getId(), currentTotal);
            return new PluginAdditionalItemsResult(additionalItems, null);
        }

        logger.info("Invoice {} currency={} total={} rounded={} adding rounding adjustment={}",
                invoice.getId(), currency, currentTotal, roundedTotal, roundingDiff);

        final String description = getConfigValue(ROUNDING_DESCRIPTION_KEY, DEFAULT_DESCRIPTION);

        additionalItems.add(new RoundingInvoiceItem(
                UUID.randomUUID(),
                invoice.getId(),
                invoice.getAccountId(),
                invoice.getInvoiceDate(),
                roundingDiff,
                currency,
                description
        ));
        return new PluginAdditionalItemsResult(additionalItems, null);
    }

    // Parses "SEK=1,EUR=0.05,NOK=1,ISK=1" into a Currency -> increment map.
    // Invalid or unrecognized entries are skipped with a warning.
    static Map<Currency, BigDecimal> parseIncrements(final String raw) {
        final Map<Currency, BigDecimal> result = new HashMap<>();
        if (raw == null || raw.trim().isEmpty()) {
            return result;
        }
        for (final String entry : raw.split(",")) {
            final String[] parts = entry.trim().split("=", 2);
            if (parts.length != 2) {
                logger.warn("Ignoring malformed rounding increment entry: '{}'", entry.trim());
                continue;
            }
            try {
                final Currency currency = Currency.valueOf(parts[0].trim().toUpperCase());
                final BigDecimal increment = new BigDecimal(parts[1].trim());
                if (increment.compareTo(BigDecimal.ZERO) <= 0) {
                    logger.warn("Ignoring non-positive rounding increment for {}: {}", currency, increment);
                    continue;
                }
                result.put(currency, increment);
            } catch (final IllegalArgumentException e) {
                logger.warn("Ignoring unrecognized currency or invalid increment in entry: '{}'", entry.trim());
            }
        }
        return result;
    }

    static BigDecimal roundToIncrement(final BigDecimal amount, final BigDecimal increment) {
        return amount.divide(increment, 0, RoundingMode.HALF_UP).multiply(increment);
    }

    private BigDecimal calculateInvoiceTotal(final Invoice invoice) {
        final String roundingDescription = getConfigValue(ROUNDING_DESCRIPTION_KEY, DEFAULT_DESCRIPTION);
        BigDecimal total = BigDecimal.ZERO;
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
