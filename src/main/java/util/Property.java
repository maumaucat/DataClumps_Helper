package util;

public abstract class Property {

    private final String name;
    private final String type;

    public Property(String name, String type) {

        // remove leading underscore from name since it might be added for private properties but is not relevant for comparison
        if (name.startsWith("_")) {
            name = name.substring(1);
        }

        this.name = name;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public String getType() {
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
