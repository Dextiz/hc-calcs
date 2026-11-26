package se.sandr.hccalcs;

import java.awt.BorderLayout;
import java.awt.Insets;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.function.IntConsumer;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.UIManager;

final class InlineNumberInput extends JPanel
{
    private final JTextField input;
    private String displayed = "";

    InlineNumberInput(String label, String name, String hint, int columns, int maximum, IntConsumer onChange)
    {
        super(new BorderLayout(4, 0));
        input = new JTextField(columns);
        input.setMargin(new Insets(2, 3, 2, 3));
        input.setHorizontalAlignment(SwingConstants.RIGHT);
        input.setToolTipText(hint);
        input.getAccessibleContext().setAccessibleName(name);
        JLabel caption = new JLabel(label);
        caption.setLabelFor(input);
        caption.setToolTipText(hint);
        add(caption, BorderLayout.WEST);
        add(input, BorderLayout.CENTER);

        input.addActionListener(event -> commit(maximum, onChange));
        input.addFocusListener(new FocusAdapter()
        {
            @Override
            public void focusGained(FocusEvent event) { input.selectAll(); }

            @Override
            public void focusLost(FocusEvent event)
            {
                if (!event.isTemporary()) commit(maximum, onChange);
            }
        });
    }

    void showValue(Integer value)
    {
        displayed = value == null ? "" : value.toString();
        if (!displayed.equals(input.getText())) input.setText(displayed);
    }

    void showWhenIdle(int value)
    {
        if (!input.hasFocus()) showValue(value);
    }

    void highlight(boolean active) { input.setForeground(active ? XpResults.GOLD : UIManager.getColor("TextField.foreground")); }

    private void commit(int maximum, IntConsumer onChange)
    {
        String text = input.getText().strip();
        if (text.equals(displayed)) return;
        try
        {
            String digits = text.replaceAll("[,\\s\u00a0\u202f]", "");
            int value = text.isEmpty() ? 0 : Integer.parseInt(digits);
            if (value < 0 || value > maximum) throw new NumberFormatException();
            showValue(value);
            onChange.accept(value);
        }
        catch (NumberFormatException ex)
        {
            input.setText(displayed);
            UIManager.getLookAndFeel().provideErrorFeedback(input);
        }
    }
}
