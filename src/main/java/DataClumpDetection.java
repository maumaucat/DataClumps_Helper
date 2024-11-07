import util.DataClumpUtil;
import util.Index;
import com.intellij.codeInspection.*;
import com.intellij.lang.javascript.psi.JSElementVisitor;
import com.intellij.lang.javascript.psi.JSParameterList;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptClass;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptFunction;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptParameter;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.openapi.diagnostic.Logger;

import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class DataClumpDetection extends LocalInspectionTool {

    final int MIN_DATACLUMPS = 2;

    private static final Logger LOG = Logger.getInstance(DataClumpDetection.class);

    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new JSElementVisitor() {

            @Override
            public void visitJSParameterList(@NotNull JSParameterList parameterList) {



                if (parameterList.getParameters().length < MIN_DATACLUMPS) return;
                checkForParameterParameterDataClumps(parameterList, holder);
                super.visitJSParameterList(parameterList);
            }

            @Override
            public void visitTypeScriptClass(@NotNull TypeScriptClass typeScriptClass) {

                super.visitTypeScriptClass(typeScriptClass);
            }

        };

    }

    private void checkForParameterParameterDataClumps(JSParameterList parameterList, ProblemsHolder holder) {
        LOG.info("CHECKING PARAMETER PARAMETER");
        LOG.info("AKTUELLE Funktion: " + PsiTreeUtil.getParentOfType(parameterList, TypeScriptFunction.class).getName());
        for (TypeScriptFunction compareToFunction : Index.getPsiFunctions().values()) {

            LOG.info("VERGLEICH FUNKTION: " + compareToFunction.getName());
            JSParameterList compareToParameterList =  compareToFunction.getParameterList();

            if (parameterList == compareToParameterList) continue;

            List<TypeScriptParameter> matchingParameter = DataClumpUtil.findMatchingParameter(parameterList, compareToParameterList);


            if (matchingParameter.size() >= MIN_DATACLUMPS) {
                LOG.info("MATCHING PARAMETER: " + matchingParameter);

                holder.registerProblem(parameterList, "Data Clump: " +
                        "Funktion: " + compareToFunction.getName()
                        + " Parameter: " + matchingParameter, ProblemHighlightType.WARNING);
            }
        }
    }

}
