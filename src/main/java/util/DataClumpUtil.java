package util;

import com.intellij.lang.javascript.psi.JSParameterList;
import com.intellij.lang.javascript.psi.JSParameterListElement;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptParameter;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class DataClumpUtil {

    private static final Logger LOG = Logger.getInstance(DataClumpUtil.class);

    public static List<TypeScriptParameter> findMatchingParameter(JSParameterList list1, JSParameterList list2) {
        LOG.info("FIND MATCHING PARAMETER");

        List<TypeScriptParameter> matchingParameter = new ArrayList<>();

        for (JSParameterListElement parameter1 : list1.getParameters()){
            for (JSParameterListElement parameter2 : list2.getParameters()){

                // Fehlerbehandlung TODO: Fehlerkonzept entwickeln
                if (!(parameter1 instanceof TypeScriptParameter && parameter2 instanceof TypeScriptParameter)){
                    LOG.error("findMatchingParameter called with no TypeScriptParameter");
                    return null;
                }
                LOG.info("Parameter1: " + parameter1.toString() + " Parameter2: " + parameter2.toString());
                if (doParameterMatch((TypeScriptParameter) parameter1, (TypeScriptParameter) parameter2)) {
                    matchingParameter.add((TypeScriptParameter) parameter1);
                    LOG.info("Matching");
                } else {
                    LOG.info("Not Matching");
                }
            }
        }
        return matchingParameter;
    }

    public static boolean doParameterMatch(@NotNull TypeScriptParameter parameter1, @NotNull TypeScriptParameter parameter2) {
        boolean namesMatch = parameter1.getName() != null && parameter2.getName() != null &&
                parameter1.getName().equals(parameter2.getName());
        boolean typesMatch = parameter1.getJSType() != null && parameter2.getJSType() != null &&
                parameter1.getJSType().equals(parameter2.getJSType());
        return namesMatch && typesMatch;
    }

    
}
