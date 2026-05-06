package org.killbill.billing.plugin.rounding;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.invoice.plugin.api.AdditionalItemsResult;
import org.killbill.billing.invoice.plugin.api.InvoiceContext;
import org.killbill.billing.osgi.libs.killbill.OSGIConfigPropertiesService;
import org.killbill.billing.osgi.libs.killbill.OSGIKillbillAPI;
import org.killbill.billing.osgi.libs.killbill.OSGIKillbillClock;
import org.killbill.clock.Clock;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class RoundingInvoicePluginTest {

    @Mock private OSGIKillbillAPI killbillAPI;
    @Mock private OSGIConfigPropertiesService configProperties;
    @Mock private OSGIKillbillClock clock;
    @Mock private Invoice invoice;
    @Mock private InvoiceContext context;

    private RoundingInvoicePlugin plugin;

    private static final UUID      INVOICE_ID = UUID.randomUUID();
    private static final UUID      ACCOUNT_ID = UUID.randomUUID();
    private static final LocalDate DATE       = new LocalDate(2024, 1, 15);

    // All 5 supported currencies exposed to the plugin under test
    private static final String ALL_INCREMENTS = "SEK=1,EUR=0.05,NOK=1,ISK=1,DKK=0.5";

    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(clock.getClock()).thenReturn(mock(Clock.class));
        plugin = new RoundingInvoicePlugin(killbillAPI, configProperties, clock);

        when(configProperties.getString(anyString())).thenReturn(null);
        when(configProperties.getString("org.killbill.billing.plugin.rounding.increments"))
                .thenReturn(ALL_INCREMENTS);

        when(invoice.getId()).thenReturn(INVOICE_ID);
        when(invoice.getAccountId()).thenReturn(ACCOUNT_ID);
        when(invoice.getCurrency()).thenReturn(Currency.SEK);
        when(invoice.getInvoiceDate()).thenReturn(DATE);
    }

    // -------------------------------------------------------------------------
    // parseIncrements
    // -------------------------------------------------------------------------

    @Test
    public void parseIncrements_validEntries() {
        Map<Currency, BigDecimal> result = RoundingInvoicePlugin.parseIncrements("SEK=1,EUR=0.05");
        assertEquals(result.size(), 2);
        assertEquals(result.get(Currency.SEK).compareTo(new BigDecimal("1")), 0);
        assertEquals(result.get(Currency.EUR).compareTo(new BigDecimal("0.05")), 0);
    }

    @Test
    public void parseIncrements_emptyString_returnsEmptyMap() {
        assertTrue(RoundingInvoicePlugin.parseIncrements("").isEmpty());
    }

    @Test
    public void parseIncrements_null_returnsEmptyMap() {
        assertTrue(RoundingInvoicePlugin.parseIncrements(null).isEmpty());
    }

    @Test
    public void parseIncrements_malformedEntry_skipped() {
        assertTrue(RoundingInvoicePlugin.parseIncrements("NOEQUALSSIGN").isEmpty());
    }

    @Test
    public void parseIncrements_invalidCurrency_skipped() {
        assertTrue(RoundingInvoicePlugin.parseIncrements("NOTACURRENCY=1").isEmpty());
    }

    @Test
    public void parseIncrements_zeroIncrement_skipped() {
        assertTrue(RoundingInvoicePlugin.parseIncrements("SEK=0").isEmpty());
    }

    @Test
    public void parseIncrements_negativeIncrement_skipped() {
        assertTrue(RoundingInvoicePlugin.parseIncrements("SEK=-1").isEmpty());
    }

    @Test
    public void parseIncrements_mixedValidAndInvalid_onlyValidKept() {
        Map<Currency, BigDecimal> result = RoundingInvoicePlugin.parseIncrements("SEK=1,BADCUR=1,NOK=1");
        assertEquals(result.size(), 2);
        assertTrue(result.containsKey(Currency.SEK));
        assertTrue(result.containsKey(Currency.NOK));
    }

    // -------------------------------------------------------------------------
    // roundToIncrement — parameterized across all currencies
    // -------------------------------------------------------------------------

    @DataProvider(name = "roundToIncrementCases")
    public Object[][] roundToIncrementCases() {
        return new Object[][] {
            // SEK — increment 1.00
            { "42.40", "1",    "42"    },  // round down
            { "42.60", "1",    "43"    },  // round up
            { "42.00", "1",    "42"    },  // already at boundary
            { "42.50", "1",    "43"    },  // half → HALF_UP → up
            // EUR — increment 0.05
            { "10.02", "0.05", "10.00" },  // round down
            { "10.03", "0.05", "10.05" },  // round up
            { "10.05", "0.05", "10.05" },  // already at boundary
            { "10.025","0.05", "10.05" },  // half → HALF_UP → up
            // NOK — increment 1.00
            { "99.40", "1",    "99"    },  // round down
            { "99.60", "1",    "100"   },  // round up
            // ISK — increment 1.00
            { "500.40","1",    "500"   },  // round down
            { "500.60","1",    "501"   },  // round up
            // DKK — increment 0.50
            { "42.24", "0.5",  "42.0"  },  // round down
            { "42.26", "0.5",  "42.5"  },  // round up
            { "42.50", "0.5",  "42.5"  },  // already at boundary
            { "42.25", "0.5",  "42.5"  },  // half → HALF_UP → up
        };
    }

    @Test(dataProvider = "roundToIncrementCases")
    public void roundToIncrement_parameterized(final String amount, final String increment, final String expected) {
        BigDecimal result = RoundingInvoicePlugin.roundToIncrement(
                new BigDecimal(amount), new BigDecimal(increment));
        assertEquals(result.compareTo(new BigDecimal(expected)), 0,
                String.format("roundToIncrement(%s, %s): expected %s but got %s", amount, increment, expected, result));
    }

    // -------------------------------------------------------------------------
    // getAdditionalInvoiceItems — edge cases
    // -------------------------------------------------------------------------

    @Test
    public void getAdditionalInvoiceItems_dryRun_returnsEmpty() {
        AdditionalItemsResult result = plugin.getAdditionalInvoiceItems(
                invoice, true, Collections.<org.killbill.billing.payment.api.PluginProperty>emptyList(), context);
        assertTrue(result.getAdditionalItems().isEmpty());
    }

    @Test
    public void getAdditionalInvoiceItems_disabled_returnsEmpty() {
        when(configProperties.getString("org.killbill.billing.plugin.rounding.enabled")).thenReturn("false");
        when(invoice.getInvoiceItems()).thenReturn(Collections.<InvoiceItem>emptyList());

        AdditionalItemsResult result = plugin.getAdditionalInvoiceItems(
                invoice, false, Collections.<org.killbill.billing.payment.api.PluginProperty>emptyList(), context);
        assertTrue(result.getAdditionalItems().isEmpty());
    }

    @Test
    public void getAdditionalInvoiceItems_unconfiguredCurrency_returnsEmpty() {
        when(invoice.getCurrency()).thenReturn(Currency.USD);
        when(invoice.getInvoiceItems()).thenReturn(Collections.<InvoiceItem>emptyList());

        AdditionalItemsResult result = plugin.getAdditionalInvoiceItems(
                invoice, false, Collections.<org.killbill.billing.payment.api.PluginProperty>emptyList(), context);
        assertTrue(result.getAdditionalItems().isEmpty());
    }

    @Test
    public void getAdditionalInvoiceItems_totalAlreadyAtBoundary_returnsEmpty() {
        // 42.00 SEK with increment 1 → diff = 0
        InvoiceItem item = fakeItem(InvoiceItemType.RECURRING, new BigDecimal("42.00"), null);
        when(invoice.getInvoiceItems()).thenReturn(Collections.singletonList(item));

        AdditionalItemsResult result = plugin.getAdditionalInvoiceItems(
                invoice, false, Collections.<org.killbill.billing.payment.api.PluginProperty>emptyList(), context);
        assertTrue(result.getAdditionalItems().isEmpty());
    }

    @Test
    public void getAdditionalInvoiceItems_zeroTotal_returnsEmpty() {
        when(invoice.getInvoiceItems()).thenReturn(Collections.<InvoiceItem>emptyList());

        AdditionalItemsResult result = plugin.getAdditionalInvoiceItems(
                invoice, false, Collections.<org.killbill.billing.payment.api.PluginProperty>emptyList(), context);
        assertTrue(result.getAdditionalItems().isEmpty());
    }

    @Test
    public void getAdditionalInvoiceItems_existingRoundingItemExcludedFromTotal() {
        // 42.60 recurring + prior 0.40 rounding → total still 42.60 → diff +0.40
        InvoiceItem recurring     = fakeItem(InvoiceItemType.RECURRING,      new BigDecimal("42.60"), null);
        InvoiceItem priorRounding = fakeItem(InvoiceItemType.EXTERNAL_CHARGE, new BigDecimal("0.40"), "Rounding adjustment");
        when(invoice.getInvoiceItems()).thenReturn(Arrays.asList(recurring, priorRounding));

        AdditionalItemsResult result = plugin.getAdditionalInvoiceItems(
                invoice, false, Collections.<org.killbill.billing.payment.api.PluginProperty>emptyList(), context);

        List<InvoiceItem> added = result.getAdditionalItems();
        assertEquals(added.size(), 1);
        assertEquals(added.get(0).getAmount().compareTo(new BigDecimal("0.40")), 0);
    }

    @Test
    public void getAdditionalInvoiceItems_nullAmountItemSkipped() {
        InvoiceItem nullAmount = fakeItem(InvoiceItemType.RECURRING, null, null);
        InvoiceItem normal     = fakeItem(InvoiceItemType.RECURRING, new BigDecimal("10.60"), null);
        when(invoice.getInvoiceItems()).thenReturn(Arrays.asList(nullAmount, normal));

        // 10.60 SEK → rounded 11 → diff +0.40
        AdditionalItemsResult result = plugin.getAdditionalInvoiceItems(
                invoice, false, Collections.<org.killbill.billing.payment.api.PluginProperty>emptyList(), context);

        List<InvoiceItem> added = result.getAdditionalItems();
        assertEquals(added.size(), 1);
        assertEquals(added.get(0).getAmount().compareTo(new BigDecimal("0.40")), 0);
    }

    // -------------------------------------------------------------------------
    // getAdditionalInvoiceItems — parameterized across all currencies
    // -------------------------------------------------------------------------

    @DataProvider(name = "roundingScenarios")
    public Object[][] roundingScenarios() {
        return new Object[][] {
            // currency,      total,                    expectedDiff
            // SEK — increment 1.00
            { Currency.SEK, new BigDecimal("42.60"), new BigDecimal("0.40")  },  // 42.60 → 43
            { Currency.SEK, new BigDecimal("42.40"), new BigDecimal("-0.40") },  // 42.40 → 42
            // EUR — increment 0.05
            { Currency.EUR, new BigDecimal("10.03"), new BigDecimal("0.02")  },  // 10.03 → 10.05
            { Currency.EUR, new BigDecimal("10.02"), new BigDecimal("-0.02") },  // 10.02 → 10.00
            // NOK — increment 1.00
            { Currency.NOK, new BigDecimal("99.60"), new BigDecimal("0.40")  },  // 99.60 → 100
            { Currency.NOK, new BigDecimal("99.40"), new BigDecimal("-0.40") },  // 99.40 → 99
            // ISK — increment 1.00
            { Currency.ISK, new BigDecimal("500.60"), new BigDecimal("0.40")  },  // 500.60 → 501
            { Currency.ISK, new BigDecimal("500.40"), new BigDecimal("-0.40") },  // 500.40 → 500
            // DKK — increment 0.50
            { Currency.DKK, new BigDecimal("42.26"), new BigDecimal("0.24")  },  // 42.26 → 42.50
            { Currency.DKK, new BigDecimal("42.24"), new BigDecimal("-0.24") },  // 42.24 → 42.00
        };
    }

    @Test(dataProvider = "roundingScenarios")
    public void getAdditionalInvoiceItems_allCurrencies(
            final Currency currency, final BigDecimal total, final BigDecimal expectedDiff) {
        when(invoice.getCurrency()).thenReturn(currency);
        InvoiceItem item = fakeItem(InvoiceItemType.RECURRING, total, null);
        when(invoice.getInvoiceItems()).thenReturn(Collections.singletonList(item));

        AdditionalItemsResult result = plugin.getAdditionalInvoiceItems(
                invoice, false, Collections.<org.killbill.billing.payment.api.PluginProperty>emptyList(), context);

        List<InvoiceItem> added = result.getAdditionalItems();
        assertEquals(added.size(), 1,
                String.format("%s total=%s: expected 1 rounding item", currency, total));
        assertEquals(added.get(0).getAmount().compareTo(expectedDiff), 0,
                String.format("%s total=%s: expected diff=%s but got %s", currency, total, expectedDiff, added.get(0).getAmount()));
        assertEquals(added.get(0).getCurrency(), currency);
    }

    private static InvoiceItem fakeItem(final InvoiceItemType type, final BigDecimal amount, final String description) {
        InvoiceItem item = mock(InvoiceItem.class);
        when(item.getInvoiceItemType()).thenReturn(type);
        when(item.getAmount()).thenReturn(amount);
        when(item.getDescription()).thenReturn(description);
        return item;
    }
}
