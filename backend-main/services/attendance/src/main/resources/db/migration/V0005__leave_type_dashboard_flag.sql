alter table leave_types
    add column show_on_dashboard boolean not null default true;

update leave_types
   set show_on_dashboard = false
 where code in ('UNPAID', 'MATERNITY', 'PATERNITY');
