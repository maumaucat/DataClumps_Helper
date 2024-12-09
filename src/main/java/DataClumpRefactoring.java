import Settings.DataClumpSettings;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.lang.ecmascript6.psi.ES6ImportDeclaration;
import com.intellij.lang.ecmascript6.psi.ES6ImportSpecifier;
import com.intellij.lang.ecmascript6.psi.impl.ES6FieldStatementImpl;
import com.intellij.lang.javascript.psi.*;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptClass;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptField;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptFunction;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptParameter;
import com.intellij.lang.javascript.psi.ecmal4.JSImportStatement;
import com.intellij.lang.javascript.psi.impl.JSPsiElementFactory;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
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

    private HashMap<Classfield, TypeScriptParameter> currentDefinedClassFields = new HashMap<>();
    private HashMap<Parameter, Classfield> currentDefiningParameters = new HashMap<>();
    private HashMap<Parameter, Classfield> otherDefiningParameters = new HashMap<>();
    private HashMap<Classfield, TypeScriptParameter> otherDefinedClassFields = new HashMap<>();
    private HashMap<Classfield, String> currentDefaultValues = new HashMap<>();
    private HashMap<Classfield, String> otherDefaultValues = new HashMap<>();

    public DataClumpRefactoring(PsiElement currentElement, PsiElement otherElement, List<Property> matchingProperties) {
        this.matchingProperties = matchingProperties;
        this.currentElement = currentElement;
        this.otherElement = otherElement;
    }

    @Override
    public @IntentionFamilyName @NotNull String getFamilyName() {

        String title = "";
        if (otherElement instanceof TypeScriptClass) {
            title = "Refactor Data Clump with " + ((TypeScriptClass) otherElement).getQualifiedName();
        } else if (otherElement instanceof TypeScriptFunction) {
            title = "Refactor Data Clump with " + ((TypeScriptFunction) otherElement).getQualifiedName();
        } else {
            CodeSmellLogger.error("Invalid element type for DataClumpRefactoring: " + otherElement.getClass(), new IllegalArgumentException());
            title = "Error refactor data clump";
        }
        return title;
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor problemDescriptor) {

        DataClumpDialog dialog = new DataClumpDialog(matchingProperties, currentElement, otherElement);

        if (!dialog.showAndGet()) return;

        CodeSmellLogger.info("Refactoring DataClump between " + currentElement + " and " + otherElement);

        List<Property> selectedProperties = dialog.getProperties();
        CodeSmellLogger.info("Selected Properties: " + selectedProperties);

        TypeScriptClass extractedClass;

        if (currentElement instanceof TypeScriptClass) {
            TypeScriptFunction constructor = (TypeScriptFunction) ((TypeScriptClass) currentElement).getConstructor();
            if (constructor != null) {
                getClassfieldDefiningParameter(constructor, currentDefiningParameters, currentDefinedClassFields);
            }
            getDefaultValues((TypeScriptClass) currentElement, selectedProperties, currentDefaultValues);
        }
        if (otherElement instanceof TypeScriptClass) {
            TypeScriptFunction constructor = (TypeScriptFunction) ((TypeScriptClass) otherElement).getConstructor();
            if (constructor != null) {
                getClassfieldDefiningParameter(constructor, otherDefiningParameters, otherDefinedClassFields);
            }
            getDefaultValues((TypeScriptClass) otherElement, selectedProperties, otherDefaultValues);
        }

        Set<Property> optional = getOptionalProperties(selectedProperties);

        if (dialog.shouldCreateNewClass()) {
            String className = dialog.getClassName();
            PsiDirectory targetDirectory = dialog.getDirectory();

            CodeSmellLogger.info("Creating new class with name " + className + " in " + targetDirectory);
            extractedClass = extractClass(targetDirectory, className, selectedProperties, optional);
        } else {
            extractedClass = dialog.getSelectedClass();
            // save the original parameters
            List<Property> originalParameters = new ArrayList<>();
            if (extractedClass.getConstructor() != null) {
                originalParameters = getParametersAsPropertyList((TypeScriptFunction) extractedClass.getConstructor());
            }
            extractedClass = PsiUtil.makeClassExported(extractedClass);
            adjustConstructor(extractedClass, selectedProperties, optional);
            addGetterAndSetter(extractedClass, selectedProperties, optional);
            // refactor calls to the constructor
            TypeScriptFunction constructor = (TypeScriptFunction) extractedClass.getConstructor();
            HashMap<Classfield, TypeScriptParameter> definedClassfields = new HashMap<>();
            HashMap<Classfield, String> defaultValues = new HashMap<>();
            getDefaultValues(extractedClass, selectedProperties, defaultValues);
            getClassfieldDefiningParameter(constructor, new HashMap<>(), definedClassfields );
            refactorFunctionCalls((TypeScriptFunction) extractedClass.getConstructor(), originalParameters, extractedClass, definedClassfields, defaultValues);
            CodeSmellLogger.info("Using existing class " + extractedClass.getQualifiedName());
        }

        // Refaktorieren der beteiligten Elemente
        refactorElement(currentElement, extractedClass, selectedProperties, currentDefinedClassFields, currentDefaultValues);
        refactorElement(otherElement, extractedClass, selectedProperties, otherDefinedClassFields, otherDefaultValues);
    }

    private void adjustConstructor(TypeScriptClass psiClass , List<Property> matchingProperties, Set<Property> optionalProperties) {

        CodeSmellLogger.info("Adjusting constructor of " + psiClass.getQualifiedName() + "...");

        TypeScriptFunction constructor = (TypeScriptFunction) psiClass.getConstructor();
        if (constructor == null) {
            // create constructor
            CodeSmellLogger.info("Creating new constructor for " + psiClass.getQualifiedName());
            TypeScriptFunction newConstructor = PsiUtil.createConstructor(psiClass, matchingProperties, optionalProperties, new ArrayList<>(), null, DataClumpSettings.getInstance().getState().includeModifiersInDetection);
            PsiUtil.addFunctionToClass(psiClass, newConstructor);
        } else {
            CodeSmellLogger.info("Adjusting existing constructor of " + psiClass.getQualifiedName());

            HashMap<Classfield, TypeScriptParameter> definedClassfields = new HashMap<>();
            HashMap<Parameter, Classfield> definingParameters = new HashMap<>();
            getClassfieldDefiningParameter(constructor, definingParameters, definedClassfields);

            // existing constructor
            List<Property> constructorFields = new ArrayList<>();
            List<Property> constructorParameter = new ArrayList<>();

            for (JSParameterListElement psiParameter : constructor.getParameterList().getParameters()) {
                CodeSmellLogger.info("Checking constructor parameter " + psiParameter.getName());

                if (PsiUtil.isParameterField((TypeScriptParameter) psiParameter)) {
                    CodeSmellLogger.info("Parameter " + psiParameter.getName() + " is a field -> adding to constructor fields");
                    Classfield field = new Classfield((TypeScriptParameter) psiParameter);
                    constructorFields.add(field);
                    if (!matchingProperties.contains(field)) {
                        CodeSmellLogger.info("Pameter " + psiParameter.getName() + " is not in the list of properties -> adding to optional properties");
                        optionalProperties.add(field);
                    }
                } else {
                    CodeSmellLogger.info("Parameter " + psiParameter.getName() + " is not a field -> adding to constructor parameters");
                    Parameter parameter = new Parameter((TypeScriptParameter) psiParameter);
                    constructorParameter.add(parameter);
                    if (definingParameters.get(parameter) == null) {
                        CodeSmellLogger.info("Parameter " + psiParameter.getName() + " is not defining a field -> adding to optional properties");
                        optionalProperties.add(parameter);
                    } else if (!matchingProperties.contains(definingParameters.get(parameter))) {
                        CodeSmellLogger.info("Parameter " + psiParameter.getName() + " defining a field that is not in the list of properties -> adding to optional properties");
                        optionalProperties.add(definingParameters.get(parameter));
                    }
                }
            }

            for (Property property : matchingProperties) {
                if (property instanceof Classfield && ((Classfield) property).getModifier().contains("abstract")) continue;

                CodeSmellLogger.info("Checking property of list " + property.getName());
                // not defined in constructor
                if (!definedClassfields.containsKey(property)) {
                    CodeSmellLogger.info("Property " + property.getName() + " not defined in constructor -> adding to constructor fields");
                    constructorFields.add(property);
                    optionalProperties.add(property);
                }
            }

            List<Property> allProperties = new ArrayList<>(constructorFields);
            allProperties.addAll(constructorParameter);

            optionalProperties.retainAll(allProperties);

            for (Property optionalProperty : optionalProperties) {
                PsiElement field = PsiUtil.getPsiField(psiClass, optionalProperty);
                if (field instanceof TypeScriptField) {
                    PsiUtil.makeFieldOptional((TypeScriptField) field);
                }
            }

            JSBlockStatement body = constructor.getBlock();

            WriteCommandAction.runWriteCommandAction(psiClass.getProject(), constructor::delete);
            TypeScriptFunction newConstructor = PsiUtil.createConstructor(psiClass, constructorFields, optionalProperties, constructorParameter, body, DataClumpSettings.getInstance().getState().includeModifiersInDetection);
            PsiUtil.addFunctionToClass(psiClass, newConstructor);

        }
    }

    private void addGetterAndSetter(TypeScriptClass psiClass, List<Property> properties, Set<Property> optional) {
        for (Property property : properties) {
            // get the classfield
            Classfield classfield = PsiUtil.getClassfield(psiClass, property.getName());
            if (classfield == null) {
                CodeSmellLogger.error("Field " + property.getName() + " not found in class " + psiClass.getQualifiedName(), new IllegalArgumentException());
                continue;
            }
            if (!PsiUtil.hasGetter(psiClass, classfield)) {
                // add underscore if necessary
                PsiElement psiElement = PsiUtil.getPsiField(psiClass, classfield);
                if (psiElement instanceof TypeScriptField psiField) {
                    if (!psiField.getName().startsWith("_")) {
                       PsiUtil.rename(psiField, "_" + psiField.getName());
                    }
                } else if (psiElement instanceof TypeScriptParameter psiParameter) {
                    if (!psiParameter.getName().startsWith("_")) {
                        PsiUtil.rename(psiParameter, "_" + psiParameter.getName());
                    }
                }

                TypeScriptFunction getter = PsiUtil.createGetter(psiClass, classfield, optional.contains(property));
                PsiUtil.addFunctionToClass(psiClass, getter);
            }
            if (!PsiUtil.hasSetter(psiClass, classfield)) {
                // add underscore if necessary
                PsiElement psiElement = PsiUtil.getPsiField(psiClass, classfield);
                if (psiElement instanceof TypeScriptField psiField) {
                    if (!psiField.getName().startsWith("_")) {
                        PsiUtil.rename(psiField, "_" + psiField.getName());
                    }
                } else if (psiElement instanceof TypeScriptParameter psiParameter) {
                    if (!psiParameter.getName().startsWith("_")) {
                        PsiUtil.rename(psiParameter, "_" + psiParameter.getName());
                    }
                }
                TypeScriptFunction setter = PsiUtil.createSetter(psiClass, classfield, optional.contains(property));
                PsiUtil.addFunctionToClass(psiClass, setter);
            }
        }
    }

    private static void getClassfieldDefiningParameter(TypeScriptFunction function, HashMap<Parameter, Classfield> definingParameters, HashMap<Classfield, TypeScriptParameter> definedClassfields) {

        definedClassfields.clear();
        definingParameters.clear();


        // iterate over all parameters of the constructor
        for (JSParameterListElement parameter : function.getParameters()) {

            // if the parameter is a field of the class -> relevant for constructor
            if (PsiUtil.isParameterField((TypeScriptParameter) parameter)) {
                Classfield field = new Classfield((TypeScriptParameter) parameter);
                definedClassfields.put(field, (TypeScriptParameter) parameter);
                definingParameters.put(new Parameter((TypeScriptParameter) parameter), field);

            } else {
                // if the parameter is assigned new value it can not be used to initialize a field
                if (PsiUtil.isAssignedNewValue((TypeScriptParameter) parameter)) continue;
                List<Classfield> fields = PsiUtil.getAssignedToField((TypeScriptParameter) parameter);
                // if two or more fields are assigned to the parameter that are in the list of properties
                // only one field is added to the constructorParameters since the parameter can not be used by both fields
                // well actually it could be used by both fields but this is not supported by this refactoring
                // at the moment but now it has to be supported
                for (Classfield field : fields) {
                    definedClassfields.put(field, (TypeScriptParameter) parameter);
                    definingParameters.put(new Parameter((TypeScriptParameter) parameter), field);
                    break;
                }
            }
        }
    }

    private void getDefaultValues(TypeScriptClass psiClass, List<Property> properties, HashMap<Classfield, String> defaultValues) {

        defaultValues.clear();
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
    }

    private Set<Property> getOptionalProperties(List<Property> properties) {
        Set<Property> optionalProperties = new HashSet<>();
        if (currentElement instanceof TypeScriptClass) {
            optionalProperties.addAll(getOptionalProperties(properties, (TypeScriptClass) currentElement, currentDefinedClassFields, currentDefaultValues, currentDefiningParameters));
        }
        if (otherElement instanceof TypeScriptClass) {
           optionalProperties.addAll(getOptionalProperties(properties, (TypeScriptClass) otherElement, otherDefinedClassFields, otherDefaultValues, otherDefiningParameters));
        }
        return optionalProperties;
    }

    private List<Property> getOptionalProperties(List<Property> properties, TypeScriptClass psiClass, HashMap<Classfield, TypeScriptParameter> constructorParameters, HashMap<Classfield, String> defaultValues, HashMap<Parameter, Classfield> definingParameters) {
        List<Property> optionalProperties = new ArrayList<>();
        for (Property property : properties) {
            if (!constructorParameters.containsKey(property) && !defaultValues.containsKey(property)) {
                optionalProperties.add(property);
            }
        }

        HashMap<Classfield, PsiElement> fieldsToElement = PsiUtil.getFieldsToElement(psiClass);
        boolean changed;
       do {
           changed = false;
           for (Property property : properties) {
               // is the property already an optional property
               if (optionalProperties.contains(property)) continue;
               // is the property assigned to an optional property
               PsiElement psiField = fieldsToElement.get(property);
               for (PsiReference reference : ReferencesSearch.search(psiField)) {
                   JSAssignmentExpression assignment = PsiTreeUtil.getParentOfType(reference.getElement(), JSAssignmentExpression.class);
                   if (assignment != null && assignment.getLOperand().getFirstChild() == reference
                   && assignment.getROperand() instanceof JSReferenceExpression referenceExpression) {
                       Property assignedProperty = PsiUtil.resolveProperty(referenceExpression);
                       CodeSmellLogger.info("Checking if " + assignedProperty + " is optional");
                       if (assignedProperty instanceof Classfield && optionalProperties.contains(assignedProperty)) {
                           CodeSmellLogger.info("Optional classfield found: " + assignedProperty);
                           CodeSmellLogger.info("Adding " + property + " to optional properties");
                           optionalProperties.add(property);
                           changed = true;
                       } else if (assignedProperty instanceof Parameter
                                       && optionalProperties.contains(definingParameters.get(assignedProperty))) {
                            CodeSmellLogger.info("Optional parameter found: " + assignedProperty);
                            CodeSmellLogger.info("Adding " + property + " to optional properties");
                           optionalProperties.add(property);
                           changed = true;
                       }
                   }

               }
           }
       } while (changed);

        return optionalProperties;
    }

    private void refactorElement(PsiElement element, TypeScriptClass extractedClass, List<Property> properties, HashMap<Classfield, TypeScriptParameter> definedClassfields, HashMap<Classfield, String> defaultValues) {

        addImport(element, extractedClass);


        if (element instanceof TypeScriptClass && !element.equals(extractedClass)) {
            refactorClass((TypeScriptClass) element, extractedClass, properties, definedClassfields, defaultValues);
        } else if (element instanceof TypeScriptFunction) {
            refactorFunction((TypeScriptFunction) element, extractedClass, properties);
        }
    }

    private void addImport(PsiElement element, TypeScriptClass extractedClass) {
        PsiFile elementFile = element.getContainingFile();
        PsiFile extractedFile = extractedClass.getContainingFile();

        if (elementFile.equals(extractedFile)) return;

        CodeSmellLogger.info("Adding import statement for " + extractedClass.getName() + " to " + elementFile.getName());
        CodeSmellLogger.info("elementfile" + elementFile);
        CodeSmellLogger.info("elmentfile valid" + elementFile.isValid());
        //check that there is not already an import statement for the extracted class
        for (ES6ImportDeclaration importStatement : PsiTreeUtil.findChildrenOfType(elementFile, ES6ImportDeclaration.class)) {
            if (importStatement.getNamedImports() != null && importStatement.getNamedImports().getText().contains(extractedClass.getName())) {
                return;
            }
        }

        String relativePath = PsiUtil.getRelativePath(elementFile, extractedFile);
        String importStatement = "import { " + extractedClass.getName() + " } from '" + relativePath + "';\n";
        PsiElement firstChild = elementFile.getFirstChild();
        WriteCommandAction.runWriteCommandAction(element.getProject(), () -> {
            elementFile.addBefore(JSElementFactory.createExpressionCodeFragment(element.getProject(), importStatement, elementFile), firstChild);
        });

    }

    private void refactorClass(TypeScriptClass psiClass, TypeScriptClass extractedClass, List<Property> properties, HashMap<Classfield, TypeScriptParameter> definedClassfields, HashMap<Classfield, String> defaultValues) {
        CodeSmellLogger.info("Refactoring class " + psiClass.getQualifiedName() + "...");

        String fieldName =  "my_" + extractedClass.getName().toLowerCase();

        // Neues Feld hinzufügen
        ES6FieldStatementImpl newFieldStatement = PsiUtil.createJSFieldStatement(
                psiClass, fieldName, extractedClass.getJSType().getTypeText(), List.of("public"), false, getDefaultInit(psiClass, extractedClass, defaultValues)
        );
        PsiUtil.addFieldToClass(psiClass, newFieldStatement);
        CodeSmellLogger.info("Field " + newFieldStatement.getText() + " added to class " + psiClass.getQualifiedName());

        // Field-Usages aktualisieren
        updateFieldReferences(psiClass, properties, fieldName);
        CodeSmellLogger.info("Field references updated.");


        // Konstruktor aktualisieren
        updateConstructor(psiClass, properties, extractedClass, fieldName, definedClassfields, defaultValues);
        CodeSmellLogger.info("Constructor updated.");


        CodeSmellLogger.info("Class refactored.");
    }

    private String getDefaultInit(TypeScriptClass psiClass, TypeScriptClass extractedClass, HashMap<Classfield, String> defaultValues) {

        if (psiClass.getConstructor() != null) return null;

        List<Property> extractedParameters = getParametersAsPropertyList((TypeScriptFunction) extractedClass.getConstructor());

        StringBuilder init = new StringBuilder();
        init.append("new ").append(extractedClass.getName()).append("(");

        for (Property property : extractedParameters) {
            if (defaultValues.containsKey(property)) {
                CodeSmellLogger.info("Found default value for property " + property.getName());
                init.append(defaultValues.get(property)).append(", ");
            } else {
                CodeSmellLogger.info("Property " + property.getName() + " not found -> using undefined");
                init.append("undefined, ");
            }
        }

        if (!extractedParameters.isEmpty()) {
            init.setLength(init.length() - 2);
        }

        init.append(");");

        return init.toString();
    }

    private void updateConstructor(TypeScriptClass psiClass, List<Property> properties, TypeScriptClass extractedClass, String fieldName, HashMap<Classfield, TypeScriptParameter> definedClassfields, HashMap<Classfield, String> defaultValues) {
        TypeScriptFunction constructor = (TypeScriptFunction) psiClass.getConstructor();
        if (constructor == null) return;

        introduceParameterObjectForConstructor(properties, constructor, extractedClass, fieldName, definedClassfields, defaultValues);

        JSStatement initialization = JSPsiElementFactory.createJSStatement(
                "this." + fieldName + " = " + fieldName + ";", psiClass
        );

        WriteCommandAction.runWriteCommandAction(psiClass.getProject(), () -> {
            constructor.getBlock().addAfter(initialization, constructor.getBlock().getFirstChild());
        });

        List<JSAssignmentExpression> markedForRemoval = new ArrayList<>();

        // remove all assignments of the form this.person.name = this.person.name
        constructor.getBlock().accept(new JSRecursiveWalkingElementVisitor() {
            @Override
            public void visitJSAssignmentExpression(@NotNull JSAssignmentExpression node) {
                super.visitJSAssignmentExpression(node);
                CodeSmellLogger.info("Checking assignment " + node.getText());
                if (node.getLOperand().getText().equals(node.getROperand().getText())) {
                    CodeSmellLogger.info("Removing assignment " + node.getText());
                    markedForRemoval.add(node);
                }
            }
        });

        for (JSAssignmentExpression assignment : markedForRemoval) {
            WriteCommandAction.runWriteCommandAction(psiClass.getProject(), assignment.getParent()::delete);
        }

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
                        replaceAssignmentWithSetter(psiClass, assignmentExpression, fieldName, classfield.getName());
                    } else { // if no assignment refactor to getter
                        replaceReferenceWithGetter(psiClass, reference, fieldName, classfield.getName());
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

    private void replaceAssignmentWithSetter(TypeScriptClass psiClass, JSAssignmentExpression assignment, String fieldName, String propertyName) {
        String expressionText;
        if (PsiTreeUtil.getParentOfType(assignment, TypeScriptClass.class) == psiClass) {
            expressionText = "this." + fieldName + "." + propertyName + " = " + assignment.getROperand().getText();
        } else {
            expressionText = assignment.getLOperand().getFirstChild().getFirstChild().getText() + "." + fieldName + "." + propertyName + " = " + assignment.getROperand().getText();
        }
        JSExpression newExpression = JSPsiElementFactory.createJSExpression(expressionText, assignment);
        WriteCommandAction.runWriteCommandAction(assignment.getProject(), () -> {
            assignment.replace(newExpression);
        });
    }

    private void replaceReferenceWithGetter(TypeScriptClass psiClass, PsiReference reference, String fieldName, String propertyName) {
        String expressionText;
        if (PsiTreeUtil.getParentOfType(reference.getElement(), TypeScriptClass.class) == psiClass) {
            expressionText = "this." + fieldName + "." + propertyName;
        } else {
            expressionText = reference.getElement().getFirstChild().getText() +  "." + fieldName + "." + propertyName;
        }

        JSExpression newExpression = JSPsiElementFactory.createJSExpression(expressionText, reference.getElement());
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

    private void refactorConstructorParameter(TypeScriptFunction constructor, List<Property> matchingProperties, HashMap<Classfield, TypeScriptParameter> definedClassfields, String newParameterName) {
        for (Classfield property : definedClassfields.keySet()) {
            if (!matchingProperties.contains(property)) continue;

            TypeScriptParameter parameter = definedClassfields.get(property);

            // replace references with the new parameter object
            for (PsiReference reference : ReferencesSearch.search(parameter)) {
                replaceReferenceWithGetter(PsiTreeUtil.getParentOfType(constructor, TypeScriptClass.class), reference, newParameterName, property.getName());
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

    private void refactorFunctionCalls(TypeScriptFunction function, List<Property> originalParameters, TypeScriptClass extractedClass, HashMap<Classfield, TypeScriptParameter> definedClassfields, HashMap<Classfield, String> defaultValues) {
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
                        } else if (definedClassfields.containsKey(property)) {
                            CodeSmellLogger.info("Found refactorable property " + property.getName());
                            CodeSmellLogger.info("Argument: " + definedClassfields.get(property).getText());
                            CodeSmellLogger.info("originalParameters: " + originalParameters);
                            CodeSmellLogger.info("Parameter searched in original: " + new Parameter(definedClassfields.get(property)));
                            int index = originalParameters.indexOf(new Parameter(definedClassfields.get(property)));
                            CodeSmellLogger.info("Found in original Parameters " + property.getName() + " at index " + index);
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

    private void introduceParameterObjectForConstructor(List<Property> properties, TypeScriptFunction constructor, TypeScriptClass extractedClass, String newParameterName, HashMap<Classfield, TypeScriptParameter> definedClassfields, HashMap<Classfield, String> defaultValues) {

        List<Property> originalParameters = getParametersAsPropertyList(constructor);

        refactorConstructorParameter(constructor, properties, definedClassfields, newParameterName);

        addClassAsParameter(constructor, extractedClass, newParameterName);

        refactorFunctionCalls(constructor, originalParameters, extractedClass, definedClassfields, defaultValues);

    }

    public void introduceParameterObject(List<Property> properties, TypeScriptFunction function, TypeScriptClass extractedClass, String newParameterName ) {

        List<Property> originalParameters = getParametersAsPropertyList(function);

        refactorFunctionParameter(function, properties, newParameterName);

        // Add the extracted class as a new parameter
        addClassAsParameter(function, extractedClass, newParameterName);

        refactorFunctionCalls(function, originalParameters, extractedClass, new HashMap<>(), new HashMap<>());

    }

    private TypeScriptClass extractClass(PsiDirectory dir, String className, List<Property> fields, Set<Property> optionalFields) {
        CodeSmellLogger.info("Extracting class...");

        List<Property> constructorFields = new ArrayList<>(fields);
        List<Property> abstractFields = new ArrayList<>();
        List<Property> declaredFields = new ArrayList<>();
        if (DataClumpSettings.getInstance().getState().includeModifiersInExtractedClass) {
            // filter all abstract fields
            abstractFields = fields.stream().filter(property -> property instanceof Classfield && ((Classfield) property).getModifier().contains("abstract")).toList();
            declaredFields = fields.stream().filter(property -> property instanceof Classfield && ((Classfield) property).getModifier().contains("declare")).toList();

            constructorFields.removeAll(abstractFields);
            constructorFields.removeAll(declaredFields);

            abstractFields.forEach(optionalFields::remove);
            declaredFields.forEach(optionalFields::remove);
        }

        TypeScriptClass psiClass = PsiUtil.createClass(dir, className, !abstractFields.isEmpty(), true);

        TypeScriptFunction constructor = PsiUtil.createConstructor(psiClass, constructorFields, optionalFields, new ArrayList<>(), null, DataClumpSettings.getInstance().getState().includeModifiersInExtractedClass);
        PsiUtil.addFunctionToClass(psiClass, constructor);

        // add abstract fields
        for (Property field : abstractFields) {
            ES6FieldStatementImpl abstractField = PsiUtil.createJSFieldStatement(psiClass, field.getName(), field.getTypesAsString(), ((Classfield)field).getModifier(), false, null);
            PsiUtil.addFieldToClass(psiClass, abstractField);
        }
        // add declared fields
        for (Property field : declaredFields) {
            ES6FieldStatementImpl declaredField = PsiUtil.createJSFieldStatement(psiClass, field.getName(), field.getTypesAsString(), ((Classfield)field).getModifier(), false, null);
            PsiUtil.addFieldToClass(psiClass, declaredField);
        }

        // Getter and Setter
        for (Property field : fields) {
            // Skip public fields as they do not need getter and setter
            if (field instanceof Classfield && ((Classfield) field).getModifier().contains("public")) continue;

            TypeScriptFunction getter;
            TypeScriptFunction setter;
            if (optionalFields.contains(field)) {
                getter = PsiUtil.createGetter(psiClass, field, true);
                setter = PsiUtil.createSetter(psiClass, field, true);
            } else {
                getter = PsiUtil.createGetter(psiClass, field, false);
                setter = PsiUtil.createSetter(psiClass, field, false);
            }
            PsiUtil.addFunctionToClass(psiClass, getter);
            PsiUtil.addFunctionToClass(psiClass, setter);
        }

        WriteCommandAction.runWriteCommandAction(dir.getProject(), () -> {
            PsiFile file = PsiFileFactory.getInstance(dir.getProject()).createFileFromText(className + ".ts", "");
            file.add(psiClass);
            dir.add(file);
        });

        // this is for some reason necessary to get the virtual file later on
        VirtualFile virtualFile = dir.getVirtualFile().findChild(className + ".ts");
        PsiFile file = PsiManager.getInstance(dir.getProject()).findFile(virtualFile);
        TypeScriptClass extractedClass = PsiTreeUtil.findChildOfType(file, TypeScriptClass.class);


        CodeSmellLogger.info("Class extracted.");
        return extractedClass;
    }

    public static Set<TypeScriptClass> getUsableClasses(List<Property> properties) {
        Set<TypeScriptClass> matchingClasses = new HashSet<>();

        // Validierung: Keine Properties angegeben
        if (properties.isEmpty()) CodeSmellLogger.error("No properties specified for DataClumpRefactoring", new IllegalArgumentException());


        // Validierung: Erste Property nicht im Index
        Property firstProperty = properties.get(0);
        CodeSmellLogger.info("First property: " + firstProperty);
        if (!Index.getPropertiesToClasses().containsKey(firstProperty)) return matchingClasses;

        // Potenziell passende Klassen finden
        List<TypeScriptClass> potentialClasses = Index.getPropertiesToClasses().get(firstProperty);
        CodeSmellLogger.info("Potential classes: " + potentialClasses);
        for (TypeScriptClass psiClass : potentialClasses) {
            CodeSmellLogger.info("Checking class " + psiClass.getQualifiedName());
            if (!psiClass.isValid() || psiClass.getName() == null) continue;
            CodeSmellLogger.info("Class is valid and not anonymous");
            if (PsiUtil.hasAll(psiClass, properties)) {
                CodeSmellLogger.info("Class has all properties");
                matchingClasses.add(psiClass);
            }
        }

        // Kopie des Sets erstellen, da wir es modifizieren
        Set<TypeScriptClass> filteredClasses = new HashSet<>(matchingClasses);

        // Überprüfung der Zuweisungen für jede Klasse
        for (TypeScriptClass psiClass : matchingClasses) {
            CodeSmellLogger.info("Checking class " + psiClass.getQualifiedName());
            for (Property property : properties) {
                CodeSmellLogger.info("Checking property " + property.getName());
                PsiElement psiField = PsiUtil.getPsiField(psiClass, property);
                CodeSmellLogger.info("PsiField: " + psiField);

                TypeScriptFunction constructor = (TypeScriptFunction) psiClass.getConstructor();
                if (constructor == null) {
                    CodeSmellLogger.info("No constructor found");
                    break;
                }

                // Referenzen des Properties überprüfen
                for (PsiReference reference : ReferencesSearch.search(psiField)) {
                    CodeSmellLogger.info("Checking reference " + reference.getElement().getText());

                    // Sicherstellen, dass die Referenz im Konstruktor liegt
                    if (PsiTreeUtil.getParentOfType(reference.getElement(), TypeScriptFunction.class) != constructor) {
                        CodeSmellLogger.info("Reference not in constructor");
                        break;
                    }

                    // Überprüfen, ob die Referenz in einer Zuweisung verwendet wird
                    JSAssignmentExpression assignment = PsiTreeUtil.getParentOfType(reference.getElement(), JSAssignmentExpression.class);
                    if (assignment == null || assignment.getLOperand().getFirstChild() != reference) {
                        CodeSmellLogger.info("Reference not in assignment or not on the left side");
                        break;
                    }

                    // Überprüfung des rechten Operanden der Zuweisung
                    if (!(assignment.getROperand() instanceof JSReferenceExpression)) {
                        CodeSmellLogger.info("Right operand not a reference expression");
                        filteredClasses.remove(psiClass);
                        break;
                    }

                    // Aufgelöste Property überprüfen
                    Property assignedProperty = PsiUtil.resolveProperty((JSReferenceExpression) assignment.getROperand());
                    if (assignedProperty == null) {
                        CodeSmellLogger.info("No Assigned property not found");
                        filteredClasses.remove(psiClass);
                        break;
                    }

                    // Klassenfeld-Parameter-Mapping überprüfen
                    HashMap<Parameter, Classfield> definingParameter = new HashMap<>();
                    getClassfieldDefiningParameter(constructor, definingParameter, new HashMap<>());

                    Classfield definingField = definingParameter.get(assignedProperty);
                    if (definingField == null || !definingField.equals(property)) {
                        CodeSmellLogger.info("Defining field not found or not equal to property");
                        filteredClasses.remove(psiClass);
                        break;
                    }
                }
            }
        }

        return filteredClasses;
    }

    @Override
    public boolean startInWriteAction() {
        // This quick fix uses a dialog to gather user input, so it should not start in a write action
        return false;
    }
}
