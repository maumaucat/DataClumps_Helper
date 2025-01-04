package util;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.javascript.psi.JSParameterList;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptFunction;
import com.intellij.lang.javascript.psi.ecmal4.JSClass;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import dataclump.DataClumpDetection;

public class DataClumpUtil {

    public static ProblemsHolder invokeInspection(PsiElement psiElement) {

        if (!(psiElement instanceof TypeScriptFunction || psiElement instanceof JSParameterList || psiElement instanceof JSClass)) {
            CodeSmellLogger.warn("Invoking inspection on invalid element: " + PsiUtil.getQualifiedName(psiElement) +
                    " of type " + psiElement.getClass() + " will not do anything. Skipping.");
            return null;
        }

        if (psiElement instanceof TypeScriptFunction) {
            psiElement = PsiTreeUtil.getChildOfType(psiElement, JSParameterList.class);
        }

        PsiFile psiFile = psiElement.getContainingFile();
        Project project = psiFile.getProject();

        ProblemsHolder problemsHolder = new ProblemsHolder(
                InspectionManager.getInstance(project),
                psiFile,
                false
        );

        DataClumpDetection inspection = new DataClumpDetection();
        PsiElementVisitor visitor = inspection.buildVisitor(problemsHolder, false);

        psiElement.accept(visitor);

        return problemsHolder;
    }
}
