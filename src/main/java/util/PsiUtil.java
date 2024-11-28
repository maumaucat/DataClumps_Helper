package util;
import com.google.rpc.Code;
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
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.FileContentUtil;

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
        List<String> modifiers = new ArrayList<>();
        modifiers.add(field.getAccessType().toString().toLowerCase());

        JSAttributeList attributeList = PsiTreeUtil.getPrevSiblingOfType(field, JSAttributeList.class);
        modifiers.addAll(getModifiers(attributeList));
        return  modifiers;
    }

    public static List<String> getModifiers(TypeScriptParameter parameter) {
        List<String> modifiers = new ArrayList<>();
        modifiers.add(parameter.getAccessType().toString().toLowerCase());

        JSAttributeList attributeList = PsiTreeUtil.getChildOfType(parameter, JSAttributeList.class);
        modifiers.addAll(getModifiers(attributeList));
        return  modifiers;
    }

    private static List<String> getModifiers(JSAttributeList attributeList) {

        List<String> modifiers = new ArrayList<>();

        // the order of the modifiers is important for the code generation
        if (attributeList.hasModifier(JSAttributeList.ModifierType.STATIC)) modifiers.add("static");
        if (attributeList.hasModifier(JSAttributeList.ModifierType.READONLY)) modifiers.add("readonly");
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
                if (isParameterField((TypeScriptParameter) psiParameter)) { //TODO NOT VERY ELEGANT
                    fields.add(new Classfield((TypeScriptParameter) psiParameter));
                }
            }
        }
        return fields;
    }

    public static boolean isParameterField(TypeScriptParameter parameter) {
        return PsiTreeUtil.getChildOfType(parameter, JSAttributeList.class).getTextLength() > 0;
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

    public static TypeScriptFunction createGetter(PsiElement context, Property property, boolean optional) {
        StringBuilder getterText = new StringBuilder();
        if (optional) {
            getterText.append("  get " + property.getName() + "(): " + property.getType() + " | undefined {\n");
        } else {
            getterText.append("  get " + property.getName() + "(): " + property.getType() + " {\n");
        }
        getterText.append("    return this._" + property.getName() + ";\n}");

        StringBuilder classCode = new StringBuilder();
        classCode.append("class PsiUtilTemp {\n");
        classCode.append(getterText);
        classCode.append("}\n");

        PsiFile psiFile = PsiFileFactory.getInstance(context.getProject()).createFileFromText( "PsiUtilTemp.ts", TypeScriptFileType.INSTANCE, classCode);
        TypeScriptClass psiClass = PsiTreeUtil.getChildOfType(psiFile, TypeScriptClass.class);

        return (TypeScriptFunction) psiClass.getFunctions()[0];
    }

    public static TypeScriptFunction createSetter(PsiElement context, Property property, boolean optional) {

        String setterText;
        if (optional) {
            setterText = "  set " + property.getName() + "(value: " + property.getType() + " | undefined) {\n";
        } else {
            setterText = "  set " + property.getName() + "(value: " + property.getType() + ") {\n";
        }

        StringBuilder classCode = new StringBuilder();
        classCode.append("class PsiUtilTemp {\n");
        classCode.append(setterText);
        classCode.append("}\n");

        PsiFile psiFile = PsiFileFactory.getInstance(context.getProject()).createFileFromText( "PsiUtilTemp.ts", TypeScriptFileType.INSTANCE, classCode);
        TypeScriptClass psiClass = PsiTreeUtil.getChildOfType(psiFile, TypeScriptClass.class);

        return (TypeScriptFunction) psiClass.getFunctions()[0];
    }

    public static TypeScriptClass createClass(PsiElement context, String className) {
        String classText = "class " + className + " {}";

        PsiFile psiFile = PsiFileFactory.getInstance(context.getProject()).createFileFromText( "PsiUtilTemp.ts", TypeScriptFileType.INSTANCE, classText);
        return PsiTreeUtil.getChildOfType(psiFile, TypeScriptClass.class);
    }

    public static TypeScriptFunction createConstructor(TypeScriptClass psiClass, List<Property> allFields, List<Property> optionalFields) {

        CodeSmellLogger.info("Creating constructor for class " + psiClass.getName());
        CodeSmellLogger.info("All fields: " + allFields);
        CodeSmellLogger.info("Optional fields: " + optionalFields);


        if (psiClass!=null && psiClass.getConstructor() != null) {
            CodeSmellLogger.error("Constructor already exists for class " + psiClass.getName(), new IllegalArgumentException());
            return null;
        }

        List<Classfield> classfields = new ArrayList<>();
        if (psiClass != null) classfields = getFields(psiClass);

        StringBuilder constructorCode = new StringBuilder();

        List<Property> requiredFields = new ArrayList<>(allFields);
        requiredFields.removeAll(optionalFields);

        CodeSmellLogger.info("Required fields: " + requiredFields);

        constructorCode.append("constructor(");
        List<Property> assignedFields = new ArrayList<>();

        for (Property field : requiredFields) {

            CodeSmellLogger.info("Adding required field " + field.getName() + " to constructor.");

            final String fieldName = field.getName();
            final String fieldType = field.getType().getTypeText();

            // if field does not yet exist in the class, define it in the constructor
            if (!classfields.contains(field)) {
                CodeSmellLogger.info("Field " + fieldName + " does not yet exist in the class, defining it in the constructor.");
                // for new Classfields use the modifier of the field
                if (field instanceof Classfield) {
                    CodeSmellLogger.info("Field " + fieldName + " is a Classfield.");
                    List<String> modifiers = ((Classfield) field).getModifier();
                    for (String modifier : modifiers) {
                        constructorCode.append(modifier + " ");
                    }
                    // If the field is public, do not use the underscore prefix
                    if (modifiers.contains("public")) {
                        CodeSmellLogger.info("Field added: " + fieldName + ": " + fieldType);
                        constructorCode.append(fieldName + ": " + fieldType + ", ");
                    } else {
                        CodeSmellLogger.info("Field added: _" + fieldName + ": " + fieldType);
                        constructorCode.append("_" + fieldName + ": " + fieldType + ", ");
                    }
                } else {
                    // For Parameters use the private as default visibility
                    CodeSmellLogger.info("Field " + fieldName + " is a Parameter.");
                    CodeSmellLogger.info("Field added: _" + fieldName + ": " + fieldType);
                    constructorCode.append("private _" + fieldName + ": " + fieldType + ", ");
                }
            } else {
                // if field already exists in the class assign it in the constructor
                CodeSmellLogger.info("Field " + fieldName + " already exists in the class, assigning it in the constructor.");
                CodeSmellLogger.info("Field added: " + fieldName + ": " + fieldType);
                constructorCode.append(fieldName + ": " + fieldType + ", ");
                assignedFields.add(field);
            }
        }

        for (Property field : optionalFields) {

            CodeSmellLogger.info("Adding optional field " + field.getName() + " to constructor.");

            final String fieldName = field.getName();
            final String fieldType = field.getType().getTypeText();

            // if field does not yet exist in the class, define it in the constructor
            if (!classfields.contains(field)) {
                CodeSmellLogger.info("Field " + fieldName + " does not yet exist in the class, defining it in the constructor.");
                // for new Classfields use the modifier of the field
                if (field instanceof Classfield) {
                    CodeSmellLogger.info("Field " + fieldName + " is a Classfield.");
                    List<String> modifiers = ((Classfield) field).getModifier();
                    for (String modifier : modifiers) {
                        constructorCode.append(modifier + " ");
                    }
                    // If the field is public, do not use the underscore prefix
                    if (modifiers.contains("public")) {
                        CodeSmellLogger.info("Field added: " + fieldName + "?: " + fieldType);
                        constructorCode.append(fieldName + "?: " + fieldType + ", ");
                    } else {
                        CodeSmellLogger.info("Field added: _" + fieldName + "?: " + fieldType);
                        constructorCode.append("_" + fieldName + "?: " + fieldType + ", ");
                    }
                } else {
                    // For Parameters use the private as default visibility
                    CodeSmellLogger.info("Field " + fieldName + " is a Parameter.");
                    CodeSmellLogger.info("Field added: _" + fieldName + "?: " + fieldType);
                    constructorCode.append("private _" + fieldName + "?: " + fieldType + ", ");
                }
            } else {
                // if field already exists in the class assign it in the constructor
                CodeSmellLogger.info("Field " + fieldName + " already exists in the class, assigning it in the constructor.");
                CodeSmellLogger.info("Field added: " + fieldName + "?: " + fieldType);
                constructorCode.append(fieldName + "?: " + fieldType + ", ");
                assignedFields.add(field);
            }
        }



        // Remove trailing comma and close the constructor
        if (!allFields.isEmpty()) {
            constructorCode.setLength(constructorCode.length() - 2);
        }

        constructorCode.append(") {");

        // add assignments
        for (Property field : assignedFields) {
            constructorCode.append("\nthis." + field.getName() + " = " + field.getName() + ";");
        }

        constructorCode.append("}\n");

        StringBuilder classCode = new StringBuilder();
        classCode.append("class PsiUtilTemp {\n");
        classCode.append(constructorCode);
        classCode.append("}\n");

        PsiFile psiFile = PsiFileFactory.getInstance(psiClass.getProject()).createFileFromText( "PsiUtilTemp.ts", TypeScriptFileType.INSTANCE, classCode);
        TypeScriptClass wrapper = PsiTreeUtil.getChildOfType(psiFile, TypeScriptClass.class);

        return (TypeScriptFunction) wrapper.getConstructor();

    }

    public static void addFunctionToClass(TypeScriptClass psiClass, TypeScriptFunction function) {

        WriteCommandAction.runWriteCommandAction(psiClass.getProject(), () -> {
            // Add the function to the correct position
            if (psiClass.getConstructor() != null) {
                CodeSmellLogger.info("Class has constructor, inserting after it.");
                psiClass.addAfter(function, psiClass.getConstructor());
            } else if (psiClass.getFunctions().length > 0) {
                CodeSmellLogger.info("Class has functions, inserting before the first one.");
                psiClass.addBefore(function, psiClass.getFunctions()[0]);
            } else {
                CodeSmellLogger.info("Class has no functions, inserting at the end.");
                psiClass.addBefore(function, psiClass.getLastChild());
            }

            // Reformat the class
            CodeStyleManager.getInstance(psiClass.getProject()).reformat(psiClass);
        });
    }


}
