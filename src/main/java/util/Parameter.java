package util;

import com.intellij.lang.javascript.psi.JSType;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptParameter;

import java.util.Objects;

/**
 * Represents a parameter in a method
 */
public class Parameter extends Property {

    /**
     * Creates a new instance of a parameter
     *
     * @param parameter The parameter to create a new instance of
     */
    public Parameter(TypeScriptParameter parameter) {
        super(Objects.requireNonNull(PsiUtil.runReadActionWithResult(parameter::getName)), PsiUtil.runReadActionWithResult(parameter::getJSType));
    }

    @Override
    public String toString() {
        return this.name;
    }

}
