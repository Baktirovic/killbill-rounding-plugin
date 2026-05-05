# Kill Bill Rounding Plugin

Automatically adds a cash rounding adjustment row to invoices before they are committed,
rounding the invoice total to the nearest configured increment using standard half-up rounding.

Only currencies explicitly listed in the `increments` configuration are affected — all others are ignored.

**Examples (DKK, increment 0.50):**
- Invoice total 42.24 → rounds to 42.00 → adds -0.24 rounding row
- Invoice total 42.26 → rounds to 42.50 → adds +0.24 rounding row
- Invoice total 42.50 → already on boundary → no rounding row added

## Supported markets (defaults)

| Country | Currency | Increment | Reason |
|---------|----------|-----------|--------|
| Sweden  | SEK      | 1.00      | 50-öre coin eliminated 2010 |
| Finland | EUR      | 0.05      | 1- and 2-cent coins not in circulation |
| Norway  | NOK      | 1.00      | 10-øre coin eliminated 2012 |
| Iceland | ISK      | 1.00      | No decimal denominations |
| Denmark | DKK      | 0.50      | 25-øre coin eliminated 2008 |

> **Note:** Finland uses EUR, so all EUR accounts will be rounded. If you have non-Finnish EUR customers, adjust the `increments` config or remove EUR entirely.

## Build

    mvn clean package -DskipTests

## Deploy JAR

    curl -X POST -u admin:password \
      -H "X-Killbill-ApiKey: YOUR_KEY" \
      -H "X-Killbill-ApiSecret: YOUR_SECRET" \
      -H "X-Killbill-CreatedBy: admin" \
      -H "Content-Type: application/octet-stream" \
      "http://localhost:8080/1.0/kb/plugins/killbill-rounding-plugin" \
      --data-binary @target/killbill-rounding-plugin-1.0.0-SNAPSHOT.jar

## Push Config (per tenant)

    curl -X POST -u admin:password \
      -H "X-Killbill-ApiKey: YOUR_KEY" \
      -H "X-Killbill-ApiSecret: YOUR_SECRET" \
      -H "X-Killbill-CreatedBy: admin" \
      -H "Content-Type: text/plain" \
      "http://localhost:8080/1.0/kb/tenants/uploadPluginConfig/killbill-rounding-plugin" \
      --data-binary @src/main/resources/rounding-plugin.properties

## Configuration Properties

| Property | Default | Description |
|----------|---------|-------------|
| org.killbill.billing.plugin.rounding.enabled | true | Enable/disable the plugin |
| org.killbill.billing.plugin.rounding.description | Rounding adjustment | Label shown on the invoice row |
| org.killbill.billing.plugin.rounding.increments | SEK=1,EUR=0.05,NOK=1,ISK=1,DKK=0.5 | Comma-separated `CURRENCY=increment` pairs |

To add or remove a currency, update the `increments` property and re-push the config. No redeployment needed.
