-- Demo data so the app and the Power BI report have something to show on a fresh
-- install. The owner ids match the fixed user ids in keycloak/greenhour-realm.json.
--
-- Market data is synthetic: a daily price curve with morning and evening peaks,
-- scaled by a per-day "wind" factor. The real importer overwrites recent days
-- with actual Energi Data Service figures on startup.

insert into household (owner_id, name, price_area) values
    ('3f0c2a8e-5d1b-4c7e-9a43-2b6f8e1d7c01', 'Anna''s apartment, Copenhagen', 'DK2'),
    ('8b4e6d2a-1c3f-4a5b-9e7d-6f2a1b3c4d02', 'Jonas'' house, Aarhus', 'DK1');

insert into appliance (household_id, name, kind, power_kw, run_minutes)
select h.id, a.name, a.kind, a.power_kw, a.run_minutes
from household h
join (values
    ('3f0c2a8e-5d1b-4c7e-9a43-2b6f8e1d7c01', 'Dishwasher', 'DISHWASHER', 1.20, 120),
    ('3f0c2a8e-5d1b-4c7e-9a43-2b6f8e1d7c01', 'Washing machine', 'WASHING_MACHINE', 1.00, 105),
    ('8b4e6d2a-1c3f-4a5b-9e7d-6f2a1b3c4d02', 'Tesla Model 3', 'EV_CHARGER', 11.00, 180),
    ('8b4e6d2a-1c3f-4a5b-9e7d-6f2a1b3c4d02', 'Dishwasher', 'DISHWASHER', 1.40, 135),
    ('8b4e6d2a-1c3f-4a5b-9e7d-6f2a1b3c4d02', 'Tumble dryer', 'DRYER', 2.50, 90)
) as a (owner_id, name, kind, power_kw, run_minutes) on a.owner_id = h.owner_id;

with slots as (
    select area,
           ts,
           extract(hour from ts at time zone 'Europe/Copenhagen')
               + extract(minute from ts at time zone 'Europe/Copenhagen') / 60.0 as local_hour,
           -- 0 = calm day, 1 = very windy day
           (hashtext((ts at time zone 'Europe/Copenhagen')::date::text)::bigint & 1023) / 1023.0 as wind,
           (hashtext(area || ts::text)::bigint & 1023) / 1023.0 - 0.5 as noise
    from generate_series(
             date_trunc('day', now() at time zone 'Europe/Copenhagen') at time zone 'Europe/Copenhagen' - interval '60 days',
             date_trunc('day', now() at time zone 'Europe/Copenhagen') at time zone 'Europe/Copenhagen' + interval '2 days' - interval '15 minutes',
             interval '15 minutes') as ts
    cross join (values ('DK1'), ('DK2')) as areas (area)
),
shaped as (
    select *,
           0.35 * exp(-power(local_hour - 8, 2) / 3)
         + 0.65 * exp(-power(local_hour - 18.5, 2) / 4)
         - 0.20 * exp(-power(local_hour - 13, 2) / 6) as daily_shape
    from slots
)
insert into market_interval (price_area, interval_start, price_dkk_per_kwh, co2_g_per_kwh, source)
select area,
       ts,
       round(greatest(-0.05,
           (0.60 + daily_shape) * (1.30 - 0.85 * wind)
           + case when area = 'DK1' then -0.04 else 0 end
           + 0.06 * noise)::numeric, 5),
       round(greatest(15,
           45 + 230 * (1 - wind) * (0.65 + 0.35 * daily_shape)
           + case when area = 'DK2' then 25 else 0 end
           + 12 * noise)::numeric, 2),
       'DEMO'
from shaped;

-- A history of runs over the last ~two months. Each one is planned the same way
-- the app does it: the user is fine with anything between 17:00 and 07:00 the next
-- morning, and the best window is compared to just starting at 17:00.
with candidate_runs as (
    select a.id as appliance_id,
           h.price_area,
           a.power_kw,
           a.run_minutes,
           case when (hashtext(a.id::text || d::text)::bigint & 3) = 0 then 'CO2' else 'PRICE' end as goal,
           (d::date + time '17:00') at time zone 'Europe/Copenhagen' as earliest_start,
           (d::date + 1 + time '07:00') at time zone 'Europe/Copenhagen' as deadline,
           (hashtext(d::text || a.name)::bigint & 15) as roll
    from appliance a
    join household h on h.id = a.household_id
    cross join generate_series(current_date - 59, current_date - 2, interval '1 day') as d
),
runs as (
    -- EV gets charged most nights, the dryer only now and then.
    select * from candidate_runs
    where roll < case
        when run_minutes >= 180 then 11
        when run_minutes <= 90 then 5
        else 8 end
)
insert into scheduled_run (appliance_id, goal, earliest_start, deadline, planned_start, planned_end,
                           energy_kwh, cost_dkk, co2_g, baseline_start, baseline_cost_dkk, baseline_co2_g, status)
select r.appliance_id,
       r.goal,
       r.earliest_start,
       r.deadline,
       best.start,
       best.start + r.run_minutes * interval '1 minute',
       round(r.power_kw * r.run_minutes / 60.0, 3),
       round(best.cost, 3),
       round(best.co2, 1),
       r.earliest_start,
       round(base.cost, 3),
       round(base.co2, 1),
       case when (hashtext(r.appliance_id::text || r.earliest_start::text)::bigint & 15) = 0 then 'CANCELLED' else 'DONE' end
from runs r
cross join lateral (
    select c.interval_start as start, w.cost, w.co2
    from market_interval c
    cross join lateral (
        select sum(m.price_dkk_per_kwh) * r.power_kw * 0.25 as cost,
               sum(m.co2_g_per_kwh) * r.power_kw * 0.25 as co2
        from market_interval m
        where m.price_area = r.price_area
          and m.interval_start >= c.interval_start
          and m.interval_start < c.interval_start + r.run_minutes * interval '1 minute'
    ) w
    where c.price_area = r.price_area
      and c.interval_start >= r.earliest_start
      and c.interval_start + r.run_minutes * interval '1 minute' <= r.deadline
    order by case when r.goal = 'CO2' then w.co2 else w.cost end, c.interval_start
    limit 1
) best
cross join lateral (
    select sum(m.price_dkk_per_kwh) * r.power_kw * 0.25 as cost,
           sum(m.co2_g_per_kwh) * r.power_kw * 0.25 as co2
    from market_interval m
    where m.price_area = r.price_area
      and m.interval_start >= r.earliest_start
      and m.interval_start < r.earliest_start + r.run_minutes * interval '1 minute'
) base;
