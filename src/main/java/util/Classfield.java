package util;
import Settings.DataClumpSettings;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptField;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptParameter;

import java.util.List;
import java.util.Objects;

/**
 * Represents a field in a class
 */
public class Classfield extends Property {

    /**
     * The modifiers of the field
     */
    private final List<String> modifier;

    /**
     * Creates a new instance of a class field
     *
     * @param field The field to create a new instance of
     */
    public Classfield(TypeScriptField field) {
        super(Objects.requireNonNull(field.getName()), field.getJSType());
        this.modifier = PsiUtil.getModifiers(field);
    }

    /**
     * Creates a new instance of a class field
     *
     * @param parameter The parameter to create a new instance of
     */
    public Classfield(TypeScriptParameter parameter) {
        super(Objects.requireNonNull(parameter.getName()), parameter.getJSType());
        this.modifier = PsiUtil.getModifiers(parameter);
    }


    /**
     * Checks if the field is public
     *
     * @return True if the field is public, false otherwise
     */
    public boolean isPublic(){
        return this.modifier.contains("public");
    }

    public List<String> getModifier() {
        return modifier;
    }

    @Override
    public String toString() {
        return this.name;
    }

    /**
     * Checks if the field matches another field
     *
     * @param field The field to compare to
     * @return True if the fields match, false otherwise
     */
    public boolean matches(Classfield field){
        if (Objects.requireNonNull(DataClumpSettings.getInstance().getState()).includeModifiersInDetection) {
            return this.name.equals(field.name)
                    && this.types.equals(field.types)
                    && this.modifier.equals(field.modifier);
        }
        return this.getName().equals(field.getName())
                && this.types.equals(field.types);
    }
}
