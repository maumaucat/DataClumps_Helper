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

    public DataClumpSettingsUI() {
        Integer[] options = {2, 3, 4, 5, 6, 7, 8, 9, 10};
        numberOfProperties = new ComboBox<>(options);
        numberOfProperties.setSelectedItem(DataClumpSettings.getInstance().getState().minNumberOfProperties);

        mainPanel = FormBuilder.createFormBuilder()
                .addLabeledComponent(new JBLabel("Minimal number of Fields or Parameters to be considered a DataClump: "), numberOfProperties, 1, false)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
    }

    // Set the value in the combo box
    public void setNumberOfProperties(int value) {
       numberOfProperties.setSelectedItem(String.valueOf(value));
    }

    // Get the value from the combo box
    public int getNumberOfProperties() {
        return (int) numberOfProperties.getSelectedItem();
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
