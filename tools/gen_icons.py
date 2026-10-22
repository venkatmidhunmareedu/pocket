#!/usr/bin/env python3
"""Fetch open-licensed Material icons (Apache-2.0, @material-design-icons/svg) and write Android vector drawables.
UI icons -> ic_<name>.xml ; record/account icon ids -> ic_i<id>.xml (same id scheme as the MyMoney reference app)."""
import re, sys, urllib.request, pathlib

OUT = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/res/drawable"
BASE = "https://cdn.jsdelivr.net/npm/@material-design-icons/svg@0.14.13/filled/"

UI = """add arrow_back arrow_forward chevron_left chevron_right search menu tune backspace check close delete edit
calendar_today schedule content_copy more_vert receipt_long pie_chart calculate account_balance_wallet label
file_download backup restore settings help feedback info sync_alt swap_horiz expand_more sort add_circle
visibility lock cloud_upload folder history star share description notifications delete_forever refresh
keyboard_arrow_down arrow_drop_down radio_button_checked radio_button_unchecked check_circle bar_chart
show_chart donut_large trending_up trending_down""".split()

IDS = {
 1:"directions_car",2:"smoking_rooms",3:"checkroom",4:"movie",5:"restaurant",6:"local_hospital",7:"home",8:"security",
 9:"shopping_cart",10:"sports_soccer",11:"account_balance",12:"phone",13:"receipt_long",14:"child_friendly",15:"devices",
 16:"face",17:"people",18:"school",19:"directions_bus",20:"call_made",
 101:"emoji_events",102:"local_offer",103:"volunteer_activism",104:"casino",105:"currency_exchange",106:"key",
 107:"payments",108:"sell",109:"call_received",
 121:"beach_access",122:"menu_book",123:"cable",124:"celebration",125:"soup_kitchen",126:"local_bar",127:"local_gas_station",
 128:"card_giftcard",129:"fitness_center",130:"flight",131:"wifi",132:"lunch_dining",133:"music_note",134:"oil_barrel",
 135:"cake",136:"pets",137:"train",138:"hiking",139:"sanitizer",140:"luggage",141:"airplane_ticket",142:"restaurant_menu",
 143:"sports_basketball",144:"sports_bar",145:"pedal_bike",146:"photo_camera",147:"park",148:"palette",149:"memory",
 150:"fireplace",151:"set_meal",152:"local_grocery_store",153:"yard",154:"queue_music",155:"icecream",156:"iron",157:"local_laundry_service",
 158:"two_wheeler",159:"format_paint",160:"smartphone",161:"local_police",162:"local_drink",163:"weekend",164:"theater_comedy",
 165:"graphic_eq",166:"computer",167:"shopping_bag",168:"bolt",169:"sports_esports",170:"baby_changing_station",
 171:"storefront",172:"medical_services",173:"build",174:"cast_for_education",175:"work",
 201:"star",202:"favorite",203:"bookmark",204:"flag",205:"lightbulb",206:"public",207:"savings",208:"trending_up",
 209:"trending_down",210:"schedule",211:"event",212:"mail",213:"lock",214:"build_circle",215:"extension",216:"science",
 217:"spa",218:"forest",219:"water_drop",220:"local_florist",221:"directions_run",222:"sports_tennis",223:"sports_cricket",
 224:"sports_golf",225:"pool",226:"hotel",227:"theaters",228:"headphones",229:"brush",230:"handyman",231:"diamond",
 232:"rocket_launch",
 301:"payments",302:"credit_card",303:"savings",304:"credit_score",305:"contactless",306:"monetization_on",
 307:"account_balance_wallet",308:"business_center",309:"send",310:"tips_and_updates",311:"card_membership",312:"inventory_2",
 321:"receipt",322:"factory",323:"event_note",324:"badge",325:"account_balance",326:"wallet",327:"laptop",328:"lock",
}

def fetch(name):
    try:
        return urllib.request.urlopen(BASE + name + ".svg", timeout=20).read().decode()
    except Exception as e:
        return None

def to_vector(svg):
    paths = re.findall(r'<path[^>]*\sd="([^"]+)"', svg)
    if not paths: return None
    body = "\n".join(f'    <path android:fillColor="#FF000000" android:pathData="{p}"/>' for p in paths)
    return ('<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">\n'
            f'{body}\n</vector>\n')

def write(fname, name):
    svg = fetch(name)
    xml = to_vector(svg) if svg else None
    if xml is None:
        print("MISSING", name, file=sys.stderr); return False
    (OUT / fname).write_text(xml); return True

OUT.mkdir(parents=True, exist_ok=True)
miss = 0
for n in UI: miss += not write(f"ic_{n}.xml", n)
for i, n in IDS.items(): miss += not write(f"ic_i{i}.xml", n)
print("done, missing:", miss)
