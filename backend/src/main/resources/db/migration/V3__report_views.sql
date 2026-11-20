-- Views for the Power BI report. Power BI connects with the read-only
-- powerbi_reader role and only sees these views, never the base tables.
-- All dates are Danish local dates, so DST days have 23 or 25 hours.

create view report_daily_market as
select (interval_start at time zone 'Europe/Copenhagen')::date as market_date,
       price_area,
       round(avg(price_dkk_per_kwh), 4)  as avg_price_dkk_per_kwh,
       min(price_dkk_per_kwh)            as min_price_dkk_per_kwh,
       max(price_dkk_per_kwh)            as max_price_dkk_per_kwh,
       round(max(price_dkk_per_kwh) - min(price_dkk_per_kwh), 4) as price_spread_dkk_per_kwh,
       round(avg(co2_g_per_kwh), 1)      as avg_co2_g_per_kwh,
       count(*)                          as slot_count,
       bool_or(source = 'DEMO')          as contains_demo_data
from market_interval
group by 1, 2;

create view report_hourly_profile as
select price_area,
       extract(isodow from interval_start at time zone 'Europe/Copenhagen')::int as iso_weekday,
       to_char(interval_start at time zone 'Europe/Copenhagen', 'Dy')            as weekday_name,
       extract(hour from interval_start at time zone 'Europe/Copenhagen')::int   as hour_of_day,
       round(avg(price_dkk_per_kwh), 4) as avg_price_dkk_per_kwh,
       round(avg(co2_g_per_kwh), 1)     as avg_co2_g_per_kwh
from market_interval
group by 1, 2, 3, 4;

create view report_run_savings as
select r.id                                                   as run_id,
       (r.planned_start at time zone 'Europe/Copenhagen')::date as run_date,
       h.id                                                   as household_id,
       h.name                                                 as household_name,
       h.price_area,
       a.kind                                                 as appliance_kind,
       a.name                                                 as appliance_name,
       r.goal,
       r.status,
       r.energy_kwh,
       r.cost_dkk,
       r.baseline_cost_dkk,
       r.baseline_cost_dkk - r.cost_dkk                       as saved_dkk,
       r.co2_g,
       r.baseline_co2_g,
       r.baseline_co2_g - r.co2_g                             as saved_co2_g,
       round(extract(epoch from r.planned_start - r.baseline_start) / 3600.0, 2) as shifted_hours
from scheduled_run r
join appliance a on a.id = r.appliance_id
join household h on h.id = a.household_id;

do $$
begin
    if not exists (select 1 from pg_roles where rolname = 'powerbi_reader') then
        create role powerbi_reader login password '${powerbiPassword}';
    end if;
end
$$;

grant usage on schema public to powerbi_reader;
grant select on report_daily_market, report_hourly_profile, report_run_savings to powerbi_reader;
