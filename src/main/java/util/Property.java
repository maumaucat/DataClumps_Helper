package util;

import com.intellij.lang.javascript.psi.JSType;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Represents a property that can be a field or a parameter
 */
public abstract class Property {

    /**
     * The name of the property
     */
    protected final String name;
    /**
     * The types of the property
     */
    protected final Set<String> types;

    /**
     * Creates a new instance of a property
     *
     * @param name  The name of the property
     * @param types The types of the property
     */
    public Property(String name, JSType types) {

        // remove leading underscore from name since it might be added for private properties but is not relevant for comparison
        if (name.startsWith("_")) {
            name = name.substring(1);
        }

        this.name = name;
        this.types = new HashSet<>();
        this.types.addAll(PsiUtil.runReadActionWithResult(() -> Arrays.asList(types.getTypeText().split("\\|"))));
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
