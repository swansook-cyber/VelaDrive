# Krabi OSM POI quality audit

Dataset: `osm-krabi-2026-10-06-v2-5289fdd79a6b`

## Summary

- POIs: 2702
- Missing address: 2232
- Missing phone: 2412
- Missing `name:th`: 2383
- Missing `name:en`: 1774
- Element types: {'node': 2410, 'relation': 15, 'way': 277}
- Verified status: {'unverified': 2702}
- Sample validation: 80/80 passed

## Coordinate clusters

| Distance | Pairs | Clusters | Records in clusters | Largest cluster |
| ---: | ---: | ---: | ---: | ---: |
| 10m | 180 | 129 | 298 | 5 |
| 25m | 1022 | 305 | 1003 | 38 |
| 50m | 3441 | 333 | 1702 | 185 |

## Deduplication estimates

| Distance | Estimated merged records | Near-duplicate pairs | Affected clusters |
| ---: | ---: | ---: | ---: |
| 10m | 18 | 18 | 18 |
| 25m | 30 | 33 | 26 |
| 50m | 50 | 57 | 41 |

## Unnamed records

- Blank-name quarantined: 701
- Structured display-name candidates: 66
- No structured candidate: 635
- Candidate fields: {'brand': 25, 'operator': 56}

## Audited samples

### RESTAURANT

| Name | OSM reference | Tags | Validation |
| --- | --- | --- | --- |
| Vogue Food Court | `node/10120898316` | amenity=food_court | PASS |
| Good time cafe | `node/11765315870` | amenity=cafe | PASS |
| Maharaja | `node/13259451325` | amenity=restaurant, cuisine=indian | PASS |
| Shanti old town | `node/2486043510` | amenity=restaurant | PASS |
| Muslim Restaurant | `node/4170903434` | amenity=restaurant, cuisine=malaysian | PASS |
| Not a Toy | `node/4772082222` | amenity=cafe, cuisine=breakfast;coffee_shop;crepe;ice_cream | PASS |
| Breakfast | `node/5484048422` | amenity=cafe | PASS |
| T Lanta | `node/6353631945` | amenity=restaurant, cuisine=local | PASS |
| Food Center | `node/7199818485` | amenity=restaurant, cuisine=seafood;barbecue;thai | PASS |
| ไร่ครูครื้น แคมป์ปิ้ง เขาต่อ กระบี่ | `way/926972243` | amenity=cafe, cuisine=coffee_shop | PASS |

### HOTEL

| Name | OSM reference | Tags | Validation |
| --- | --- | --- | --- |
| Koh Hai Fantasy Resort | `node/1001395211` | tourism=hotel | PASS |
| SAii Phi Phi Island Village Resort | `node/12526618859` | tourism=hotel | PASS |
| Thai Village Hotel | `node/2085694846` | tourism=hotel | PASS |
| Sai-Ngam Botanic Garden Resort | `node/3419574708` | tourism=guest_house | PASS |
| Krabi Forest Homestay | `node/4143815190` | tourism=hotel | PASS |
| Baan Reenaa Lanta | `node/4688974265` | tourism=hotel | PASS |
| The Chill @ Krabi Hotel | `node/5839948191` | tourism=hotel | PASS |
| Andaman Lanta Resort | `node/702448722` | tourism=hotel | PASS |
| Adam Bungalows | `relation/19799646` | tourism=guest_house | PASS |
| Seashell Resort Krabi | `way/873037321` | tourism=hotel | PASS |

### TOURISM

| Name | OSM reference | Tags | Validation |
| --- | --- | --- | --- |
| ประติมากรรมน้ำใจ | `node/10129485777` | tourism=artwork | PASS |
| Gap between Karsts | `node/11461360740` | tourism=viewpoint | PASS |
| Statue of Koh Lanta | `node/12629943801` | tourism=attraction | PASS |
| View Point 2 | `node/2653537261` | tourism=viewpoint | PASS |
| Khao Thong | `node/4615501789` | tourism=viewpoint | PASS |
| entrance | `node/5301974323` | tourism=viewpoint | PASS |
| Hin Daeng | `node/5585813422` | tourism=attraction | PASS |
| ถ้ำหินย้อย | `node/6384967285` | tourism=attraction | PASS |
| Cave | `node/7546186285` | tourism=viewpoint | PASS |
| Krabi Hot Spring | `way/99307403` | tourism=attraction | PASS |

### FERRY

| Name | OSM reference | Tags | Validation |
| --- | --- | --- | --- |
| ท่าเรือเจ้าฟ้า | `node/10136622867` | amenity=ferry_terminal | PASS |
| ท่าเรือหินขวาง | `node/10181649000` | amenity=ferry_terminal | PASS |
| อ่าวน้ำเมา | `node/1286410591` | amenity=ferry_terminal | PASS |
| หาดต้นไทร | `node/2457772223` | amenity=ferry_terminal | PASS |
| ท่าเรือมารีน่า | `node/2502554722` | amenity=ferry_terminal | PASS |
| อ่าวไร่เลย์ (ตะวันออก) | `node/265688020` | amenity=ferry_terminal | PASS |
| ท่าเรืออ่าวต้นไทร | `node/3233091096` | amenity=ferry_terminal | PASS |
| อ่าวน้ำเมา | `node/6126121289` | amenity=ferry_terminal | PASS |
| ท่าเรือเกาะจัม | `node/642936790` | amenity=ferry_terminal | PASS |
| Din Deng Noi FERRY | `way/686512115` | amenity=ferry_terminal | PASS |

### HALAL_RESTAURANT

| Name | OSM reference | Tags | Validation |
| --- | --- | --- | --- |
| Phra Ae Seafood | `node/11703996669` | amenity=restaurant, cuisine=seafood;thai, diet:halal=yes | PASS |
| Summa Thai Seafood | `node/12695418653` | amenity=restaurant, cuisine=thai;seafood, diet:halal=yes | PASS |
| ฮาลาลฟู้ด | `node/3432501611` | amenity=restaurant, diet:halal=only | PASS |
| Halal restaurant | `node/4361390797` | amenity=restaurant, cuisine=malaysian;thai, diet:halal=yes | PASS |
| Plant-erion Vegie Restaurant | `node/5638649721` | amenity=restaurant, cuisine=indian;seafood;thai;pizza, diet:halal=yes | PASS |
| Khawfhang Restaurant | `node/6021568690` | amenity=restaurant, cuisine=thai;seafood, diet:halal=yes | PASS |
| Aree BaBa | `node/6021568937` | amenity=restaurant, cuisine=thai;seafood, diet:halal=yes | PASS |
| Pizza & Classic kohjum | `node/7153710898` | amenity=restaurant, cuisine=thai;italian, diet:halal=yes | PASS |
| Yaya Cups Cafe | `node/8940767259` | amenity=restaurant, cuisine=coffee_shop;thai, diet:halal=yes | PASS |
| มากันบุฟเฟต์ | `node/9606215756` | amenity=restaurant, diet:halal=yes | PASS |

### FUEL

| Name | OSM reference | Tags | Validation |
| --- | --- | --- | --- |
| เอสโซ่ | `node/10132281004` | amenity=fuel | PASS |
| Independent | `node/11327612228` | amenity=fuel | PASS |
| Motorbike | `node/11360074069` | amenity=fuel | PASS |
| เชลล์ | `node/11433231072` | amenity=fuel | PASS |
| ป.ต.ท. | `node/13667102440` | amenity=fuel | PASS |
| บางจาก | `node/3374332533` | amenity=fuel | PASS |
| พีที | `node/4463799656` | amenity=fuel | PASS |
| เชลล์ | `node/634835632` | amenity=fuel | PASS |
| บางจาก | `way/1029534322` | amenity=fuel | PASS |
| PT Petroleum | `way/491361236` | amenity=fuel | PASS |

### HOSPITAL

| Name | OSM reference | Tags | Validation |
| --- | --- | --- | --- |
| โรงพยาบาลกระบี่ | `node/10127760493` | amenity=hospital, healthcare=hospital | PASS |
| โรงพยาบาลเกาะพีพี | `node/1150191198` | amenity=hospital, healthcare=hospital | PASS |
| โรงพยาบาลคลองท่อม | `node/2784775764` | amenity=hospital | PASS |
| โรงพยาบาลกระบี่นครินทร์ อินเตอร์เนชั่นแนล | `node/5068687521` | amenity=hospital, healthcare=hospital | PASS |
| Koh Lanta Hospital | `node/6317749586` | amenity=hospital | PASS |
| โรงพยาบาลเกาะลันตา | `node/7856632160` | amenity=hospital, healthcare=hospital | PASS |
| โรงพยาบาลเขาพนม | `node/7856667409` | amenity=hospital | PASS |
| โรงพยาบาลปลายพระยา | `way/1028352368` | amenity=hospital | PASS |
| Wattanapat hospital ao nang | `way/1227068087` | amenity=hospital, healthcare=hospital | PASS |
| Kohjum Tambon Health Promotion Hospital | `way/646514742` | amenity=hospital, healthcare=hospital | PASS |

### MOSQUE

| Name | OSM reference | Tags | Validation |
| --- | --- | --- | --- |
| มัสยิดนูหรุนยันน๊ะ | `node/11377505508` | amenity=place_of_worship, religion=muslim | PASS |
| Al-Islah Koh Phi Phi Mosque | `node/2330200012` | amenity=place_of_worship, religion=muslim | PASS |
| มัสยิดบ้านช่องไม้ดำ | `node/4306950092` | amenity=place_of_worship, religion=muslim | PASS |
| Ba-Lai | `node/4826556321` | amenity=place_of_worship, religion=muslim | PASS |
| มัสยิดบ้านนาตีน | `node/6186080470` | amenity=place_of_worship, religion=muslim | PASS |
| Masjid darussalam | `node/7161046604` | amenity=place_of_worship, religion=muslim | PASS |
| มัสยิดบ้านแหลมสัก | `way/1561270282` | amenity=place_of_worship, religion=muslim | PASS |
| มัสยิดบ้านคลองม่วง | `way/399675767` | amenity=place_of_worship, religion=muslim | PASS |
| มัสยิดบ้านคลองแห้ง | `way/659547702` | amenity=place_of_worship, religion=muslim | PASS |
| มัสยิดทรายขาว | `way/660722327` | amenity=place_of_worship, religion=muslim | PASS |

