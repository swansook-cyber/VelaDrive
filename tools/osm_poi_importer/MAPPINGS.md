# OSM to Vela category mappings

Mappings are deliberately conservative and deterministic. Unlisted or ambiguous tags are skipped rather than forced into `OTHER`.

| Vela category | Accepted OSM tags |
| --- | --- |
| `FUEL` | `amenity=fuel` |
| `HOSPITAL` | `amenity=hospital`, `healthcare=hospital` |
| `RESTAURANT` | `amenity=restaurant`, `fast_food`, `food_court`, or `cafe` without explicit halal metadata |
| `HALAL_RESTAURANT` | A mapped food amenity plus `diet:halal=yes/only`, `halal=yes/only`, or a `halal` token in `cuisine` |
| `HOTEL` | `tourism=hotel`, `hostel`, `guest_house`, `motel`, `resort`, or `chalet` |
| `MARKET` | `amenity=marketplace` |
| `SHOPPING` | `shop=mall` or `department_store` |
| `POLICE` | `amenity=police` |
| `AIRPORT` | `aeroway=aerodrome` or `terminal` |
| `BUS_TERMINAL` | `amenity=bus_station` |
| `TOURISM` | `tourism=attraction`, `aquarium`, `artwork`, `gallery`, `museum`, `theme_park`, `viewpoint`, or `zoo` |
| `CONVENIENCE_STORE` | `shop=convenience` |
| `AUTO_SERVICE` | `shop=car_repair` or `craft=car_repair` |
| `TIRE_SERVICE` | `shop=tyres` |
| `BANK` | `amenity=bank` |
| `ATM` | `amenity=atm` |
| `EV_CHARGER` | `amenity=charging_station` |
| `PIER` | `man_made=pier` |
| `FERRY` | `amenity=ferry_terminal` |
| `MOSQUE` | `amenity=place_of_worship` and `religion=muslim` |
| `GOVERNMENT` | `office=government`, `amenity=townhall`, or `amenity=courthouse` |

Halal status is never inferred from a name, language, nearby mosque, or assumed community identity.

Bare `route=ferry` ways and relations are deliberately not emitted as destination POIs. Their representative points may be offshore or mid-route. They remain in the retained OSM snapshot for future routing analysis. Physical `amenity=ferry_terminal` features map to `FERRY`, while `man_made=pier` maps to `PIER`.

Automatic fallback deduplication requires a normalized-name match within 10 metres. The 25 m and 50 m distances are audit-only review bands and never trigger automatic merging.

