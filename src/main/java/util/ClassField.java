package util;
import com.intellij.lang.javascript.psi.JSType;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptField;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptParameter;

public class ClassField extends Property {

    private final String visibility;

    public ClassField(TypeScriptField field) {
        super(field.getName(), field.getJSType());
        this.visibility = field.getAccessType().toString();
    }

    public ClassField(TypeScriptParameter parameter) {
        super(parameter.getName(), parameter.getJSType());
        this.visibility = parameter.getAccessType().toString();
    }

    public String getVisibility() {
        return visibility;
    }

    @Override
    public String toString() {
        return "[ClassField: name=" + this.getName() + ", type=" + this.getType() + ", visibility=" + this.getVisibility() + "]";
    }

    public boolean matches(ClassField field) {
        return this.getName().equals(field.getName())
                && this.getType().equals(field.getType())
                && this.getVisibility().equals(field.getVisibility());
    }
}
