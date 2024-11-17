import com.intellij.lang.javascript.psi.JSField;
import com.intellij.lang.javascript.psi.JSParameterListElement;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptField;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptParameter;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import util.ClassField;
import util.Index;
import com.intellij.codeInspection.*;
import com.intellij.lang.javascript.psi.JSElementVisitor;
import com.intellij.lang.javascript.psi.JSParameterList;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptClass;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptFunction;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import util.Parameter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


//TODO beautify delete when invalid

public class DataClumpDetection extends LocalInspectionTool {

    static final int MIN_DATACLUMPS = 2;

    private static final Logger LOG = Logger.getInstance(DataClumpDetection.class);

    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new JSElementVisitor() {

            @Override
            public void visitJSParameterList(@NotNull JSParameterList parameterList) {
                TypeScriptFunction psiFunction = PsiTreeUtil.getParentOfType(parameterList, TypeScriptFunction.class);
                Index.updateFunction(psiFunction);
                //Index.printParametersToFunctions();

                if (parameterList.getParameters().length >= MIN_DATACLUMPS) {
                    detectParameterParameterDataClumps(psiFunction, holder);
                }

                super.visitJSParameterList(parameterList);
            }

            @Override
            public void visitTypeScriptClass(@NotNull TypeScriptClass typeScriptClass) {
                Index.updateClass(typeScriptClass);
                //Index.printClassFieldsToClasses();

                if (typeScriptClass.getFields().length >= MIN_DATACLUMPS) {
                    detectFieldFieldDataClumps(typeScriptClass, holder);
                }

                super.visitTypeScriptClass(typeScriptClass);
            }
        };
    }


    public static void detectFieldFieldDataClumps(TypeScriptClass currentClass, ProblemsHolder holder) {
        HashMap<TypeScriptClass, List<ClassField>> potentialDataClumps = new HashMap<>();

        List<TypeScriptClass> invalidClasses = new ArrayList<>();

        // all classfields of the class
        for (ClassField classField : Index.getClassesToClassFields().get(currentClass)) {
            // all casses with that classfield
            for (TypeScriptClass otherClass : Index.getClassFieldsToClasses().get(classField)) {
                if (otherClass == currentClass) continue;
                // remove invalid classes
                if (!otherClass.isValid()) {
                    invalidClasses.add(otherClass);
                    continue;
                }

                if (!potentialDataClumps.containsKey(otherClass)) {
                    potentialDataClumps.put(otherClass, new ArrayList<>());
                }
                potentialDataClumps.get(otherClass).add(classField);
            }
        }

        for (TypeScriptClass invalidClass : invalidClasses) {
            Index.getClassesToClassFields().remove(invalidClass);
        }

        for (TypeScriptClass otherClass : potentialDataClumps.keySet()) {
            List<ClassField> matchingFields = potentialDataClumps.get(otherClass);
            // actually a  data clump
            if (matchingFields.size() >= MIN_DATACLUMPS) {

                // alle doppelten Fields makieren
                for (JSField psiField : currentClass.getFields()) {
                    if (matchingFields.contains(new ClassField((TypeScriptField) psiField))) {
                        holder.registerProblem(psiField, "Data Clump with Class " + otherClass.getName() +
                                ". Identified Fields: " + matchingFields + ".", new DataClumpRefactoring(currentClass, otherClass, new ArrayList<>(matchingFields)));
                    }
                }
            }
        }

    }

    public static void detectParameterParameterDataClumps(TypeScriptFunction currentFunction, ProblemsHolder holder) {

        HashMap<TypeScriptFunction, List<Parameter>> potentialDataClumps = new HashMap<>();

        List<TypeScriptFunction> invalidFunctions = new ArrayList<>();

        for (Parameter parameter : Index.getFunctionsToParameters().get(currentFunction)) {
            for (TypeScriptFunction otherFunction : Index.getParametersToFunctions().get(parameter)) {
                if (otherFunction == currentFunction) continue;
                // remove invalid classes
                if (!otherFunction.isValid()) {
                    invalidFunctions.add(otherFunction);
                    continue;
                }

                if (!potentialDataClumps.containsKey(otherFunction)) {
                    potentialDataClumps.put(otherFunction, new ArrayList<>());
                }
                potentialDataClumps.get(otherFunction).add(parameter);
            }
        }


        // remove all detected invalid functions
        for (TypeScriptFunction invalidFunction : invalidFunctions) {
            Index.getFunctionsToParameters().remove(invalidFunction);
        }

        for (TypeScriptFunction otherFunction : potentialDataClumps.keySet()) {
            List<Parameter> matchingParameter = potentialDataClumps.get(otherFunction);
            if (matchingParameter.size() >= MIN_DATACLUMPS) {
                for (JSParameterListElement psiParameter : currentFunction.getParameters())
                    if (matchingParameter.contains(new Parameter((TypeScriptParameter) psiParameter))) {
                    holder.registerProblem(psiParameter, "Data Clump with Function " +
                                    otherFunction.getName() +
                                    ". Identified Parameter: "
                                    + matchingParameter + ".",
                            new DataClumpRefactoring(currentFunction, otherFunction, new ArrayList<>(matchingParameter)));
                }
            }
        }
    }


}
