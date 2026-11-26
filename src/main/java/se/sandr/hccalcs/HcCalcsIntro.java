package se.sandr.hccalcs;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.util.ImageUtil;

final class HcCalcsIntro extends JPanel
{
    static final String VERSION = "0.2.13";
    private static final int TEXT_WIDTH = 176;
    private final JButton open = new JButton("Open calculators");

    HcCalcsIntro(Runnable onOpen)
    {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(12, 8, 12, 8));

        JLabel heading = new JLabel("Welcome to HCW Calcs");
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 17f));
        heading.setForeground(XpResults.GOLD);
        addCentered(heading);
        add(Box.createVerticalStrut(8));
        addCentered(copy("Thank you for using this plugin!", true));
        add(Box.createVerticalStrut(8));
        addCentered(copy("If you have suggestions, PM me in-game<br><b>@dextiz</b> or <b>@mega hard</b>.", true));
        add(Box.createVerticalStrut(8));
        addCentered(copy("<b>Remember to do your calcs.</b>", true));
        add(Box.createVerticalStrut(12));

        JLabel artwork = new JLabel(new ImageIcon(ImageUtil.loadImageResource(HcCalcsIntro.class, "/hc-calcs-welcome.png")));
        artwork.getAccessibleContext().setAccessibleDescription("Pixel art of a nerd with glasses holding a calculator");
        addCentered(artwork);
        add(Box.createVerticalStrut(12));

        JPanel notes = new JPanel();
        notes.setLayout(new BoxLayout(notes, BoxLayout.Y_AXIS));
        notes.setBorder(BorderFactory.createEmptyBorder(8, 4, 8, 4));
        JPanel label = new JPanel(new BorderLayout(8, 0));
        JLabel title = new JLabel("Patch notes");
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        title.setForeground(XpResults.GOLD);
        label.add(title, BorderLayout.WEST);
        label.add(new JLabel(VERSION), BorderLayout.EAST);
        label.setMaximumSize(new Dimension(Integer.MAX_VALUE, label.getPreferredSize().height));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        notes.add(label);
        notes.add(Box.createVerticalStrut(8));
        JLabel updates = copy("• Edit your level, XP and goal directly<br>"
            + "• See goal costs when you select a method<br>"
            + "• Quick herb settings and Prayer methods", false);
        updates.setAlignmentX(Component.LEFT_ALIGNMENT);
        notes.add(updates);
        addCentered(notes);
        add(Box.createVerticalStrut(8));

        open.setMaximumSize(new Dimension(Integer.MAX_VALUE, open.getPreferredSize().height));
        open.addActionListener(event -> onOpen.run());
        addCentered(open);
    }

    private static JLabel copy(String text, boolean center)
    {
        return new JLabel("<html><div style='width:" + TEXT_WIDTH + "px;text-align:" + (center ? "center" : "left")
            + "'>" + text + "</div></html>");
    }

    private void addCentered(javax.swing.JComponent component)
    {
        component.setAlignmentX(Component.CENTER_ALIGNMENT);
        add(component);
    }
}
