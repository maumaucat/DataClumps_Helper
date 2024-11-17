package util;

import com.intellij.lang.ecmascript6.psi.impl.ES6FieldStatementImpl;
import com.intellij.lang.javascript.TypeScriptFileType;
import com.intellij.lang.javascript.psi.JSField;
import com.intellij.lang.javascript.psi.JSParameterListElement;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptClass;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptField;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptFunction;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptParameter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiTreeChangeListener;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import listener.PsiChangeListener;

import java.lang.reflect.Field;
import java.util.*;

public class Index {

    private static HashMap<Parameter,List<TypeScriptFunction>> parametersToFunctions;

    private static HashMap<ClassField, List<TypeScriptClass>> classFieldsToClasses;

    private static HashMap<TypeScriptClass, List<ClassField>> classesToClassFields;

    private static HashMap<TypeScriptFunction, List<Parameter>> functionsToParameters;

    private static final Logger LOG = Logger.getInstance(Index.class);

    public static HashMap<Parameter,List<TypeScriptFunction>> getParametersToFunctions() {
        return parametersToFunctions;
    }

    public static HashMap<ClassField, List<TypeScriptClass>> getClassFieldsToClasses() {
        return classFieldsToClasses;
    }

    public static HashMap<TypeScriptClass, List<ClassField>> getClassesToClassFields() {
        return classesToClassFields;
    }

    public static HashMap<TypeScriptFunction, List<Parameter>> getFunctionsToParameters() {
        return functionsToParameters;
    }

    public static void addClass(TypeScriptClass psiClass) {

        classesToClassFields.put(psiClass, new ArrayList<>());

        // iterate all FieldStatements
        for (JSField field : psiClass.getFields()) {
            // error handling
            if (!(field instanceof TypeScriptField)) continue;
            if (field.getName() == null || field.getJSType() == null) continue;

            ClassField classField = new ClassField((TypeScriptField) field);
            classesToClassFields.get(psiClass).add(classField);
            addClassFieldForClass(psiClass, classField);

        }
    }

    public static void addFunction(TypeScriptFunction psiFunction) {

        functionsToParameters.put(psiFunction, new ArrayList<Parameter>());

        // iterate all Parameters in function
        for (JSParameterListElement psiParameter : psiFunction.getParameters()) {
            //TODO Can this happen?
            if (!(psiParameter instanceof TypeScriptParameter)) continue;
            if (psiParameter.getName() == null || psiParameter.getJSType() == null) continue;
            // Parameter schon im index -> Funktion speichern in vorhandener Liste
            Parameter parameter = new Parameter((TypeScriptParameter) psiParameter);
            functionsToParameters.get(psiFunction).add(parameter);

            addFunctionForParameter(psiFunction, parameter);

        }
    }

    public static void updateFunction(TypeScriptFunction psiFunction) {

        if (!functionsToParameters.containsKey(psiFunction)) {
            addFunction(psiFunction);
            return;
        }

        List<Parameter> new_Parameters = new ArrayList<>();

        for (JSParameterListElement psiParameter : psiFunction.getParameters()) {
            // invalid / incomplete Parameter
            if (psiParameter.getName() == null || psiParameter.getJSType() == null) continue;
            Parameter parameter = new Parameter((TypeScriptParameter) psiParameter);
            new_Parameters.add(parameter);
        }

        List<Parameter> toBeRemoved = new ArrayList<>(functionsToParameters.get(psiFunction));
        toBeRemoved.removeAll(new_Parameters);

        List<Parameter> toBeAdded = new ArrayList<>(new_Parameters);
        toBeAdded.removeAll(functionsToParameters.get(psiFunction));

        functionsToParameters.put(psiFunction, new_Parameters);

        for (Parameter parameter : toBeRemoved) {
            parametersToFunctions.get(parameter).remove(psiFunction);
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

        // alle aktuellen Klassenfelder der Klasse speichern
        List<ClassField> new_Fields = new ArrayList<>();
        for (JSField psiField : psiClass.getFields()) {
            // invalid / incomplete Parameter
            if (psiField.getName() == null || psiField.getJSType() == null) continue;
            ClassField classField = new ClassField((TypeScriptField) psiField);
            new_Fields.add(classField);
        }

        // alle alten KlassenFelder
        List<ClassField> toBeRemoved = new ArrayList<>(classesToClassFields.get(psiClass));
        // alle alten Klassenfelder ohne die, die auch im neuen sind
        toBeRemoved.removeAll(new_Fields);

        // alle neuen KlassenFelder
        List<ClassField> toBeAdded = new ArrayList<>(new_Fields);
        //Ohne die, die eh schon da sind
        toBeAdded.removeAll(classesToClassFields.get(psiClass));

        classesToClassFields.put(psiClass, new_Fields);

        for (ClassField classField : toBeRemoved) {
            classFieldsToClasses.get(classField).remove(psiClass);
        }

        for (ClassField classField: new_Fields) {
            addClassFieldForClass(psiClass, classField);
        }
    }

    public static void addFunctionForParameter(TypeScriptFunction function, Parameter parameter) {
        if (parametersToFunctions.containsKey(parameter)) {
            // schon eingetragen -> nothing to do
            if (parametersToFunctions.get(parameter).contains(function)) return;
            parametersToFunctions.get(parameter).add(function);
        } else { // ansonsten neuen Eintrag
            List<TypeScriptFunction> functions = new ArrayList<>();
            functions.add(function);
            parametersToFunctions.put(parameter, functions);
        }

    }

    public static void addClassFieldForClass(TypeScriptClass clazz, ClassField classField) {
        // wenn schon da add to list
        if (classFieldsToClasses.containsKey(classField)) {
            // wenn bereits eingetragen nothing to do
            if (classFieldsToClasses.get(classField).contains(clazz)) return;
            classFieldsToClasses.get(classField).add(clazz);
        } else { // sonst neuer Eintrag
            List<TypeScriptClass> classList = new ArrayList<>();
            classList.add(clazz);
            classFieldsToClasses.put(classField, classList);
        }
    }

    public static void resetIndex(Project project) {

        parametersToFunctions = new HashMap<>();
        classFieldsToClasses = new HashMap<>();
        classesToClassFields = new HashMap<>();
        functionsToParameters = new HashMap<>();

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

                //iterate all classes in file
                for (TypeScriptClass psiClass : PsiTreeUtil.getChildrenOfTypeAsList(psiFile, TypeScriptClass.class)) {
                    addClass(psiClass);
                }
            }
        });

        printClassFieldsToClasses();
        printParametersToFunctions();
        printClassesToClassFields();
        printFunctionsToParameter();

        //TODO replace project with whatever intellij actually wants
        //PsiManager.getInstance(project).addPsiTreeChangeListener(new PsiChangeListener(), project);
    }

    public static void printClassFieldsToClasses() {
        ApplicationManager.getApplication().runReadAction(() -> {
        LOG.info("ClassField to classes");
        for (ClassField classField : classFieldsToClasses.keySet()) {
            List<TypeScriptClass> classList = classFieldsToClasses.get(classField);
            StringBuilder classes = new StringBuilder("[");
            for (TypeScriptClass typeScriptClass : classList) {
                classes.append(typeScriptClass.getName()).append(",");
            }
            classes.append("]");
            LOG.info(classField.toString() + " " + classes);
        }
        LOG.info("ClassField to classes"); });
    }

    public static void printParametersToFunctions() {
        ApplicationManager.getApplication().runReadAction(() -> {
        LOG.info("Parameters to Functions");
        for (Parameter parameter : parametersToFunctions.keySet()) {
            List<TypeScriptFunction> functions = parametersToFunctions.get(parameter);
            StringBuilder parameters = new StringBuilder("[");
            for (TypeScriptFunction function : functions) {
                parameters.append(function.getName()).append(",");
            }
            parameters.append("]");
            LOG.info(parameter.toString() + " " + parameters);
        }
        LOG.info("Parameters to Functions"); });
    }

    public static void printFunctionsToParameter() {
        ApplicationManager.getApplication().runReadAction(() -> {
            LOG.info("Functions to Parameter");
            for (TypeScriptFunction function : functionsToParameters.keySet()) {
                StringBuilder parameters = new StringBuilder("[");
                for (Parameter parameter : functionsToParameters.get(function)) {
                    parameters.append(parameter.toString()).append(",");
                }
                parameters.append("]");
                LOG.info(function.getName() + " " + parameters);
            }
            LOG.info("Functions to Parameter");
        });
    }

    public static void printClassesToClassFields() {
        ApplicationManager.getApplication().runReadAction(() -> {
            LOG.info("Classes to ClassFields");
            for (TypeScriptClass clazz : classesToClassFields.keySet()) {
                StringBuilder classFields = new StringBuilder("[");
                for (ClassField classField : classesToClassFields.get(clazz)) {
                    classFields.append(classField.toString()).append(",");
                }
                classFields.append("]");
                LOG.info(clazz.getName() + " " + classFields);
            }
        });
        LOG.info("Classes to ClassFields");
    }

}

