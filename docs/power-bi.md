# Power BI report

The database exposes three views for reporting. Power BI connects with a
read-only role, `powerbi_reader`, which can select from these views and
nothing else. The views are created in
[`V3__report_views.sql`](../backend/src/main/resources/db/migration/V3__report_views.sql).

| View | One row per | Used for |
|---|---|---|
| `report_daily_market` | Danish calendar day and price area | Price and CO2 trends, daily spread |
| `report_hourly_profile` | Price area, weekday and hour | "When is power cheap?" heat map |
| `report_run_savings` | Scheduled run | Savings per household, appliance and goal |

All dates and hours are Danish local time, so DST days correctly have 23 or 25 hours.

## 1. Connect

1. Start the stack with `docker compose up -d` and wait for the backend to finish starting.
2. In Power BI Desktop: **Get data → PostgreSQL database**.
3. Server: `localhost:5433`, Database: `greenhour`, Data Connectivity mode: **Import**.
4. When asked for credentials, choose **Database** and enter
   user `powerbi_reader`, password `powerbi`
   (or whatever you set `POWERBI_PASSWORD` to in `.env`).
5. If you're asked about encryption, choose to connect without it. The local container doesn't use TLS.
6. Select the three `report_*` views and click **Load**.

> Port 5433 is used on purpose so the container doesn't clash with a locally
> installed PostgreSQL on 5432.

## 2. Model

No relationships are needed between the views for the pages below. Two small tweaks help:

- In `report_hourly_profile`, set **Sort by column** on `weekday_name` to `iso_weekday`,
  otherwise the weekdays come out alphabetically.
- In `report_run_savings`, set `run_date` and in `report_daily_market` set `market_date`
  to the **Date** data type.

## 3. Measures

Create these in `report_run_savings` (Modeling → New measure):

```dax
Completed Runs =
CALCULATE ( COUNTROWS ( report_run_savings ), report_run_savings[status] = "DONE" )

Money Saved (DKK) =
CALCULATE ( SUM ( report_run_savings[saved_dkk] ), report_run_savings[status] = "DONE" )

CO2 Avoided (kg) =
DIVIDE (
    CALCULATE ( SUM ( report_run_savings[saved_co2_g] ), report_run_savings[status] = "DONE" ),
    1000
)

Saving % =
DIVIDE (
    [Money Saved (DKK)],
    CALCULATE ( SUM ( report_run_savings[baseline_cost_dkk] ), report_run_savings[status] = "DONE" )
)

Avg Hours Shifted =
CALCULATE ( AVERAGE ( report_run_savings[shifted_hours] ), report_run_savings[status] = "DONE" )

Cancellation Rate =
DIVIDE (
    CALCULATE ( COUNTROWS ( report_run_savings ), report_run_savings[status] = "CANCELLED" ),
    COUNTROWS ( report_run_savings )
)
```

And these in `report_daily_market`:

```dax
Avg Price (DKK/kWh) = AVERAGE ( report_daily_market[avg_price_dkk_per_kwh] )

Avg Daily Spread = AVERAGE ( report_daily_market[price_spread_dkk_per_kwh] )

Avg CO2 (g/kWh) = AVERAGE ( report_daily_market[avg_co2_g_per_kwh] )
```

## 4. Report pages

**Page 1 – Savings overview**
- Four cards: Completed Runs, Money Saved (DKK), CO2 Avoided (kg), Saving %.
- Clustered bar chart: Money Saved (DKK) by `appliance_kind`. The EV charger
  should dominate, since it uses by far the most energy per run.
- Line chart: Money Saved (DKK) by `run_date` (week granularity).
- Slicers: `household_name`, `goal`.

**Page 2 – When is power cheap?**
- Matrix: rows `weekday_name`, columns `hour_of_day`, values average of
  `avg_price_dkk_per_kwh`. Turn on **conditional formatting → background color**
  (green low, red high). This heat map is the core idea of the app in one picture.
- Slicer: `price_area`.
- A second matrix with `avg_co2_g_per_kwh` shows that cheap and clean hours often,
  but not always, line up.

**Page 3 – Market trend**
- Line chart: Avg Price (DKK/kWh) by `market_date`, legend `price_area`.
- Column chart: Avg Daily Spread by `market_date`. A large spread means more to
  gain from moving consumption.
- Use `contains_demo_data` as a page filter (= False) to show only real Energinet data.

## 5. Refresh

The report uses Import mode, so click **Refresh** after new runs or new market
data. Market data is refreshed by the backend every hour.

## About the demo data

On a fresh database, Flyway migration V2 creates about two months of generated
market data and a history of runs for the two demo households, so the report has
something to show straight away. The backend then replaces the most recent days
with real data from Energi Data Service. Generated days are flagged with
`contains_demo_data = true`.
