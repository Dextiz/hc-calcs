# HCW Calcs

RuneLite calculators for Hardcore Worlds, based on [hcw.sandr.se](https://hcw.sandr.se/).

- Herblore, Cooking, Fletching, Smithing, Crafting, Runecrafting, Prayer, and Farming include training goals and trade-volume filters. Prayer has ensouled heads with rune costs and Magic requirements. Farming includes herb and allotment yields, patch settings, and editable harvest quantities.
- Edit your current level or total XP and your goal in the top row. The Level / XP toggle and both values are saved per skill. Clear the level field to use live RuneLite stats and clear Goal to remove the target. Select a result to see its total cost or profit, required actions, and upfront cost where applicable.
- Choose Herbs in Farming to show the patch count, magic secateurs and compost. Other farming options are under Setup. Prayer methods are selectable above the results.
- Runecrafting shows XP, rune output, and profit per essence and per hour. Runs per hour starts from a route estimate where available, or 50 trips per hour as a planning value. A run is a full round trip. Select your repaired essence pouches under Setup to calculate how much fits per trip. The calculator deducts their inventory slots and applies level requirements. Manual pace and essence overrides are saved per method. Auto restores calculated essence capacity. Talismans, necklace charges, and Magic Imbue are charged per altar use. Estimates use base rune yields and omit outfit bonuses and travel costs.
- Mastering Mixology compares herb sources, paste targets, and potion orders. Combat XP imports Magic and Ranged levels and equipped bonuses, then compares Ancient Magicks and chinchompas against six full-HP targets.
- High Alchemy lists items with at least 100 reported HCW buys in a completed UTC day. NMZ Shop forecasts rewards and accounts for the daily herb-box limit.
- Kingdom recommends the worker allocation with the highest estimated profit using HCW sell prices and the saved plan. Each available task has a segmented bar showing its recommended worker count.
- The welcome screen shows patch notes on your first opening of each version. The panel then reads real skill levels and XP from the client and remembers the calculator and filters. Missing levels default to 99. Enter a level to model a different character.
- XP calculators show the best GP/XP and a minimum XP per action field. Green positive GP/XP means profit and red negative GP/XP means cost. Results display at most one decimal. Cooking compares XP per successful cook and Combat XP compares expected XP per cast or throw.
- Buy costs and sale values use HCW quotes. Sell price overrides last until the next successful price update.

Opening the calculators fetches prices directly from the [OSRS Wiki real-time prices API](https://oldschool.runescape.wiki/w/RuneScape:Real-time_Prices) at `prices.runescape.wiki/api/v2/osrs-hc/latest`. It refreshes every 30 minutes while open and allows a manual refresh after 60 seconds. Volume-dependent calculators fetch `/24h` on demand. High Alchemy also fetches `/mapping`. The plugin uses the latest successful snapshots in memory between refreshes. Requests carry a descriptive User-Agent and contain no player name, levels, XP, or equipment.

## Development

Set `JAVA_HOME` to a Java 11 JDK. On Windows, run `.\gradlew.bat build` to compile and verify the calculations, then `.\gradlew.bat run` to start RuneLite with HCW Calcs in the sidebar. On Linux and macOS, use `./gradlew`. The developer client uses a separate local profile under `build/dev-home`.

The calculators use the JSON bundled in this repository. To regenerate it from a local copy of the website source, put the plugin checkout inside the website project and run `node hc-calcs/tools/sync-recipes.cjs` from the website root. Pass `--check` to compare the bundled JSON with the website data.

## License and data sources

The original plugin code is available under the BSD 2-Clause license. See the `LICENSE` file. The Gradle Wrapper is distributed under the Apache License 2.0 and retains its upstream notices.

The bundled JSON tables adapt item and gameplay information from the Old School RuneScape Wiki. Adapted wiki material is available under [CC BY-NC-SA 3.0](https://creativecommons.org/licenses/by-nc-sa/3.0/) according to the [wiki's licensing terms](https://meta.weirdgloop.org/w/Licensing).

- [Herblore XP](https://oldschool.runescape.wiki/w/Herblore/Experience_table), [Cooking burn levels](https://oldschool.runescape.wiki/w/Cooking/Burn_level), [skilling success rates](https://oldschool.runescape.wiki/w/Skilling_success_rate), [Fletching XP](https://oldschool.runescape.wiki/w/Fletching/Experience_table), [Smithing XP](https://oldschool.runescape.wiki/w/Smithing/Experience_table), and [Crafting XP](https://oldschool.runescape.wiki/w/Crafting/Experience_table).
- [Runecraft XP and output](https://oldschool.runescape.wiki/w/Runecraft), [essence pouch capacities](https://oldschool.runescape.wiki/w/Essence_pouch), [combination runes](https://oldschool.runescape.wiki/w/Combination_runes), and [binding necklace](https://oldschool.runescape.wiki/w/Binding_necklace).
- Runecrafting route estimates draw on the Wiki guides for [nature](https://oldschool.runescape.wiki/w/Money_making_guide/Crafting_nature_runes_through_the_Abyss), [astral](https://oldschool.runescape.wiki/w/Money_making_guide/Crafting_astral_runes), [true blood](https://oldschool.runescape.wiki/w/Money_making_guide/Crafting_blood_runes), [Arceuus blood](https://oldschool.runescape.wiki/w/Money_making_guide/Crafting_blood_runes_at_Arceuus_(max)), [soul](https://oldschool.runescape.wiki/w/Money_making_guide/Crafting_soul_runes) and [wrath](https://oldschool.runescape.wiki/w/Money_making_guide/Crafting_wrath_runes).
- [Prayer bones](https://oldschool.runescape.wiki/w/Calculator:Prayer/Bones), [blessed bone shards](https://oldschool.runescape.wiki/w/Blessed_bone_shards), [offering spells](https://oldschool.runescape.wiki/w/Offering_spells), [reanimation spells](https://oldschool.runescape.wiki/w/Reanimation_spells), [Farming seeds](https://oldschool.runescape.wiki/w/Seeds), and [herb yields](https://oldschool.runescape.wiki/w/Calculator:Farming/Herbs).
- [Ancient Magicks](https://oldschool.runescape.wiki/w/Ancient_Magicks), [chinchompas](https://oldschool.runescape.wiki/w/Chinchompa_(weapon)), [armoured zombies](https://oldschool.runescape.wiki/w/Armoured_zombie_(Zemouregal%27s_Base)), and [maniacal monkeys](https://oldschool.runescape.wiki/w/Maniacal_monkey).
- [Mastering Mixology](https://oldschool.runescape.wiki/w/Mastering_Mixology/Strategies), [NMZ rewards](https://oldschool.runescape.wiki/w/Dom_Onion%27s_Reward_Shop), [herb boxes](https://oldschool.runescape.wiki/w/Herb_box), and [Miscellania loot](https://oldschool.runescape.wiki/w/Calculator:Miscellania_loot).
