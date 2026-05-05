package org.killbill.billing.plugin.rounding;

import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;

public class RoundingInvoiceItem implements InvoiceItem {

    private final UUID id;
    private final UUID invoiceId;
    private final UUID accountId;
    private final LocalDate startDate;
    private final BigDecimal amount;
    private final Currency currency;
    private final String description;

    public RoundingInvoiceItem(final UUID id,
                                final UUID invoiceId,
                                final UUID accountId,
                                final LocalDate startDate,
                                final BigDecimal amount,
                                final Currency currency,
                                final String description) {
        this.id = id;
        this.invoiceId = invoiceId;
        this.accountId = accountId;
        this.startDate = startDate;
        this.amount = amount;
        this.currency = currency;
        this.description = description;
    }

    @Override public InvoiceItemType getInvoiceItemType() { return InvoiceItemType.EXTERNAL_CHARGE; }
    @Override public UUID getId() { return id; }
    @Override public UUID getInvoiceId() { return invoiceId; }
    @Override public UUID getAccountId() { return accountId; }
    @Override public UUID getBundleId() { return null; }
    @Override public UUID getSubscriptionId() { return null; }
    @Override public String getProductName() { return null; }
    @Override public String getPrettyProductName() { return null; }
    @Override public String getPlanName() { return null; }
    @Override public String getPrettyPlanName() { return null; }
    @Override public String getPhaseName() { return null; }
    @Override public String getPrettyPhaseName() { return null; }
    @Override public String getUsageName() { return null; }
    @Override public String getPrettyUsageName() { return null; }
    @Override public BigDecimal getRate() { return null; }
    @Override public BigDecimal getAmount() { return amount; }
    @Override public LocalDate getStartDate() { return startDate; }
    @Override public LocalDate getEndDate() { return startDate; }
    @Override public Currency getCurrency() { return currency; }
    @Override public String getDescription() { return description; }
    @Override public UUID getLinkedItemId() { return null; }
    @Override public BigDecimal getQuantity() { return null; }
    @Override public String getItemDetails() { return null; }
    @Override public UUID getChildAccountId() { return null; }
    @Override public DateTime getCatalogEffectiveDate() { return null; }
    @Override public DateTime getCreatedDate() { return DateTime.now(); }
    @Override public DateTime getUpdatedDate() { return DateTime.now(); }

    @Override
    public boolean matches(final Object other) {
        if (!(other instanceof InvoiceItem)) { return false; }
        final InvoiceItem o = (InvoiceItem) other;
        return InvoiceItemType.EXTERNAL_CHARGE.equals(o.getInvoiceItemType())
                && description.equals(o.getDescription())
                && invoiceId.equals(o.getInvoiceId());
    }
}
