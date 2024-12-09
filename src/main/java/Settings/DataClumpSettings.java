package Settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

@State(
        name = "Settings.DataClumpSettings.java",
        storages = @Storage("Settings.DataClumpSettings.xml")
)
public final class DataClumpSettings implements PersistentStateComponent<DataClumpSettings.State> {


    private State myState = new State();

    public static class State {
        @NonNls
        public int minNumberOfProperties = 3;
        public boolean includeModifiersInDetection = true;
        public boolean includeModifiersInExtractedClass = true;
        // Hierachy of classes to be considered for data clump detection
    }

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