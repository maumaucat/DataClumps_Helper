package Settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Represents the settings for the data clump detection and extraction.
 * Stores the settings in the project settings.
 */
@State(
        name = "Settings.DataClumpSettings.java",
        storages = @Storage("Settings.DataClumpSettings.xml")
)
public final class DataClumpSettings implements PersistentStateComponent<DataClumpSettings.State> {

    /**
     * Represents the modifier types that can be considered
     */
    public enum Modifier {
        ALL,
        VISIBILITY,
        NONE
    }

    /**
     * The state of the settings
     */
    private State myState = new State();

    /**
     * Represents the state of the settings
     */
    public static class State {
        @NonNls
        public int minNumberOfProperties = 3;
        public Modifier includeModifiersInDetection = Modifier.ALL;
        public Modifier includeModifiersInExtractedClass = Modifier.ALL; ;
        public boolean includeFieldsInSameHierarchy = true;
    }

    /**
     * Returns the instance of the settings
     *
     * @return The instance of the settings
     */
    public static DataClumpSettings getInstance() {
        return ApplicationManager.getApplication()
                .getService(DataClumpSettings.class);
    }

    @Override
    public State getState() {
        return myState;
    }

    @Override
    public void loadState(@NotNull State state) {
        myState = state;
    }


}


