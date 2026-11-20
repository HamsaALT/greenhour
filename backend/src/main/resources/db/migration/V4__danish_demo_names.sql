-- The demo users are Danish households, so their own names for things should be
-- Danish too. These are user-entered values, not UI text, so they don't follow
-- the language switch. Only rows still carrying the original demo names change.

update household set name = 'Annas lejlighed, København'
where owner_id = '3f0c2a8e-5d1b-4c7e-9a43-2b6f8e1d7c01' and name = 'Anna''s apartment, Copenhagen';

update household set name = 'Jonas'' hus, Aarhus'
where owner_id = '8b4e6d2a-1c3f-4a5b-9e7d-6f2a1b3c4d02' and name = 'Jonas'' house, Aarhus';

update appliance a set name = v.new_name
from household h,
     (values ('Dishwasher', 'Opvaskemaskine'),
             ('Washing machine', 'Vaskemaskine'),
             ('Tumble dryer', 'Tørretumbler'),
             ('Tesla Model 3', 'Elbil (Tesla Model 3)')) as v (old_name, new_name)
where a.household_id = h.id
  and h.owner_id in ('3f0c2a8e-5d1b-4c7e-9a43-2b6f8e1d7c01', '8b4e6d2a-1c3f-4a5b-9e7d-6f2a1b3c4d02')
  and a.name = v.old_name;
