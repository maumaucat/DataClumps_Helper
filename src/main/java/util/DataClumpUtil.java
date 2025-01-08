package util;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.javascript.psi.JSParameterList;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptFunction;
import com.intellij.lang.javascript.psi.ecmal4.JSClass;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import dataclump.DataClumpDetection;
import org.jetbrains.annotations.NotNull;

/**
 * Utility class for methods related to the data clump detection and refactoring.
 */
public class DataClumpUtil {

    /**
     * Invokes the data clump inspection on the given element.
     *
     * @param psiElement the element to be inspected
     * @return the problems holder containing the inspection results or null if the element is invalid
     */
    public static ProblemsHolder invokeInspection(@NotNull PsiElement psiElement) {

        // the inspection can only be invoked on functions, parameter lists or classes
        if (!(psiElement instanceof TypeScriptFunction || psiElement instanceof JSParameterList || psiElement instanceof JSClass)) {
            CodeSmellLogger.warn("Invoking inspection on invalid element: " + PsiUtil.getQualifiedName(psiElement) +
                    " of type " + psiElement.getClass() + " will not do anything. Skipping.");
            return null;
        }

        // if the element is a function, get the parameter list
        if (psiElement instanceof TypeScriptFunction) {
            @NotNull PsiElement finalPsiElement = psiElement;
            psiElement = PsiUtil.runReadActionWithResult(() -> PsiTreeUtil.getChildOfType(finalPsiElement, JSParameterList.class));
        }

        assert psiElement != null;
        PsiFile psiFile = PsiUtil.runReadActionWithResult(psiElement::getContainingFile);
        Project project = PsiUtil.runReadActionWithResult(psiFile::getProject);

        ProblemsHolder problemsHolder = new ProblemsHolder(
                InspectionManager.getInstance(project),
                psiFile,
                false
        );

        DataClumpDetection inspection = new DataClumpDetection();
        PsiElementVisitor visitor = inspection.buildVisitor(problemsHolder, false);

        @NotNull PsiElement finalPsiElement1 = psiElement;
        ApplicationManager.getApplication().runReadAction(() -> finalPsiElement1.accept(visitor));

        return problemsHolder;
    }
}
