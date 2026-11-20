-- Day-ahead prices and CO2 intensity in 15-minute slots. The Danish day-ahead
-- market moved from hourly to quarter-hourly products in October 2025.
create table market_interval (
    price_area        varchar(3)    not null,
    interval_start    timestamptz   not null,
    price_dkk_per_kwh numeric(10, 5) not null,
    co2_g_per_kwh     numeric(7, 2),
    co2_is_forecast   boolean       not null default false,
    -- DEMO rows come from V2 and are replaced as soon as real data is imported.
    source            varchar(10)   not null default 'ENERGINET',
    updated_at        timestamptz   not null default now(),
    primary key (price_area, interval_start),
    constraint market_interval_area_chk check (price_area in ('DK1', 'DK2')),
    constraint market_interval_source_chk check (source in ('ENERGINET', 'DEMO'))
);

-- One household per Keycloak user. owner_id is the token's "sub" claim.
create table household (
    id          bigint generated always as identity primary key,
    owner_id    varchar(64) not null unique,
    name        varchar(80) not null,
    price_area  varchar(3)  not null default 'DK2',
    created_at  timestamptz not null default now(),
    constraint household_area_chk check (price_area in ('DK1', 'DK2'))
);

create table appliance (
    id            bigint generated always as identity primary key,
    household_id  bigint       not null references household (id) on delete cascade,
    name          varchar(60)  not null,
    kind          varchar(20)  not null,
    power_kw      numeric(5, 2) not null,
    run_minutes   integer      not null,
    created_at    timestamptz  not null default now(),
    constraint appliance_name_uq unique (household_id, name),
    constraint appliance_kind_chk check (kind in ('DISHWASHER', 'WASHING_MACHINE', 'DRYER', 'EV_CHARGER', 'HEAT_PUMP', 'OTHER')),
    constraint appliance_power_chk check (power_kw > 0 and power_kw <= 22),
    -- Runs are planned in whole market slots, so durations are multiples of 15 minutes.
    constraint appliance_minutes_chk check (run_minutes between 15 and 720 and run_minutes % 15 = 0)
);

-- energy_kwh is copied from the appliance when the run is planned, so editing an
-- appliance later doesn't rewrite history.
create table scheduled_run (
    id                 bigint generated always as identity primary key,
    appliance_id       bigint        not null references appliance (id) on delete cascade,
    goal               varchar(10)   not null,
    earliest_start     timestamptz   not null,
    deadline           timestamptz   not null,
    planned_start      timestamptz   not null,
    planned_end        timestamptz   not null,
    energy_kwh         numeric(7, 3) not null,
    cost_dkk           numeric(9, 3) not null,
    co2_g              numeric(10, 1),
    baseline_start     timestamptz   not null,
    baseline_cost_dkk  numeric(9, 3) not null,
    baseline_co2_g     numeric(10, 1),
    status             varchar(10)   not null default 'PLANNED',
    created_at         timestamptz   not null default now(),
    constraint scheduled_run_goal_chk check (goal in ('PRICE', 'CO2', 'BALANCED')),
    constraint scheduled_run_status_chk check (status in ('PLANNED', 'DONE', 'CANCELLED')),
    constraint scheduled_run_window_chk check (deadline > earliest_start and planned_end <= deadline)
);

create index scheduled_run_appliance_idx on scheduled_run (appliance_id, planned_start);
