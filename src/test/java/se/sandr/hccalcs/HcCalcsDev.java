package se.sandr.hccalcs;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public final class HcCalcsDev
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(HcCalcsPlugin.class);
        RuneLite.main(args);
    }
}
