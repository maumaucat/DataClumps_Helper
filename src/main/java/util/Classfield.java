package util;
import Settings.DataClumpSettings;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptField;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptParameter;

import java.util.List;

public class Classfield extends Property {

    private final List<String> modifier;

    public Classfield(TypeScriptField field) {
        super(field.getName(), field.getJSType());
        this.modifier = PsiUtil.getModifiers(field);
    }

    public Classfield(TypeScriptParameter parameter) {
        super(parameter.getName(), parameter.getJSType());
        this.modifier = PsiUtil.getModifiers(parameter);
    }


    public List<String> getModifier() {
        return modifier;
    }

    public boolean isPublic(){
        return this.modifier.contains("public");
    }

    @Override
    public String toString() {
        return this.name;
    }

    public boolean matches(Classfield field){
        if (DataClumpSettings.getInstance().getState().includeModifiersInDetection) {
            return this.name.equals(field.name)
                    && this.types.equals(field.types)
                    && this.modifier.equals(field.modifier);
        }
        return this.getName().equals(field.getName())
                && this.types.equals(field.types);
    }
}
