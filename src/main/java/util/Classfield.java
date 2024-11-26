package util;
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

    @Override
    public String toString() {
        return "[ClassField: name=" + this.getName() + ", type=" + this.getType() + ", modifiers=" + this.getModifier() + "]";
    }

    public boolean matches(Classfield field){
         return this.getName().equals(field.getName())
                && this.getType().equals(field.getType())
                && this.getModifier().equals(field.getModifier());
    }
}
