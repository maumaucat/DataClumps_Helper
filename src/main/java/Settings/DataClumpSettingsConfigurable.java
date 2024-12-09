package Settings;

import com.intellij.internal.statistic.eventLog.validator.rules.ValidationError;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.ui.ValidationInfo;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;
import util.CodeSmellLogger;

import javax.swing.*;
import java.util.Objects;

/**
 * Provides controller functionality for application settings.
 */
final class DataClumpSettingsConfigurable implements Configurable {

    private DataClumpSettingsUI mySettingsComponent;

    // A default constructor with no arguments is required because
    // this implementation is registered as an applicationConfigurable

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "Data Clump Helper";
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
        return mySettingsComponent.getPreferredFocusedComponent();
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        mySettingsComponent = new DataClumpSettingsUI();
        return mySettingsComponent.getPanel();
    }

    @Override
    public boolean isModified() {
        DataClumpSettings.State state =
                Objects.requireNonNull(DataClumpSettings.getInstance().getState());
        return mySettingsComponent.getNumberOfProperties() != state.minNumberOfProperties
                || mySettingsComponent.getIncludeModifiersInDetection() != state.includeModifiersInDetection
                || mySettingsComponent.getIncludeModifiersInExtractedClass() != state.includeModifiersInExtractedClass;
    }

    @Override
    public void apply() {
        DataClumpSettings.State state = Objects.requireNonNull(DataClumpSettings.getInstance().getState());
        state.minNumberOfProperties = mySettingsComponent.getNumberOfProperties();
        state.includeModifiersInDetection = mySettingsComponent.getIncludeModifiersInDetection();
        state.includeModifiersInExtractedClass = mySettingsComponent.getIncludeModifiersInExtractedClass();
    }


    @Override
    public void reset() {
        DataClumpSettings.State state =
                Objects.requireNonNull(DataClumpSettings.getInstance().getState());
        mySettingsComponent.setNumberOfProperties(state.minNumberOfProperties);
        mySettingsComponent.setIncludeModifiersInDetection(state.includeModifiersInDetection);
        mySettingsComponent.setIncludeModifiersInExtractedClass(state.includeModifiersInExtractedClass);
    }

    @Override
    public void disposeUIResources() {
        mySettingsComponent = null;
    }
}
