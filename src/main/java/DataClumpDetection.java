import com.intellij.lang.javascript.psi.JSParameterListElement;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptParameter;
import com.intellij.lang.javascript.psi.ecmal4.JSClass;
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

import java.util.*;


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
            public void visitTypeScriptClass(@NotNull TypeScriptClass TypeScriptClass) {
                CodeSmellLogger.info("-------------------------------------------");
                CodeSmellLogger.info("Visiting Class " + TypeScriptClass.getName());
                CodeSmellLogger.info("-------------------------------------------");

                Index.updateClass(TypeScriptClass);
                if (PsiUtil.getFields(TypeScriptClass).size() >= MIN_DATACLUMPS) {
                    detectDataClumpForClass(TypeScriptClass, holder);
                }
                super.visitTypeScriptClass(TypeScriptClass);
            }
        };
    }

    public static void detectDataClumpForClass(TypeScriptClass currentClass, ProblemsHolder holder) {

        HashMap<TypeScriptClass, List<Classfield>> potentialFieldFieldDataClumps = new HashMap<>();
        HashMap<TypeScriptFunction, List<Classfield>> potentialFieldParameterDataClumps = new HashMap<>();

        // all Properties/Classfields the class has
        for (Classfield classfield : Index.getClassesToClassFields().get(currentClass)) {
            // alle Klassen die eine passende Property haben
            for (TypeScriptClass otherClass : Index.getPropertiesToClasses().get(classfield)) {
                if (otherClass == currentClass) continue;
                if (!otherClass.isValid()) continue; // TODO DELETE INVALID ENTRIES


                List<Classfield> classfieldList = Index.getClassesToClassFields().get(otherClass);
                if (classfieldList.get(classfieldList.indexOf(classfield)).matches(classfield)) {
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
                if (!checkClassFunctionPair(otherFunction, currentClass)) continue;

                // add to Map
                if (!potentialFieldParameterDataClumps.containsKey(otherFunction)) {
                    potentialFieldParameterDataClumps.put(otherFunction, new ArrayList<>());
                }
                potentialFieldParameterDataClumps.get(otherFunction).add(classfield);
            }
        }

        HashMap<Classfield, PsiElement> currentFields = PsiUtil.getFieldsToElement(currentClass);

        for (TypeScriptClass otherClass : potentialFieldFieldDataClumps.keySet()) {

            List<Classfield> matchingFields = potentialFieldFieldDataClumps.get(otherClass);
            if (matchingFields.size() >= MIN_DATACLUMPS) {
                for (Map.Entry<Classfield, PsiElement> entry : currentFields.entrySet()) {
                    if (matchingFields.contains(entry.getKey())) {
                        String otherClassName = "anonymous";
                        if (otherClass.getName() != null) {
                            otherClassName = otherClass.getName();
                        }
                        holder.registerProblem(entry.getValue(), "Data Clump with Class " + otherClassName +
                                ". Identified Fields: " + matchingFields + ".", new DataClumpRefactoring(currentClass, otherClass, new ArrayList<>(matchingFields)));
                    }
                }
            }
        }

        for (TypeScriptFunction otherFunction : potentialFieldParameterDataClumps.keySet()) {
            List<Classfield> matchingFields = potentialFieldParameterDataClumps.get(otherFunction);
            if (matchingFields.size() >= MIN_DATACLUMPS) {
                for (Map.Entry<Classfield, PsiElement> entry : currentFields.entrySet()) {
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
        HashMap<TypeScriptClass, List<Classfield>> potentialParameterFieldDataClumps = new HashMap<>();
        HashMap<TypeScriptFunction, List<Parameter>> potentialParameterParameterDataClumps = new HashMap<>();

        // all Properties/Parameter the class has
        for (Parameter parameter : Index.getFunctionsToParameters().get(currentFunction)) {

            // alle Funktionen, die eine passende Property haben
            for (TypeScriptFunction otherFunction : Index.getPropertiesToFunctions().get(parameter)) {
                if (!checkFunctions(currentFunction, otherFunction)) continue;

                // add to Map
                if (!potentialParameterParameterDataClumps.containsKey(otherFunction)) {
                    potentialParameterParameterDataClumps.put(otherFunction, new ArrayList<>());
                }
                potentialParameterParameterDataClumps.get(otherFunction).add(parameter);
            }

            // alle Klassen die eine passende Property haben
            if (!Index.getPropertiesToClasses().containsKey(parameter)) continue;
            for (TypeScriptClass otherClass : Index.getPropertiesToClasses().get(parameter)) {
                if (!checkClassFunctionPair(currentFunction, otherClass)) continue;

                // add to Map
                if (!potentialParameterFieldDataClumps.containsKey(otherClass)) {
                    potentialParameterFieldDataClumps.put(otherClass, new ArrayList<>());
                }

                Classfield classfield = Index.getMatchingClassFieldForClass(otherClass, parameter);
                potentialParameterFieldDataClumps.get(otherClass).add(classfield);

            }
        }

        for (TypeScriptClass otherClass : potentialParameterFieldDataClumps.keySet()) {
            List<Classfield> matchingParameter = potentialParameterFieldDataClumps.get(otherClass);
            if (matchingParameter.size() >= MIN_DATACLUMPS) {
                for (JSParameterListElement psiParameter : currentFunction.getParameters())
                    if (matchingParameter.contains(new Parameter((TypeScriptParameter) psiParameter))) {
                        String otherClassName = "anonymous";
                        if (otherClass.getName() != null) {
                            otherClassName = otherClass.getName();
                        }

                        holder.registerProblem(psiParameter, "Data Clump with Class " +
                                        otherClassName +
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

    private static boolean checkClassFunctionPair(TypeScriptFunction function, TypeScriptClass TypeScriptClass) {
        TypeScriptClass functionClass = PsiTreeUtil.getParentOfType(function, TypeScriptClass.class);
        return function.isValid() && TypeScriptClass.isValid()
                && !inSameHierarchy(functionClass, TypeScriptClass);

    }

    private static boolean checkFunctions(TypeScriptFunction function1, TypeScriptFunction function2) {

        return function1.isValid() && function2.isValid()
                && function1 != function2
                && !isOverridden(function1, function2);
    }

    private static boolean isOverridden(TypeScriptFunction function1, TypeScriptFunction function2) {
        TypeScriptClass class1 = PsiTreeUtil.getParentOfType(function1, TypeScriptClass.class);
        TypeScriptClass class2 = PsiTreeUtil.getParentOfType(function2, TypeScriptClass.class);


        if (class1 == class2 || !function1.getName().equals(function2.getName())) return false;

        List<TypeScriptClass> common = getCommonClassesInHierarchy(class1, class2);
        for (TypeScriptClass commonClass : common) {
            if (commonClass.findFunctionByName(function1.getName()) != null) {
                return true;
            }
        }
        return false;

    }


    private static boolean inSameHierarchy(TypeScriptClass class1, TypeScriptClass class2) {
        return !getCommonClassesInHierarchy(class1, class2).isEmpty();
    }

    private static List<TypeScriptClass> getCommonClassesInHierarchy(TypeScriptClass class1, TypeScriptClass class2) {
        if (class1 == null || class2 == null) return new ArrayList<>();

        CodeSmellLogger.info("Checking if " + class1.getName() + " and " + class2.getName() + " are in the same hierarchy.");

        List<TypeScriptClass> superClasses1 = resolveHierarchy(class1);
        List<TypeScriptClass> superClasses2 = resolveHierarchy(class2);

        CodeSmellLogger.info("Superclasses of " + class1.getName() + ": " + superClasses1);
        CodeSmellLogger.info("Superclasses of " + class2.getName() + ": " + superClasses2);

        superClasses1.retainAll(superClasses2);

        CodeSmellLogger.info("Common Superclasses: " + superClasses1);
        return superClasses1;
    }


    private static List<TypeScriptClass> resolveHierarchy(TypeScriptClass tsClass) {

        List<TypeScriptClass> superClasses = new ArrayList<>();
        superClasses.add(tsClass);

        for (JSClass psiClass : tsClass.getSuperClasses()) {
            if (!(psiClass instanceof TypeScriptClass)) {
                CodeSmellLogger.warn("Superclass is not a TypeScriptClass: " + psiClass.getText() + ". Type : " + psiClass.getClass());
            } else {
                superClasses.addAll(resolveHierarchy((TypeScriptClass) psiClass));
            }
        }

        for (JSClass psiClass : tsClass.getImplementedInterfaces()) {
            if (!(psiClass instanceof TypeScriptClass)) {
                CodeSmellLogger.warn("Interface is not a TypeScriptClass: " + psiClass.getText() + ". Type : " + psiClass.getClass());
            } else {
                superClasses.addAll(resolveHierarchy((TypeScriptClass) psiClass));
            }
        }

        return superClasses;
    }
}
