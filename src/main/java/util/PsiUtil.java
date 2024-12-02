package util;
import com.intellij.lang.ecmascript6.psi.impl.ES6FieldStatementImpl;
import com.intellij.lang.javascript.TypeScriptFileType;
import com.intellij.lang.javascript.psi.*;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptClass;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptField;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptFunction;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptParameter;
import com.intellij.lang.javascript.psi.ecmal4.JSAttributeList;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.rename.RenameProcessor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;


public class PsiUtil {

    public static ES6FieldStatementImpl createJSFieldStatement(PsiElement context, String name, JSType type, List<String> modifiers, boolean optional) {
        StringBuilder fieldText = new StringBuilder();
        for (String modifier : modifiers) {
            fieldText.append(modifier + " ");
        }
        if (optional) {
            fieldText.append(name + "?: " + type.getTypeText() + ";");
        } else {
            fieldText.append(name + " : " + type.getTypeText() + ";");
        }

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

    // classfields must match
    public static boolean hasAll(TypeScriptClass psiClass, List<Property> properties) {
        List<Classfield> classProperties = Index.getClassesToClassFields().get(psiClass);
        for (Property property : properties) {
            if (!classProperties.contains(property)) return false;
            if (property instanceof Classfield && !classProperties.get(classProperties.indexOf(property)).matches((Classfield) property)) return false;
        }
        return true;
    }

    public static Classfield getClassfield(TypeScriptClass psiClass, String fieldName) {
        for (Classfield classfield : getFields(psiClass)) {
            if (classfield.getName().equals(fieldName)) return classfield;
        }
        return null;
    }

    public static PsiElement getField(TypeScriptClass psiClass, String fieldName) {
        HashMap<Classfield, PsiElement> fields = getFieldsToElement(psiClass);
        for (Classfield classfield : fields.keySet()) {
            if (classfield.getName().equals(fieldName)) return fields.get(classfield);
        }
        return null;
    }

    public static PsiElement getPsiField(TypeScriptClass psiClass, Property property) {
        HashMap<Classfield, PsiElement> fields = getFieldsToElement(psiClass);
        return fields.get(property);
    }

    public static void rename(PsiElement element, String newName) {
        // Initialize a RenameProcessor to rename the field and update all references
        RenameProcessor renameProcessor = new RenameProcessor(
                element.getProject(),       // Current project
                element,  // The field to be renamed
                newName,  // New name for the field
                false,         // Search in comments and strings (set true if needed)
                true           // Search for text occurrences (e.g., in non-code files)
        );

        // Run the refactoring process
        renameProcessor.run();
    }

    public static void makeFieldOptional(TypeScriptField field) {
        ES6FieldStatementImpl statement = PsiTreeUtil.getParentOfType(field, ES6FieldStatementImpl.class);
        WriteCommandAction.runWriteCommandAction(field.getProject(), () -> {
            statement.replace(createJSFieldStatement(field, field.getName(), field.getJSType(), getModifiers(field), true));
        });

    }

    public static boolean hasSetter(TypeScriptClass psiClass, Classfield classfield) {
        if (classfield.isPublic()) return true;
        for (JSFunction psiFunction : psiClass.getFunctions()) {
            if (!psiFunction.isSetProperty()) continue;

            String setterName = psiFunction.getName();
            String fieldName = classfield.getName();

            // Setter should match field name directly or follow "set" convention
            boolean nameMatches = setterName.equals(fieldName);

            // Check if the parameter type of the setter matches the field type
            boolean typeMatches = psiFunction.getParameters()[0].getJSType().equals(classfield.getType());

            //TODO make sure that the right value is set
            //TODO make sure that nothing is modified

            if (nameMatches && typeMatches) return true;
        }
        return false;
    }

    public static boolean hasGetter(TypeScriptClass psiClass, Classfield classfield) {
        if (classfield.isPublic()) return true;
        for (JSFunction psiFunction : psiClass.getFunctions()) {
            if (!psiFunction.isGetProperty()) continue;

            String getterName = psiFunction.getName();
            String fieldName = classfield.getName();

            // Getter should match field name directly or follow "get" convention
            boolean nameMatches = getterName.equals(fieldName);

            // Check if the return type of the getter matches the field type
            boolean typeMatches = psiFunction.getReturnType().equals(classfield.getType());

            //TODO make sure that the right value is returned
            //TODO make sure that nothing is modified

            if (nameMatches && typeMatches) return true;
        }
        return false;
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

    public static boolean isAssignedNewValue(TypeScriptParameter parameter) {
        // check if the parameter is assigned in the constructor to a field of the class that is in the properties list
        for (PsiReference reference : ReferencesSearch.search(parameter)) {
            // is the reference an assignment?
            JSAssignmentExpression assignment = PsiTreeUtil.getParentOfType(reference.getElement(), JSAssignmentExpression.class);

            if (assignment != null && assignment.getLOperand().getFirstChild() == reference) {
                return true;
            }
        }
        return false;
    }


    public static List<Classfield> getAssignedToField(TypeScriptParameter parameter) {
        List<Classfield> fields = new ArrayList<>();
        for (PsiReference reference : ReferencesSearch.search(parameter)) {
            // is the reference an assignment?
            JSAssignmentExpression assignment = PsiTreeUtil.getParentOfType(reference.getElement(), JSAssignmentExpression.class);

            if (assignment != null && assignment.getROperand() == reference) {
                if (assignment.getLOperand().getFirstChild() instanceof JSReferenceExpression referenceExpression) {
                    Classfield field = resolveField(referenceExpression);
                    if (field != null) fields.add(field);
                }
            }
        }
        return fields;
    }

    public static Classfield resolveField(JSReferenceExpression reference) {

        PsiElement definition = reference.resolve();
        if (definition instanceof TypeScriptField tsField) {
            return new Classfield(tsField);
        }
        if (definition instanceof TypeScriptParameter tsParameter && PsiUtil.isParameterField(tsParameter)) {
            return new Classfield(tsParameter);
        }

        return null;
    }

    public static Property resolveProperty(JSReferenceExpression reference) {
        PsiElement definition = reference.resolve();
        if (definition instanceof TypeScriptField tsField) {
            return new Classfield(tsField);
        }
        if (definition instanceof TypeScriptParameter tsParameter) {
            if (PsiUtil.isParameterField(tsParameter)) {
                return new Classfield(tsParameter);
            } else {
                return new Parameter(tsParameter);
            }
        }

        return null;
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
        setterText += "    this._" + property.getName() + " = value;\n}";

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

    public static TypeScriptFunction createConstructor(TypeScriptClass psiClass, List<Property> allFields, Set<Property> optional, List<Property> allParameters, JSBlockStatement body) {

        CodeSmellLogger.info("Creating constructor for class " + psiClass.getName());
        CodeSmellLogger.info("All fields: " + allFields);
        CodeSmellLogger.info("Optional fields: " + optional);


        if (psiClass != null && psiClass.getConstructor() != null) {
            CodeSmellLogger.error("Constructor already exists for class " + psiClass.getName(), new IllegalArgumentException());
            return null;
        }

        List<Classfield> classfields = new ArrayList<>();
        if (psiClass != null) classfields = getFields(psiClass);

        StringBuilder constructorCode = new StringBuilder();

        List<Property> requiredProperties = new ArrayList<>(allFields);
        requiredProperties.addAll(allParameters);
        requiredProperties.removeAll(optional);

        CodeSmellLogger.info("Required Properties: " + requiredProperties);
        CodeSmellLogger.info("Optional: " + optional);

        constructorCode.append("constructor(");
        List<Property> assignedFields = new ArrayList<>();

        for (Property property : requiredProperties) {

            final String propertyName = property.getName();
            final String propertyType = property.getType();

            // if property does not yet exist in the class, define it in the constructor
            if (!classfields.contains(property) && allFields.contains(property)) {
                // for new Classfields use the modifier of the property
                if (property instanceof Classfield) {
                    List<String> modifiers = ((Classfield) property).getModifier();
                    for (String modifier : modifiers) {
                        constructorCode.append(modifier + " ");
                    }
                    // If the property is public, do not use the underscore prefix
                    if (modifiers.contains("public")) {
                        constructorCode.append(propertyName + ": " + propertyType + ", ");
                    } else {
                        constructorCode.append("_" + propertyName + ": " + propertyType + ", ");
                    }
                } else {
                    // For Parameters use the private as default visibility
                    constructorCode.append("private _" + propertyName + ": " + propertyType + ", ");
                }
            } else {
                // if property already exists in the class or is a parameter just add it
                constructorCode.append(propertyName + ": " + propertyType + ", ");
                if (allFields.contains(property)) {
                    assignedFields.add(property);
                }
            }
        }

        for (Property property : optional) {

            final String propertyName = property.getName();
            final String propertyType = property.getType();

            // if property does not yet exist in the class, define it in the constructor
            if (!classfields.contains(property) && allFields.contains(property)) {
                // for new Classfields use the modifier of the property
                if (property instanceof Classfield) {
                    List<String> modifiers = ((Classfield) property).getModifier();
                    for (String modifier : modifiers) {
                        constructorCode.append(modifier + " ");
                    }
                    // If the property is public, do not use the underscore prefix
                    if (modifiers.contains("public")) {
                        constructorCode.append(propertyName + "?: " + propertyType + ", ");
                    } else {
                        constructorCode.append("_" + propertyName + "?: " + propertyType + ", ");
                    }
                } else {
                    // For Parameters use the private as default visibility
                    constructorCode.append("private _" + propertyName + "?: " + propertyType + ", ");
                }
            } else {
                constructorCode.append(propertyName + "?: " + propertyType + ", ");
                if (allFields.contains(property)) {
                    assignedFields.add(property);
                }
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

        // add the constructor body
        if (body != null) {
            constructorCode.append("\n" + removeBrackets(body.getText()));
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

    public static String removeBrackets(String text) {
        text = text.trim();
        return text.substring(1, text.length() - 1);
    }

}
