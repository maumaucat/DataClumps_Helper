package util;

import Settings.DataClumpSettings;
import com.intellij.lang.ecmascript6.psi.impl.ES6FieldStatementImpl;
import com.intellij.lang.javascript.TypeScriptFileType;
import com.intellij.lang.javascript.psi.*;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptClass;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptField;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptFunction;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptParameter;
import com.intellij.lang.javascript.psi.ecmal4.JSAttributeList;
import com.intellij.lang.javascript.psi.ecmal4.JSClass;
import com.intellij.lang.javascript.psi.ecmal4.JSSuperExpression;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.DialogWrapperDialog;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.rename.RenameProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;


public class PsiUtil {

    /**
     * Creates a new field statement in the given context.
     *
     * @param context      The context in which the field statement should be created.
     * @param name         The name of the field.
     * @param type         The type of the field.
     * @param modifiers    The modifiers of the field.
     * @param defaultValue The default value of the field.
     **/
    public static ES6FieldStatementImpl createJSFieldStatement(PsiElement context, String name, String type, String visibility, List<String> modifiers, String defaultValue) {

        StringBuilder fieldText = new StringBuilder();

        fieldText.append(visibility).append(" ");

        for (String modifier : modifiers) {
            fieldText.append(modifier).append(" ");
        }

        fieldText.append(name).append(" : ").append(type);


        if (defaultValue != null) {
            fieldText.append(" = ").append(defaultValue);
        }

        fieldText.append(";");


        StringBuilder classCode = new StringBuilder();
        classCode.append("class PsiUtilTemp {\n");
        classCode.append(fieldText);
        classCode.append("}\n");


        PsiFile psiFile = runWriteCommandWithResult(context.getProject(), () -> PsiFileFactory.getInstance(context.getProject())
                .createFileFromText("PsiUtilTemp.ts", TypeScriptFileType.INSTANCE, classCode));

        TypeScriptClass psiClass = runReadActionWithResult(() -> PsiTreeUtil.getChildOfType(psiFile, TypeScriptClass.class));
        return runReadActionWithResult(() -> PsiTreeUtil.getChildOfType(psiClass, ES6FieldStatementImpl.class));
    }

    /**
     * Creates a new parameter in the given context.
     *
     * @param context The context in which the parameter should be created.
     * @param name    The name of the parameter.
     * @param type    The type of the parameter.
     * @return The created parameter.
     */
    public static TypeScriptParameter createTypeScriptParameter(PsiElement context, String name, JSType type) {

        String parameterText = name + " : " + type.getTypeText();

        StringBuilder functionCode = new StringBuilder();
        functionCode.append("function psiUtilTemp(");
        functionCode.append(parameterText);
        functionCode.append(") {}");

        PsiFile psiFile = runWriteCommandWithResult(context.getProject(), () -> PsiFileFactory.getInstance(context.getProject())
                .createFileFromText("PsiUtilTemp.ts", TypeScriptFileType.INSTANCE, functionCode));
        TypeScriptFunction psiFunction = runReadActionWithResult(() -> PsiTreeUtil.getChildOfType(psiFile, TypeScriptFunction.class));

        assert psiFunction != null;

        return (TypeScriptParameter) runReadActionWithResult(() -> psiFunction.getParameters()[0]);
    }

    /**
     * Creates a new parameter list in the given context.
     *
     * @param context    The context in which the parameter list should be created.
     * @param parameters The parameters of the parameter list.
     * @return The created parameter list.
     */
    public static JSParameterList createJSParameterList(PsiElement context, TypeScriptParameter[] parameters) {

        StringBuilder functionCode = new StringBuilder("function psiUtilTemp(");

        for (TypeScriptParameter parameter : parameters) {
            functionCode.append(parameter.getText());
            functionCode.append(",");
        }

        if (parameters.length > 0) {
            functionCode.deleteCharAt(functionCode.length() - 1);
        }

        functionCode.append(") {}");

        PsiFile psiFile = runWriteCommandWithResult(context.getProject(), () -> PsiFileFactory.getInstance(context.getProject())
                .createFileFromText("PsiUtilTemp.ts", TypeScriptFileType.INSTANCE, functionCode));
        TypeScriptFunction psiFunction = runReadActionWithResult(() -> PsiTreeUtil.getChildOfType(psiFile, TypeScriptFunction.class));

        assert psiFunction != null;

        return runReadActionWithResult(psiFunction::getParameterList);
    }

    /**
     * Creates a getter function in the given context for the given property.
     *
     * @param context  The context in which the getter should be created.
     * @param property The property for which the getter should return the value.
     * @return The created getter function.
     */
    public static TypeScriptFunction createGetter(PsiElement context, Property property) {

        String getterText = "  get " + property.getName() +
                "(): " + property.getTypesAsString() + " {\n" +
                "    return this._" + property.getName() + ";\n}";

        StringBuilder classCode = new StringBuilder();
        classCode.append("class PsiUtilTemp {\n");
        classCode.append(getterText);
        classCode.append("}\n");

        PsiFile psiFile = runWriteCommandWithResult(context.getProject(), () -> PsiFileFactory.getInstance(context.getProject())
                .createFileFromText("PsiUtilTemp.ts", TypeScriptFileType.INSTANCE, classCode));
        TypeScriptClass psiClass = runReadActionWithResult(() -> PsiTreeUtil.getChildOfType(psiFile, TypeScriptClass.class));

        assert psiClass != null;

        return runReadActionWithResult(() -> (TypeScriptFunction) psiClass.getFunctions()[0]);
    }

    /**
     * Creates a setter function in the given context for the given property.
     *
     * @param context  The context in which the setter should be created.
     * @param property The property for which the setter should set the value.
     * @return The created setter function.
     */
    public static TypeScriptFunction createSetter(PsiElement context, Property property) {

        String setterText = "  set " + property.getName() +
                "(value: " + property.getTypesAsString() + ") {\n" +
                "    this._" + property.getName() + " = value;\n}";

        StringBuilder classCode = new StringBuilder();
        classCode.append("class PsiUtilTemp {\n");
        classCode.append(setterText);
        classCode.append("}\n");

        PsiFile psiFile = runWriteCommandWithResult(context.getProject(), () -> PsiFileFactory.getInstance(context.getProject())
                .createFileFromText("PsiUtilTemp.ts", TypeScriptFileType.INSTANCE, classCode));
        TypeScriptClass psiClass = runReadActionWithResult(() -> PsiTreeUtil.getChildOfType(psiFile, TypeScriptClass.class));

        assert psiClass != null;

        return runReadActionWithResult(() -> (TypeScriptFunction) psiClass.getFunctions()[0]);
    }

    /**
     * Creates a new class in the given context.
     *
     * @param context       The context in which the class should be created.
     * @param className     The name of the class.
     * @param abstractClass Whether the class should be abstract.
     * @param export        Whether the class should be exported.
     * @return The created class.
     */
    public static TypeScriptClass createClass(PsiElement context, String className, boolean abstractClass, boolean export) {

        StringBuilder classText = new StringBuilder();

        if (export) {
            classText.append("export ");
        }

        if (abstractClass) {
            classText.append("abstract ");
        }

        classText.append("class ").append(className).append(" {\n}\n");

        PsiFile psiFile = runWriteCommandWithResult(context.getProject(), () -> PsiFileFactory.getInstance(context.getProject())
                .createFileFromText("PsiUtilTemp.ts", TypeScriptFileType.INSTANCE, classText));

        return runReadActionWithResult(() -> PsiTreeUtil.getChildOfType(psiFile, TypeScriptClass.class));
    }


    /**
     * Creates a new constructor in the given class with the given parameters and fields and body.
     *
     * @param psiClass          The class for which the constructor should be created.
     * @param allFields         The fields of the class that should be assigned in the constructor.
     * @param allParameters     The parameters the constructor should have.
     * @param body              The body of the constructor.
     * @param includedModifiers The modifiers that should be included in the constructor.
     */
    public static TypeScriptFunction createConstructor(@NotNull TypeScriptClass psiClass,
                                                       List<Property> allFields,
                                                       List<Property> allParameters,
                                                       JSBlockStatement body,
                                                       DataClumpSettings.Modifier includedModifiers) {

        // Prepare the data structures
        List<Property> toBeAssignedFields = new ArrayList<>();
        // Get all classfields of the class not including the constructor parameters that are fields
        // since they can be assigned in the constructor parameters and other classfields cannot be assigned in the constructor
        // parameters since they cannot be assigned twice
        List<Classfield> classfields = new ArrayList<>();
        ApplicationManager.getApplication().runReadAction(() -> {
            // iterate all FieldStatements
            for (JSField field : psiClass.getFields()) {
                if (!(field instanceof TypeScriptField) || field.getName() == null || field.getJSType() == null)
                    continue;
                classfields.add(new Classfield((TypeScriptField) field));
            }

        });
        List<Property> allProperties = new ArrayList<>(allFields);
        allProperties.addAll(allParameters);

        // Build the constructor code
        StringBuilder constructorCode = new StringBuilder("constructor(");

        // Add the properties to the constructor code
        appendPropertyListToConstructorCode(allProperties, allFields, classfields, toBeAssignedFields, constructorCode, includedModifiers);

        // Clean up and finalize the constructor definition
        if (!allFields.isEmpty() || !allParameters.isEmpty()) { // Check if the constructor has parameters
            constructorCode.setLength(constructorCode.length() - 2); // Remove trailing comma
        }
        constructorCode.append(") {");

        // Add the constructor body and field assignments
        addConstructorBodyAndAssignments(constructorCode, body, toBeAssignedFields);

        // Wrap in a temporary class and create the PsiFile
        StringBuilder classCode = new StringBuilder("class PsiUtilTemp {\n");
        classCode.append(constructorCode);
        classCode.append("}\n");

        PsiFile psiFile = runWriteCommandWithResult(psiClass.getProject(), () -> PsiFileFactory.getInstance(psiClass.getProject()).createFileFromText("PsiUtilTemp.ts", TypeScriptFileType.INSTANCE, classCode));
        TypeScriptClass wrapper = PsiTreeUtil.getChildOfType(psiFile, TypeScriptClass.class);

        assert wrapper != null;

        return (TypeScriptFunction) wrapper.getConstructor();
    }

    /**
     * Appends a list of properties to the constructor code.
     *
     * @param properties         The properties to be added to the constructor code.
     * @param allFields          The properties of @param properties that are fields.
     * @param classfields        The fields that already exist in the class.
     * @param toBeAssignedFields The fields that should be assigned in the constructor body.
     *                           This list is updated by this method.
     *                           If a property is already a field in the class, it is added to this list.
     * @param constructorCode    The constructor code to which the properties should be added.
     *                           This StringBuilder is updated by this method.
     * @param includedModifiers  The modifiers that should be included in the constructor.
     */
    private static void appendPropertyListToConstructorCode(List<Property> properties,
                                                            List<Property> allFields,
                                                            List<Classfield> classfields,
                                                            List<Property> toBeAssignedFields,
                                                            StringBuilder constructorCode,
                                                            DataClumpSettings.Modifier includedModifiers) {

        // iterate over all properties and add them to the constructor code
        for (Property property : properties) {

            String propertyName = property.getName();
            String propertyType = property.getTypesAsString();

            CodeSmellLogger.info("Classfields: " + classfields.toString());

            // If the property should be a field in the class and does not yet exist, define it in the constructor
            if (allFields.contains(property) && !classfields.contains(property)) {

                // For new Classfields, use the modifier of the property if includedModifiers is set to ALL or VISIBILITY
                if (property instanceof Classfield && includedModifiers != DataClumpSettings.Modifier.NONE) {

                    constructorCode.append(((Classfield) property).getVisibility()).append(" ");

                    // Add modifiers if includedModifiers is set to ALL
                    if (includedModifiers == DataClumpSettings.Modifier.ALL) {
                        List<String> modifiers = ((Classfield) property).getModifiers();
                        for (String modifier : modifiers) {
                            switch (modifier) {
                                case "readonly" -> {
                                }
                                case "abstract" ->
                                        CodeSmellLogger.error("Abstract modifier is not allowed for class fields", new IllegalArgumentException());
                                case "declare" ->
                                        CodeSmellLogger.error("Declare modifier is not allowed for class fields", new IllegalArgumentException());
                                case "static" ->
                                        CodeSmellLogger.error("Static modifier is not allowed for class fields", new IllegalArgumentException());
                                default -> constructorCode.append(modifier).append(" ");
                            }
                        }
                    }

                    // Add the property with its type and visibility
                    if (!((Classfield) property).getVisibility().equals("public")) {
                        constructorCode.append("_").append(propertyName).append(": ").append(propertyType).append(", ");
                    } else {
                        constructorCode.append(propertyName).append(": ").append(propertyType).append(", ");
                    }
                } else { // If the property has no modifiers or modifiers should not be included, use the default visibility
                    constructorCode.append("private _").append(propertyName).append(": ").append(propertyType).append(", ");
                }
            } else { // If the property already exists in the class or is a parameter, just add it as a parameter
                constructorCode.append(propertyName).append(": ").append(propertyType).append(", ");
                // If the property is a field in the class, add it to the list of fields to be assigned in the body
                if (allFields.contains(property)) {
                    toBeAssignedFields.add(property);
                }
            }
        }
    }

    /**
     * Adds the constructor body and field assignments to the constructor code.
     *
     * @param constructorCode    The constructor code to which the body and assignments should be added.
     *                           This StringBuilder is updated by this method.
     * @param body               The body of the constructor.
     *                           If provided, this body is added to the constructor code.
     * @param toBeAssignedFields The fields that should be assigned in the constructor body.
     */
    private static void addConstructorBodyAndAssignments(StringBuilder constructorCode,
                                                         JSBlockStatement body,
                                                         List<Property> toBeAssignedFields) {

        JSExpressionStatement superCall = getSuperCall(body);
        if (superCall != null) {
            constructorCode.append("\n").append(superCall.getText()).append(";");
            superCall.delete();
        }

        // Add field assignments
        for (Property field : toBeAssignedFields) {
            constructorCode.append("\nthis.").append(field.getName()).append(" = ").append(field.getName()).append(";");
        }

        // Add the constructor body if provided
        if (body != null) {
            constructorCode.append("\n").append(removeBrackets(body.getText()));
        }

        constructorCode.append("}\n");
    }

    /**
     * Returns the super call in the given constructor body.
     *
     * @param body The body of the constructor.
     * @return The super call in the constructor body. Null if no super call is found.
     */
    public static JSExpressionStatement getSuperCall(JSBlockStatement body) {
        if (body != null) {
            JSSuperExpression superKey = runReadActionWithResult(() -> PsiTreeUtil.findChildOfType(body, JSSuperExpression.class));
            if (superKey != null) {
                return runReadActionWithResult(() -> PsiTreeUtil.getParentOfType(superKey, JSExpressionStatement.class));
            }
        }
        return null;
    }

    /**
     * Adds a field to the given class.
     *
     * @param psiClass The class to which the field should be added.
     * @param field    The field to be added.
     */
    public static void addFieldToClass(TypeScriptClass psiClass, ES6FieldStatementImpl field) {

        // Find the position where the field should be inserted
        PsiElement insertBefore;
        ES6FieldStatementImpl firstField = runReadActionWithResult(() -> PsiTreeUtil.getChildOfType(psiClass, ES6FieldStatementImpl.class));
        if (firstField != null) {
            insertBefore = firstField;
        } else if (psiClass.getFunctions().length > 0) {
            insertBefore = psiClass.getFunctions()[0];
        } else {
            insertBefore = psiClass.getLastChild();
        }

        // Insert the field
        WriteCommandAction.runWriteCommandAction(psiClass.getProject(), () -> {
            psiClass.addBefore(field, insertBefore);
        });

    }

    /**
     * Adds a function to the given class.
     *
     * @param psiClass The class to which the function should be added.
     * @param function The function to be added.
     */
    public static void addFunctionToClass(TypeScriptClass psiClass, TypeScriptFunction function) {

        WriteCommandAction.runWriteCommandAction(psiClass.getProject(), () -> {
            // Add the function to the correct position
            if (psiClass.getConstructor() != null) {
                psiClass.addAfter(function, psiClass.getConstructor());
            } else if (psiClass.getFunctions().length > 0) {
                psiClass.addBefore(function, psiClass.getFunctions()[0]);
            } else {
                psiClass.addBefore(function, psiClass.getLastChild());
            }
        });
    }

    /**
     * Adds a parameter to the given parameter list.
     *
     * @param parameter     The parameter to be added.
     * @param parameterList The parameter list to which the parameter should be added.
     */
    public static void addParameterToParameterList(TypeScriptParameter parameter, JSParameterList parameterList) {

        // collect all parameters in the parameter list and convert them to TypeScriptParameters
        JSParameterListElement[] listElements = runReadActionWithResult(parameterList::getParameters);
        TypeScriptParameter[] parameters = new TypeScriptParameter[listElements.length + 1];

        for (int i = 0; i < listElements.length; i++) {
            parameters[i] = (TypeScriptParameter) listElements[i];
        }
        parameters[parameters.length - 1] = parameter;

        // create a new parameter list with the new parameter and replace the old one with it
        JSParameterList newParameterList = createJSParameterList(parameterList, parameters);
        WriteCommandAction.runWriteCommandAction(parameterList.getProject(), () -> {
            parameterList.replace(newParameterList);
        });
    }

    /**
     * Renames the given element to the given new name. All references to the element are updated.
     *
     * @param element The element to be renamed.
     * @param newName The new name of the element.
     */
    public static void rename(PsiNamedElement element, String newName) {
        RenameProcessor renameProcessor = new RenameProcessor(
                element.getProject(),
                element,
                newName,
                false,
                true
        );

        ApplicationManager.getApplication().runWriteAction(renameProcessor::run);
    }

    /**
     * Makes a given class exportable by adding the export keyword in front of it.
     *
     * @param psiClass The class to be made exportable.
     * @return The class with the export keyword in front of it.
     */
    public static TypeScriptClass makeClassExported(TypeScriptClass psiClass) {

        if (psiClass.isExported()) return psiClass;

        PsiDirectory dir = runReadActionWithResult(() -> psiClass.getContainingFile().getContainingDirectory());
        String fileName = runReadActionWithResult(() -> psiClass.getContainingFile().getName());

        StringBuilder classCode = new StringBuilder();
        classCode.append("export ");
        classCode.append(psiClass.getText());

        PsiFile psiFile = runWriteCommandWithResult(psiClass.getProject(), () -> PsiFileFactory.getInstance(psiClass.getProject()).createFileFromText("PsiUtilTemp.ts", TypeScriptFileType.INSTANCE, classCode));
        TypeScriptClass newClass = runReadActionWithResult(() -> PsiTreeUtil.getChildOfType(psiFile, TypeScriptClass.class));

        assert newClass != null;

        WriteCommandAction.runWriteCommandAction(psiClass.getProject(), () -> {
            psiClass.replace(newClass);
        });

        // this is necessary to get the virtual file later on
        VirtualFile virtualFile = runReadActionWithResult(() -> dir.getVirtualFile().findChild(fileName));
        assert virtualFile != null;

        PsiFile file = runReadActionWithResult(() -> PsiManager.getInstance(dir.getProject()).findFile(virtualFile));
        assert file != null;

        for (TypeScriptClass clazz : runReadActionWithResult(() -> PsiTreeUtil.findChildrenOfType(file, TypeScriptClass.class))) {
            if (Objects.equals(runReadActionWithResult(clazz::getQualifiedName), runReadActionWithResult(psiClass::getQualifiedName))) {
                return clazz;
            }
        }

        CodeSmellLogger.error("Class " + runReadActionWithResult(psiClass::getName) + " not found in file " + runReadActionWithResult(file::getName), new Exception());
        return null;
    }

    /**
     * Removes the brackets surrounding the given text.
     *
     * @param text The text from which the brackets should be removed.
     * @return The text without the brackets.
     */
    public static String removeBrackets(String text) {
        text = text.trim();
        return text.substring(1, text.length() - 1);
    }

    /**
     * Returns all Modifiers of a attributeList as a list of strings.
     *
     * @param attributeList The attributeList of which the modifiers should be returned.
     * @return The modifiers of the attributeList as a list of strings.
     */
    private static List<String> getModifiers(JSAttributeList attributeList) {

        List<String> modifiers = new ArrayList<>();

        // the order of the modifiers is important for the code generation
        ApplicationManager.getApplication().runReadAction(() -> {
            if (attributeList.hasModifier(JSAttributeList.ModifierType.STATIC)) modifiers.add("static");
            if (attributeList.hasModifier(JSAttributeList.ModifierType.READONLY)) modifiers.add("readonly");
            if (attributeList.hasModifier(JSAttributeList.ModifierType.ABSTRACT)) modifiers.add("abstract");
            if (attributeList.hasModifier(JSAttributeList.ModifierType.DECLARE)) modifiers.add("declare");
        });

        return modifiers;
    }

    /**
     * Returns all Modifiers of a field as a list of strings.
     * The visibility of the field is not included in the modifiers.
     *
     * @param field The field of which the modifiers should be returned.
     * @return The modifiers of the field as a list of strings.
     */
    public static List<String> getModifiers(TypeScriptField field) {

        JSAttributeList attributeList = runReadActionWithResult(() -> PsiTreeUtil.getPrevSiblingOfType(field, JSAttributeList.class));

        if (attributeList == null) {
            return new ArrayList<>();
        }

        return new ArrayList<>(getModifiers(attributeList));
    }

    /**
     * Returns all Modifiers of a parameter that is defining a field as a list of strings.
     * The visibility of the parameter is not included in the modifiers.
     *
     * @param parameter The parameter of which the modifiers should be returned.
     * @return The modifiers of the parameter as a list of strings.
     */
    public static List<String> getModifiers(TypeScriptParameter parameter) {

        JSAttributeList attributeList = runReadActionWithResult(() -> PsiTreeUtil.getChildOfType(parameter, JSAttributeList.class));

        assert attributeList != null;

        return new ArrayList<>(getModifiers(attributeList));
    }

    /**
     * Returns all Modifiers of a parameter that is defining a field as a list of strings.
     * The visibility of the parameter is included in the modifiers.
     *
     * @param field The field of which the modifiers should be returned.
     * @return The modifiers of the field as a list of strings including the visibility.
     */
    public static List<String> getModifiersIncludingVisibility(TypeScriptField field) {

        List<String> modifiers = getModifiers(field);
        modifiers.add(runReadActionWithResult(() -> field.getAccessType().toString().toLowerCase()));

        return modifiers;
    }

    /**
     * Returns all Modifiers of a parameter that is defining a field as a list of strings.
     * The visibility of the parameter is included in the modifiers.
     *
     * @param parameter The parameter of which the modifiers should be returned.
     * @return The modifiers of the parameter as a list of strings including the visibility.
     */
    public static List<String> getModifiersIncludingVisibility(TypeScriptParameter parameter) {

        List<String> modifiers = getModifiers(parameter);
        modifiers.add(runReadActionWithResult(() -> parameter.getAccessType().toString().toLowerCase()));

        return modifiers;
    }

    /**
     * Returns given a parameter all classfields that that parameter is assigned to.
     *
     * @param parameter The parameter for which the assigned fields should be returned.
     * @return The classfields that the parameter is assigned to.
     */
    public static List<Classfield> getAssignedToField(TypeScriptParameter parameter) {

        List<Classfield> fields = new ArrayList<>();
        ApplicationManager.getApplication().runReadAction(() -> {
            for (PsiReference reference : ReferencesSearch.search(parameter)) {
                // is the reference an assignment?
                JSAssignmentExpression assignment = PsiTreeUtil.getParentOfType(reference.getElement(), JSAssignmentExpression.class);

                if (assignment != null && assignment.getROperand() == reference) {
                    if (Objects.requireNonNull(assignment.getLOperand()).getFirstChild() instanceof JSReferenceExpression referenceExpression) {
                        Classfield field = resolveField(referenceExpression);
                        if (field != null) fields.add(field);
                    }
                }
            }
        });
        return fields;
    }

    /**
     * Returns all Classfields for a class.
     *
     * @param psiClass The class for which the fields should be returned.
     * @return The fields of the class as a list of Classfields.
     */
    public static List<Classfield> getClassfields(JSClass psiClass) {

        List<Classfield> fields = new ArrayList<>();

        ApplicationManager.getApplication().runReadAction(() -> {
            // iterate all FieldStatements
            for (JSField field : psiClass.getFields()) {
                if (!(field instanceof TypeScriptField) || field.getName() == null || field.getJSType() == null)
                    continue;
                fields.add(new Classfield((TypeScriptField) field));
            }

            // iterate constructor Parameter
            TypeScriptFunction constructor = (TypeScriptFunction) psiClass.getConstructor();
            if (constructor != null) {
                for (JSParameterListElement psiParameter : constructor.getParameters()) {
                    if (!(psiParameter instanceof TypeScriptParameter) || psiParameter.getName() == null || psiParameter.getJSType() == null)
                        continue;
                    // test if parameter is actually field
                    if (isParameterField((TypeScriptParameter) psiParameter)) {
                        fields.add(new Classfield((TypeScriptParameter) psiParameter));
                    }
                }
            }
        });
        return fields;
    }

    /**
     * Returns all PsiElements (FieldStatements and Parameters) that define a field for a class.
     *
     * @param psiClass The class for which the fields should be returned.
     * @return The fields of the class as a list of PsiElements.
     */
    public static List<PsiElement> getPsiFields(JSClass psiClass) {

        List<PsiElement> fields = new ArrayList<>();

        ApplicationManager.getApplication().runReadAction(() -> {
            // add all FieldStatements
            for (JSField field : psiClass.getFields()) {
                if (field.getName() == null || field.getJSType() == null) continue;
                fields.add(field);
            }

            // iterate constructor Parameter
            TypeScriptFunction constructor = (TypeScriptFunction) psiClass.getConstructor();
            if (constructor != null) {
                for (JSParameterListElement psiParameter : constructor.getParameters()) {
                    // test if parameter is actually field
                    // filter out unfinished fields
                    if (psiParameter instanceof TypeScriptParameter && isParameterField((TypeScriptParameter) psiParameter) && psiParameter.getName() != null && psiParameter.getJSType() != null) {
                        fields.add(psiParameter);
                    }
                }
            }
        });
        return fields;

    }

    /**
     * Returns the classfield with the given name in the given class.
     *
     * @param psiClass  The class in which the field should be found.
     * @param fieldName The name of the field to be found.
     * @return The classfield with the given name in the given class. Null if the field is not found.
     */
    public static Classfield getClassfield(TypeScriptClass psiClass, String fieldName) {

        for (Classfield classfield : getClassfields(psiClass)) {
            if (classfield.getName().equals(fieldName)) return classfield;
        }

        return null;
    }

    /**
     * Returns the PsiElement (Parameter or Field) from a class that corresponds to the given classfield.
     *
     * @param psiClass   The class in which the property should be found.
     * @param classfield The classfield to be found.
     * @return The PsiElement that corresponds to the given classfield. Null if the classfield is not found.
     */
    public static @Nullable PsiElement getPsiField(JSClass psiClass, Classfield classfield) {
        for (PsiElement element : getPsiFields(psiClass)) {
            if (element instanceof TypeScriptField field && classfield.matches(new Classfield(field))) {
                return field;
            }
            if (element instanceof TypeScriptParameter parameter && classfield.matches(new Classfield(parameter))) {
                return parameter;
            }
        }
        CodeSmellLogger.warn("Field " + classfield.getName() + " not found in class " + runReadActionWithResult(psiClass::getName));
        return null;
    }

    /**
     * Returns the PsiElement (Parameter or Field) from a class that corresponds to the given name.
     *
     * @param psiClass The class in which the property should be found.
     * @param name     The name of the property to be found.
     * @return The PsiElement that corresponds to the given name. Null if the property is not found.
     */
    public static PsiElement getPsiField(JSClass psiClass, String name) {
        for (PsiElement element : getPsiFields(psiClass)) {
            Classfield classfield = null;
            if (element instanceof TypeScriptField field) {
                classfield = new Classfield(field);
            } else if (element instanceof TypeScriptParameter parameter) {
                classfield = new Classfield(parameter);
            }
            assert classfield != null;
            if (classfield.getName().equals(name)) {
                return element;
            }
        }

        CodeSmellLogger.warn("Field " + name + " not found in class " + runReadActionWithResult(psiClass::getName));
        return null;
    }

    /**
     * Returns the TypeScriptParameter from a function that corresponds to the given parameter.
     *
     * @param function  The function in which the parameter should be found.
     * @param parameter The parameter to be found.
     * @return The TypeScriptParameter that corresponds to the given parameter. Null if the parameter is not found.
     */
    public static TypeScriptParameter getPsiParameter(TypeScriptFunction function, Parameter parameter) {

        for (JSParameterListElement psiParameter : runReadActionWithResult(function::getParameters)) {
            if (!(psiParameter instanceof TypeScriptParameter)) continue;
            if (parameter.equals(new Parameter((TypeScriptParameter) psiParameter))) {
                return (TypeScriptParameter) psiParameter;
            }
        }

        CodeSmellLogger.warn("Parameter " + parameter + " not found in function " + runReadActionWithResult(function::getName));
        return null;
    }

    /**
     * Returns the TypeScriptParameter from a function that corresponds to the given name.
     *
     * @param function The function in which the parameter should be found.
     * @param name     The name of the parameter to be found.
     * @return The TypeScriptParameter that corresponds to the given name. Null if the parameter is not found.
     */
    public static TypeScriptParameter getPsiParameter(TypeScriptFunction function, String name) {

        // make sure that underscore is not part of the name
        if (name.startsWith("_")) {
            name = name.substring(1);
        }

        for (JSParameterListElement psiParameter : runReadActionWithResult(function::getParameters)) {
            // make sure that underscore is not part of the name
            String parameterName = runReadActionWithResult(psiParameter::getName);
            if (parameterName != null && parameterName.startsWith("_")) {
                parameterName = parameterName.substring(1);
            }
            if (Objects.equals(parameterName, name)) {
                return (TypeScriptParameter) psiParameter;
            }
        }

        CodeSmellLogger.warn("Parameter " + name + " not found in function " + runReadActionWithResult(function::getName));
        return null;
    }

    /**
     * Returns the name of the given element if it has one or "anonymous" otherwise.
     *
     * @param element The element of which the name should be returned.
     * @return The name of the given element if it has one or "anonymous" otherwise.
     */
    public static String getName(PsiElement element) {
        String name = "anonymous";
        if (element instanceof PsiNamedElement namedElement) {
            name = runReadActionWithResult(namedElement::getName);
        }
        return name;
    }

    /**
     * Returns the qualified name of the given element if it has one or the name otherwise.
     * The qualified name is the name of the element including the names of its parent elements.
     *
     * @param element The element of which the qualified name should be returned.
     * @return The qualified name of the given element if it has one or the name otherwise.
     */
    public static String getQualifiedName(PsiElement element) {
        // functions and classes are not PsiQualifiedNamedElements but have a qualified name
        if (element instanceof TypeScriptFunction function) {
            String name = runReadActionWithResult(function::getQualifiedName);
            if (name != null) return name;
        } else if (element instanceof TypeScriptClass clazz) {
            String name = runReadActionWithResult(clazz::getQualifiedName);
            if (name != null) return name;
        } else if (element instanceof PsiQualifiedNamedElement) {
            return runReadActionWithResult(((PsiQualifiedNamedElement) element)::getQualifiedName);
        }
        return getName(element);
    }

    /**
     * Returns weather the given class has a setter for the given classfield.
     * If the classfield is public, it is considered to have a setter.
     * Otherwise, checks if there is a set function with the same name as the classfield.
     * The function does not check if the setter actually sets the field. It only checks if a setter exists.
     *
     * @param psiClass   The class in which the setter should be found.
     * @param classfield The classfield for which the setter should be found.
     */
    public static boolean hasSetter(TypeScriptClass psiClass, Classfield classfield) {

        if (runReadActionWithResult(classfield::isPublic)) return true;

        for (JSFunction psiFunction : runReadActionWithResult(psiClass::getFunctions)) {
            if (!runReadActionWithResult(psiFunction::isSetProperty)) continue;

            String setterName = runReadActionWithResult(psiFunction::getName);
            String fieldName = classfield.getName();

            if (setterName == null) continue;

            if (setterName.equals(fieldName)) return true;
        }
        return false;
    }

    /**
     * Returns weather the given class has a getter for the given classfield.
     * If the classfield is public, it is considered to have a getter.
     * Otherwise, checks if there is a get function with the same name as the classfield.
     * The function does not check if the getter actually gets the field. It only checks if a getter exists.
     *
     * @param psiClass   The class in which the getter should be found.
     * @param classfield The classfield for which the getter should be found.
     */
    public static boolean hasGetter(TypeScriptClass psiClass, Classfield classfield) {

        if (runReadActionWithResult(classfield::isPublic)) return true;

        for (JSFunction psiFunction : runReadActionWithResult(psiClass::getFunctions)) {
            if (!runReadActionWithResult(psiFunction::isGetProperty)) continue;

            String getterName = runReadActionWithResult(psiFunction::getName);
            String fieldName = classfield.getName();

            if (getterName == null) continue;
            if (getterName.equals(fieldName)) return true;
        }
        return false;
    }

    /**
     * Returns weather the given parameter is defining a field.
     *
     * @param parameter The parameter to be checked.
     * @return True if the parameter is defining a field, false otherwise.
     */
    public static boolean isParameterField(TypeScriptParameter parameter) {
        return Objects.requireNonNull(runReadActionWithResult(() -> PsiTreeUtil.getChildOfType(parameter, JSAttributeList.class))).getTextLength() > 0;
    }

    /**
     * Returns weather the given class has all the given properties.
     *
     * @param psiClass   The class in which the properties should be found.
     * @param properties The properties to be found.
     * @return True if the class has all the given properties, false otherwise.
     */
    public static boolean hasAll(TypeScriptClass psiClass, List<Property> properties) {

        List<Classfield> classProperties = Index.getClassesToClassFields().get(psiClass);

        for (Property property : properties) {
            if (!classProperties.contains(property)) return false;
            if (property instanceof Classfield && !classProperties.get(classProperties.indexOf(property)).matches((Classfield) property))
                return false;
        }
        return true;
    }

    /**
     * Returns for a given reference the corresponding Classfield.
     *
     * @param reference The reference for which the Classfield should be resolved.
     * @return The Classfield that corresponds to the given reference.
     * Null if the reference does not correspond to a Classfield.
     */
    public static Classfield resolveField(JSReferenceExpression reference) {

        PsiElement definition = runReadActionWithResult(reference::resolve);
        if (definition instanceof TypeScriptField tsField) {
            return new Classfield(tsField);
        }
        if (definition instanceof TypeScriptParameter tsParameter && PsiUtil.isParameterField(tsParameter)) {
            return new Classfield(tsParameter);
        }
        // check if the reference is a setter for a property (assuming getters are named the same as the property)
        if (definition instanceof TypeScriptFunction tsFunction && runReadActionWithResult(tsFunction::isSetProperty)) {
            TypeScriptClass psiClass = runReadActionWithResult(() -> PsiTreeUtil.getParentOfType(tsFunction, TypeScriptClass.class));
            if (psiClass != null) {
                return getClassfield(psiClass, reference.getReferenceName());
            }
        }
        return null;
    }

    /**
     * Returns for a given reference the corresponding Property.
     *
     * @param reference The reference for which the Property should be resolved.
     * @return The Property that corresponds to the given reference.
     * Null if the reference does not correspond to a Property.
     */
    public static Property resolveProperty(JSReferenceExpression reference) {
        PsiElement definition = runReadActionWithResult(reference::resolve);

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

    /**
     * Returns the relative path from one file to another. In the way that the path can be used in an import statement.
     *
     * @param from The file from which the path should be calculated.
     * @param to   The file to which the path should be calculated.
     * @return The relative path from one file to another.
     */
    public static String getRelativePath(PsiFile from, PsiFile to) {
        String fromPathString = runReadActionWithResult(from.getVirtualFile()::getPath);
        String toPathString = runReadActionWithResult(to.getVirtualFile()::getPath);

        Path fromPath = Paths.get(fromPathString);
        Path toPath = Paths.get(toPathString);

        Path relativePath = fromPath.relativize(toPath);

        String relativePathString = relativePath.toString();

        return "./" + relativePathString.replace("\\", "/").substring(3, relativePathString.length() - 3);
    }

    /**
     * Runs the given action as a read action and returns the result.
     *
     * @param action the action to be run
     * @param <T>    the type of the result
     * @return the result of the action
     */
    public static <T> T runReadActionWithResult(Supplier<T> action) {
        return ReadAction.compute(action::get);
    }

    /**
     * Runs the given action as a write action and returns the result.
     *
     * @param project the project in which the action should be run
     * @param action  the action to be run
     */
    public static <T> T runWriteCommandWithResult(Project project, Supplier<T> action) {
        return WriteCommandAction.writeCommandAction(project).compute(action::get);
    }


}
