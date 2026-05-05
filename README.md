# Kill Bill Rounding Plugin

Automatically adds a **ceiling rounding adjustment** row to invoices before they are committed,
bringing the invoice total up to the nearest whole number.

**Example:** Invoice total of 42.30 -> adds 0.70 rounding row -> final total 43.00

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
      -d "org.killbill.billing.plugin.rounding.enabled=true"

## Configuration Properties

| Property                                              | Default              | Description                        |
|-------------------------------------------------------|----------------------|------------------------------------|
| org.killbill.billing.plugin.rounding.enabled          | true                 | Enable/disable the plugin          |
| org.killbill.billing.plugin.rounding.description      | Rounding adjustment  | Label shown on the invoice row     |
