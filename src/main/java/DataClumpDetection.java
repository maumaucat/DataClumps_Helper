import com.intellij.lang.javascript.psi.JSField;
import com.intellij.lang.javascript.psi.JSParameterListElement;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptField;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptParameter;
import com.intellij.lang.javascript.psi.ecmal4.JSAttributeList;
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
import util.Property;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


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
                if (psiFunction.isConstructor()) {
                    /*TypeScriptClass psiClass = PsiTreeUtil.getParentOfType(psiFunction, TypeScriptClass.class);
                    Index.updateClass(psiClass);
                    detectDataClumpForField(psiClass, holder);*/ // dachte brauch ich aber scheinbar doch nicht und mit doppelte regestrierung *-*
                }
                else {
                    Index.updateFunction(psiFunction);
                    if (parameterList.getParameters().length >= MIN_DATACLUMPS) {
                        detectDataClumpForFunction(psiFunction, holder);
                    }
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
        LOG.info(currentClass.getName());

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

        HashMap<ClassField,PsiElement> currentFields = Index.getFieldsToElement(currentClass);
        LOG.info("Looking for dataclumps of " + currentClass);

        for (TypeScriptClass otherClass : potentialFieldFieldDataClumps.keySet()) {
            List<ClassField> matchingFields = potentialFieldFieldDataClumps.get(otherClass);
            if (matchingFields.size() >= MIN_DATACLUMPS) {
                LOG.info("DataClump with " + otherClass.getName());
                for (Map.Entry<ClassField,PsiElement> entry : currentFields.entrySet()) {
                    if (matchingFields.contains(entry.getKey())){

                        LOG.info("Registering problem for element: " + entry.getValue().getText());
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
        LOG.info(currentFunction.getName());
        if (currentFunction.isConstructor()) return;
        HashMap<TypeScriptClass, List<Parameter>> potentialParameterFieldDataClumps = new HashMap<>();
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
                potentialParameterFieldDataClumps.get(otherClass).add(parameter);

            }
        }

        for (TypeScriptClass otherClass : potentialParameterFieldDataClumps.keySet()) {
            List<Parameter> matchingParameter = potentialParameterFieldDataClumps.get(otherClass);
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

    public static List<TypeScriptClass> getClassesThatHaveAll(List<Property> properties) {
        LOG.info("Properties that shall match: " + properties.toString());
        List<TypeScriptClass> classes = new ArrayList<>();

        for (Property property : properties) {
            if (Index.getPropertiesToClasses().get(property) == null) continue;
            for (TypeScriptClass psiClass : Index.getPropertiesToClasses().get(property)) {
                if (!psiClass.isValid() || classes.contains(psiClass)) continue;
                LOG.info("Class to check:" + psiClass.getName());
                if (hasAll(psiClass, properties)) classes.add(psiClass);
            }
        }
        return classes;
    }

    // classfields must match
    public static boolean hasAll(TypeScriptClass psiClass, List<Property> properties) {
        List<ClassField> classProperties = Index.getClassesToClassFields().get(psiClass);
        for (Property property : properties) {
            LOG.info("Checking if class has "+ property.getName());
            if (!classProperties.contains(property)) return false;
            LOG.info("contained");
            if (property instanceof ClassField && !classProperties.get(classProperties.indexOf(property)).matches((ClassField) property)) return false;
            LOG.info("matches");
        }
        return true;
    }


}
