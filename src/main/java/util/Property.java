package util;

import com.intellij.lang.javascript.psi.JSType;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public abstract class Property {

    protected final String name;
    protected final Set<String> types;

    public Property(String name, JSType types) {

        // remove leading underscore from name since it might be added for private properties but is not relevant for comparison
        if (name.startsWith("_")) {
            name = name.substring(1);
        }

        this.name = name;
        this.types = new HashSet<>();
        this.types.addAll(Arrays.asList(types.getTypeText().split("\\|")));
    }

    public String getName() {
        return name;
    }

    public Set<String> getTypes() {
        return types;
    }

    public String getTypesAsString() {
        return String.join("|", types);
    }

    @Override
    public int hashCode() {
        return name.hashCode() + types.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Property otherProperty)) return false;
        return name.equals(otherProperty.name) && types.equals(otherProperty.types);
    }

    @Override
    public abstract String toString();

}
