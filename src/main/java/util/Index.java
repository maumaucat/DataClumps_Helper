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

    /**
     * Maps a Property to a List of TypeScriptFunctions that use this Property as a parameter
     */
    private static HashMap<Property,List<TypeScriptFunction>> propertiesToFunctions;

    /**
     * Maps a Property to a List of TypeScriptClasses that use this Property as a field
     */
    private static HashMap<Property, List<TypeScriptClass>> propertiesToClasses;

    /**
     * Maps a TypeScriptClass to a List of Classfields that are in this class
     */
    private static HashMap<TypeScriptClass, List<Classfield>> classesToClassFields;

    /**
     * Maps a TypeScriptFunction to a List of Parameters that are in this function
     */
    private static HashMap<TypeScriptFunction, List<Parameter>> functionsToParameters;

    /**
     * Maps a qualified name to a TypeScriptClass
     */
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

    /**
     * Returns all the Parameters of a TypeScriptFunction
     *
     * @param psiFunction The TypeScriptFunction to get the Parameters from
     * @return A List of Parameters
     */
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

    /**
     * Returns the matching ClassField for a Property in a TypeScriptClass
     *
     * @param psiClass The TypeScriptClass to get the matching ClassField from
     * @param property The Property to get the matching ClassField for
     * @return The matching ClassField
     */
    public static Classfield getMatchingClassFieldForClass(TypeScriptClass psiClass, Property property) { //TODO duplicate to getField?
        List<Classfield> classfields = classesToClassFields.get(psiClass);
        for (Classfield classField : classfields) {
            if (classField.equals(property)) return classField;
        }
        CodeSmellLogger.warn("No matching ClassField found for " + property + " in " + psiClass);
        return null;
    }

    /**
     * Adds a TypeScriptClass to the index
     *
     * @param psiClass The TypeScriptClass to add
     */
    public static void addClass(TypeScriptClass psiClass) {

        if (psiClass.getQualifiedName() != null) {
            qualifiedNamesToClasses.put(psiClass.getQualifiedName(), psiClass);
        }

        classesToClassFields.put(psiClass, new ArrayList<>());
        List<Classfield> classfields = PsiUtil.getClassfields(psiClass);

        for (Classfield classField : classfields) {
            classesToClassFields.get(psiClass).add(classField);
            addClassForClassfield(psiClass, classField);
        }
    }

    /**
     * Adds a TypeScriptFunction to the index
     *
     * @param psiFunction The TypeScriptFunction to add
     */
    public static void addFunction(TypeScriptFunction psiFunction) {

        if (psiFunction.isConstructor()) return;

        functionsToParameters.put(psiFunction, new ArrayList<>());
        List<Parameter> parameters = getParameters(psiFunction);

        // iterate all Parameters in function
        for (Parameter parameter : parameters) {
            functionsToParameters.get(psiFunction).add(parameter);
            addFunctionForParameter(psiFunction, parameter);
        }
    }

    /**
     * Updates a TypeScriptFunction in the index
     *
     * @param psiFunction The TypeScriptFunction to update
     */
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

    /**
     * Updates a TypeScriptClass in the index
     *
     * @param psiClass The TypeScriptClass to update
     */
    public static void updateClass(TypeScriptClass psiClass) {

        // wenn die Klasse neu ist -> hinzufügen
        if (!classesToClassFields.containsKey(psiClass)) {
            addClass(psiClass);
            return;
        }

        if (psiClass.getQualifiedName() != null) {
            qualifiedNamesToClasses.put(psiClass.getQualifiedName(), psiClass);
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
            addClassForClassfield(psiClass, classField);
        }
    }

    /**
     * Adds a new Function for a Parameter to the index
     *
     * @param function The TypeScriptFunction to add
     * @param parameter The Parameter to add the function for
     */
    public static void addFunctionForParameter(TypeScriptFunction function, Parameter parameter) {
        if (propertiesToFunctions.containsKey(parameter)) {
            if (propertiesToFunctions.get(parameter).contains(function)) return;
            propertiesToFunctions.get(parameter).add(function);
        } else {
            List<TypeScriptFunction> functions = new ArrayList<>();
            functions.add(function);
            propertiesToFunctions.put(parameter, functions);
        }

    }

    /**
     * Adds a new Class for a ClassField to the index
     *
     * @param psiClass The TypeScriptClass to add
     * @param classField The ClassField to add the class for
     */
    public static void addClassForClassfield(TypeScriptClass psiClass, Classfield classField) {
        if (propertiesToClasses.containsKey(classField)) {
            if (propertiesToClasses.get(classField).contains(psiClass)) return;
            propertiesToClasses.get(classField).add(psiClass);
        } else {
            List<TypeScriptClass> classList = new ArrayList<>();
            classList.add(psiClass);
            propertiesToClasses.put(classField, classList);
        }
    }

    /**
     * Removes an element from the index
     *
     * @param element The element to remove
     */
    public static void removeElement(PsiElement element) {

        if (element instanceof TypeScriptFunction psiFunction) {
            for (Parameter parameter : functionsToParameters.get(psiFunction)) {
                propertiesToFunctions.get(parameter).remove(psiFunction);
            }
            functionsToParameters.remove(psiFunction);
        }

        if (element instanceof TypeScriptClass psiClass) {

            for (Classfield classField : classesToClassFields.get(psiClass)) {
                propertiesToClasses.get(classField).remove(psiClass);
            }
            classesToClassFields.remove(psiClass);
            qualifiedNamesToClasses.remove(psiClass.getQualifiedName());
        }


    }

    /**
     * Resets the index and rebuilds it
     *
     * @param project The project to reset the index for
     */
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

    /**
     * Prints the index
     */
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

    /**
     * Prints the index
     */
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

    /**
     * Prints the index
     */
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

    /**
     * Prints the index
     */
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

