-- Repurposes factory_tours from visitor-tour tracking into employee travel
-- requests. Table/route/class names stay (FactoryTour*, /factory-tours) —
-- only the domain fields change. Approval chains (FACTORY_TOUR_LOCAL /
-- FACTORY_TOUR_INTL, seeded in V0014) are untouched: travel_type keeps the
-- same two values (LOCAL, INTERNATIONAL) the chains already key off.

alter table factory_tours
    add column request_id              varchar(24),
    add column request_date            date,
    add column tour_start              timestamp,
    add column tour_end                timestamp,
    add column origin                  varchar(128),
    add column destination             varchar(128),
    add column city                    varchar(128),
    add column country                 varchar(64),
    add column currency                varchar(8),
    add column vehicle_source          varchar(24),
    add column transportation_type     varchar(64),
    add column vehicle_no              varchar(32),
    add column total_distance_km       numeric(10, 2),
    add column additional_travel_persons jsonb,
    add column card_no                 varchar(32),
    add column name                    varchar(128),
    add column section_id              bigint,
    add column designation_id          bigint,
    add column grade_id                bigint;

update factory_tours
   set tour_start = tour_date::timestamp,
       tour_end   = tour_date::timestamp
 where tour_date is not null;

-- COMPLETED is dropped from TourStatus — fold any existing rows into APPROVED.
update factory_tours set status = 'APPROVED' where status = 'COMPLETED';

alter table factory_tours rename column type to travel_type;

alter table factory_tours
    drop column visitor_organization,
    drop column visitor_count,
    drop column contact_person,
    drop column contact_email,
    drop column notes,
    drop column tour_date;

alter table factory_tours alter column purpose drop not null;

create unique index uq_factory_tours_request_id on factory_tours (request_id) where request_id is not null;
create index idx_factory_tours_tour_start on factory_tours (tour_start);
create index idx_factory_tours_card_no on factory_tours (card_no);

create table factory_tour_sequence (
    year        int primary key,
    last_value  int not null default 0
);
