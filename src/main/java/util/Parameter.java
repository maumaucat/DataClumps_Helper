package util;
import com.intellij.lang.javascript.psi.JSType;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptParameter;

public class Parameter extends Property{

    public Parameter(TypeScriptParameter parameter) {
        super(parameter.getName(), parameter.getJSType());
    }

    public Parameter(String name, JSType type) {
        super(name, type);
    }

    @Override
    public String toString() {
        return this.name;
    }

}
