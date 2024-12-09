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

    public static final int DEFAULT_NUMBER_OF_PROPERTIES = 3;

    private State myState = new State();

    public static class State {
        @NonNls
        public int minNumberOfProperties = DEFAULT_NUMBER_OF_PROPERTIES;
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