package se.sandr.hccalcs;

import com.google.gson.Gson;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import okhttp3.OkHttpClient;

@PluginDescriptor(
    name = "HCW Calcs",
    description = "Hardcore Worlds training, combat, and reward calculators",
    tags = {"hardcore", "skilling", "calculator", "prices"}
)
public class HcCalcsPlugin extends Plugin
{
    static final Skill[] SKILLS = {Skill.HERBLORE, Skill.COOKING, Skill.FLETCHING, Skill.SMITHING, Skill.CRAFTING,
        Skill.RUNECRAFT};

    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private ClientToolbar clientToolbar;
    @Inject private ConfigManager configManager;
    @Inject private SkillIconManager icons;
    @Inject private ItemManager itemManager;
    @Inject private Gson gson;
    @Inject private OkHttpClient http;

    private volatile HcCalcsPanel panel;
    private NavigationButton navigation;

    @Override
    protected void startUp() throws Exception
    {
        Map<String, RecipeCatalog> catalogs = RecipeCatalog.load(gson);
        // PluginManager starts and stops plugins on the Swing event-dispatch thread.
        HcCalcsPanel view = new HcCalcsPanel(catalogs, UtilityCalculator.load(), new MarketClient(http), configManager, icons,
            this::importCombatLoadout);
        panel = view;
        navigation = NavigationButton.builder()
            .tooltip("HCW Calcs")
            .icon(ImageUtil.loadImageResource(HcCalcsPlugin.class, "/hc-calcs-icon.png"))
            .priority(12)
            .panel(view)
            .build();
        clientToolbar.addNavigation(navigation);
        clientThread.invokeLater(this::readLevels);
    }

    @Override
    protected void shutDown()
    {
        HcCalcsPanel view = panel;
        panel = null;
        if (navigation != null)
        {
            clientToolbar.removeNavigation(navigation);
            navigation = null;
        }
        if (view != null)
        {
            view.dispose();
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        GameState state = event.getGameState();
        if (state == GameState.LOGGED_IN || state == GameState.LOGIN_SCREEN ||
            state == GameState.LOGIN_SCREEN_AUTHENTICATOR)
        {
            readLevels();
        }
    }

    @Subscribe
    public void onStatChanged(StatChanged event)
    {
        if (event.getSkill() != Skill.OVERALL && client.getGameState() == GameState.LOGGED_IN)
        {
            readLevels();
        }
    }

    private void readLevels()
    {
        HcCalcsPanel view = panel;
        if (view == null)
        {
            return;
        }
        Map<Skill, Integer> xp = new EnumMap<>(Skill.class);
        Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
        if (client.getGameState() == GameState.LOGGED_IN)
        {
            for (Skill skill : Skill.values())
            {
                if (skill == Skill.OVERALL) continue;
                int level = client.getRealSkillLevel(skill);
                if (level >= 1 && level <= 99)
                {
                    levels.put(skill, level);
                    xp.put(skill, client.getSkillExperience(skill));
                }
            }
        }
        SwingUtilities.invokeLater(() ->
        {
            if (panel == view)
            {
                view.setStats(levels, xp);
            }
        });
    }

    private void importCombatLoadout(Consumer<CombatLoadout> complete)
    {
        HcCalcsPanel view = panel;
        clientThread.invokeLater(() ->
        {
            CombatLoadout loadout = CombatLoadout.read(client, itemManager::getItemStats);
            SwingUtilities.invokeLater(() ->
            {
                if (panel == view) complete.accept(loadout);
            });
        });
    }
}
