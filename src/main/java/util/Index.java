package util;
import com.intellij.lang.javascript.TypeScriptFileType;
import com.intellij.lang.javascript.psi.JSParameterListElement;
import com.intellij.lang.javascript.psi.ecma6.*;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptClass;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.*;

public class Index {

    private static HashMap<Property,List<TypeScriptFunction>> propertiesToFunctions;

    private static HashMap<Property, List<TypeScriptClass>> propertiesToClasses;

    private static HashMap<TypeScriptClass, List<Classfield>> classesToClassFields;

    private static HashMap<TypeScriptFunction, List<Parameter>> functionsToParameters;

    private static HashMap<String, TypeScriptClass> qualifiedNamesToClasses;

    public static HashMap<Property,List<TypeScriptFunction>> getPropertiesToFunctions() {
        return propertiesToFunctions;
    }

    public static HashMap<Property, List<TypeScriptClass>> getPropertiesToClasses() {
        return propertiesToClasses;
    }

    public static HashMap<TypeScriptClass, List<Classfield>> getClassesToClassFields() {
        return classesToClassFields;
    }

    public static HashMap<String, TypeScriptClass> getQualifiedNamesToClasses() {
        return qualifiedNamesToClasses;
    }

    public static HashMap<TypeScriptFunction, List<Parameter>> getFunctionsToParameters() {
        return functionsToParameters;
    }

    private static List<Parameter> getParameters(TypeScriptFunction psiFunction) {
        List<Parameter> parameters = new ArrayList<>();
        for (JSParameterListElement psiParameter : psiFunction.getParameters()) {
            // invalid / incomplete Parameter
            if (psiParameter.getName() == null || psiParameter.getJSType() == null) continue;
            Parameter parameter = new Parameter((TypeScriptParameter) psiParameter);
            parameters.add(parameter);
        }
        return parameters;
    }

    public static Classfield getMatchingClassFieldForClass(TypeScriptClass psiClass, Property property) { //TODO duplicate to getField?
        List<Classfield> classfields = classesToClassFields.get(psiClass);
        for (Classfield classField : classfields) {
            if (classField.equals(property)) return classField;
        }
        CodeSmellLogger.error("No matching ClassField found for " + property + " in " + psiClass, new IllegalArgumentException());
        return null;
    }

    public static void addClass(TypeScriptClass psiClass) {

        if (psiClass.getQualifiedName() != null) {
            qualifiedNamesToClasses.put(psiClass.getQualifiedName(), (TypeScriptClass) psiClass);
        }

        classesToClassFields.put(psiClass, new ArrayList<>());
        List<Classfield> classfields = PsiUtil.getClassfields(psiClass);

        for (Classfield classField : classfields) {
            classesToClassFields.get(psiClass).add(classField);
            addClassFieldForClass(psiClass, classField);
        }
    }

    public static void addFunction(TypeScriptFunction psiFunction) {

        if (psiFunction.isConstructor()) return;

        functionsToParameters.put(psiFunction, new ArrayList<Parameter>());
        List<Parameter> parameters = getParameters(psiFunction);

        // iterate all Parameters in function
        for (Parameter parameter : parameters) {
            functionsToParameters.get(psiFunction).add(parameter);
            addFunctionForParameter(psiFunction, parameter);
        }
    }

    public static void updateFunction(TypeScriptFunction psiFunction) {

        if (psiFunction.isConstructor()) return;

        if (!functionsToParameters.containsKey(psiFunction)) {
            addFunction(psiFunction);
            return;
        }

        List<Parameter> new_Parameters = getParameters(psiFunction);
        List<Parameter> toBeRemoved = new ArrayList<>(functionsToParameters.get(psiFunction));
        toBeRemoved.removeAll(new_Parameters);

        functionsToParameters.put(psiFunction, new_Parameters);

        for (Parameter parameter : toBeRemoved) {
            propertiesToFunctions.get(parameter).remove(psiFunction);
        }

        for (Parameter parameter: new_Parameters) {
            addFunctionForParameter(psiFunction, parameter);
        }
    }

    public static void updateClass(TypeScriptClass psiClass) {

        // wenn die Klasse neu ist -> hinzufügen
        if (!classesToClassFields.containsKey(psiClass)) {
            addClass(psiClass);
            return;
        }

        if (psiClass.getQualifiedName() != null) {
            qualifiedNamesToClasses.put(psiClass.getQualifiedName(), (TypeScriptClass) psiClass);
        }

        // alle aktuellen Klassenfelder der Klasse speichern
        List<Classfield> new_Fields = PsiUtil.getClassfields(psiClass);

        // alle alten KlassenFelder
        List<Classfield> toBeRemoved = new ArrayList<>(classesToClassFields.get(psiClass));
        // alle alten Klassenfelder ohne die, die auch im neuen sind
        toBeRemoved.removeAll(new_Fields);

        // eintrag in classes to classFields austauschen
        classesToClassFields.put(psiClass, new_Fields);

        for (Classfield classField : toBeRemoved) {
            propertiesToClasses.get(classField).remove(psiClass);
        }

        for (Classfield classField: new_Fields) {
            addClassFieldForClass(psiClass, classField);
        }
    }

    public static void addFunctionForParameter(TypeScriptFunction function, Parameter parameter) {
        if (propertiesToFunctions.containsKey(parameter)) {
            // schon eingetragen -> nothing to do
            if (propertiesToFunctions.get(parameter).contains(function)) return;
            propertiesToFunctions.get(parameter).add(function);
        } else { // ansonsten neuen Eintrag
            List<TypeScriptFunction> functions = new ArrayList<>();
            functions.add(function);
            propertiesToFunctions.put(parameter, functions);
        }

    }

    public static void addClassFieldForClass(TypeScriptClass psiClass, Classfield classField) {
        // wenn schon da add to list
        if (propertiesToClasses.containsKey(classField)) {
            // wenn bereits eingetragen nothing to do
            if (propertiesToClasses.get(classField).contains(psiClass)) return;
            propertiesToClasses.get(classField).add(psiClass);
        } else { // sonst neuer Eintrag
            List<TypeScriptClass> classList = new ArrayList<>();
            classList.add(psiClass);
            propertiesToClasses.put(classField, classList);
        }
    }

    public static void removeElement(PsiElement element) {

        if (element instanceof TypeScriptFunction psiFunction) {
            functionsToParameters.remove(psiFunction);
            for (Parameter parameter : getParameters(psiFunction)) {
                propertiesToFunctions.get(parameter).remove(psiFunction);
            }
            functionsToParameters.remove(psiFunction);
        }

        if (element instanceof TypeScriptClass psiClass) {
            classesToClassFields.remove(psiClass);
            for (Classfield classField : PsiUtil.getClassfields(psiClass)) {
                propertiesToClasses.get(classField).remove(psiClass);
            }
            classesToClassFields.remove(psiClass);
            qualifiedNamesToClasses.remove(psiClass.getQualifiedName());
        }


    }

    public static void resetIndex(Project project) {

        propertiesToFunctions = new HashMap<>();
        propertiesToClasses = new HashMap<>();
        classesToClassFields = new HashMap<>();
        functionsToParameters = new HashMap<>();
        qualifiedNamesToClasses = new HashMap<>();

        CodeSmellLogger.info("Building index...");

        // Read operations need this
        ApplicationManager.getApplication().runReadAction(() -> {

            PsiManager manager = PsiManager.getInstance(project);

            // Alle TypeScriptFiles
            for (VirtualFile virtualFile : FileTypeIndex.getFiles(TypeScriptFileType.INSTANCE, GlobalSearchScope.projectScope(project))) {
                PsiFile psiFile = manager.findFile(virtualFile);

                // iterate all functions in file
                for (TypeScriptFunction psiFunction : PsiTreeUtil.findChildrenOfType(psiFile, TypeScriptFunction.class)) {
                    addFunction(psiFunction);
                }

                Collection<PsiElement> allClasses = List.of(PsiTreeUtil.collectElements(psiFile, element ->
                        element instanceof TypeScriptClass
                ));

                for (PsiElement psiElement : allClasses) {
                    TypeScriptClass psiClass = (TypeScriptClass) psiElement;
                    addClass(psiClass);
                }
            }
        });

        CodeSmellLogger.info("Index was build.");

        printClassFieldsToClasses();
        printParametersToFunctions();
        printClassesToClassFields();
        printFunctionsToParameter();

    }

    public static void printClassFieldsToClasses() {
        ApplicationManager.getApplication().runReadAction(() -> {
        CodeSmellLogger.info("ClassesFields to Classes: ");
        for (Property classField : propertiesToClasses.keySet()) {
            List<TypeScriptClass> classList = propertiesToClasses.get(classField);
            StringBuilder classes = new StringBuilder("[");
            for (TypeScriptClass psiClass : classList) {
                if (psiClass.getName() != null) {
                    classes.append(psiClass.getName()).append(",");
                } else {
                    classes.append("anonymous").append(",");
                }
            }
            classes.append("]");
            CodeSmellLogger.info(classField + " : " + classes);
        }});
    }

    public static void printParametersToFunctions() {
        ApplicationManager.getApplication().runReadAction(() -> {
        CodeSmellLogger.info("Parameters to Functions: ");
        for (Property parameter : propertiesToFunctions.keySet()) {
            List<TypeScriptFunction> functions = propertiesToFunctions.get(parameter);
            StringBuilder parameters = new StringBuilder("[");
            for (TypeScriptFunction function : functions) {
                parameters.append(function.getName()).append(",");
            }
            parameters.append("]");
            CodeSmellLogger.info(parameter + " " + parameters);
        }
        });
    }

    public static void printFunctionsToParameter() {
        ApplicationManager.getApplication().runReadAction(() -> {
            CodeSmellLogger.info("Functions to Parameter: ");
            for (TypeScriptFunction function : functionsToParameters.keySet()) {
                StringBuilder parameters = new StringBuilder("[");
                for (Parameter parameter : functionsToParameters.get(function)) {
                    parameters.append(parameter.toString()).append(",");
                }
                parameters.append("]");
                CodeSmellLogger.info(function.getName() + " " + parameters);
            }
        });
    }

    public static void printClassesToClassFields() {
        ApplicationManager.getApplication().runReadAction(() -> {
            CodeSmellLogger.info("Classes to ClassFields: ");
            for (TypeScriptClass clazz : classesToClassFields.keySet()) {
                StringBuilder classFields = new StringBuilder("[");
                for (Classfield classField : classesToClassFields.get(clazz)) {
                    classFields.append(classField.toString()).append(",");
                }
                classFields.append("]");
                if (clazz.getName() != null){
                    CodeSmellLogger.info(clazz.getName() + " " + classFields);
                } else {
                    CodeSmellLogger.info("anonymous " + classFields);
                }
            }
        });
    }

}

