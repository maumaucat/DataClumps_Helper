package util;

import Settings.DataClumpSettings;
import com.google.rpc.Code;
import com.intellij.lang.javascript.TypeScriptFileType;
import com.intellij.lang.javascript.psi.JSParameterListElement;
import com.intellij.lang.javascript.psi.ecma6.*;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptClass;
import com.intellij.lang.javascript.psi.ecmal4.JSClass;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import evoluation.DiagnosticTool;

import java.util.*;

public class Index {

    /**
     * Indicates if the index was built and is ready to use
     */
    private static boolean indexBuilt = false;

    /**
     * List of listeners that are notified when the index is built
     */
    private static final List<Runnable> listeners = new ArrayList<>();

    /**
     * The project to build the index for
     */
    private static Project project;

    /**
     * Maps a Property to a List of TypeScriptFunctions that use this Property as a parameter
     */
    private static HashMap<Property, List<TypeScriptFunction>> propertiesToFunctions;
    /**
     * Maps a Property to a List of TypeScriptClasses that use this Property as a field
     */
    private static HashMap<Property, List<JSClass>> propertiesToClasses;
    /**
     * Maps a TypeScriptClass to a List of Classfields that are in this class
     */
    private static HashMap<JSClass, List<Classfield>> classesToClassFields;
    /**
     * Maps a TypeScriptFunction to a List of Parameters that are in this function
     */
    private static HashMap<TypeScriptFunction, List<Parameter>> functionsToParameters;
    /**
     * Maps a qualified name to a class or interface
     */
    private static HashMap<String, JSClass> qualifiedNamesToClasses;
    /**
     * Maps a function name to a list of classes that contain this function
     */
    private static HashMap<String, List<JSClass>> functionNamesToClasses;

    public static boolean isIndexBuilt() {
        return indexBuilt;
    }

    public static HashMap<String, List<JSClass>> getFunctionNamesToClasses() {
        return functionNamesToClasses;
    }

    public static HashMap<Property, List<TypeScriptFunction>> getPropertiesToFunctions() {
        return propertiesToFunctions;
    }

    public static HashMap<Property, List<JSClass>> getPropertiesToClasses() {
        return propertiesToClasses;
    }

    public static HashMap<JSClass, List<Classfield>> getClassesToClassFields() {
        return classesToClassFields;
    }

    public static HashMap<String, JSClass> getQualifiedNamesToClasses() {
        return qualifiedNamesToClasses;
    }

    public static HashMap<TypeScriptFunction, List<Parameter>> getFunctionsToParameters() {
        return functionsToParameters;
    }

    public static Project getProject() {
        return project;
    }

    /**
     * Returns all the Parameters of a TypeScriptFunction
     *
     * @param psiFunction The TypeScriptFunction to get the Parameters from
     * @return A List of Parameters
     */
    //TODO MOVE TO PSIUTIL
    private static List<Parameter> getParameters(TypeScriptFunction psiFunction) {
        List<Parameter> parameters = new ArrayList<>();
        ApplicationManager.getApplication().runReadAction(() -> {
            for (JSParameterListElement psiParameter : psiFunction.getParameters()) {
                // invalid / incomplete Parameter
                if (!(psiParameter instanceof TypeScriptParameter) || psiParameter.getName() == null || psiParameter.getJSType() == null)
                    continue;
                Parameter parameter = new Parameter((TypeScriptParameter) psiParameter);
                parameters.add(parameter);
            }
        });
        return parameters;
    }

    /**
     * Returns the matching ClassField for a Property in a TypeScriptClass
     *
     * @param psiClass The TypeScriptClass to get the matching ClassField from
     * @param property The Property to get the matching ClassField for
     * @return The matching ClassField
     */
    public static Classfield getMatchingClassFieldForClass(JSClass psiClass, Property property) { //TODO duplicate to getField?
        List<Classfield> classfields = classesToClassFields.get(psiClass);
        for (Classfield classField : classfields) {
            if (classField.equals(property)) return classField;
        }
        CodeSmellLogger.warn("No matching ClassField found for " + property + " in " + PsiUtil.getQualifiedName(psiClass));
        return null;
    }

    /**
     * Adds a TypeScriptClass to the index
     *
     * @param psiClass The TypeScriptClass to add
     */
    public static void addClass(JSClass psiClass) {

        List<Classfield> classfields = PsiUtil.getClassfields(psiClass);

        // if the class has less than the minimum number of properties, it does not need to be added to the index (no data clump possible)
        // done to reduce the size of the index and to improve performance
        if (classfields.size() < Objects.requireNonNull(DataClumpSettings.getInstance().getState()).minNumberOfProperties)
            return;


        String qualifiedName = PsiUtil.runReadActionWithResult(psiClass::getQualifiedName);
        if (qualifiedName != null) {
            qualifiedNamesToClasses.put(qualifiedName, psiClass);
        }

        classesToClassFields.put(psiClass, new ArrayList<>());

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

        if (PsiUtil.runReadActionWithResult(psiFunction::isConstructor)) return;

        addClassToFunctionName(psiFunction);

        List<Parameter> parameters = getParameters(psiFunction);
        // if the function has less than the minimum number of properties, it does not need to be added to the index (no data clump possible)
        // done to reduce the size of the index and to improve performance
        if (parameters.size() < Objects.requireNonNull(DataClumpSettings.getInstance().getState()).minNumberOfProperties)
            return;
        functionsToParameters.put(psiFunction, new ArrayList<>());

        // iterate all Parameters in function
        for (Parameter parameter : parameters) {
            functionsToParameters.get(psiFunction).add(parameter);
            addFunctionForParameter(psiFunction, parameter);
        }
    }

    /**
     * Adds the containing class to the function name in the index
     *
     * @param psiFunction The TypeScriptFunction to add the class to
     */
    private static void addClassToFunctionName(TypeScriptFunction psiFunction) {
        if (PsiUtil.runReadActionWithResult(psiFunction::getName) != null) {
            JSClass containingClass = PsiUtil.runReadActionWithResult(()->PsiTreeUtil.getParentOfType(psiFunction, JSClass.class));
            if (containingClass != null) {
                if (functionNamesToClasses.containsKey(psiFunction.getName())) {
                    functionNamesToClasses.get(psiFunction.getName()).add(containingClass);
                } else {
                    List<JSClass> classes = new ArrayList<>();
                    classes.add(containingClass);
                    functionNamesToClasses.put(psiFunction.getName(), classes);
                }
            }
        }
    }

    /**
     * Updates a TypeScriptFunction in the index
     *
     * @param psiFunction The TypeScriptFunction to update
     */
    public static void updateFunction(TypeScriptFunction psiFunction) {

        if (PsiUtil.runReadActionWithResult(psiFunction::isConstructor)) return;
        addClassToFunctionName(psiFunction);


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

        for (Parameter parameter : new_Parameters) {
            addFunctionForParameter(psiFunction, parameter);
        }
    }

    /**
     * Updates a TypeScriptClass in the index
     *
     * @param psiClass The TypeScriptClass to update
     */
    public static void updateClass(JSClass psiClass) {

        // wenn die Klasse neu ist -> hinzufügen
        if (!classesToClassFields.containsKey(psiClass)) {
            addClass(psiClass);
            return;
        }

        String qualifiedName = PsiUtil.runReadActionWithResult(psiClass::getQualifiedName);
        if (qualifiedName != null) {
            qualifiedNamesToClasses.put(qualifiedName, psiClass);
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

        for (Classfield classField : new_Fields) {
            addClassForClassfield(psiClass, classField);
        }
    }

    /**
     * Adds a new Function for a Parameter to the index
     *
     * @param function  The TypeScriptFunction to add
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
     * @param psiClass   The TypeScriptClass to add
     * @param classField The ClassField to add the class for
     */
    public static void addClassForClassfield(JSClass psiClass, Classfield classField) {
        if (propertiesToClasses.containsKey(classField)) {
            if (propertiesToClasses.get(classField).contains(psiClass)) return;
            propertiesToClasses.get(classField).add(psiClass);
        } else {
            List<JSClass> classList = new ArrayList<>();
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
            JSClass psiClass = PsiTreeUtil.getParentOfType(psiFunction, JSClass.class);
            if (psiClass != null) {
                functionNamesToClasses.get(psiFunction.getName()).remove(psiClass);
            }
        }

        if (element instanceof JSClass psiClass) {

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

        long startTime;
        if (DiagnosticTool.DIAGNOSTIC_MODE) {
            startTime = System.nanoTime();
        } else {
            startTime = 0;
        }

        indexBuilt = false;
        Index.project = project;

        propertiesToFunctions = new HashMap<>();
        propertiesToClasses = new HashMap<>();
        classesToClassFields = new HashMap<>();
        functionsToParameters = new HashMap<>();
        qualifiedNamesToClasses = new HashMap<>();
        functionNamesToClasses = new HashMap<>();


        ApplicationManager.getApplication().executeOnPooledThread(() -> {

            CodeSmellLogger.info("Building index...");
            PsiManager manager = PsiManager.getInstance(project);

            // Alle TypeScriptFiles
            for (VirtualFile virtualFile : PsiUtil.runReadActionWithResult(() -> FileTypeIndex.getFiles(TypeScriptFileType.INSTANCE, GlobalSearchScope.projectScope(project)))) {
                PsiFile psiFile = PsiUtil.runReadActionWithResult(() -> manager.findFile(virtualFile));

                Collection<TypeScriptFunction> allFunctions = new ArrayList<>();
                Collection<PsiElement> allClasses = new ArrayList<>();
                Collection<PsiElement> allInterfaces = new ArrayList<>();

                // read all functions, classes and interfaces from file
                ApplicationManager.getApplication().runReadAction(() -> {
                    allFunctions.addAll(PsiTreeUtil.findChildrenOfType(psiFile, TypeScriptFunction.class));
                    allClasses.addAll(List.of(PsiTreeUtil.collectElements(psiFile, element ->
                            element instanceof TypeScriptClass
                    )));
                    allInterfaces.addAll(List.of(PsiTreeUtil.collectElements(psiFile, element ->
                            element instanceof TypeScriptInterface
                    )));
                });


                // iterate all functions in file
                for (TypeScriptFunction psiFunction : allFunctions) {
                    addFunction(psiFunction);
                }
                //iterate all classes in file
                for (PsiElement psiElement : allClasses) {
                    TypeScriptClass psiClass = (TypeScriptClass) psiElement;
                    addClass(psiClass);
                }
                //iterate all interfaces in file
                for (PsiElement psiElement : allInterfaces) {
                    TypeScriptInterface psiInterface = (TypeScriptInterface) psiElement;
                    addClass(psiInterface);
                }

            }


            indexBuilt = true;

            if (DiagnosticTool.DIAGNOSTIC_MODE) {
                long endTime = System.nanoTime();
                long duration = endTime - startTime;
                DiagnosticTool.addMeasurement(new DiagnosticTool.IndexMeasurement(project, duration));
            }

            notifyListeners();
            CodeSmellLogger.info("Index was build.");
        });

    }


    public static synchronized void addIndexBuildListener(Runnable listener) {
        if (indexBuilt) {
            listener.run();
        } else {
            listeners.add(listener);
        }
    }

    private static synchronized void notifyListeners() {
        for (Runnable listener : listeners) {
            listener.run();
        }
        listeners.clear();
    }

    public static void printSize() {
        CodeSmellLogger.info("Index size: " +
                propertiesToFunctions.size() + " " +
                propertiesToClasses.size() + " " +
                classesToClassFields.size() + " " +
                functionsToParameters.size() + " " +
                qualifiedNamesToClasses.size() + " " +
                functionNamesToClasses.size());
    }

    /**
     * Prints the index
     */
    public static void printClassFieldsToClasses() {
        ApplicationManager.getApplication().runReadAction(() -> {
            CodeSmellLogger.info("ClassesFields to Classes: ");
            for (Property classField : propertiesToClasses.keySet()) {
                List<JSClass> classList = propertiesToClasses.get(classField);
                StringBuilder classes = new StringBuilder("[");
                for (JSClass psiClass : classList) {
                    if (psiClass.getName() != null) {
                        classes.append(psiClass.getName()).append(",");
                    } else {
                        classes.append("anonymous").append(",");
                    }
                }
                classes.append("]");
                CodeSmellLogger.info("Classfield: " + classField + " : " + classes);
            }
        });
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
                CodeSmellLogger.info("Parameter: " + parameter + " " + parameters);
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
                CodeSmellLogger.info("Function: " + function.getName() + " " + parameters);
            }
        });
    }

    /**
     * Prints the index
     */
    public static void printClassesToClassFields() {
        ApplicationManager.getApplication().runReadAction(() -> {
            CodeSmellLogger.info("Classes to ClassFields: ");
            for (JSClass psiClass : classesToClassFields.keySet()) {
                StringBuilder classFields = new StringBuilder("[");
                for (Classfield classField : classesToClassFields.get(psiClass)) {
                    classFields.append(classField.toString()).append(",");
                }
                classFields.append("]");
                if (psiClass.getName() != null) {
                    CodeSmellLogger.info("Class: " + psiClass.getName() + " " + classFields);
                } else {
                    CodeSmellLogger.info("Class: anonymous " + classFields);
                }
            }
        });
    }
}

