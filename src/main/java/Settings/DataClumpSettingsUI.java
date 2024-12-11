package Settings;

import com.intellij.ide.HelpTooltip;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.ContextHelpLabel;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.UIUtil;
import kotlinx.coroutines.flow.Flow;
import util.CodeSmellLogger;

import javax.swing.*;
import java.awt.*;

/**
 * Supports creating and managing a {@link JPanel} for the Settings Dialog.
 */
public class DataClumpSettingsUI {


    private final JPanel mainPanel;
    private final ComboBox<Integer> numberOfProperties;
    private final JCheckBox includeModifiersInDetection = new JCheckBox("Include Modifiers in Detection");
    private final JCheckBox includeModifiersInExtractedClass = new JCheckBox("Include Modifiers in Extracted Class");



    /**
     * Creates a new instance of the settings UI
     */
    public DataClumpSettingsUI() {

        Integer[] options = {2, 3, 4, 5, 6, 7, 8, 9, 10};
        numberOfProperties = new ComboBox<>(options);
        numberOfProperties.setSelectedItem(DataClumpSettings.getInstance().getState().minNumberOfProperties);
        includeModifiersInDetection.setSelected(DataClumpSettings.getInstance().getState().includeModifiersInDetection);
        includeModifiersInExtractedClass.setSelected(DataClumpSettings.getInstance().getState().includeModifiersInExtractedClass);
        if (!includeModifiersInDetection.isSelected()) {
            includeModifiersInExtractedClass.setEnabled(false);
        }


        String TOOLTIP_NUMBER_OF_PROPERTIES = "The minimal number of fields or parameters that should be equal in order to be considered a data clump.";
        String TOOLTIP_INCLUDE_MODIFIERS_IN_DETECTION = "If selected, the modifiers of the fields will be considered when detecting data clumps. " +
                "In this case the modifier must be equal do two fields can be equal. " +
                "If deselected, only the names and types of the fields will be considered.";
        String TOOLTIP_INCLUDE_MODIFIERS_IN_EXTRACTED_CLASS = "If selected, the modifiers of the fields will be included in the extracted class. " +
                "If deselected, all fields will be private in the extracted class " +
                "or if using an existing class, the modifiers will stay as they are in that class.";

        mainPanel = FormBuilder.createFormBuilder()
                .addComponent(new JBLabel("Settings for data clump detection: "))
                .addLabeledComponent(new JBLabel("Number of Fields or Parameters: "), addHelpToolTip(numberOfProperties, TOOLTIP_NUMBER_OF_PROPERTIES), 1, false)
                .addComponent(addHelpToolTip(includeModifiersInDetection, TOOLTIP_INCLUDE_MODIFIERS_IN_DETECTION), 1)
                .addComponent(new JBLabel("Settings for extracting class: "))
                .addComponent(addHelpToolTip(includeModifiersInExtractedClass, TOOLTIP_INCLUDE_MODIFIERS_IN_EXTRACTED_CLASS), 1)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();


        // if include modifiers in detection is deselected, include modifiers in extracted class should also be deselected
        includeModifiersInDetection.addActionListener(e -> {
            if (!includeModifiersInDetection.isSelected()) {
                includeModifiersInExtractedClass.setSelected(false);
                includeModifiersInExtractedClass.setEnabled(false);
            } else {
                includeModifiersInExtractedClass.setEnabled(true);
            }
        });
    }

    /**
     * Adds a help tooltip to a component. The tooltip is displayed when hovering over the help icon.
     *
     * @param component The component to add the tooltip to
     * @param text The text of the tooltip
     * @return The component with the tooltip
     */
    private JComponent addHelpToolTip(JComponent component, String text) {

        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.add(component);

        JLabel tip = new JBLabel(IconLoader.getIcon("/icons/contextHelp.svg", getClass()));
        HelpTooltip helpTooltip = new HelpTooltip();
        helpTooltip.setDescription(text);
        helpTooltip.installOn(tip);

        panel.add(tip);
        return panel;
    }


    public void setNumberOfProperties(int value) {
       numberOfProperties.setSelectedItem(String.valueOf(value));
    }

    public int getNumberOfProperties() {
        return (int) numberOfProperties.getSelectedItem();
    }

    public void setIncludeModifiersInDetection(boolean value) {
        includeModifiersInDetection.setSelected(value);
    }

    public boolean getIncludeModifiersInDetection() {
        return includeModifiersInDetection.isSelected();
    }

    public void setIncludeModifiersInExtractedClass(boolean value) {
        includeModifiersInExtractedClass.setSelected(value);
    }

    public boolean getIncludeModifiersInExtractedClass() {
        return includeModifiersInExtractedClass.isSelected();
    }

    public JPanel getPanel() {
        return mainPanel;
    }

    public JComponent getPreferredFocusedComponent() {
        return numberOfProperties;
    }
}
