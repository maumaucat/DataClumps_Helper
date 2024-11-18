import com.esotericsoftware.kryo.kryo5.minlog.Log;
import com.google.protobuf.DescriptorProtos;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.lang.ecmascript6.psi.impl.ES6FieldStatementImpl;
import com.intellij.lang.javascript.TypeScriptFileType;
import com.intellij.lang.javascript.buildTools.JSPsiUtil;
import com.intellij.lang.javascript.psi.*;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptClass;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptField;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptFunction;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptParameter;
import com.intellij.lang.javascript.psi.ecmal4.JSClass;
import com.intellij.lang.javascript.psi.impl.JSPsiElementFactory;

import com.intellij.lang.typescript.psi.TypeScriptPsiUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import util.Property;
import util.PsiUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;


public class DataClumpRefactoring implements LocalQuickFix {

    List<Property> matching;
    PsiElement current;
    PsiElement other;

    private static final Logger LOG = Logger.getInstance(DataClumpRefactoring.class);


    public DataClumpRefactoring(PsiElement current, PsiElement other, List<Property> matching) {
        this.matching = matching;
        this.current = current;
        this.other = other;
    }

    @Override
    public @IntentionFamilyName @NotNull String getFamilyName() {
        if (other instanceof TypeScriptClass) {
            return "Refactor Data Clump with " + ((TypeScriptClass) other).getQualifiedName();
        } else if (other instanceof TypeScriptFunction) {
            return "Refactor Data Clump with " + ((TypeScriptFunction) other).getQualifiedName();
        }
        return "Refactor data clump(error)";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor problemDescriptor) {

        DataClumpDialog dialog = new DataClumpDialog(this.matching, current, other);
        if (!dialog.showAndGet()) return;
        dialog.getData();




        PsiDirectory dir = current.getContainingFile().getContainingDirectory();
        String className = "TestClass";
        TypeScriptClass extractClass = extractClass(dir, className, matching);


        if (current instanceof TypeScriptClass currentClass) {
            refactorClass(currentClass, extractClass, matching);
        } else if (current instanceof TypeScriptFunction currentFunction) {
            refactorFunction(currentFunction, extractClass, matching);
        }

        if (other instanceof TypeScriptClass otherClass) {
            refactorClass(otherClass, extractClass, matching);
        } else if (other instanceof TypeScriptFunction otherFunction) {
            refactorFunction(otherFunction, extractClass, matching);
        }

    }

    public static void refactorFunction(TypeScriptFunction psiFunction, TypeScriptClass extractedClass, List<Property> properties) {

        Project project = psiFunction.getContainingFile().getProject();
        String newParameterName = extractedClass.getQualifiedName();

        introduceParameterObject(project, properties, psiFunction, extractedClass ,newParameterName);

    }

    public void refactorClass(TypeScriptClass psiClass, TypeScriptClass extractedClass, List<Property> properties) {

        Project project = psiClass.getProject();
        String newFieldName = extractedClass.getName().toLowerCase();
        String newParameterName = newFieldName;

        // add new field
        ES6FieldStatementImpl newFieldStatement = PsiUtil.createJSFieldStatement(psiClass, newFieldName, extractedClass.getJSType(), "private");

        WriteCommandAction.runWriteCommandAction(project, () -> {
            PsiElement[] fields = psiClass.getFields();
            if (fields.length > 0) {
                psiClass.addBefore(newFieldStatement, PsiTreeUtil.getParentOfType(fields[fields.length - 1], ES6FieldStatementImpl.class));
            } else {
                // sollte nicht passieren können
                LOG.error("refactored class has no fields");
            }
        });

        //HashMap<Property,Object> defaultValues = new HashMap();

        for (JSField field : psiClass.getFields()) {
            if (properties.contains(new Property(field.getName(), field.getJSType()))) {
                for (PsiReference reference : ReferencesSearch.search(field)) {
                    // if it is assignment -> refactor to setter
                    JSAssignmentExpression assignmentExpression = PsiTreeUtil.getParentOfType(reference.getElement(), JSAssignmentExpression.class);
                    if (assignmentExpression != null && assignmentExpression.getLOperand().getFirstChild() == reference) {
                        // replace this.fieldname with setter
                        JSExpression setter = JSPsiElementFactory.createJSExpression("this." + newFieldName + ".set_" + field.getName() + "(" + assignmentExpression.getROperand().getText() + ")", psiClass);
                        assignmentExpression.replace(setter);
                    } else { // if no assignment refactor to getter
                        JSExpression oldExpression = (JSExpression) reference.getElement();
                        JSExpression getter = JSPsiElementFactory.createJSExpression("this." + newFieldName + ".get_" + field.getName() + "()", psiClass);
                        oldExpression.replace(getter);
                    }
                }

                // default Werte merken
                /*JSLiteralExpression defaultValue = PsiTreeUtil.getChildOfType(field, JSLiteralExpression.class);
                if (defaultValue != null) {
                    defaultValues.put(new Property(field.getName(), field.getJSType()),defaultValue.getValue());
                }*/

                field.delete();
            }
        }

        // constructor
        TypeScriptFunction constructor = (TypeScriptFunction) psiClass.getConstructor();
        if (constructor != null) {

            introduceParameterObject(project, properties, constructor, extractedClass, newParameterName);

            //TODO remove calls like: this.testclass.set_name(testclass.get_name());

            // add initialization
            JSStatement statement = JSPsiElementFactory.createJSStatement("this." + newFieldName + " = " + newParameterName + ";", psiClass);
            constructor.getBlock().addAfter(statement, constructor.getBlock().getFirstChild());

        }
    }

    public static void introduceParameterObject(Project project, List<Property> properties, TypeScriptFunction function, TypeScriptClass extractedClass, String newParameterName) {

        // save aufbau of originalParameterList and remove all Paramaters that will be replaced by Object
        List<Property> originalParameterList = new ArrayList<>();

        for (JSParameterListElement parameter : function.getParameters()) {
            Property currentParameter = new Property(parameter.getName(), parameter.getJSType());

            originalParameterList.add(currentParameter);

            if (properties.contains(currentParameter)) {
                // refactor calls within the method
                for (PsiReference reference : ReferencesSearch.search(parameter)) {
                    JSExpression getter = JSPsiElementFactory.createJSExpression(newParameterName + ".get_" + parameter.getName() + "()", function);
                    reference.getElement().replace(getter);
                }
                // remove parameter
                parameter.delete();
            }
        }

        // add extracted class as new Parameter
        JSParameterList parameterList = function.getParameterList();
        TypeScriptParameter newParameter = PsiUtil.createTypeScriptParameter(function, newParameterName , extractedClass.getJSType());
        PsiUtil.addParameterToParameterList(newParameter, parameterList);

        // I need this for down there
        List<Property> extractedParameterList = new ArrayList<>();
        for (JSParameterListElement parameter : extractedClass.getConstructor().getParameters()) {
            extractedParameterList.add(new Property(parameter.getName(), parameter.getJSType()));
        }

        // find all calls of the function and refactor them
        for (PsiReference call : ReferencesSearch.search(function)) {

            JSArgumentList argumentList = PsiTreeUtil.getNextSiblingOfType(call.getElement(), JSArgumentList.class);
            JSExpression[] originalArguments = argumentList.getArguments();

            StringBuilder newArgumentListString = new StringBuilder("(");

            for (JSParameterListElement parameter : function.getParameters()) {

                if (parameter.getJSType().equals(extractedClass.getJSType())) {

                    newArgumentListString.append("new " + extractedClass.getName() + "(");
                    for (Property property : extractedParameterList) {
                        newArgumentListString.append(originalArguments[originalParameterList.indexOf(property)].getText());
                        newArgumentListString.append(",");
                    }
                    newArgumentListString.deleteCharAt(newArgumentListString.length() - 1);
                    newArgumentListString.append(")");
                } else {
                    newArgumentListString.append(originalArguments[originalParameterList.indexOf(new Property(parameter.getName(), parameter.getJSType()))].getText());
                }
                newArgumentListString.append(",");
            }
            newArgumentListString.deleteCharAt(newArgumentListString.length() - 1);
            newArgumentListString.append(")");

            WriteCommandAction.runWriteCommandAction(project, () -> {
                JSExpression newerParameterList = JSPsiElementFactory.createJSExpression(newArgumentListString.toString(), argumentList);
                LOG.info(newerParameterList.getText());
                argumentList.replace(newerParameterList);
            });

        }
    }

    public TypeScriptClass extractClass(PsiDirectory dir, String className, List<Property> fields) {

        //TODO Formatter?
        StringBuilder classCode = new StringBuilder();
        classCode.append("class " + className + " {\n");

        // 2. Füge Klassenvariablen und den Konstruktor hinzu
        for (Property field : fields) {
            final String fieldName = field.getName();
            final String fieldType = field.getType().getTypeText();
            classCode.append("  private " + fieldName + ": " + fieldType + ";\n");
        }

        classCode.append("\n");
        classCode.append("  constructor(");

        for (Property field : fields) {
            final String fieldName = field.getName();
            final String fieldType = field.getType().getTypeText();
            classCode.append(fieldName + ": " + fieldType + ", ");
        }

        // Entferne das letzte Komma und Leerzeichen
        if (!fields.isEmpty()) {
            classCode.setLength(classCode.length() - 2);
        }

        classCode.append(") {\n");

        for (Property field : fields) {
            final String fieldName = field.getName();
            final String fieldType = field.getType().getTypeText();
            classCode.append("    this." + fieldName + " = " + fieldName + ";\n");
        }

        classCode.append("  }\n\n");

        // 3. Füge Getter- und Setter-Methoden hinzu
        for (Property field : fields) {
            final String fieldName = field.getName();
            final String fieldType = field.getType().getTypeText();
            classCode.append("  get_" + fieldName + "(): " + fieldType + " {\n");
            classCode.append("    return this." + fieldName + ";\n");
            classCode.append("  }\n\n");

            classCode.append("  set_" + fieldName + "(value: " + fieldType + ") {\n");
            classCode.append("    this." + fieldName + " = value;\n");
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

        return PsiTreeUtil.getChildOfType(psiFile[0], TypeScriptClass.class);
    }


    // so dialog (atw) does explode if not there
    @Override
    public boolean startInWriteAction() {
        return false;
    }
}
