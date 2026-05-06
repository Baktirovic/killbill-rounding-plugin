package org.killbill.billing.plugin.rounding;

import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.mockito.Mockito;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class RoundingInvoiceItemTest {

    private static final UUID       ID          = UUID.randomUUID();
    private static final UUID       INVOICE_ID  = UUID.randomUUID();
    private static final UUID       ACCOUNT_ID  = UUID.randomUUID();
    private static final LocalDate  DATE        = new LocalDate(2024, 6, 1);
    private static final BigDecimal AMOUNT      = new BigDecimal("0.40");
    private static final Currency   CURRENCY    = Currency.SEK;
    private static final String     DESCRIPTION = "Rounding adjustment";

    private RoundingInvoiceItem create() {
        return new RoundingInvoiceItem(ID, INVOICE_ID, ACCOUNT_ID, DATE, AMOUNT, CURRENCY, DESCRIPTION);
    }

    @Test public void getId()             { assertEquals(create().getId(), ID); }
    @Test public void getInvoiceId()      { assertEquals(create().getInvoiceId(), INVOICE_ID); }
    @Test public void getAccountId()      { assertEquals(create().getAccountId(), ACCOUNT_ID); }
    @Test public void getAmount()         { assertEquals(create().getAmount(), AMOUNT); }
    @Test public void getCurrency()       { assertEquals(create().getCurrency(), CURRENCY); }
    @Test public void getDescription()    { assertEquals(create().getDescription(), DESCRIPTION); }
    @Test public void getInvoiceItemType(){ assertEquals(create().getInvoiceItemType(), InvoiceItemType.EXTERNAL_CHARGE); }

    @Test
    public void startAndEndDateAreTheSame() {
        RoundingInvoiceItem item = create();
        assertEquals(item.getStartDate(), DATE);
        assertEquals(item.getEndDate(), DATE);
    }

    @Test
    public void unusedFieldsReturnNull() {
        RoundingInvoiceItem item = create();
        assertNull(item.getBundleId());
        assertNull(item.getSubscriptionId());
        assertNull(item.getProductName());
        assertNull(item.getPlanName());
        assertNull(item.getPhaseName());
        assertNull(item.getUsageName());
        assertNull(item.getRate());
        assertNull(item.getQuantity());
        assertNull(item.getLinkedItemId());
        assertNull(item.getItemDetails());
        assertNull(item.getChildAccountId());
        assertNull(item.getCatalogEffectiveDate());
    }

    @Test
    public void matches_sameInvoiceAndDescription_returnsTrue() {
        RoundingInvoiceItem item = create();
        RoundingInvoiceItem other = new RoundingInvoiceItem(
                UUID.randomUUID(), INVOICE_ID, ACCOUNT_ID, DATE, new BigDecimal("-0.40"), CURRENCY, DESCRIPTION);
        assertTrue(item.matches(other));
    }

    @Test
    public void matches_differentInvoiceId_returnsFalse() {
        RoundingInvoiceItem item = create();
        RoundingInvoiceItem other = new RoundingInvoiceItem(
                UUID.randomUUID(), UUID.randomUUID(), ACCOUNT_ID, DATE, AMOUNT, CURRENCY, DESCRIPTION);
        assertFalse(item.matches(other));
    }

    @Test
    public void matches_differentDescription_returnsFalse() {
        RoundingInvoiceItem item = create();
        RoundingInvoiceItem other = new RoundingInvoiceItem(
                UUID.randomUUID(), INVOICE_ID, ACCOUNT_ID, DATE, AMOUNT, CURRENCY, "Other charge");
        assertFalse(item.matches(other));
    }

    @Test
    public void matches_nonExternalChargeType_returnsFalse() {
        RoundingInvoiceItem item = create();
        InvoiceItem other = Mockito.mock(InvoiceItem.class);
        Mockito.when(other.getInvoiceItemType()).thenReturn(InvoiceItemType.RECURRING);
        Mockito.when(other.getDescription()).thenReturn(DESCRIPTION);
        Mockito.when(other.getInvoiceId()).thenReturn(INVOICE_ID);
        assertFalse(item.matches(other));
    }

    @Test
    public void matches_null_returnsFalse() {
        assertFalse(create().matches(null));
    }

    @Test
    public void matches_nonInvoiceItemObject_returnsFalse() {
        assertFalse(create().matches("not an invoice item"));
    }
}
