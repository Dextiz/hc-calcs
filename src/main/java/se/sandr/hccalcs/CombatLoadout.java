package se.sandr.hccalcs;

import java.util.function.IntFunction;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Prayer;
import net.runelite.api.Skill;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemStats;

final class CombatLoadout
{
    final int magic, ranged, baseRanged;
    final int magicAttack, rangedAttack, rangedStrength;
    final double magicDamage;
    final String magicPrayer, rangedPrayer, salve;
    final boolean missingGear;

    CombatLoadout(int magic, int ranged, int baseRanged, int magicAttack, int rangedAttack, int rangedStrength,
        double magicDamage, String magicPrayer, String rangedPrayer, String salve, boolean missingGear)
    {
        this.magic = magic;
        this.ranged = ranged;
        this.baseRanged = baseRanged;
        this.magicAttack = magicAttack;
        this.rangedAttack = rangedAttack;
        this.rangedStrength = rangedStrength;
        this.magicDamage = magicDamage;
        this.magicPrayer = magicPrayer;
        this.rangedPrayer = rangedPrayer;
        this.salve = salve;
        this.missingGear = missingGear;
    }

    static CombatLoadout baseline(int magic, int ranged)
    {
        return new CombatLoadout(magic, ranged, ranged, 0, 0, 0, 0, "none", "none", "none", false);
    }

    // Read the client and its item data on the game thread.
    static CombatLoadout read(Client client, IntFunction<ItemStats> lookup)
    {
        if (client.getGameState() != GameState.LOGGED_IN)
            return null;
        ItemContainer equipment = client.getItemContainer(InventoryID.WORN);
        int magicAttack = 0, rangedAttack = 0, rangedStrength = 0;
        double magicDamage = 0;
        boolean missingGear = equipment == null;
        String salve = "none";
        if (equipment != null)
        {
            for (EquipmentInventorySlot slot : EquipmentInventorySlot.values())
            {
                Item item = equipment.getItem(slot.getSlotIdx());
                if (item == null || item.getId() <= 0) continue;
                int id = item.getId();
                if (slot == EquipmentInventorySlot.AMULET)
                    salve = salve(id);
                ItemStats stats = lookup.apply(id);
                ItemEquipmentStats bonus = stats == null ? null : stats.getEquipment();
                if (bonus == null)
                {
                    missingGear = true;
                    continue;
                }
                magicAttack += bonus.getAmagic();
                magicDamage += bonus.getMdmg();
                // Each chin supplies its own weapon bonus and uses no ammo.
                if (slot != EquipmentInventorySlot.WEAPON && slot != EquipmentInventorySlot.AMMO)
                {
                    rangedAttack += bonus.getArange();
                    rangedStrength += bonus.getRstr();
                }
            }
        }
        String magicPrayer = active(client, new Prayer[]{Prayer.AUGURY, Prayer.MYSTIC_VIGOUR, Prayer.MYSTIC_MIGHT,
            Prayer.MYSTIC_LORE, Prayer.MYSTIC_WILL}, new String[]{"augury", "vigour", "might", "lore", "will"});
        String rangedPrayer = active(client, new Prayer[]{Prayer.RIGOUR, Prayer.DEADEYE, Prayer.EAGLE_EYE,
            Prayer.HAWK_EYE, Prayer.SHARP_EYE}, new String[]{"rigour", "deadeye", "eagle", "hawk", "sharp"});
        return new CombatLoadout(client.getBoostedSkillLevel(Skill.MAGIC), client.getBoostedSkillLevel(Skill.RANGED),
            client.getRealSkillLevel(Skill.RANGED), magicAttack, rangedAttack, rangedStrength, magicDamage,
            magicPrayer, rangedPrayer, salve, missingGear);
    }

    private static String active(Client client, Prayer[] prayers, String[] names)
    {
        for (int i = 0; i < prayers.length; i++)
            if (client.isPrayerActive(prayers[i])) return names[i];
        return "none";
    }

    private static String salve(int id)
    {
        switch (id)
        {
            case ItemID.NZONE_SALVE_AMULET:
            case ItemID.SW_SALVE_AMULET:
            case ItemID.PVPA_SALVE_AMULET:
                return "i";
            case ItemID.NZONE_SALVE_AMULET_E:
            case ItemID.SW_SALVE_AMULET_E:
            case ItemID.PVPA_SALVE_AMULET_E:
                return "ei";
            default:
                return "none";
        }
    }
}
