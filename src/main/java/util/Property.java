package util;

import com.intellij.lang.javascript.psi.JSType;

public class Property {

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
        if (!(obj instanceof Property otherParameter)) return false;
        return otherParameter.name.equals(name) &&
                otherParameter.type.equals(type);
    }

    @Override
    public String toString() {
        return "Property " + name + " : " + type;
    }
}
