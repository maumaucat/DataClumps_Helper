import com.intellij.lang.javascript.psi.JSParameterListElement;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptParameter;
import com.intellij.psi.PsiElement;
import util.*;
import com.intellij.codeInspection.*;
import com.intellij.lang.javascript.psi.JSElementVisitor;
import com.intellij.lang.javascript.psi.JSParameterList;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptClass;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptFunction;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


//TODO Delete Invalid Classes and Functions to save storage

public class DataClumpDetection extends LocalInspectionTool {

    static final int MIN_DATACLUMPS = 2;

    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new JSElementVisitor() {

            @Override
            public void visitJSParameterList(@NotNull JSParameterList parameterList) {
                TypeScriptFunction psiFunction = PsiTreeUtil.getParentOfType(parameterList, TypeScriptFunction.class);
                if (psiFunction.isConstructor()) return;
                Index.updateFunction(psiFunction);
                if (parameterList.getParameters().length >= MIN_DATACLUMPS) {
                    detectDataClumpForFunction(psiFunction, holder);
                }
                super.visitJSParameterList(parameterList);
            }

            @Override
            public void visitTypeScriptClass(@NotNull TypeScriptClass typeScriptClass) {
                Index.updateClass(typeScriptClass);
                if (typeScriptClass.getFields().length >= MIN_DATACLUMPS) {
                    detectDataClumpForField(typeScriptClass, holder);
                }
                super.visitTypeScriptClass(typeScriptClass);
            }
        };
    }


    public static void detectDataClumpForField(TypeScriptClass currentClass, ProblemsHolder holder) {

        HashMap<TypeScriptClass, List<ClassField>> potentialFieldFieldDataClumps = new HashMap<>();
        HashMap<TypeScriptFunction, List<ClassField>> potentialFieldParameterDataClumps = new HashMap<>();

        // all Properties/Classfields the class has
        for (ClassField classfield : Index.getClassesToClassFields().get(currentClass)) {
            // alle Klassen die eine passende Property haben
            for (TypeScriptClass otherClass : Index.getPropertiesToClasses().get(classfield)) {
                if (otherClass == currentClass) continue;
                if(!otherClass.isValid()) continue; // TODO DELETE INVALID ENTRIES

                List<ClassField> classFieldList = Index.getClassesToClassFields().get(otherClass);
                if (classFieldList.get(classFieldList.indexOf(classfield)).matches(classfield)) {
                    // add to Map
                    if (!potentialFieldFieldDataClumps.containsKey(otherClass)) {
                        potentialFieldFieldDataClumps.put(otherClass, new ArrayList<>());
                    }
                    potentialFieldFieldDataClumps.get(otherClass).add(classfield);
                }

            }

            // alle Funktionen, die eine passende Property haben
            if (!Index.getPropertiesToFunctions().containsKey(classfield)) continue;
            for (TypeScriptFunction otherFunction : Index.getPropertiesToFunctions().get(classfield)) {
                if (!otherFunction.isValid()) continue; // TODO DELETE INVALID ENTRIES

                // add to Map
                if (!potentialFieldParameterDataClumps.containsKey(otherFunction)) {
                    potentialFieldParameterDataClumps.put(otherFunction, new ArrayList<>());
                }
                potentialFieldParameterDataClumps.get(otherFunction).add(classfield);
            }
        }

        HashMap<ClassField,PsiElement> currentFields = PsiUtil.getFieldsToElement(currentClass);

        for (TypeScriptClass otherClass : potentialFieldFieldDataClumps.keySet()) {
            List<ClassField> matchingFields = potentialFieldFieldDataClumps.get(otherClass);
            if (matchingFields.size() >= MIN_DATACLUMPS) {
                for (Map.Entry<ClassField,PsiElement> entry : currentFields.entrySet()) {
                    if (matchingFields.contains(entry.getKey())){
                        holder.registerProblem(entry.getValue(), "Data Clump with Class " + otherClass.getName() +
                                ". Identified Fields: " + matchingFields + ".", new DataClumpRefactoring(currentClass, otherClass, new ArrayList<>(matchingFields)));
                    }
                }
            }
        }

        for (TypeScriptFunction otherFunction : potentialFieldParameterDataClumps.keySet()) {
            List<ClassField> matchingFields = potentialFieldParameterDataClumps.get(otherFunction);
            if (matchingFields.size() >= MIN_DATACLUMPS) {
                for (Map.Entry<ClassField,PsiElement> entry : currentFields.entrySet()) {
                    if (matchingFields.contains(entry.getKey())) {
                        holder.registerProblem(entry.getValue(), "Data Clump with Function " + otherFunction.getName() +
                                ". Identified Fields: " + matchingFields + ".", new DataClumpRefactoring(currentClass, otherFunction, new ArrayList<>(matchingFields)));
                    }
                }

            }
        }
    }


    public static void detectDataClumpForFunction(TypeScriptFunction currentFunction, ProblemsHolder holder) {
        if (currentFunction.isConstructor()) return;
        HashMap<TypeScriptClass, List<ClassField>> potentialParameterFieldDataClumps = new HashMap<>();
        HashMap<TypeScriptFunction, List<Parameter>> potentialParameterParameterDataClumps = new HashMap<>();

        // all Properties/Parameter the class has
        for (Parameter parameter : Index.getFunctionsToParameters().get(currentFunction)) {

            // alle Funktionen, die eine passende Property haben
            for (TypeScriptFunction otherFunction : Index.getPropertiesToFunctions().get(parameter)) {
                if (otherFunction == currentFunction) continue;
                if (!otherFunction.isValid()) continue; // TODO DELETE INVALID ENTRIES

                // add to Map
                if (!potentialParameterParameterDataClumps.containsKey(otherFunction)) {
                    potentialParameterParameterDataClumps.put(otherFunction, new ArrayList<>());
                }
                potentialParameterParameterDataClumps.get(otherFunction).add(parameter);
            }

            // alle Klassen die eine passende Property haben
            if (!Index.getPropertiesToClasses().containsKey(parameter)) continue;
            for (TypeScriptClass otherClass : Index.getPropertiesToClasses().get(parameter)) {
                if(!otherClass.isValid()) continue; // TODO DELETE INVALID ENTRIES

                // add to Map
                if (!potentialParameterFieldDataClumps.containsKey(otherClass)) {
                    potentialParameterFieldDataClumps.put(otherClass, new ArrayList<>());
                }

                ClassField classfield = Index.getMatchingClassFieldForClass(otherClass, parameter);
                potentialParameterFieldDataClumps.get(otherClass).add(classfield);

            }
        }

        for (TypeScriptClass otherClass : potentialParameterFieldDataClumps.keySet()) {
            List<ClassField> matchingParameter = potentialParameterFieldDataClumps.get(otherClass);
            if (matchingParameter.size() >= MIN_DATACLUMPS) {
                for (JSParameterListElement psiParameter : currentFunction.getParameters())
                    if (matchingParameter.contains(new Parameter((TypeScriptParameter) psiParameter))) {
                        holder.registerProblem(psiParameter, "Data Clump with Class " +
                                        otherClass.getName() +
                                        ". Identified Parameter: "
                                        + matchingParameter + ".",
                                new DataClumpRefactoring(currentFunction, otherClass, new ArrayList<>(matchingParameter)));
                    }
                }
            }

        for (TypeScriptFunction otherFunction : potentialParameterParameterDataClumps.keySet()) {
            List<Parameter> matchingParameter = potentialParameterParameterDataClumps.get(otherFunction);
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
