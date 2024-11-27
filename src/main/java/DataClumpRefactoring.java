import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.lang.ecmascript6.psi.impl.ES6FieldStatementImpl;
import com.intellij.lang.javascript.TypeScriptFileType;
import com.intellij.lang.javascript.psi.*;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptClass;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptField;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptFunction;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptParameter;
import com.intellij.lang.javascript.psi.impl.JSPsiElementFactory;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import util.*;

import java.util.*;

public class DataClumpRefactoring implements LocalQuickFix {

    private final List<Property> matchingProperties;
    private final PsiElement currentElement;
    private final PsiElement otherElement;

    public DataClumpRefactoring(PsiElement currentElement, PsiElement otherElement, List<Property> matchingProperties) {
        this.matchingProperties = matchingProperties;
        this.currentElement = currentElement;
        this.otherElement = otherElement;
    }

    @Override
    public @IntentionFamilyName @NotNull String getFamilyName() {
        if (otherElement instanceof TypeScriptClass) {
            return "Refactor Data Clump with " + ((TypeScriptClass) otherElement).getQualifiedName();
        } else if (otherElement instanceof TypeScriptFunction) {
            return "Refactor Data Clump with " + ((TypeScriptFunction) otherElement).getQualifiedName();
        }
        return "Refactor Data Clump (Error)";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor problemDescriptor) {

        DataClumpDialog dialog = new DataClumpDialog(matchingProperties, currentElement);

        if (!dialog.showAndGet()) return;

        CodeSmellLogger.info("Refactoring DataClump between " + currentElement + " and " + otherElement);

        List<Property> selectedProperties = dialog.getProperties();
        CodeSmellLogger.info("Selected Properties: " + selectedProperties);

        TypeScriptClass extractedClass;
        HashMap<Classfield, TypeScriptParameter> currentConstructorParameters = new HashMap<>();
        HashMap<Classfield, TypeScriptParameter> otherConstructorParameters = new HashMap<>();
        HashMap<Classfield, String> currentDefaultValues = new HashMap<>();
        HashMap<Classfield, String> otherDefaultValues = new HashMap<>();

        if (currentElement instanceof TypeScriptClass) {
            currentConstructorParameters = getRefactorableParameters((TypeScriptClass) currentElement, selectedProperties);
            currentDefaultValues = getDefaultValues((TypeScriptClass) currentElement, selectedProperties);
        }
        if (otherElement instanceof TypeScriptClass) {
            otherConstructorParameters = getRefactorableParameters((TypeScriptClass) otherElement, selectedProperties);
            otherDefaultValues = getDefaultValues((TypeScriptClass) otherElement, selectedProperties);
        }


        if (dialog.shouldCreateNewClass()) {
            String className = dialog.getClassName();
            PsiDirectory targetDirectory = dialog.getDirectory();

            CodeSmellLogger.info("Creating new class with name " + className + " in " + targetDirectory);


            // Erstellen der neuen Klasse
            List<Property> optional = getOptionalProperties(selectedProperties, currentConstructorParameters, otherConstructorParameters, currentDefaultValues, otherDefaultValues);
            extractedClass = extractClass(targetDirectory, className, selectedProperties, optional);
        } else {
            extractedClass = dialog.getSelectedClass();
            CodeSmellLogger.info("Using existing class " + extractedClass.getQualifiedName());
        }


        // Refaktorieren der beteiligten Elemente
        refactorElement(currentElement, extractedClass, selectedProperties, currentConstructorParameters, currentDefaultValues);
        refactorElement(otherElement, extractedClass, selectedProperties, otherConstructorParameters, otherDefaultValues);
    }

    private HashMap<Classfield, TypeScriptParameter> getRefactorableParameters(TypeScriptClass psiClass, List<Property> properties) {

        HashMap<Classfield, TypeScriptParameter> constructorParameters = new HashMap<>();

        TypeScriptFunction constructor = (TypeScriptFunction) psiClass.getConstructor();
        if (constructor == null) return constructorParameters;

        // iterate over all parameters of the constructor
        for (JSParameterListElement parameter : constructor.getParameters()) {

            Classfield correspondingClassfield = null;
            boolean canBeReplaced = true;

            // if the parameter is a field of the class
            if (PsiUtil.isParameterField((TypeScriptParameter) parameter)) {
                correspondingClassfield = new Classfield((TypeScriptParameter) parameter);
            } else {
                // check if the parameter is assigned in the constructor to a field of the class that is in the properties list
                for (PsiReference reference : ReferencesSearch.search(parameter)) {
                    // is the reference an assignment?
                    JSAssignmentExpression assignment = PsiTreeUtil.getParentOfType(reference.getElement(), JSAssignmentExpression.class);

                    if (assignment != null && assignment.getROperand() == reference) {
                        correspondingClassfield = resolveAssignedField(assignment.getLOperand());

                    } else if (assignment != null && assignment.getLOperand().getFirstChild() == reference) {
                        canBeReplaced = false;
                    }
                }
            }
            if (properties.contains(correspondingClassfield) && canBeReplaced) {
                constructorParameters.put(correspondingClassfield, (TypeScriptParameter) parameter);
            }
        }
        CodeSmellLogger.info("Constructor Parameters that can be refactord: " + constructorParameters + " of " + constructor);
        return constructorParameters;
    }

    private Classfield resolveAssignedField(JSExpression leftOperand) {
        if (!(leftOperand.getFirstChild() instanceof JSReferenceExpression reference)) return null;

        PsiElement definition = reference.resolve();
        if (definition instanceof TypeScriptField tsField) {
            return new Classfield(tsField);
        }
        if (definition instanceof TypeScriptParameter tsParameter && PsiUtil.isParameterField(tsParameter)) {
            return new Classfield(tsParameter);
        }
        return null;
    }

    private HashMap<Classfield, String> getDefaultValues(TypeScriptClass psiClass, List<Property> properties) {
        HashMap<Classfield, String> defaultValues = new HashMap<>();
        HashMap<Classfield, PsiElement> fieldsToElement = PsiUtil.getFieldsToElement(psiClass);

        for (Map.Entry<Classfield, PsiElement> entry : fieldsToElement.entrySet()) {
            Classfield classfield = entry.getKey();
            PsiElement psiField = entry.getValue();

            if (properties.contains(classfield)) {
                JSExpression initializer;
                if (psiField instanceof TypeScriptField) {
                    initializer = ((TypeScriptField) psiField).getInitializer();
                } else {
                    initializer = ((TypeScriptParameter) psiField).getInitializer();
                }
                if (initializer != null) {
                    defaultValues.put(classfield, initializer.getText());
                }
            }
        }
        return defaultValues;
    }

    private List<Property> getOptionalProperties(List<Property> properties, HashMap<Classfield, TypeScriptParameter> currentConstructorParameters, HashMap<Classfield, TypeScriptParameter> otherConstructorParameters, HashMap<Classfield, String> currentDefaultValues, HashMap<Classfield, String> otherDefaultValues) {
        List<Property> optionalProperties = new ArrayList<>();
        if (currentElement instanceof TypeScriptClass) {
            for (Property property : properties) {
                if (!currentConstructorParameters.containsKey(property) && !currentDefaultValues.containsKey(property)) {
                    optionalProperties.add(property);
                }
            }
        }
        if (otherElement instanceof TypeScriptClass) {
            for (Property property : properties) {
                if (optionalProperties.contains(property)) continue;
                if (!otherConstructorParameters.containsKey(property) && !otherDefaultValues.containsKey(property)) {
                    optionalProperties.add(property);
                }
            }
        }

        return optionalProperties;
    }

    private void refactorElement(PsiElement element, TypeScriptClass extractedClass, List<Property> properties, HashMap<Classfield, TypeScriptParameter> constructorParameters, HashMap<Classfield, String> defaultValues) {
        if (element instanceof TypeScriptClass) {
            refactorClass((TypeScriptClass) element, extractedClass, properties, constructorParameters, defaultValues);
        } else if (element instanceof TypeScriptFunction) {
            refactorFunction((TypeScriptFunction) element, extractedClass, properties);
        }
    }

    private void refactorClass(TypeScriptClass psiClass, TypeScriptClass extractedClass, List<Property> properties, HashMap<Classfield, TypeScriptParameter> constructorParameters, HashMap<Classfield, String> defaultValues) {
        CodeSmellLogger.info("Refactoring class " + psiClass.getQualifiedName() + "...");

        String fieldName = extractedClass.getName().toLowerCase();

        // Neues Feld hinzufügen
        addNewFieldToClass(psiClass, extractedClass, fieldName);

        // Field-Usages aktualisieren
        updateFieldReferences(psiClass, properties, fieldName);

        // Konstruktor aktualisieren
        updateConstructor(psiClass, properties, extractedClass, fieldName, constructorParameters, defaultValues);

        CodeSmellLogger.info("Class refactord.");
    }

    private void addNewFieldToClass(TypeScriptClass psiClass, TypeScriptClass extractedClass, String fieldName) {

        ES6FieldStatementImpl newFieldStatement = PsiUtil.createJSFieldStatement(
                psiClass, fieldName, extractedClass.getJSType(), "private"
        );

        PsiElement[] existingFields = psiClass.getFields();
        PsiElement insertPosition = (existingFields.length > 0)
                ? PsiTreeUtil.getParentOfType(existingFields[existingFields.length - 1], ES6FieldStatementImpl.class)
                : PsiTreeUtil.getChildOfType(psiClass, TypeScriptFunction.class);

        WriteCommandAction.runWriteCommandAction(psiClass.getProject(), () -> {
            psiClass.addBefore(newFieldStatement, insertPosition);
        });

    }

    private void updateConstructor(TypeScriptClass psiClass, List<Property> properties, TypeScriptClass extractedClass, String fieldName, HashMap<Classfield, TypeScriptParameter> constructorParameters, HashMap<Classfield, String> defaultValues) {
        TypeScriptFunction constructor = (TypeScriptFunction) psiClass.getConstructor();
        if (constructor == null) return;

        introduceParameterObjectForConstructor(properties, constructor, extractedClass, fieldName, constructorParameters, defaultValues);

        JSStatement initialization = JSPsiElementFactory.createJSStatement(
                "this." + fieldName + " = " + fieldName + ";", psiClass
        );

        WriteCommandAction.runWriteCommandAction(psiClass.getProject(), () -> {
            constructor.getBlock().addAfter(initialization, constructor.getBlock().getFirstChild());
        });

    }

    private void updateFieldReferences(TypeScriptClass psiClass, List<Property> properties, String fieldName) {

        HashMap<Classfield,PsiElement> fieldsToElement = PsiUtil.getFieldsToElement(psiClass);

        for (Map.Entry<Classfield,PsiElement> entry : fieldsToElement.entrySet()) {
            Classfield classfield = entry.getKey();
            PsiElement psiField = entry.getValue();

            if (properties.contains(classfield)) {

                for (PsiReference reference : ReferencesSearch.search(psiField)) {
                    // if it is assignment -> refactor to setter
                    JSAssignmentExpression assignmentExpression = PsiTreeUtil.getParentOfType(reference.getElement(), JSAssignmentExpression.class);
                    if (assignmentExpression != null && assignmentExpression.getLOperand().getFirstChild() == reference) {
                        replaceAssignmentWithSetter(assignmentExpression, fieldName, classfield);
                    } else { // if no assignment refactor to getter
                        replaceReferenceWithGetter(reference, fieldName, classfield);
                    }
                }


                if (psiField instanceof TypeScriptField) {
                    WriteCommandAction.runWriteCommandAction(psiClass.getProject(), () -> {
                        psiField.delete();
                    });
                }
            }
        }
    }

    private void replaceAssignmentWithSetter(JSAssignmentExpression assignment, String fieldName, Classfield field) {
        JSExpression newExpression = JSPsiElementFactory.createJSExpression(
                "this." + fieldName + "." + field.getName() + " = " + assignment.getROperand().getText(), assignment
        );
        WriteCommandAction.runWriteCommandAction(assignment.getProject(), () -> {
            assignment.replace(newExpression);
        });
    }

    private void replaceReferenceWithGetter(PsiReference reference, String fieldName, Classfield property) {
        JSExpression newExpression = JSPsiElementFactory.createJSExpression(
                "this." + fieldName + "." + property.getName(), reference.getElement()
        );
        WriteCommandAction.runWriteCommandAction(reference.getElement().getProject(), () -> {
            reference.getElement().replace(newExpression);
        });
    }

    private void refactorFunction(TypeScriptFunction psiFunction, TypeScriptClass extractedClass, List<Property> properties) {
        CodeSmellLogger.info("Refactoring function " + psiFunction.getQualifiedName() + "...");

        String newParameterName = extractedClass.getQualifiedName();

        introduceParameterObject(properties, psiFunction, extractedClass, newParameterName);

        CodeSmellLogger.info("Function refactored.");
    }

    private void refactorConstructorParameter(TypeScriptFunction constructor, HashMap<Classfield, TypeScriptParameter> constructorParameter, String newParameterName) {
        for (Classfield property : constructorParameter.keySet()) {
            TypeScriptParameter parameter = constructorParameter.get(property);

            // replace references with the new parameter object
            for (PsiReference reference : ReferencesSearch.search(parameter)) {
                replaceReferenceWithGetter(reference, newParameterName, property);
            }

            // remove the parameter from the constructor
            WriteCommandAction.runWriteCommandAction(constructor.getProject(), parameter::delete);
        }
    }

    private void refactorFunctionParameter(TypeScriptFunction function, List<Property> properties, String newParameterName) {
        // Process the function's current parameters
        for (JSParameterListElement parameter : function.getParameters()) {
            // Replace references to selected parameters with getter calls on the new object
            Parameter currentParameter = new Parameter((TypeScriptParameter) parameter);
            if (properties.contains(currentParameter)) {
                for (PsiReference reference : ReferencesSearch.search(parameter)) {
                    JSExpression newExpression = JSPsiElementFactory.createJSExpression(
                            newParameterName + "." + currentParameter.getName(), reference.getElement()
                    );
                    WriteCommandAction.runWriteCommandAction(reference.getElement().getProject(), () -> {
                        reference.getElement().replace(newExpression);
                    });
                }
                // Remove the parameter from the function's signature
                WriteCommandAction.runWriteCommandAction(function.getProject(), parameter::delete);
            }
        }
    }

    private void addClassAsParameter(TypeScriptFunction function, TypeScriptClass extractedClass, String newParameterName) {
        JSParameterList parameterList = function.getParameterList();
        TypeScriptParameter newParameter = PsiUtil.createTypeScriptParameter(function, newParameterName, extractedClass.getJSType());
        PsiUtil.addParameterToParameterList(newParameter, parameterList);
    }

    private List<Property> getParametersAsPropertyList(TypeScriptFunction function) {
        List<Property> parameters = new ArrayList<>();
        for (JSParameterListElement parameter : function.getParameters()) {
            Property property = new Parameter((TypeScriptParameter) parameter);
            parameters.add(property);
        }
        return parameters;
    }

    private void refactorFunctionCalls(TypeScriptFunction function, List<Property> originalParameters, TypeScriptClass extractedClass, HashMap<Classfield, TypeScriptParameter> refactorableParameter, HashMap<Classfield, String> defaultValues) {
        CodeSmellLogger.info("Refactoring function calls...");
        List<Property> extractedParameters = getParametersAsPropertyList((TypeScriptFunction) extractedClass.getConstructor());

        for (PsiReference functionCall : ReferencesSearch.search(function)) {
            CodeSmellLogger.info("Refactoring function call " + functionCall.getElement().getText());
            JSArgumentList argumentList = PsiTreeUtil.getNextSiblingOfType(functionCall.getElement(), JSArgumentList.class);
            JSExpression[] originalArguments = argumentList.getArguments();

            StringBuilder updatedArguments = new StringBuilder("(");

            for (JSParameterListElement parameter : function.getParameters()) {
                CodeSmellLogger.info("Checking parameter " + parameter.getText());
                if (parameter.getJSType().equals(extractedClass.getJSType())) {
                    CodeSmellLogger.info("Creating new parameter object for " + parameter.getText());
                    // Replace with a constructor call for the new parameter object
                    updatedArguments.append("new ").append(extractedClass.getName()).append("(");
                    for (Property property : extractedParameters) {
                        CodeSmellLogger.info("Checking property " + property.getName());
                        if (!function.isConstructor()) {
                            int index = originalParameters.indexOf(property);
                            if (index == -1) {
                                CodeSmellLogger.error("Property " + property.getName() + " not found in original parameters", new IndexOutOfBoundsException());
                                continue;
                            }
                            CodeSmellLogger.info("Found in original Parameters " + property.getName() + " at index " + index);
                            CodeSmellLogger.info("Argument: " + originalArguments[index].getText());
                            updatedArguments.append(originalArguments[index].getText()).append(", ");
                        } else if (refactorableParameter.containsKey(property)) {
                            CodeSmellLogger.info("Found refactorable property " + property.getName());
                            CodeSmellLogger.info("Argument: " + refactorableParameter.get(property).getText());
                            int index = originalParameters.indexOf(new Parameter(refactorableParameter.get(property)));
                            updatedArguments.append(originalArguments[index].getText()).append(", ");
                        }
                        else if (defaultValues.containsKey(property)) {
                            CodeSmellLogger.info("Found default value for property " + property.getName());
                            updatedArguments.append(defaultValues.get(property)).append(", ");
                        } else {
                            CodeSmellLogger.info("Property " + property.getName() + " not found -> using undefined");
                            updatedArguments.append("undefined, ");
                        }
                    }
                    // Remove trailing comma
                    if (updatedArguments.charAt(updatedArguments.length() - 2) == ',') {
                        updatedArguments.setLength(updatedArguments.length() - 2);
                    }
                    updatedArguments.append(")");
                } else {
                    // Append remaining original arguments
                    CodeSmellLogger.info("Appending original argument " + originalArguments[originalParameters.indexOf(new Parameter((TypeScriptParameter) parameter))].getText());
                    updatedArguments.append(originalArguments[originalParameters.indexOf(new Parameter((TypeScriptParameter) parameter))].getText());
                }
                updatedArguments.append(", ");
            }

            // Remove trailing comma and close the argument list
            if (updatedArguments.charAt(updatedArguments.length() - 2) == ',') {
                updatedArguments.setLength(updatedArguments.length() - 2);
            }
            updatedArguments.append(")");

            // Update the function call with the new argument list
            WriteCommandAction.runWriteCommandAction(function.getProject(), () -> {
                JSExpression newArguments = JSPsiElementFactory.createJSExpression(updatedArguments.toString(), argumentList);
                argumentList.replace(newArguments);
            });

        }

    }

    private void introduceParameterObjectForConstructor(List<Property> properties, TypeScriptFunction constructor, TypeScriptClass extractedClass, String newParameterName, HashMap<Classfield, TypeScriptParameter> constructorParameter, HashMap<Classfield, String> defaultValues) {

        List<Property> originalParameters = getParametersAsPropertyList(constructor);

        refactorConstructorParameter(constructor, constructorParameter, newParameterName);

        addClassAsParameter(constructor, extractedClass, newParameterName);

        refactorFunctionCalls(constructor, originalParameters, extractedClass, constructorParameter, defaultValues);

    }

    public void introduceParameterObject(List<Property> properties, TypeScriptFunction function, TypeScriptClass extractedClass, String newParameterName ) {

        List<Property> originalParameters = getParametersAsPropertyList(function);

        refactorFunctionParameter(function, properties, newParameterName);

        // Add the extracted class as a new parameter
        addClassAsParameter(function, extractedClass, newParameterName);

        refactorFunctionCalls(function, originalParameters, extractedClass, new HashMap<>(), new HashMap<>());

    }

    public TypeScriptClass extractClass(PsiDirectory dir, String className, List<Property> fields, List<Property> optionalFields) {
        CodeSmellLogger.info("Extracting class...");

        StringBuilder classCode = new StringBuilder();
        classCode.append("class " + className + " {\n\n");

        // constructor
        classCode.append("  constructor(");

        List<Property> requiredFields = new ArrayList<>(fields);
        requiredFields.removeAll(optionalFields);

        for (Property field : requiredFields) {
            final String fieldName = field.getName();
            final String fieldType = field.getType().getTypeText();

            if (field instanceof Classfield) {
                List<String> modifiers = ((Classfield) field).getModifier();
                for (String modifier :modifiers) {
                    classCode.append(modifier + " ");
                }
                // If the field is public, do not use the underscore prefix
                if (modifiers.contains("public")) {
                    classCode.append(fieldName + ": " + fieldType + ", ");
                } else {
                    classCode.append("_" + fieldName + ": " + fieldType + ", ");
                }
            } else {
                // For Parameters use the private as default visibility
                classCode.append("private _" + fieldName + ": " + fieldType + ", ");
            }
        }
        for (Property field : optionalFields) {
            final String fieldName = field.getName();
            final String fieldType = field.getType().getTypeText();

            if (field instanceof Classfield) {
                List<String> modifiers = ((Classfield) field).getModifier();
                for (String modifier :modifiers) {
                    classCode.append(modifier + " ");
                }
                // If the field is public, do not use the underscore prefix
                if (modifiers.contains("public")) {
                    classCode.append(fieldName + "?: " + fieldType + ", ");
                } else {
                    classCode.append("_" + fieldName + "?: " + fieldType + ", ");
                }
            } else {
                // For Parameters use the private as default visibility
                classCode.append("private _" + fieldName + "?: " + fieldType + ", ");
            }
        }


        // Remove trailing comma and close the constructor
        if (!fields.isEmpty()) {
            classCode.setLength(classCode.length() - 2);
        }

        classCode.append(") {}\n\n");

        // Getter and Setter
        for (Property field : fields) {
            // Skip public fields as they do not need getter and setter
            if (field instanceof Classfield && ((Classfield) field).getModifier().contains("public")) continue;

            final String fieldName = field.getName();
            final String fieldType = field.getType().getTypeText();
            if (optionalFields.contains(field)) {
                classCode.append("  get " + fieldName + "(): " + fieldType + " | undefined {\n");
            } else {
                classCode.append("  get " + fieldName + "(): " + fieldType + " {\n");
            }
            classCode.append("    return this._" + fieldName + ";\n");
            classCode.append("  }\n\n");

            classCode.append("  set " + fieldName + "(value: " + fieldType + ") {\n");
            classCode.append("    this._" + fieldName + " = value;\n");
            classCode.append("  }\n\n");
        }

        classCode.append("}\n");

        // Create the new class file and add it to the directory
        final PsiFile[] psiFile = new PsiFile[1];

        WriteCommandAction.runWriteCommandAction(dir.getProject(), () -> {
            psiFile[0] = PsiFileFactory.getInstance(dir.getProject()).createFileFromText(className +" .ts", TypeScriptFileType.INSTANCE, classCode);
            dir.add(psiFile[0]);
        });

        CodeSmellLogger.info("Class extracted.");
        return PsiTreeUtil.getChildOfType(psiFile[0], TypeScriptClass.class);
    }

    @Override
    public boolean startInWriteAction() {
        // This quick fix uses a dialog to gather user input, so it should not start in a write action
        return false;
    }
}
