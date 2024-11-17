package util;

import com.intellij.lang.javascript.psi.ecma6.TypeScriptParameter;

public class Parameter extends Property{

    public Parameter(TypeScriptParameter parameter) {
        super(parameter.getName(), parameter.getJSType());
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Parameter otherParameter)) return false;
        return otherParameter.getName().equals(this.getName()) &&
                otherParameter.getType().equals(this.getType());
    }

    @Override
    public String toString() {
        return "[Parameter: name=" + this.getName() + ", type=" + this.getType() + "]";
    }
}
