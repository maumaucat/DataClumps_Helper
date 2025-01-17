package Settings;

import com.intellij.ide.HelpTooltip;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.FormBuilder;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;

/**
 * Supports creating and managing a {@link JPanel} for the Settings Dialog.
 */
public class DataClumpSettingsUI {


    private final JPanel mainPanel;
    private final ComboBox<Integer> numberOfProperties;
    private final ComboBox<DataClumpSettings.Modifier> includeModifiersInDetection = new ComboBox<>(DataClumpSettings.Modifier.values());
    private final ComboBox<DataClumpSettings.Modifier> includeModifiersInExtractedClass = new ComboBox<>();

    /**
     * Creates a new instance of the settings UI
     */
    public DataClumpSettingsUI() {

        Integer[] options = {2, 3, 4, 5, 6, 7, 8, 9, 10};
        numberOfProperties = new ComboBox<>(options);
        numberOfProperties.setSelectedItem(Objects.requireNonNull(DataClumpSettings.getInstance().getState()).minNumberOfProperties);
        includeModifiersInDetection.setSelectedItem(DataClumpSettings.getInstance().getState().includeModifiersInDetection);
        if (includeModifiersInDetection.getSelectedItem() == DataClumpSettings.Modifier.NONE) {
            includeModifiersInExtractedClass.addItem(DataClumpSettings.Modifier.NONE);
        } else if (includeModifiersInDetection.getSelectedItem() == DataClumpSettings.Modifier.VISIBILITY) {
            includeModifiersInExtractedClass.addItem(DataClumpSettings.Modifier.NONE);
            includeModifiersInExtractedClass.addItem(DataClumpSettings.Modifier.VISIBILITY);
        } else {
            includeModifiersInExtractedClass.addItem(DataClumpSettings.Modifier.NONE);
            includeModifiersInExtractedClass.addItem(DataClumpSettings.Modifier.VISIBILITY);
            includeModifiersInExtractedClass.addItem(DataClumpSettings.Modifier.ALL);
        }
        includeModifiersInExtractedClass.setSelectedItem(DataClumpSettings.getInstance().getState().includeModifiersInExtractedClass);

        String TOOLTIP_NUMBER_OF_PROPERTIES = "The minimal number of fields or parameters that should be equal in order to be considered a data clump.";
        String TOOLTIP_INCLUDE_MODIFIERS_IN_DETECTION = "Select the modifier types that should be considered when detecting data clumps. " +
                "In case of ALL the fields must have the same modifiers to be considered equal. " +
                "In case of VISIBILITY, only the visibility of the fields will be considered. " +
                "In case of NONE, the modifiers of the fields will not be considered.";
        String TOOLTIP_INCLUDE_MODIFIERS_IN_EXTRACTED_CLASS = "Select the modifier types that should be included in the extracted class. " +
                "In case the class is newly created. " +
                "In case of ALL all modifiers will be included in the extracted class. " +
                "In case of VISIBILITY only the visibility modifiers will be included in the extracted class. " +
                "In case of NONE no modifiers will be included in the extracted class." +
                "The selected modifier must be included in the detected data clumps.";

        mainPanel = FormBuilder.createFormBuilder()
                .addComponent(new JBLabel("Settings for data clump detection: "))
                .addLabeledComponent(new JBLabel("Number of Fields or Parameters: "), addHelpToolTip(numberOfProperties, TOOLTIP_NUMBER_OF_PROPERTIES), 1, false)
                .addLabeledComponent(new JBLabel("Include modifiers in detection: "),addHelpToolTip(includeModifiersInDetection, TOOLTIP_INCLUDE_MODIFIERS_IN_DETECTION), 1)
                .addComponent(new JBLabel("Settings for extracting class: "))
                .addLabeledComponent(new JBLabel("Include modifiers in the extracted class: "),addHelpToolTip(includeModifiersInExtractedClass, TOOLTIP_INCLUDE_MODIFIERS_IN_EXTRACTED_CLASS), 1)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();


        // only the detected modifier can be selected in the extracted class
        includeModifiersInDetection.addActionListener(e -> {
            if (includeModifiersInDetection.getSelectedItem() == DataClumpSettings.Modifier.NONE) {
                includeModifiersInExtractedClass.removeAllItems();
                includeModifiersInExtractedClass.addItem(DataClumpSettings.Modifier.NONE);
                includeModifiersInExtractedClass.setSelectedItem(DataClumpSettings.Modifier.NONE);
            } else if (includeModifiersInDetection.getSelectedItem() == DataClumpSettings.Modifier.VISIBILITY) {
                DataClumpSettings.Modifier selected = (DataClumpSettings.Modifier) includeModifiersInExtractedClass.getSelectedItem();
                includeModifiersInExtractedClass.removeAllItems();
                includeModifiersInExtractedClass.addItem(DataClumpSettings.Modifier.NONE);
                includeModifiersInExtractedClass.addItem(DataClumpSettings.Modifier.VISIBILITY);
                if (selected == DataClumpSettings.Modifier.ALL) {
                    includeModifiersInExtractedClass.setSelectedItem(DataClumpSettings.Modifier.VISIBILITY);
                } else {
                    includeModifiersInExtractedClass.setSelectedItem(DataClumpSettings.Modifier.NONE);
                }
            } else {
                DataClumpSettings.Modifier selected = (DataClumpSettings.Modifier) includeModifiersInExtractedClass.getSelectedItem();
                includeModifiersInExtractedClass.removeAllItems();
                includeModifiersInExtractedClass.addItem(DataClumpSettings.Modifier.NONE);
                includeModifiersInExtractedClass.addItem(DataClumpSettings.Modifier.VISIBILITY);
                includeModifiersInExtractedClass.addItem(DataClumpSettings.Modifier.ALL);
                includeModifiersInExtractedClass.setSelectedItem(selected);
            }

        });
    }

    /**
     * Adds a help tooltip to a component. The tooltip is displayed when hovering over the help icon.
     *
     * @param component The component to add the tooltip to
     * @param text      The text of the tooltip
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
        assert numberOfProperties.getSelectedItem() != null;
        return (int) numberOfProperties.getSelectedItem();
    }

    public void setIncludeModifiersInDetection(DataClumpSettings.Modifier value) {
        includeModifiersInDetection.setSelectedItem(value);
    }

    public DataClumpSettings.Modifier getIncludeModifiersInDetection() {
        return (DataClumpSettings.Modifier) includeModifiersInDetection.getSelectedItem();
    }

    public void setIncludeModifiersInExtractedClass(DataClumpSettings.Modifier value) {
        includeModifiersInExtractedClass.setSelectedItem(value);
    }

    public DataClumpSettings.Modifier getIncludeModifiersInExtractedClass() {
        return (DataClumpSettings.Modifier) includeModifiersInExtractedClass.getSelectedItem();
    }

    public JPanel getPanel() {
        return mainPanel;
    }

    public JComponent getPreferredFocusedComponent() {
        return numberOfProperties;
    }
}
