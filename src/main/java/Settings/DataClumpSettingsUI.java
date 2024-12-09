package Settings;

import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.FormBuilder;
import util.CodeSmellLogger;

import javax.swing.*;

/**
 * Supports creating and managing a {@link JPanel} for the Settings Dialog.
 */
public class DataClumpSettingsUI {

    private final JPanel mainPanel;
    private final ComboBox<Integer> numberOfProperties;
    private final JCheckBox includeModifiersInDetection = new JCheckBox("Include Modifiers in Detection");
    private final JCheckBox includeModifiersInExtractedClass = new JCheckBox("Include Modifiers in Extracted Class");

    public DataClumpSettingsUI() {
        Integer[] options = {2, 3, 4, 5, 6, 7, 8, 9, 10};
        numberOfProperties = new ComboBox<>(options);
        numberOfProperties.setSelectedItem(DataClumpSettings.getInstance().getState().minNumberOfProperties);
        includeModifiersInExtractedClass.setSelected(DataClumpSettings.getInstance().getState().includeModifiersInExtractedClass);
        includeModifiersInDetection.setSelected(DataClumpSettings.getInstance().getState().includeModifiersInDetection);

        mainPanel = FormBuilder.createFormBuilder()
                .addComponent(new JBLabel("Settings for data clump detection: "))
                .addLabeledComponent(new JBLabel("Minimal number of Fields or Parameters to be considered a DataClump: "), numberOfProperties, 1, false)
                .addComponent(includeModifiersInDetection)
                .addComponent(new JBLabel("Settings for extracting class: "))
                .addComponent(includeModifiersInExtractedClass)
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

    // Set the value in the combo box
    public void setNumberOfProperties(int value) {
       numberOfProperties.setSelectedItem(String.valueOf(value));
    }

    // Get the value from the combo box
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

    // Get the main panel for the settings dialog
    public JPanel getPanel() {
        return mainPanel;
    }

    // Get the component that should be focused by default
    public JComponent getPreferredFocusedComponent() {
        return numberOfProperties;
    }
}
