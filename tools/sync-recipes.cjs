// Build-time export from the website. RuneLite only loads the resulting static JSON.
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const root = path.resolve(__dirname, "../..");
const read = file => fs.readFileSync(path.join(root, file), "utf8");
const readJs = (file, expression) => vm.runInNewContext(read(file) + "\n" + expression);
const readInline = (file, first, last, expression) => {
  const source = read(file);
  const start = source.indexOf(first);
  const end = source.indexOf(last, start);
  if (start < 0 || end < 0) throw new Error("Recipe declarations moved in " + file);
  return vm.runInNewContext(source.slice(start, end) + "\n" + expression);
};

const prices = ([id, name, quantity = 1], goggles = false) => ({ id, name, quantity, goggles });
const amuletException = new Set([4417, 3408]);
const extraGoggleIngredients = new Set([5943, 5952, 5940]);
const canSave = (category, id, name, index, count) => {
  if (amuletException.has(id) || name === "Crystal dust") return false;
  if (id === 31587) return name === "Squid paste";
  if (category !== "Potions" && category !== "Barbarian mixes") return false;
  return index === count - 1 || (index === 1 && extraGoggleIngredients.has(id));
};

function training(key, dataset) {
  const rows = Object.entries(dataset.recipesByCategory).flatMap(([category, recipes]) =>
    recipes.map(([level, id, name, quantity, xp, inputs, loot]) => ({
      level, name, category, xp,
      quantity,
      amulet: key === "herblore" && category === "Potions" && quantity === 0.75 && !amuletException.has(id),
      selfGatheredFree: key === "smithing" || key === "crafting",
      inputs: inputs.map((item, index) => prices(item,
        key === "herblore" && canSave(category, id, item[1], index, inputs.length))),
      outputs: (loot ?? [[id, name, quantity]]).map(item => prices(item)),
    }))
  );
  return { rows, taxExempt: dataset.taxExempt };
}

const production = readJs("production-data.js", "productionSkills");
const fletching = readInline("fletching.html", "const recipesByCategory", "const fields",
  "({recipesByCategory, taxExempt: [...taxExempt]})");
const cooking = readInline("cooking.html", "const recipes = ", "const numberFormat",
  "({recipes, taxExempt: [...taxExempt]})");

const catalog = {
  herblore: training("herblore", readJs("herblore-data.js", "herbloreSkill")),
  cooking: {
    taxExempt: cooking.taxExempt,
    rows: cooking.recipes.map(([level, id, name, inputs, xp, range, fire, gauntlets]) => ({
      level, name, xp, category: id === 1993 ? "Wine" : "Fish",
      quantity: 1,
      inputs: inputs.map(item => prices(item)),
      outputs: [prices([id, name, 1])],
      range, fire, gauntlets,
    })),
  },
  fletching: training("fletching", fletching),
  smithing: training("smithing", production.smithing),
  crafting: training("crafting", production.crafting),
};

for (const [skill, { rows }] of Object.entries(catalog)) {
  if (!rows.length || rows.some(row => !row.name || !Number.isInteger(row.level) ||
      !Number.isFinite(row.xp) || !row.inputs.length || !row.outputs.length ||
      [...row.inputs, ...row.outputs].some(item => !item.name || !Number.isFinite(item.quantity)))) {
    throw new Error("Unexpected " + skill + " recipe data");
  }
  console.log(skill + ": " + rows.length);
}

const output = path.join(__dirname, "../src/main/resources/hc-calcs-recipes.json");
const serialized = "{\n" + Object.entries(catalog).map(([skill, { taxExempt, rows }]) =>
  `  "${skill}": {\n    "taxExempt": ${JSON.stringify(taxExempt)},\n    "rows": [\n` +
  rows.map(row => "      " + JSON.stringify(row)).join(",\n") + "\n    ]\n  }"
).join(",\n") + "\n}\n";
function write(file, contents) {
  if (process.argv.includes("--check")) {
    if (fs.readFileSync(file, "utf8") !== contents) throw new Error("Bundled data needs to be regenerated");
  } else {
    fs.mkdirSync(path.dirname(file), { recursive: true });
    fs.writeFileSync(file, contents);
  }
}
write(output, serialized);

const combined = (files, expression) =>
  vm.runInNewContext(files.map(read).join("\n") + "\n" + expression);
const farming = combined(["farming-data.js", "farming-herbs.js"],
  "({crops: farmingCrops, composts: farmingComposts, taxExempt: [...farmingTaxExempt], herbs: farmingHerbData, runes: farmingResurrectionRunes})");
const prayer = readInline("prayer.html", "const bones", "const methods =",
  "({bones, ashes, reanimations, runeNames: reanimationRuneNames})");
const combat = readInline("combat-xp.js", "const targets", "function hitChance",
  "({targets, runeIds, spells, chins, magicPrayers, rangedPrayers, salveBonuses})");
const mixology = readInline("mixology.js", "const pasteTypes", "const target =",
  "({pasteTypes, supplies, orders: potionOrders})");
const nmz = vm.runInNewContext(read("nmz-rewards.js") + "\n" +
  read("nmz-shop.js").split("const gpFormat")[0] + "\n" +
  "({rewards: [...resources, ...benefits].map(({category, ...reward}) => reward), herbs: nmzHerbBoxContents, boxLimit: nmzHerbBoxDailyLimit})");
const kingdom = readJs("kingdom-data.js",
  "({departments: kingdomDepartments, rates: kingdomRates, gems: kingdomGems, fishing: kingdomFishingLoot, herbs: kingdomHerbs, herbSeeds: kingdomHerbSeeds, flaxSeeds: kingdomFlaxSeeds, treeSeeds: kingdomTreeSeeds, rings: kingdomNestRings, farmSeeds: kingdomFarmSeeds})");
console.log("prayer: " + (prayer.bones.length + prayer.ashes.length + prayer.reanimations.flatMap(s => s.heads).length));
console.log("farming: " + farming.crops.length);
const extra = { prayer, farming, combat, mixology, nmz, kingdom };
const encode = value => Array.isArray(value)
  ? "[\n" + value.map(row => "      " + JSON.stringify(row)).join(",\n") + "\n    ]"
  : JSON.stringify(value);
write(path.join(__dirname, "../src/main/resources/hc-calcs-utilities.json"),
  "{\n" + Object.entries(extra).map(([id, data]) => "  " + JSON.stringify(id) + ": {\n" +
    Object.entries(data).map(([key, value]) => "    " + JSON.stringify(key) + ": " + encode(value)).join(",\n") +
    "\n  }").join(",\n") + "\n}\n");
