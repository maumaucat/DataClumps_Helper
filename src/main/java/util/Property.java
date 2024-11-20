package util;

import com.intellij.lang.javascript.psi.JSType;

public abstract class Property {

    private final String name;
    private final JSType type;

    public Property(String name, JSType type) {
        this.name = name;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public JSType getType() {
        return type;
    }

    @Override
    public int hashCode() {
        return name.hashCode() + type.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Property otherProperty)) return false;
        return name.equals(otherProperty.name) && type.equals(otherProperty.type);
    }

    @Override
    public abstract String toString();

}
