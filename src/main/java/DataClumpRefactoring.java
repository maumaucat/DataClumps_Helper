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
        TypeScriptClass extractedClass;

        if (dialog.shouldCreateNewClass()) {
            String className = dialog.getClassName();
            PsiDirectory targetDirectory = dialog.getDirectory();

            CodeSmellLogger.info("Creating new class with name " + className + " in " + targetDirectory);
            // Erstellen der neuen Klasse
            extractedClass = extractClass(targetDirectory, className, selectedProperties);
        } else {
            extractedClass = dialog.getSelectedClass();
            CodeSmellLogger.info("Using existing class " + extractedClass.getQualifiedName());
        }


        // Refaktorieren der beteiligten Elemente
        refactorElement(currentElement, extractedClass, selectedProperties);
        refactorElement(otherElement, extractedClass, selectedProperties);
    }

    private void refactorElement(PsiElement element, TypeScriptClass extractedClass, List<Property> properties) {
        if (element instanceof TypeScriptClass) {
            refactorClass((TypeScriptClass) element, extractedClass, properties);
        } else if (element instanceof TypeScriptFunction) {
            refactorFunction((TypeScriptFunction) element, extractedClass, properties);
        }
    }

    private void refactorClass(TypeScriptClass psiClass, TypeScriptClass extractedClass, List<Property> properties) {
        CodeSmellLogger.info("Refactoring class " + psiClass.getQualifiedName() + "...");

        String fieldName = extractedClass.getName().toLowerCase();

        // Neues Feld hinzufügen
        addNewFieldToClass(psiClass, extractedClass, fieldName);

        // Verwendungen der Eigenschaften aktualisieren
        updateFieldReferences(psiClass, properties, fieldName);

        // Konstruktor aktualisieren
        updateConstructor(psiClass, properties, extractedClass, fieldName);

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

    private void updateConstructor(TypeScriptClass psiClass, List<Property> properties, TypeScriptClass extractedClass, String fieldName) {
        TypeScriptFunction constructor = (TypeScriptFunction) psiClass.getConstructor();
        if (constructor == null) return;

        introduceParameterObject(properties, constructor, extractedClass, fieldName);

        //TODO remove calls like: this.person.name = person.name;
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
            Classfield field = entry.getKey();
            PsiElement element = entry.getValue();

            if (properties.contains(field)) {
                for (PsiReference reference : ReferencesSearch.search(element)) {
                    // if it is assignment -> refactor to setter
                    JSAssignmentExpression assignmentExpression = PsiTreeUtil.getParentOfType(reference.getElement(), JSAssignmentExpression.class);
                    if (assignmentExpression != null && assignmentExpression.getLOperand().getFirstChild() == reference) {
                        replaceAssignmentWithSetter(assignmentExpression, fieldName, field);
                    } else { // if no assignment refactor to getter
                        replaceReferenceWithGetter(reference, fieldName, field);
                    }
                }

                if (element instanceof TypeScriptField) {
                    WriteCommandAction.runWriteCommandAction(psiClass.getProject(), () -> {
                        element.delete();
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

    private void replaceReferenceWithGetter(PsiReference reference, String fieldName, Classfield field) {
        JSExpression newExpression = JSPsiElementFactory.createJSExpression(
                "this." + fieldName + "." + field.getName(), reference.getElement()
        );
        WriteCommandAction.runWriteCommandAction(reference.getElement().getProject(), () -> {
            reference.getElement().replace(newExpression);
        });
    }

    public static void refactorFunction(TypeScriptFunction psiFunction, TypeScriptClass extractedClass, List<Property> properties) {
        CodeSmellLogger.info("Refactoring function " + psiFunction.getQualifiedName() + "...");

        String newParameterName = extractedClass.getQualifiedName();

        introduceParameterObject(properties, psiFunction, extractedClass, newParameterName);

        CodeSmellLogger.info("Function refactored.");
    }

    public static void introduceParameterObject(List<Property> properties, TypeScriptFunction function, TypeScriptClass extractedClass, String newParameterName) {

        Project project = function.getProject();
        List<Property> originalParameters = new ArrayList<>();

        // Process the function's current parameters
        for (JSParameterListElement originalParameter : function.getParameters()) {
            Property originalProperty = new Parameter((TypeScriptParameter) originalParameter);
            originalParameters.add(originalProperty);


            // Replace references to selected parameters with getter calls on the new object
            if (properties.contains(originalProperty)) {
                for (PsiReference reference : ReferencesSearch.search(originalParameter)) {
                    JSExpression getter = JSPsiElementFactory.createJSExpression(newParameterName + "." + originalParameter.getName(), function);
                    WriteCommandAction.runWriteCommandAction(project, () -> {
                        reference.getElement().replace(getter);
                    });
                }
                // Remove the parameter from the function's signature
                WriteCommandAction.runWriteCommandAction(project, originalParameter::delete);
            }
        }

        // Add the extracted class as a new parameter
        JSParameterList parameterList = function.getParameterList();
        TypeScriptParameter newParameter = PsiUtil.createTypeScriptParameter(function, newParameterName, extractedClass.getJSType());
        PsiUtil.addParameterToParameterList(newParameter, parameterList);

        // Gather properties from the extracted class constructor
        List<Property> extractedConstructorParameters = new ArrayList<>();
        for (JSParameterListElement parameter : extractedClass.getConstructor().getParameters()) {
            extractedConstructorParameters.add(new Parameter(parameter.getName().substring(1), parameter.getJSType()));
        }

        // Update all calls to the function to use the new parameter object
        for (PsiReference functionCall : ReferencesSearch.search(function)) {
            JSArgumentList argumentList = PsiTreeUtil.getNextSiblingOfType(functionCall.getElement(), JSArgumentList.class);
            JSExpression[] originalArguments = argumentList.getArguments();
            StringBuilder updatedArguments = new StringBuilder("(");

            for (JSParameterListElement parameter : function.getParameters()) {
                if (parameter.getJSType().equals(extractedClass.getJSType())) {
                    // Replace with a constructor call for the new parameter object
                    updatedArguments.append("new ").append(extractedClass.getName()).append("(");
                    for (Property property : extractedConstructorParameters) {
                        updatedArguments.append(originalArguments[originalParameters.indexOf(property)].getText()).append(", ");
                    }
                    // Remove trailing comma
                    if (updatedArguments.charAt(updatedArguments.length() - 2) == ',') {
                        updatedArguments.setLength(updatedArguments.length() - 2);
                    }
                    updatedArguments.append(")");
                } else {
                    // Append remaining original arguments
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
            WriteCommandAction.runWriteCommandAction(project, () -> {
                JSExpression newArguments = JSPsiElementFactory.createJSExpression(updatedArguments.toString(), argumentList);
                argumentList.replace(newArguments);
            });
        }
    }

    public TypeScriptClass extractClass(PsiDirectory dir, String className, List<Property> fields) {
        CodeSmellLogger.info("Extracting class...");
        //TODO Formatter?
        StringBuilder classCode = new StringBuilder();
        classCode.append("class " + className + " {\n\n");

        // constructor
        classCode.append("  constructor(");

        for (Property field : fields) {
            final String fieldName = field.getName();
            final String fieldType = field.getType().getTypeText();
            classCode.append("private _" + fieldName + ": " + fieldType + ", ");
        }

        // Entferne das letzte Komma und Leerzeichen
        if (!fields.isEmpty()) {
            classCode.setLength(classCode.length() - 2);
        }

        classCode.append(") {}\n\n");

        // Getter- und Setter
        for (Property field : fields) {
            final String fieldName = field.getName();
            final String fieldType = field.getType().getTypeText();
            classCode.append("  get " + fieldName + "(): " + fieldType + " {\n");
            classCode.append("    return this._" + fieldName + ";\n");
            classCode.append("  }\n\n");

            classCode.append("  set " + fieldName + "(value: " + fieldType + ") {\n");
            classCode.append("    this._" + fieldName + " = value;\n");
            classCode.append("  }\n\n");
        }

        classCode.append("}\n");


        final PsiFile[] psiFile = new PsiFile[1];

        // Schreibaktion ausführen
        WriteCommandAction.runWriteCommandAction(dir.getProject(), () -> {

            // Datei mit dem Klassentext erstellen
            psiFile[0] = PsiFileFactory.getInstance(dir.getProject()).createFileFromText(className +" .ts", TypeScriptFileType.INSTANCE, classCode);

            // Datei zum Verzeichnis hinzufügen
            dir.add(psiFile[0]);
        });

        CodeSmellLogger.info("Class extracted.");
        return PsiTreeUtil.getChildOfType(psiFile[0], TypeScriptClass.class);
    }

    // so dialog (atw) does explode if not there
    @Override
    public boolean startInWriteAction() {
        return false;
    }
}
