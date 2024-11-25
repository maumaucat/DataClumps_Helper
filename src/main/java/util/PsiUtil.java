package util;
import com.intellij.lang.ecmascript6.psi.impl.ES6FieldStatementImpl;
import com.intellij.lang.javascript.TypeScriptFileType;
import com.intellij.lang.javascript.psi.*;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptClass;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptField;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptFunction;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptParameter;
import com.intellij.lang.javascript.psi.ecmal4.JSAttributeList;
import com.intellij.lang.javascript.psi.ecmal4.JSClass;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


public class PsiUtil {
    //TODO check for all 5 of them if they are actually necessary or can be replaced by create Statement

    public static ES6FieldStatementImpl createJSFieldStatement(JSClass context, String name, JSType type, String visibility) {
        String fieldText = visibility + " " + name + " : " + type.getTypeText() + ";";

        StringBuilder classCode = new StringBuilder();
        classCode.append("class PsiUtilTemp {\n");
        classCode.append(fieldText);
        classCode.append("}\n");

        PsiFile psiFile = PsiFileFactory.getInstance(context.getProject()).createFileFromText( "PsiUtilTemp.ts", TypeScriptFileType.INSTANCE, classCode);
        TypeScriptClass psiClass = PsiTreeUtil.getChildOfType(psiFile, TypeScriptClass.class);

        return PsiTreeUtil.getChildOfType(psiClass, ES6FieldStatementImpl.class);
    }

    public static TypeScriptParameter createTypeScriptParameter(PsiElement context, String name, JSType type) {
        String parameterText = name + " : " + type.getTypeText();

        StringBuilder functionCode = new StringBuilder();
        functionCode.append("function psiUtilTemp(");
        functionCode.append(parameterText);
        functionCode.append(") {}");

        PsiFile psiFile = PsiFileFactory.getInstance(context.getProject()).createFileFromText( "PsiUtilTemp.ts", TypeScriptFileType.INSTANCE, functionCode);
        TypeScriptFunction psiFunction = PsiTreeUtil.getChildOfType(psiFile, TypeScriptFunction.class);

        return (TypeScriptParameter) psiFunction.getParameters()[0];
    }

    public static void addParameterToParameterList(TypeScriptParameter parameter, JSParameterList parameterList) {

        JSParameterListElement[] listElements = parameterList.getParameters();
        TypeScriptParameter[] parameters = new TypeScriptParameter[listElements.length + 1];

        for (int i = 0; i < listElements.length; i++) {
            parameters[i] = (TypeScriptParameter) listElements [i];
        }
        parameters[parameters.length - 1] = parameter;

        JSParameterList newParameterList = createJSParameterList(parameterList, parameters);
        WriteCommandAction.runWriteCommandAction(parameterList.getProject(), () -> {
            parameterList.replace(newParameterList);
        });


    }

    public static JSParameterList createJSParameterList(PsiElement context, TypeScriptParameter[] parameters) {

        StringBuilder functionCode = new StringBuilder();
        functionCode.append("function psiUtilTemp(");
        for (TypeScriptParameter parameter : parameters) {
            functionCode.append(parameter.getText());
            functionCode.append(",");
        }
        functionCode.deleteCharAt(functionCode.length() - 1);
        functionCode.append(") {}");

        PsiFile psiFile = PsiFileFactory.getInstance(context.getProject()).createFileFromText( "PsiUtilTemp.ts", TypeScriptFileType.INSTANCE, functionCode);
        TypeScriptFunction psiFunction = PsiTreeUtil.getChildOfType(psiFile, TypeScriptFunction.class);

        return psiFunction.getParameterList();
    }

    public static List<String> getModifiers(TypeScriptField field) {
        CodeSmellLogger.info("Getting Modifiers for Field " + field.getName());
        List<String> modifiers = new ArrayList<>();
        modifiers.add(field.getAccessType().toString());

        JSAttributeList attributeList = PsiTreeUtil.getPrevSiblingOfType(field, JSAttributeList.class);
        modifiers.addAll(getModifiers(attributeList));
        CodeSmellLogger.info("Modifiers for Field " + field.getName() + ": " + modifiers);
        return  modifiers;
    }

    public static List<String> getModifiers(TypeScriptParameter parameter) {
        CodeSmellLogger.info("Getting Modifiers for Parameter " + parameter.getName());
        List<String> modifiers = new ArrayList<>();
        modifiers.add(parameter.getAccessType().toString());

        JSAttributeList attributeList = PsiTreeUtil.getChildOfType(parameter, JSAttributeList.class);
        modifiers.addAll(getModifiers(attributeList));
        CodeSmellLogger.info("Modifiers for Parameter " + parameter.getName() + ": " + modifiers);
        return  modifiers;
    }

    private static List<String> getModifiers(JSAttributeList attributeList) {

        List<String> modifiers = new ArrayList<>();

        if (attributeList.hasModifier(JSAttributeList.ModifierType.READONLY)) modifiers.add("readonly");
        if (attributeList.hasModifier(JSAttributeList.ModifierType.STATIC)) modifiers.add("static");
        if (attributeList.hasModifier(JSAttributeList.ModifierType.ABSTRACT)) modifiers.add("abstract");
        if (attributeList.hasModifier(JSAttributeList.ModifierType.DECLARE)) modifiers.add("declare");


        return modifiers;
    }

    public static List<TypeScriptClass> getClassesThatHaveAll(List<Property> properties) {
        List<TypeScriptClass> classes = new ArrayList<>();

        for (Property property : properties) {
            if (Index.getPropertiesToClasses().get(property) == null) continue;
            for (TypeScriptClass psiClass : Index.getPropertiesToClasses().get(property)) {
                if (!psiClass.isValid() || classes.contains(psiClass)) continue;
                if (hasAll(psiClass, properties)) classes.add(psiClass);
            }
        }
        return classes;
    }

    // classfields must match
    public static boolean hasAll(TypeScriptClass psiClass, List<Property> properties) {
        List<Classfield> classProperties = Index.getClassesToClassFields().get(psiClass);
        for (Property property : properties) {
            if (!classProperties.contains(property)) return false;
            if (property instanceof Classfield && !classProperties.get(classProperties.indexOf(property)).matches((Classfield) property)) return false;
        }
        return true;
    }

    public static List<Classfield> getFields(TypeScriptClass psiClass) {
        List<Classfield> fields = new ArrayList<>();
        // iterate all FieldStatements
        for (JSField field : psiClass.getFields()) {
            if (field.getName() == null || field.getJSType() == null) continue;
            fields.add(new Classfield((TypeScriptField) field));

        }
        // iterate constructor Parameter
        TypeScriptFunction constructor = (TypeScriptFunction) psiClass.getConstructor();
        if (constructor != null) {
            for (JSParameterListElement psiParameter : constructor.getParameters()) {
                if (psiParameter.getName() == null || psiParameter.getJSType() == null) continue;
                // test if parameter is actually field
                if (PsiTreeUtil.getChildOfType(psiParameter, JSAttributeList.class).getTextLength() > 0) { //TODO NOT VERY ELEGANT
                    fields.add(new Classfield((TypeScriptParameter) psiParameter));
                }
            }
        }
        return fields;
    }

    public static HashMap<Classfield, PsiElement> getFieldsToElement(TypeScriptClass psiClass) {
        HashMap<Classfield, PsiElement> fields = new HashMap<>();
        // iterate all FieldStatements
        for (JSField field : psiClass.getFields()) {
            if (field.getName() == null || field.getJSType() == null) continue;
            fields.put(new Classfield((TypeScriptField) field), field);

        }
        // iterate constructor Parameter
        TypeScriptFunction constructor = (TypeScriptFunction) psiClass.getConstructor();
        if (constructor != null) {
            for (JSParameterListElement psiParameter : constructor.getParameters()) {
                if (psiParameter.getName() == null || psiParameter.getJSType() == null) continue;
                // test if parameter is actually field
                if (PsiTreeUtil.getChildOfType(psiParameter, JSAttributeList.class).getTextLength() > 0) { //TODO NOT VERY ELEGANT
                    fields.put(new Classfield((TypeScriptParameter) psiParameter), psiParameter);
                }
            }
        }
        return fields;

    }

}
