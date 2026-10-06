[out:json][timeout:240];
area(3601908779)->.krabi;
(
  nwr["amenity"~"^(fuel|hospital|restaurant|fast_food|food_court|cafe|marketplace|police|bus_station|bank|atm|charging_station|ferry_terminal|place_of_worship|townhall|courthouse)$"](area.krabi);
  nwr["healthcare"="hospital"](area.krabi);
  nwr["tourism"~"^(hotel|hostel|guest_house|motel|resort|chalet|attraction|aquarium|artwork|gallery|museum|theme_park|viewpoint|zoo)$"](area.krabi);
  nwr["shop"~"^(mall|department_store|convenience|car_repair|tyres)$"](area.krabi);
  nwr["craft"="car_repair"](area.krabi);
  nwr["aeroway"~"^(aerodrome|terminal)$"](area.krabi);
  nwr["man_made"="pier"](area.krabi);
  nwr["route"="ferry"](area.krabi);
  nwr["office"="government"](area.krabi);
);
out center tags;
