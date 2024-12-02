package util;
import com.intellij.lang.javascript.psi.JSType;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptParameter;

public class Parameter extends Property{

    public Parameter(TypeScriptParameter parameter) {
        super(parameter.getName(), parameter.getJSType().getTypeText());
    }

    public Parameter(String name, JSType type) {
        super(name, type.getTypeText());
    }

    @Override
    public String toString() {
        return "[Parameter: name=" + this.getName() + ", type=" + this.getType() + "]";
    }

}
