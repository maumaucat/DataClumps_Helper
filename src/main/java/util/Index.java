package util;

import com.intellij.lang.javascript.TypeScriptFileType;
import com.intellij.lang.javascript.psi.JSArgumentList;
import com.intellij.lang.javascript.psi.JSField;
import com.intellij.lang.javascript.psi.JSParameter;
import com.intellij.lang.javascript.psi.JSParameterListElement;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptClass;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptField;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptFunction;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptParameter;
import com.intellij.lang.javascript.psi.ecmal4.JSAttributeList;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
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

    private static final Logger LOG = Logger.getInstance(Index.class);

    private static HashMap<Property,List<TypeScriptFunction>> propertiesToFunctions;

    private static HashMap<Property, List<TypeScriptClass>> propertiesToClasses;

    private static HashMap<TypeScriptClass, List<ClassField>> classesToClassFields;

    private static HashMap<TypeScriptFunction, List<Parameter>> functionsToParameters;

    public static HashMap<Property,List<TypeScriptFunction>> getPropertiesToFunctions() {
        return propertiesToFunctions;
    }

    public static HashMap<Property, List<TypeScriptClass>> getPropertiesToClasses() {
        return propertiesToClasses;
    }

    public static HashMap<TypeScriptClass, List<ClassField>> getClassesToClassFields() {
        return classesToClassFields;
    }

    public static HashMap<TypeScriptFunction, List<Parameter>> getFunctionsToParameters() {
        return functionsToParameters;
    }

    private static List<ClassField> getFields(TypeScriptClass psiClass) {
        List<ClassField> fields = new ArrayList<>();
        // iterate all FieldStatements
        for (JSField field : psiClass.getFields()) {
            if (field.getName() == null || field.getJSType() == null) continue;
            fields.add(new ClassField((TypeScriptField) field));

        }
        // iterate constructor Parameter
        TypeScriptFunction constructor = (TypeScriptFunction) psiClass.getConstructor();
        if (constructor != null) {
            for (JSParameterListElement psiParameter : constructor.getParameters()) {
                if (psiParameter.getName() == null || psiParameter.getJSType() == null) continue;
                // test if parameter is actually field
                if (PsiTreeUtil.getChildOfType(psiParameter, JSAttributeList.class).getTextLength() > 0) { //TODO NOT VERY ELEGANT
                    fields.add(new ClassField((TypeScriptParameter) psiParameter));
                }
            }
        }
        return fields;
    }

    //TODO überlegen ob mit getFields aus Index zusammenlegen und wohin die funktion gehöhrt
    public static HashMap<ClassField, PsiElement> getFieldsToElement(TypeScriptClass psiClass) {
        HashMap<ClassField, PsiElement> fields = new HashMap<>();
        // iterate all FieldStatements
        for (JSField field : psiClass.getFields()) {
            if (field.getName() == null || field.getJSType() == null) continue;
            fields.put(new ClassField((TypeScriptField) field), field);

        }
        // iterate constructor Parameter
        TypeScriptFunction constructor = (TypeScriptFunction) psiClass.getConstructor();
        if (constructor != null) {
            for (JSParameterListElement psiParameter : constructor.getParameters()) {
                if (psiParameter.getName() == null || psiParameter.getJSType() == null) continue;
                // test if parameter is actually field
                if (PsiTreeUtil.getChildOfType(psiParameter, JSAttributeList.class).getTextLength() > 0) { //TODO NOT VERY ELEGANT
                    fields.put(new ClassField((TypeScriptParameter) psiParameter), psiParameter);
                }
            }
        }
        return fields;

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

    public static void addClass(TypeScriptClass psiClass) {

        classesToClassFields.put(psiClass, new ArrayList<>());
        List<ClassField> classFields = getFields(psiClass);

        for (ClassField classField : classFields) {
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

        // alle aktuellen Klassenfelder der Klasse speichern
        List<ClassField> new_Fields = getFields(psiClass);

        // alle alten KlassenFelder
        List<ClassField> toBeRemoved = new ArrayList<>(classesToClassFields.get(psiClass));
        // alle alten Klassenfelder ohne die, die auch im neuen sind
        toBeRemoved.removeAll(new_Fields);

        // eintrag in classes to classFields austauschen
        classesToClassFields.put(psiClass, new_Fields);

        for (ClassField classField : toBeRemoved) {
            propertiesToClasses.get(classField).remove(psiClass);
        }

        for (ClassField classField: new_Fields) {
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

    public static void addClassFieldForClass(TypeScriptClass psiClass, ClassField classField) {
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

    public static void resetIndex(Project project) {

        propertiesToFunctions = new HashMap<>();
        propertiesToClasses = new HashMap<>();
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
    }

    public static void printClassFieldsToClasses() {
        ApplicationManager.getApplication().runReadAction(() -> {
        LOG.info("ClassField to classes");
        for (Property classField : propertiesToClasses.keySet()) {
            List<TypeScriptClass> classList = propertiesToClasses.get(classField);
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
        for (Property parameter : propertiesToFunctions.keySet()) {
            List<TypeScriptFunction> functions = propertiesToFunctions.get(parameter);
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

