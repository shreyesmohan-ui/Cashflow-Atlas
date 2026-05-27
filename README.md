# Cashflow Atlas Android

Native Android version of Cashflow Atlas. It reads bank SMS alerts locally, extracts debit/credit transactions, categorizes spending with customizable keyword rules, and shows budget insights on-device.

## What It Includes

- Runtime `READ_SMS` permission request
- Recent inbox scan using Android's SMS provider
- Manual SMS paste fallback for devices or builds without SMS permission
- Local SMS parser for debit/credit, amount, merchant, date, category, and confidence
- Editable finance profile: income, emergency fund, savings goal, monthly save target
- Fully customizable categories: label, budget, color, keyword rules
- Manual transaction ledger
- Imported SMS transaction ledger
- Human-readable budget and runway insights
- Custom native Canvas money-map with Flow and Risk views
- JSON persistence using `SharedPreferences`
- Plain Java Android views with no Compose or AndroidX dependency

## Open In Android Studio

1. Open Android Studio.
2. Choose **Open**.
3. Select this folder:

```text
android-cashflow-atlas/
```

4. Let Gradle sync.
5. Run the `app` configuration on an Android device or emulator.

## Build From Terminal

```text
gradlew.bat :app:assembleDebug
```

The debug APK is created at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## SMS Permission Note

This app requests `READ_SMS` so it can scan bank SMS alerts on-device. For personal use, side-loaded builds, college demos, and portfolio demos, that is fine after the user grants runtime permission.

Publishing an app with SMS permissions on Google Play is restricted. If you want Play Store distribution, either remove direct inbox scanning and keep the manual SMS paste/import flow, or ensure the app qualifies under Google's SMS permission policy.

## Important Files

```text
app/src/main/java/com/shrey/cashflowatlas/MainActivity.java
app/src/main/java/com/shrey/cashflowatlas/AtlasView.java
app/src/main/java/com/shrey/cashflowatlas/sms/SmsParser.java
app/src/main/java/com/shrey/cashflowatlas/sms/SmsReader.java
app/src/main/java/com/shrey/cashflowatlas/analytics/FinanceAnalyzer.java
app/src/main/java/com/shrey/cashflowatlas/data/CashflowStore.java
app/src/main/java/com/shrey/cashflowatlas/model/
```

## Customization Ideas

- Add encrypted storage for sensitive transactions
- Add monthly filters and CSV export
- Add notification-based transaction capture
- Add biometric lock
- Add charts for recurring bills and subscription detection
