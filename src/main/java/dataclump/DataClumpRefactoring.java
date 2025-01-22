package dataclump;

import Settings.DataClumpSettings;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.lang.ecmascript6.psi.ES6ImportDeclaration;
import com.intellij.lang.ecmascript6.psi.impl.ES6FieldStatementImpl;
import com.intellij.lang.javascript.TypeScriptFileType;
import com.intellij.lang.javascript.psi.*;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptClass;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptField;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptFunction;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptParameter;
import com.intellij.lang.javascript.psi.ecmal4.JSClass;
import com.intellij.lang.javascript.psi.impl.JSPsiElementFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import evoluation.DiagnosticTool;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import util.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;


/**
 * Refactoring für Data Clumps.
 * The Data Clump code smell occurs when multiple properties are always used together.
 * This refactoring extracts the properties into a new or existing class and replaces the properties with the new class.
 */
public class DataClumpRefactoring implements LocalQuickFix {

    /**
     * List of properties that are always used together
     */
    private List<Property> matchingProperties;
    /**
     * The first element that contains the data clump
     */
    private SmartPsiElementPointer<PsiElement> currentElement;
    /**
     * The second element that contains the data clump
     */
    private SmartPsiElementPointer<PsiElement> otherElement;

    /**
     * Maps the classfields to the parameters that define them in the constructor for the first element
     */
    private final HashMap<Classfield, Parameter> currentDefinedClassFields = new HashMap<>();
    /**
     * Maps the parameters to the classfields that they define in the constructor for the second element
     */
    private final HashMap<Classfield, Parameter> otherDefinedClassFields = new HashMap<>();

    /**
     * Maps the constructor parameters to the classfields that they define in the constructor for the first element
     */
    private final HashMap<Parameter, Classfield> currentDefiningParameters = new HashMap<>();
    /**
     * Maps the constructor parameters to the classfields that they define in the constructor for the second element
     */
    private final HashMap<Parameter, Classfield> otherDefiningParameters = new HashMap<>();
    /**
     * Maps the classfields to their default values for the first element
     */
    private final HashMap<Classfield, String> currentDefaultValues = new HashMap<>();
    /**
     * Maps the classfields to their default values for the second element
     */
    private final HashMap<Classfield, String> otherDefaultValues = new HashMap<>();

    /**
     * Creates a new dataclump.DataClumpRefactoring.
     *
     * @param currentElement     the first element that contains the data clump
     * @param otherElement       the second element that contains the data clump
     * @param matchingProperties the properties that are always used together
     */
    public DataClumpRefactoring(@NotNull PsiElement currentElement, @NotNull PsiElement otherElement, @NotNull List<Property> matchingProperties) {
        ApplicationManager.getApplication().runReadAction(() -> {
            SmartPointerManager psiPointerManager = SmartPointerManager.getInstance(currentElement.getProject());
            this.matchingProperties = matchingProperties;
            this.currentElement = psiPointerManager.createSmartPsiElementPointer(currentElement);
            this.otherElement = psiPointerManager.createSmartPsiElementPointer(otherElement);
        });
    }

    /**
     * Returns the name of the refactoring family.
     * This is used to distinguish between different refactorings by the element by the element they form a data clump with.
     *
     * @return the name of the refactoring family
     */
    @Override
    public @IntentionFamilyName @NotNull String getFamilyName() {

        String title;
        PsiElement otherElement = this.otherElement.getElement();

        if (otherElement instanceof TypeScriptClass) {
            title = "Refactor Data Clump with " + ((TypeScriptClass) otherElement).getQualifiedName();
        } else if (otherElement instanceof TypeScriptFunction) {
            title = "Refactor Data Clump with " + ((TypeScriptFunction) otherElement).getQualifiedName();
        } else {
            title = "Error refactor data clump " + PsiUtil.getName(otherElement);
        }
        return title;
    }

    /**
     * This method is called when the user selects the refactoring from the context menu.
     * It opens a dialog to select the properties that should be extracted.
     * If the user selects to create a new class, a new class is created and the properties are extracted.
     * If the user selects an existing class, the properties are extracted and added to the existing class.
     *
     * @param project           the current project
     * @param problemDescriptor the problem descriptor
     */
    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor problemDescriptor) {
        CodeSmellLogger.info("up to date");

        long startTime;
        ReportFormat.DataClumpTypeContext dataClumpTypeContext;
        if (DiagnosticTool.DIAGNOSTIC_MODE) {
            startTime = System.nanoTime();
            dataClumpTypeContext = ReportFormat.getDataClumpsTypeContext(Objects.requireNonNull(this.currentElement.getElement()), Objects.requireNonNull(this.otherElement.getElement()), matchingProperties);
        } else {
            dataClumpTypeContext = null;
            startTime = 0;
        }

        // make sure that the refactoring is executed in the event dispatch thread to avoid issues with the UI and PSI
        ApplicationManager.getApplication().invokeLater(() -> {
            // show dialog to select properties
            DataClumpDialog dialog = new DataClumpDialog(this, matchingProperties, Objects.requireNonNull(currentElement.getElement()), Objects.requireNonNull(otherElement.getElement()));

            if (!dialog.showAndGet()) return;

            PsiElement currentElement = this.currentElement.getElement();
            PsiElement otherElement = this.otherElement.getElement();

            CodeSmellLogger.info("Refactoring DataClump between " + PsiUtil.getQualifiedName(currentElement) + " and " + PsiUtil.getQualifiedName(otherElement));

            List<Property> selectedProperties = dialog.getProperties();

            TypeScriptClass extractedClass;

            // in case that the to be refactored element is a class,
            // the information about the constructor and the default values of the classfields are extracted
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

            // create or use existing class
            if (dialog.shouldCreateNewClass()) {
                String className = dialog.getClassName();
                PsiDirectory targetDirectory = dialog.getDirectory();
                CodeSmellLogger.info("Creating new class with name " + className + " in " + targetDirectory);
                extractedClass = extractClass(targetDirectory, className, selectedProperties);
                Index.addClass(extractedClass);
            } else {
                extractedClass = dialog.getSelectedClass();
                if (hasConflictingSetterOrGetter(extractedClass, selectedProperties)) {
                    Messages.showMessageDialog("The selected class contains a getter or setter with the same name as one of the properties to be extracted. Please select another class.", "Error", Messages.getErrorIcon());
                    applyFix(project, problemDescriptor);
                    return;
                }
                CodeSmellLogger.info("Using existing class " + extractedClass.getQualifiedName());

                // save the original parameters -> needed for refactoring the function calls
                List<Property> originalParameters = new ArrayList<>();
                if (extractedClass.getConstructor() != null) {
                    originalParameters = getParametersAsPropertyList((TypeScriptFunction) extractedClass.getConstructor());
                }

                extractedClass = PsiUtil.makeClassExported(extractedClass);
                assert extractedClass != null;
                Index.updateClass(extractedClass); // since the class was replaced the index must be updated

                adjustConstructor(extractedClass, selectedProperties);
                addGetterAndSetter(extractedClass, selectedProperties);

                // refactor the function calls of the extracted class since the constructor was adjusted
                TypeScriptFunction constructor = (TypeScriptFunction) extractedClass.getConstructor();
                assert constructor != null;
                HashMap<Classfield, String> defaultValues = new HashMap<>();
                getDefaultValues(extractedClass, selectedProperties, defaultValues);

                refactorConstructorCalls(constructor, originalParameters, defaultValues);
            }

            TypeScriptClass finalExtractedClass = extractedClass;
            WriteCommandAction.runWriteCommandAction(project, () -> {
                PsiDocumentManager.getInstance(project).commitAllDocuments();
                CodeStyleManager.getInstance(project).reformat(finalExtractedClass);

            });

            List<Classfield> dataClump = new ArrayList<>();
            for (Property property : selectedProperties) {
                dataClump.add(PsiUtil.getClassfield(extractedClass, property.getName()));
            }

            // refactor the elements that contain the data clump
            refactorElement(currentElement, extractedClass, dataClump, currentDefinedClassFields, currentDefaultValues);
            refactorElement(otherElement, extractedClass, dataClump, otherDefinedClassFields, otherDefaultValues);

            WriteCommandAction.runWriteCommandAction(project, () -> {
                PsiDocumentManager.getInstance(project).commitAllDocuments();
                CodeStyleManager.getInstance(project).reformat(currentElement);
                CodeStyleManager.getInstance(project).reformat(otherElement);
                PsiDocumentManager.getInstance(project).commitAllDocuments();
            });

            CodeSmellLogger.info("Refactoring done.");
        });
        if (DiagnosticTool.DIAGNOSTIC_MODE) {
            long endTime = System.nanoTime();
            long duration = (endTime - startTime);
            DiagnosticTool.addMeasurement(new DiagnosticTool.RefactoringMeasurement(project, duration, dataClumpTypeContext));
        }

    }

    /**
     * Creates a new class with the given name in the given directory and extracts the given properties as fields.
     *
     * @param dir       the directory in which the class should be created
     * @param className the name of the class
     * @param fields    the properties that should be extracted as fields
     * @return the created class
     */
    private TypeScriptClass extractClass(PsiDirectory dir, String className, List<Property> fields) {
        CodeSmellLogger.info("Extracting class...");

        List<Property> constructorFields = new ArrayList<>(fields);
        List<Property> abstractFields = new ArrayList<>();
        List<Property> declaredFields = new ArrayList<>();

        // if the option to include modifiers in the extracted class is enabled
        // the abstract and declared fields are separated from the constructor fields
        if (Objects.requireNonNull(DataClumpSettings.getInstance().getState()).includeModifiersInExtractedClass == DataClumpSettings.Modifier.ALL) {
            abstractFields = fields.stream().filter(property -> property instanceof Classfield && ((Classfield) property).getModifiers().contains("abstract")).toList();
            declaredFields = fields.stream().filter(property -> property instanceof Classfield && ((Classfield) property).getModifiers().contains("declare")).toList();

            constructorFields.removeAll(abstractFields);
            constructorFields.removeAll(declaredFields);
        }

        // create class
        TypeScriptClass psiClass = PsiUtil.createClass(dir, className, !abstractFields.isEmpty(), true);

        // add a constructor that defines the fields that are not abstract or declared
        TypeScriptFunction constructor = PsiUtil.createConstructor(psiClass, constructorFields, new ArrayList<>(), null, Objects.requireNonNull(DataClumpSettings.getInstance().getState()).includeModifiersInExtractedClass);
        PsiUtil.addFunctionToClass(psiClass, constructor);

        // add abstract fields
        for (Property field : abstractFields) {
            ES6FieldStatementImpl abstractField = PsiUtil.createJSFieldStatement(psiClass, field.getName(), field.getTypesAsString(), ((Classfield) field).getVisibility(), ((Classfield) field).getModifiers(), null);
            PsiUtil.addFieldToClass(psiClass, abstractField);
        }
        // add declared fields
        for (Property field : declaredFields) {
            ES6FieldStatementImpl declaredField = PsiUtil.createJSFieldStatement(psiClass, field.getName(), field.getTypesAsString(), ((Classfield) field).getVisibility(), ((Classfield) field).getModifiers(), null);
            PsiUtil.addFieldToClass(psiClass, declaredField);
        }

        // Getter and Setter
        for (Property field : fields) {
            // Skip public fields as they do not need getter and setter
            if (DataClumpSettings.getInstance().getState().includeModifiersInExtractedClass != DataClumpSettings.Modifier.NONE
                    && field instanceof Classfield
                    && ((Classfield) field).isPublic())
                continue;

            TypeScriptFunction getter = PsiUtil.createGetter(psiClass, field);
            TypeScriptFunction setter = PsiUtil.createSetter(psiClass, field);

            PsiUtil.addFunctionToClass(psiClass, getter);
            PsiUtil.addFunctionToClass(psiClass, setter);
        }

        // add the class to the directory
        WriteCommandAction.runWriteCommandAction(dir.getProject(), () -> {
            PsiFile file = PsiFileFactory.getInstance(dir.getProject()).createFileFromText(className + ".ts", TypeScriptFileType.INSTANCE, "");
            file.add(psiClass);
            dir.add(file);
            PsiDocumentManager.getInstance(dir.getProject()).commitAllDocuments();
        });

        // get the virtual file of the created class and find the class in the file
        // this is necessary to get the class with the correct references //TODO
        VirtualFile virtualFile = dir.getVirtualFile().findChild(className + ".ts");
        assert virtualFile != null;
        PsiFile file = PsiManager.getInstance(dir.getProject()).findFile(virtualFile);
        TypeScriptClass extractedClass = PsiTreeUtil.findChildOfType(file, TypeScriptClass.class);

        CodeSmellLogger.info("Class extracted.");
        return extractedClass;
    }

    /**
     * Adjusts the constructor of the given class to include the extracted properties.
     *
     * @param psiClass           the class to adjust the constructor of
     * @param matchingProperties the properties that are extracted
     */
    private void adjustConstructor(TypeScriptClass psiClass, List<Property> matchingProperties) {

        CodeSmellLogger.info("Adjusting constructor of " + psiClass.getQualifiedName() + "...");

        TypeScriptFunction constructor = (TypeScriptFunction) psiClass.getConstructor();

        if (constructor == null) {
            // create new constructor
            TypeScriptFunction newConstructor = PsiUtil.createConstructor(psiClass, matchingProperties, new ArrayList<>(), null, Objects.requireNonNull(DataClumpSettings.getInstance().getState()).includeModifiersInDetection);
            PsiUtil.addFunctionToClass(psiClass, newConstructor);
        } else {

            // get the classfields that are defined in the constructor
            HashMap<Classfield, Parameter> definedClassfields = new HashMap<>();
            getClassfieldDefiningParameter(constructor, new HashMap<>(), definedClassfields);

            // the fields and parameters that are defined in the constructor
            List<Property> constructorFields = new ArrayList<>();
            List<Property> constructorParameter = new ArrayList<>();

            // iterate over all parameters of the constructor
            for (JSParameterListElement psiParameter : Objects.requireNonNull(constructor.getParameterList()).getParameters()) {

                // if the parameter is a classfield -> add it to the constructor fields
                if (PsiUtil.isParameterField((TypeScriptParameter) psiParameter)) {
                    constructorFields.add(new Classfield((TypeScriptParameter) psiParameter));
                } else {
                    // if the parameter is not a classfield -> add it to the constructor parameters
                    constructorParameter.add(new Parameter((TypeScriptParameter) psiParameter));
                }
            }

            // get the properties that are not defined in the constructor
            for (Property property : matchingProperties) {
                // skip abstract fields or declared fields
                if (property instanceof Classfield && (((Classfield) property).getModifiers().contains("abstract") ||
                        ((Classfield) property).getModifiers().contains("declare"))) continue;

                if (!definedClassfields.containsKey(property)) {
                    // if there is another property already with the same name -> it is skipped to not make it even more complex
                    if (constructorParameter.stream().anyMatch(field -> field.getName().equals(property.getName()))) {
                        // if the property is already a parameter -> skip
                    } else {
                        constructorFields.add(property);
                    }

                }
            }

            // remove the constructor and create a new one with the adjusted parameters
            JSBlockStatement body = constructor.getBlock();

            TypeScriptFunction newConstructor = PsiUtil.createConstructor(psiClass, constructorFields, constructorParameter, body, Objects.requireNonNull(DataClumpSettings.getInstance().getState()).includeModifiersInDetection);
            WriteCommandAction.runWriteCommandAction(psiClass.getProject(), () -> {
                constructor.delete();
                PsiDocumentManager.getInstance(psiClass.getProject()).commitAllDocuments();
            });
            PsiUtil.addFunctionToClass(psiClass, newConstructor);
        }
    }

    /**
     * Gets for the given function the parameters mapped to the classfields they define in the function as well as
     * the classfields mapped to the parameters that define them in the function.
     *
     * @param function           the function to get the defining parameters and defined classfields for
     * @param definingParameters the map to store the defining parameters
     * @param definedClassfields the map to store the defined classfields
     */
    private void getClassfieldDefiningParameter(TypeScriptFunction function, HashMap<Parameter, Classfield> definingParameters, HashMap<Classfield, Parameter> definedClassfields) {

        definedClassfields.clear();
        definingParameters.clear();

        // iterate over all parameters of the constructor
        for (JSParameterListElement psiParameter : function.getParameters()) {

            // if the parameter is a field of the class -> relevant for constructor
            if (PsiUtil.isParameterField((TypeScriptParameter) psiParameter)) {
                Classfield field = new Classfield((TypeScriptParameter) psiParameter);
                Parameter parameter = new Parameter((TypeScriptParameter) psiParameter);
                definedClassfields.put(field, parameter);
                definingParameters.put(parameter, field);
            } else {
                // if (PsiUtil.isAssignedNewValue((TypeScriptParameter) psiParameter)) continue;
                List<Classfield> fields = PsiUtil.getAssignedToField((TypeScriptParameter) psiParameter);

                for (Classfield field : fields) {
                    Parameter parameter = new Parameter((TypeScriptParameter) psiParameter);
                    definedClassfields.put(field, parameter);
                    definingParameters.put(parameter, field);
                    break;
                }
            }
        }
    }

    /**
     * Gets all the default values of the properties of the given class.
     *
     * @param psiClass      the class to get the default values for
     * @param properties    the properties to get the default values for
     * @param defaultValues the map to store the default values mapped to the properties
     */
    private void getDefaultValues(TypeScriptClass psiClass, List<Property> properties, HashMap<Classfield, String> defaultValues) {

        defaultValues.clear();

        List<Classfield> classfields = Index.getClassesToClassFields().get(psiClass);

        for (Classfield classfield : classfields) {
            if (properties.contains(classfield)) {
                PsiElement psiField = PsiUtil.getPsiField(psiClass, classfield);
                assert psiField != null;
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


    /**
     * Adds getter and setter to the extracted class for the given properties if they do not already exist.
     *
     * @param psiClass   the class to add the getter and setter to
     * @param properties the properties to add the getter and setter for
     */
    private void addGetterAndSetter(TypeScriptClass psiClass, List<Property> properties) {
        for (Property property : properties) {
            Classfield classfield = PsiUtil.getClassfield(psiClass, property.getName());
            if (classfield == null) {
                CodeSmellLogger.error("Field " + property.getName() + " not found in class " + psiClass.getQualifiedName(), new IllegalArgumentException());
                continue;
            }

            if (!PsiUtil.hasGetter(psiClass, classfield)) {
                PsiElement psiElement = PsiUtil.getPsiField(psiClass, classfield);
                // add underscore if necessary
                if (psiElement instanceof TypeScriptField psiField) {
                    if (!Objects.requireNonNull(psiField.getName()).startsWith("_")) {
                        PsiUtil.rename(psiField, "_" + psiField.getName());
                    }
                } else if (psiElement instanceof TypeScriptParameter psiParameter) {
                    if (!Objects.requireNonNull(psiParameter.getName()).startsWith("_")) {
                        PsiUtil.rename(psiParameter, "_" + psiParameter.getName());
                    }
                }

                TypeScriptFunction getter = PsiUtil.createGetter(psiClass, classfield);
                PsiUtil.addFunctionToClass(psiClass, getter);
            }

            if (!PsiUtil.hasSetter(psiClass, classfield)) {
                PsiElement psiElement = PsiUtil.getPsiField(psiClass, classfield);
                // add underscore if necessary
                if (psiElement instanceof TypeScriptField psiField) {
                    if (!Objects.requireNonNull(psiField.getName()).startsWith("_")) {
                        PsiUtil.rename(psiField, "_" + psiField.getName());
                    }
                } else if (psiElement instanceof TypeScriptParameter psiParameter) {
                    if (!Objects.requireNonNull(psiParameter.getName()).startsWith("_")) {
                        PsiUtil.rename(psiParameter, "_" + psiParameter.getName());
                    }
                }
                TypeScriptFunction setter = PsiUtil.createSetter(psiClass, classfield);
                PsiUtil.addFunctionToClass(psiClass, setter);
            }
        }
    }

    /**
     * Gets the String representation of the default initialization of the extracted class
     * that uses the default values of the classfields.
     *
     * @param psiClass       the class that contains the data clump
     * @param extractedClass the class that contains the extracted properties
     * @param defaultValues  the default values of the classfields of the class
     * @return the default initialization of the extracted class
     */
    private String getDefaultInit(TypeScriptClass psiClass, TypeScriptClass extractedClass, HashMap<Classfield, String> defaultValues) {

        if (psiClass.getConstructor() != null) return null;

        List<Property> extractedParameters = getParametersAsPropertyList((TypeScriptFunction) Objects.requireNonNull(extractedClass.getConstructor()));

        StringBuilder init = new StringBuilder();
        init.append("new ").append(extractedClass.getName()).append("(");

        for (Property property : extractedParameters) {
            if (defaultValues.containsKey(property)) {
                init.append(defaultValues.get(property)).append(", ");
            } else {
                init.append(DefaultValues.getDefaultValue(property)).append(", ");
            }
        }

        if (!extractedParameters.isEmpty()) {
            init.setLength(init.length() - 2);
        }

        init.append(");");

        return init.toString();
    }

    /**
     * Refactors the given element by adding the extracted class as a field or parameter depending on the type of the element.
     *
     * @param element            the element that contains the data clump to be refactored
     * @param extractedClass     the class that contains the extracted properties
     * @param dataClump          the properties that are extracted
     * @param definedClassfields the classfields that are defined in the constructor (by the parameters) of the element
     * @param defaultValues      the default values of the classfields of the element
     */
    private void refactorElement(PsiElement element, TypeScriptClass extractedClass, List<Classfield> dataClump, HashMap<Classfield, Parameter> definedClassfields, HashMap<Classfield, String> defaultValues) {

        // refactor the element depending on its type
        // if the element is the extacted class, the refactoring is already done
        if (element instanceof TypeScriptClass psiClass && !Objects.equals(psiClass.getQualifiedName(), extractedClass.getQualifiedName())) {
            addImport(element, extractedClass);
            refactorClass(psiClass, extractedClass, dataClump, definedClassfields, defaultValues);
            Index.updateClass(psiClass);
        } else if (element instanceof TypeScriptFunction function) {
            addImport(element, extractedClass);
            refactorFunction(function, extractedClass, dataClump);
            Index.updateFunction(function);
        }
    }

    /**
     * Adds an import statement for the extracted class to the element if it is not already imported.
     *
     * @param element        the element to which the import statement should be added
     * @param extractedClass the class that should be imported
     */
    private void addImport(PsiElement element, TypeScriptClass extractedClass) {

        PsiFile elementFile = element.getContainingFile();
        PsiFile extractedFile = extractedClass.getContainingFile();

        if (elementFile.equals(extractedFile)) return;

        //check that there is not already an import statement for the extracted class
        for (ES6ImportDeclaration importStatement : PsiTreeUtil.findChildrenOfType(elementFile, ES6ImportDeclaration.class)) {
            if (importStatement.getNamedImports() != null
                    && importStatement.getNamedImports().getText().contains(Objects.requireNonNull(extractedClass.getName()))) {
                return;
            }
        }

        // add the import statement
        String relativePath = PsiUtil.getRelativePath(elementFile, extractedFile);
        String importStatement = "import { " + extractedClass.getName() + " } from '" + relativePath + "';\n";
        PsiElement firstChild = elementFile.getFirstChild();
        WriteCommandAction.runWriteCommandAction(element.getProject(), () -> {
            elementFile.addBefore(JSElementFactory.createExpressionCodeFragment(element.getProject(), importStatement, elementFile), firstChild);
            PsiDocumentManager.getInstance(element.getProject()).commitAllDocuments();
        });

    }

    /**
     * Refactors the given class that contains the data clump to use the extracted class instead of the extracted properties.
     * The extracted fields are replaced with a field of the extracted class.
     * The constructor is updated to use the extracted class as a parameter instead of the extracted properties.
     * The constructor calls are updated to use the extracted class instead of the extracted properties.
     * The field references are updated to use the extracted class instead of the extracted properties.
     *
     * @param psiClass           the class that contains the data clump
     * @param extractedClass     the class that contains the extracted properties
     * @param dataClump          the properties that are extracted
     * @param definedClassfields the classfields that are defined in the constructor (by the parameters) of the class
     * @param defaultValues      the default values of the classfields of the class
     */
    private void refactorClass(TypeScriptClass psiClass, TypeScriptClass extractedClass, List<Classfield> dataClump, HashMap<Classfield, Parameter> definedClassfields, HashMap<Classfield, String> defaultValues) {
        CodeSmellLogger.info("Refactoring class " + psiClass.getQualifiedName() + "...");

        String fieldName = "my_" + Objects.requireNonNull(extractedClass.getName()).toLowerCase(); // this should be a unique name

        // add the extracted class as a field
        ES6FieldStatementImpl newFieldStatement = PsiUtil.createJSFieldStatement(
                psiClass, fieldName, extractedClass.getJSType().getTypeText(), "public", new ArrayList<>(), getDefaultInit(psiClass, extractedClass, defaultValues)
        );
        PsiUtil.addFieldToClass(psiClass, newFieldStatement);

        // update all field references by replacing the extracted properties with getter and setter calls on the extracted class
        updateFieldReferences(psiClass, dataClump, fieldName);

        // adjust the constructor to use the extracted class as a parameter instead of the extracted properties
        // also adjust all constructor calls to use the extracted class instead of the extracted properties
        updateConstructor(psiClass, dataClump, extractedClass, fieldName, definedClassfields, defaultValues);

        CodeSmellLogger.info("Class refactored.");
    }

    /**
     * Updates the field references of the given class by replacing the extracted properties
     * with getter and setter calls on the extracted class.
     *
     * @param psiClass  the class that contains the data clump
     * @param dataClump the properties that are extracted
     * @param fieldName the name of the field that contains the extracted class
     */
    private void updateFieldReferences(TypeScriptClass psiClass, List<Classfield> dataClump, String fieldName) {

        List<Classfield> classfields = Index.getClassesToClassFields().get(psiClass);

        // iterate over all classfields of the class
        for (Classfield classfield : classfields) {
            // if the classfield is one of the extracted properties -> refactor the references
            if (dataClump.contains(classfield)) {

                PsiElement psiField = PsiUtil.getPsiField(psiClass, classfield);
                assert psiField != null;

                // iterate over all references to the field
                for (PsiReference reference : ReferencesSearch.search(psiField)) {
                    // resolve references also finds references that have the same name but do not reference the field
                    // so we need to check if the reference actually resolves to the field
                    if (reference.resolve() != psiField) continue;
                    // if it is assignment -> refactor to setter
                    JSAssignmentExpression assignmentExpression = PsiTreeUtil.getParentOfType(reference.getElement(), JSAssignmentExpression.class);
                    if (assignmentExpression != null && Objects.requireNonNull(assignmentExpression.getLOperand()).getFirstChild() == reference) {
                        replaceAssignmentWithSetter(psiClass, assignmentExpression, fieldName, classfield.getName());
                    } else { // if no assignment refactor to getter
                        replaceReferenceWithGetter(psiClass, reference, fieldName, classfield.getName(), true);
                    }
                }

                // remove the classfield from the class if it is a field
                // if it is a constructor parameter it will be removed later
                if (psiField instanceof TypeScriptField) {
                    WriteCommandAction.runWriteCommandAction(psiClass.getProject(), () -> {
                        psiField.delete();
                        PsiDocumentManager.getInstance(psiClass.getProject()).commitAllDocuments();
                    });
                }
            }
        }
    }

    /**
     * Updates the constructor of the given class to use the extracted class as a parameter instead of the extracted properties.
     * The constructor calls are updated to use the extracted class instead of the extracted properties.
     *
     * @param psiClass           the class that contains the data clump
     * @param dataClump          the properties that are extracted
     * @param extractedClass     the class that contains the extracted properties
     * @param fieldName          the name of the field that contains the extracted class
     * @param definedClassfields the classfields that are defined in the constructor (by the parameters) of the class
     * @param defaultValues      the default values of the classfields of the class
     */
    private void updateConstructor(TypeScriptClass psiClass, List<Classfield> dataClump, TypeScriptClass extractedClass, String fieldName, HashMap<Classfield, Parameter> definedClassfields, HashMap<Classfield, String> defaultValues) {

        CodeSmellLogger.info("Updating constructor of " + psiClass.getQualifiedName() + "...");

        TypeScriptFunction constructor = (TypeScriptFunction) psiClass.getConstructor();
        if (constructor == null) return;

        // save the original parameters -> needed for refactoring the function calls
        List<Property> originalParameters = getParametersAsPropertyList(constructor);

        // remove the extracted properties from the constructor and replace the references with getter calls on the extracted class
        refactorConstructorParameter(constructor, dataClump, definedClassfields, fieldName);

        // add the extracted class as a parameter to the constructor
        addClassAsParameter(constructor, extractedClass, fieldName);

        // refactor the function calls of the constructor since the constructor was adjusted
        refactorFunctionCalls(constructor, originalParameters, extractedClass, dataClump, definedClassfields, defaultValues);


        // add initialization of the field
        JSStatement initialization = JSPsiElementFactory.createJSStatement(
                "this." + fieldName + " = " + fieldName + ";", psiClass
        );

        // add the initialization to the constructor block after the super call if it exists
        JSBlockStatement block = constructor.getBlock();
        assert block != null;
        JSExpressionStatement superCall = PsiUtil.getSuperCall(block);
        PsiElement insertPos;
        if (superCall != null) {
            insertPos = superCall;
        } else {
            insertPos = block.getFirstChild();
        }
        WriteCommandAction.runWriteCommandAction(psiClass.getProject(), () -> {
            block.addAfter(initialization, insertPos);
            PsiDocumentManager.getInstance(psiClass.getProject()).commitAllDocuments();
        });

        // remove all assignments of the form this.person.name = this.person.name
        List<JSAssignmentExpression> markedForRemoval = new ArrayList<>();

        Objects.requireNonNull(constructor.getBlock()).accept(new JSRecursiveWalkingElementVisitor() {
            @Override
            public void visitJSAssignmentExpression(@NotNull JSAssignmentExpression node) {
                super.visitJSAssignmentExpression(node);
                if (Objects.requireNonNull(node.getLOperand()).getText().equals(Objects.requireNonNull(node.getROperand()).getText())) {
                    markedForRemoval.add(node);
                }
            }
        });

        for (JSAssignmentExpression assignment : markedForRemoval) {
            WriteCommandAction.runWriteCommandAction(psiClass.getProject(), () -> {
                assignment.getParent().delete();
                PsiDocumentManager.getInstance(psiClass.getProject()).commitAllDocuments();
            });
        }
    }

    /**
     * Deletes all parameters from the constructor that are part of the extracted properties
     * or define a field that is part of the extracted properties. The references to the parameters are replaced
     * with getter calls on the extracted class.
     *
     * @param constructor        the constructor to refactor
     * @param dataClump          the properties that are extracted
     * @param definedClassfields the classfields that are defined in the constructor (by the parameters) of the class
     * @param newParameterName   the name of the new parameter object
     */
    private void refactorConstructorParameter(TypeScriptFunction constructor, List<Classfield> dataClump, HashMap<Classfield, Parameter> definedClassfields, String newParameterName) {

        // iterate over all properties that are defined in the constructor

        for (Classfield property : definedClassfields.keySet()) {
            if (!dataClump.contains(property)) continue; // only refactor the extracted properties

            TypeScriptParameter parameter = PsiUtil.getPsiParameter(constructor, definedClassfields.get(property));
            assert parameter != null;

            // replace all references to the parameter with getter calls on the extracted class
            for (PsiReference reference : ReferencesSearch.search(parameter)) {
                replaceReferenceWithGetter(PsiTreeUtil.getParentOfType(constructor, TypeScriptClass.class), reference, newParameterName, property.getName(), false);
            }

            // remove the parameter from the constructor
            WriteCommandAction.runWriteCommandAction(constructor.getProject(), () -> {
                        parameter.delete();
                        PsiDocumentManager.getInstance(constructor.getProject()).commitAllDocuments();
                    }
            );
        }
    }

    /**
     * Adds the extracted class as a parameter to the given function.
     *
     * @param function         the function to which the parameter should be added
     * @param extractedClass   the class that should be added as a parameter
     * @param newParameterName the name of the new parameter
     */
    private void addClassAsParameter(TypeScriptFunction function, TypeScriptClass extractedClass, String newParameterName) {
        JSParameterList parameterList = function.getParameterList();
        assert parameterList != null;
        TypeScriptParameter newParameter = PsiUtil.createTypeScriptParameter(function, newParameterName, extractedClass.getJSType());
        PsiUtil.addParameterToParameterList(newParameter, parameterList);
    }


    /**
     * Refactors all calls of the given constructor to match the new function signature after new arguments where added.
     *
     * @param constructor        the constructor to refactor
     * @param originalParameters the original parameters of the constructor before the refactoring
     * @param defaultValues      the default values of the classfields of the class
     */
    private void refactorConstructorCalls(TypeScriptFunction constructor, List<Property> originalParameters, HashMap<Classfield, String> defaultValues) {
        if (!constructor.isConstructor()) return;

        HashMap<Parameter, Classfield> definingParameter = new HashMap<>();
        getClassfieldDefiningParameter(constructor, definingParameter, new HashMap<>());

        for (PsiReference functionCall : ReferencesSearch.search(constructor)) {

            JSArgumentList argumentList = PsiTreeUtil.getNextSiblingOfType(functionCall.getElement(), JSArgumentList.class);
            assert argumentList != null;
            JSExpression[] originalArguments = argumentList.getArguments();

            // create the new argument list
            StringBuilder updatedArguments = new StringBuilder("(");

            for (JSParameterListElement currentPsiParameter : constructor.getParameters()) {
                Parameter currentParameter = new Parameter((TypeScriptParameter) currentPsiParameter);

                String currentValue;
                // first try original Parameter
                int index = originalParameters.indexOf(currentParameter);
                if (index != -1) {
                    currentValue = originalArguments[index].getText();
                } else {
                    Classfield definedClassfield = definingParameter.get(currentParameter);
                    if (definedClassfield != null && defaultValues.get(definedClassfield) != null) {
                        currentValue = defaultValues.get(definedClassfield);
                    } else {
                        currentValue = DefaultValues.getDefaultValue(currentParameter);
                    }
                }
                updatedArguments.append(currentValue).append(", ");
            }

            // Remove trailing comma and close the argument list
            if (updatedArguments.charAt(updatedArguments.length() - 2) == ',') {
                updatedArguments.setLength(updatedArguments.length() - 2);
            }
            updatedArguments.append(")");

            // Update the function call with the new argument list
            WriteCommandAction.runWriteCommandAction(constructor.getProject(), () -> {
                JSExpression newArguments = JSPsiElementFactory.createJSExpression(updatedArguments.toString(), argumentList);
                argumentList.replace(newArguments);
                PsiDocumentManager.getInstance(constructor.getProject()).commitAllDocuments();
            });

        }

    }


    /**
     * Refactors the function calls to match the new function signature after the extracted class was added as a parameter.
     *
     * @param function                   the function to refactor
     * @param originalParameters         the original parameters of the function before the refactoring
     *                                   in the form of properties in the right order.
     *                                   This is the way their values will appear in the function call
     * @param extractedClass             the class that was extracted
     * @param dataClump                  the properties that were extracted
     * @param originalDefinedClassfields the classfields that are defined in the constructor (by the parameters) of the class.
     *                                   Only needed if the function is a constructor.
     * @param defaultValues              the default values of the classfields of the class.
     *                                   Only needed if the function is a constructor.
     */
    private void refactorFunctionCalls(TypeScriptFunction function,
                                       List<Property> originalParameters,
                                       TypeScriptClass extractedClass,
                                       List<Classfield> dataClump,
                                       @Nullable HashMap<Classfield, Parameter> originalDefinedClassfields,
                                       @Nullable HashMap<Classfield, String> defaultValues) {

        List<Property> extractedParameters = getParametersAsPropertyList((TypeScriptFunction) Objects.requireNonNull(extractedClass.getConstructor()));
        HashMap<Classfield, Parameter> extractedDefinedClassfields = new HashMap<>();
        HashMap<Parameter, Classfield> extractedDefiningParameters = new HashMap<>();
        getClassfieldDefiningParameter((TypeScriptFunction) extractedClass.getConstructor(), extractedDefiningParameters, extractedDefinedClassfields);

        for (PsiReference functionCall : ReferencesSearch.search(function)) {

            CodeSmellLogger.info("Refactoring function call... " + functionCall.getElement().getText());
            JSArgumentList argumentList = PsiTreeUtil.getNextSiblingOfType(functionCall.getElement(), JSArgumentList.class);
            if (argumentList == null) continue;
            JSExpression[] originalArguments = argumentList.getArguments();

            // create the new argument list
            StringBuilder updatedArguments = new StringBuilder("(");

            for (JSParameterListElement currentFunctionsPsiParameter : function.getParameters()) {

                // if the next parameter expects the extracted class
                // -> replace the argument with a constructor call for the extracted class
                if (Objects.equals(currentFunctionsPsiParameter.getJSType(), extractedClass.getJSType())) {

                    updatedArguments.append("new ").append(extractedClass.getName()).append("(");
                    for (Property extractedClassProperty : extractedParameters) {

                        if (!dataClump.contains(extractedClassProperty)) {
                            updatedArguments.append(DefaultValues.getDefaultValue(extractedClassProperty)).append(", ");
                            continue;
                        }

                        if (!function.isConstructor()) {

                            if (extractedClassProperty instanceof Classfield) {
                                int index = originalParameters.indexOf(extractedClassProperty);
                                if (index == -1) {
                                    CodeSmellLogger.error("Property " + extractedClassProperty.getName() + " not found in original parameters", new IndexOutOfBoundsException());
                                    continue;
                                }
                                updatedArguments.append(originalArguments[index].getText()).append(", ");
                            } else if (extractedClassProperty instanceof Parameter) {
                                Classfield definedClassfield = extractedDefiningParameters.get(extractedClassProperty);
                                int index = originalParameters.indexOf(definedClassfield);
                                if (index == -1) {
                                    CodeSmellLogger.error("Property " + extractedClassProperty.getName() + " not found in original parameters", new IndexOutOfBoundsException());
                                    continue;
                                }
                                updatedArguments.append(originalArguments[index].getText()).append(", ");
                            }

                        } else {
                            // if the function is a constructor -> use the defined classfields and default values
                            assert originalDefinedClassfields != null;
                            assert defaultValues != null;

                            if (extractedClassProperty instanceof Classfield) {

                                if (originalDefinedClassfields.containsKey(extractedClassProperty)) { // if the property is defined in the constructor -> use the corresponding parameter
                                    int index = originalParameters.indexOf(originalDefinedClassfields.get(extractedClassProperty));
                                    updatedArguments.append(originalArguments[index].getText()).append(", ");
                                } else if (defaultValues.containsKey(extractedClassProperty)) { // if the property has a default value -> use the default value
                                    updatedArguments.append(defaultValues.get(extractedClassProperty)).append(", ");
                                } else { // if the property is not defined in the constructor and has no default value -> use undefined
                                    updatedArguments.append(DefaultValues.getDefaultValue(extractedClassProperty)).append(", ");
                                }

                            } else if (extractedClassProperty instanceof Parameter) {
                                Classfield definedClassfield = extractedDefiningParameters.get(extractedClassProperty);

                                if (originalDefinedClassfields.containsKey(definedClassfield)) { // if the property is defined in the constructor -> use the corresponding parameter
                                    int index = originalParameters.indexOf(originalDefinedClassfields.get(definedClassfield));
                                    updatedArguments.append(originalArguments[index].getText()).append(", ");
                                } else if (defaultValues.containsKey(definedClassfield)) { // if the property has a default value -> use the default value
                                    updatedArguments.append(defaultValues.get(definedClassfield)).append(", ");
                                } else { // if the property is not defined in the constructor and has no default value -> use undefined
                                    updatedArguments.append(DefaultValues.getDefaultValue(extractedClassProperty)).append(", ");
                                }
                            }


                        }
                    }
                    // Remove trailing comma
                    if (updatedArguments.charAt(updatedArguments.length() - 2) == ',') {
                        updatedArguments.setLength(updatedArguments.length() - 2);
                    }
                    updatedArguments.append(")");
                } else {
                    // Append original arguments
                    updatedArguments.append(originalArguments[originalParameters.indexOf(new Parameter((TypeScriptParameter) currentFunctionsPsiParameter))].getText());
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
                PsiDocumentManager.getInstance(function.getProject()).commitAllDocuments();
            });
        }
    }

    /**
     * Gets the parameters of the given function as a list of properties.
     * The order of the properties is the same as the order of the parameters.
     *
     * @param function the function to get the parameters from
     * @return the parameters of the function as a list of properties
     */
    private List<Property> getParametersAsPropertyList(TypeScriptFunction function) {
        List<Property> parameters = new ArrayList<>();
        for (JSParameterListElement psiParameter : function.getParameters()) {
            if (PsiUtil.isParameterField((TypeScriptParameter) psiParameter)) {
                Classfield field = new Classfield((TypeScriptParameter) psiParameter);
                parameters.add(field);
            } else {
                Parameter parameter = new Parameter((TypeScriptParameter) psiParameter);
                parameters.add(parameter);
            }
        }
        return parameters;
    }

    /**
     * Refactors the given class that contains the data clump to use the extracted class instead of the extracted properties.
     * The extracted fields are replaced with a field of the extracted class.
     * The function calls are updated to use the extracted class instead of the extracted properties.
     *
     * @param psiFunction    the function that contains the data clump
     * @param extractedClass the class that contains the extracted properties
     * @param dataClump      the properties that are extracted
     */
    private void refactorFunction(TypeScriptFunction psiFunction, TypeScriptClass extractedClass, List<Classfield> dataClump) {
        CodeSmellLogger.info("Refactoring function " + psiFunction.getQualifiedName() + "...");

        String newParameterName = "my_" + Objects.requireNonNull(extractedClass.getName()).toLowerCase(); // this should be a unique name

        // save the original parameters -> needed for refactoring the function calls
        List<Property> originalParameters = getParametersAsPropertyList(psiFunction);

        // remove the extracted properties from the function and replace the references with getter calls on the extracted class
        refactorFunctionParameter(psiFunction, dataClump, newParameterName);

        // Add the extracted class as a new parameter
        addClassAsParameter(psiFunction, extractedClass, newParameterName);

        // refactor the function calls of the function since the function was adjusted
        refactorFunctionCalls(psiFunction, originalParameters, extractedClass, dataClump, new HashMap<>(), new HashMap<>());

        CodeSmellLogger.info("Function refactored.");
    }

    /**
     * Deletes all parameters from the function that are part of the extracted properties
     * and replaces the references to the parameters with getter calls on the extracted class.
     *
     * @param function         the function to refactor
     * @param dataClump        the properties that are extracted
     * @param newParameterName the name of the new parameter object
     */
    private void refactorFunctionParameter(TypeScriptFunction function, List<Classfield> dataClump, String newParameterName) {

        for (JSParameterListElement parameter : function.getParameters()) {
            Parameter currentParameter = new Parameter((TypeScriptParameter) parameter);
            if (dataClump.contains(currentParameter)) { // only refactor the extracted properties

                // replace all references to the parameter with getter calls on the extracted class
                for (PsiReference reference : ReferencesSearch.search(parameter)) {
                    JSExpression newExpression = JSPsiElementFactory.createJSExpression(
                            newParameterName + "." + currentParameter.getName(), reference.getElement()
                    );
                    WriteCommandAction.runWriteCommandAction(reference.getElement().getProject(), () -> {
                        reference.getElement().replace(newExpression);
                        PsiDocumentManager.getInstance(reference.getElement().getProject()).commitAllDocuments();
                    });
                }
                // Remove the parameter from the function's signature
                WriteCommandAction.runWriteCommandAction(function.getProject(), () -> {
                    parameter.delete();
                    PsiDocumentManager.getInstance(function.getProject()).commitAllDocuments();
                });
            }
        }
    }

    /**
     * Replaces the given assignment with a setter call on the extracted class.
     *
     * @param psiClass     the class that contains the data clump
     * @param assignment   the assignment to replace
     * @param fieldName    the name of the field that contains the extracted class
     * @param propertyName the name of the property that is assigned
     */
    private void replaceAssignmentWithSetter(TypeScriptClass psiClass, JSAssignmentExpression assignment, String fieldName, String propertyName) {

        String expressionText;
        if (PsiTreeUtil.getParentOfType(assignment, TypeScriptClass.class) == psiClass) {
            expressionText = "this." + fieldName + "." + propertyName + " = " + Objects.requireNonNull(assignment.getROperand()).getText();
        } else {
            expressionText = Objects.requireNonNull(assignment.getLOperand()).getFirstChild().getFirstChild().getText() + "." + fieldName + "." + propertyName + " = " + Objects.requireNonNull(assignment.getROperand()).getText();
        }

        JSExpression newExpression = JSPsiElementFactory.createJSExpression(expressionText, assignment);
        WriteCommandAction.runWriteCommandAction(assignment.getProject(), () -> {
            assignment.replace(newExpression);
            PsiDocumentManager.getInstance(assignment.getProject()).commitAllDocuments();
        });
    }

    /**
     * Replaces the given reference with a getter call on the extracted class.
     *
     * @param psiClass         the class that contains the data clump
     * @param reference        the reference to replace
     * @param fieldName        the name of the field that contains the extracted class
     * @param propertyName     the name of the property that is referenced
     * @param isFieldReference if the reference is a field reference
     */
    private void replaceReferenceWithGetter(TypeScriptClass psiClass, PsiReference reference, String fieldName, String propertyName, boolean isFieldReference) {
        String expressionText;
        if (PsiTreeUtil.getParentOfType(reference.getElement(), TypeScriptClass.class) == psiClass && isFieldReference) {
            expressionText = "this." + fieldName + "." + propertyName;
        } else {
            expressionText = fieldName + "." + propertyName;
        }

        JSExpression newExpression = JSPsiElementFactory.createJSExpression(expressionText, reference.getElement());
        WriteCommandAction.runWriteCommandAction(reference.getElement().getProject(), () -> {
            reference.getElement().replace(newExpression);
            PsiDocumentManager.getInstance(reference.getElement().getProject()).commitAllDocuments();
        });
    }

    /**
     * Gets the classes that can be used for the data clump refactoring.
     *
     * @param properties the properties that are part of the data clump
     * @return the classes that can be used for the data clump refactoring
     */
    public Set<TypeScriptClass> getUsableClasses(List<Property> properties) {
        Set<TypeScriptClass> matchingClasses = new HashSet<>();

        // validate properties
        if (properties.isEmpty())
            CodeSmellLogger.error("No properties specified for dataclump.DataClumpRefactoring", new IllegalArgumentException());

        Property firstProperty = properties.get(0);

        if (!Index.getPropertiesToClasses().containsKey(firstProperty)) return matchingClasses;

        // find all classes that contain all properties
        List<JSClass> potentialClasses = Index.getPropertiesToClasses().get(firstProperty);
        for (JSClass psiClass : potentialClasses) {
            // filter all invalid, anonymous classes and interfaces since they cannot be used for the refactoring
            if (!psiClass.isValid() || psiClass.getName() == null || !(psiClass instanceof TypeScriptClass)) continue;
            if (PsiUtil.hasAll((TypeScriptClass) psiClass, properties)) matchingClasses.add((TypeScriptClass) psiClass);
        }

        // filter classes that have the properties assigned in the constructor
        // also filter classes where the properties are readonly
        Set<TypeScriptClass> filteredClasses = new HashSet<>(matchingClasses);

        for (TypeScriptClass psiClass : matchingClasses) {
            for (Property property : properties) {

                PsiElement psiField = PsiUtil.getPsiField(psiClass, property.getName());
                assert psiField != null;

                // check if the property is readonly -> cannot be refactored
                if (psiField instanceof TypeScriptField field) {
                    if (PsiUtil.getModifiers(field).contains("readonly")) {
                        filteredClasses.remove(psiClass);
                        break;
                    }
                } else if (psiField instanceof TypeScriptParameter parameter) {
                    if (PsiUtil.getModifiers(parameter).contains("readonly")) {
                        filteredClasses.remove(psiClass);
                        break;
                    }
                }
                TypeScriptFunction constructor = (TypeScriptFunction) psiClass.getConstructor();
                if (constructor == null) break;

                // check if the property is assigned in the constructor
                for (PsiReference reference : ReferencesSearch.search(psiField)) {

                    // check if the reference is in the constructor
                    if (PsiTreeUtil.getParentOfType(reference.getElement(), TypeScriptFunction.class) != constructor)
                        break;

                    // check if the reference is an assignment
                    JSAssignmentExpression assignment = PsiTreeUtil.getParentOfType(reference.getElement(), JSAssignmentExpression.class);
                    if (assignment == null || Objects.requireNonNull(assignment.getLOperand()).getFirstChild() != reference)
                        break;


                    // check if the right operand is a reference expression
                    if (!(assignment.getROperand() instanceof JSReferenceExpression)) {
                        filteredClasses.remove(psiClass);
                        break;
                    }

                    // check if the right operand is a property
                    Property assignedProperty = PsiUtil.resolveProperty((JSReferenceExpression) assignment.getROperand());
                    if (assignedProperty == null) {
                        filteredClasses.remove(psiClass);
                        break;
                    }

                    // check if the assigned property is the same as the property
                    HashMap<Parameter, Classfield> definingParameter = new HashMap<>();
                    getClassfieldDefiningParameter(constructor, definingParameter, new HashMap<>());

                    Classfield definingField = definingParameter.get(assignedProperty);
                    if (definingField == null || !definingField.equals(property)) {
                        filteredClasses.remove(psiClass);
                        break;
                    }
                }
            }
        }

        return filteredClasses;
    }

    /**
     * Checks if the given data clump can be refactored to the given class in terms of getter and setter conflicts.
     * For each property in the data clump, it is checked if the class already has a getter or setter with the same name.
     * The user is asked if the getter or setter can be used for the property.
     * If that's not the case, the class cannot be used for the refactoring.
     *
     * @param psiClass  the class to check
     * @param dataClump the data clump to refactor
     * @return false if the class can be used for the refactoring in terms of getter and setter, true otherwise
     */
    private boolean hasConflictingSetterOrGetter(TypeScriptClass psiClass, List<Property> dataClump) {
        for (Property property : dataClump) {
            if (hasConflictingSetter(psiClass, property.getName())) return true;
            if (hasConflictingGetter(psiClass, property.getName())) return true;
        }
        return false;
    }

    /**
     * Checks if the given class has a conflicting setter for the given field.
     * If a setter for the field already exists, the user is asked if the setter can be used for the field.
     *
     * @param psiClass  the class to check
     * @param fieldName the field to check
     * @return true if the class has a conflicting setter, false otherwise
     */
    private boolean hasConflictingSetter(TypeScriptClass psiClass, String fieldName) {

        for (JSFunction psiFunction : psiClass.getFunctions()) {
            if (!psiFunction.isSetProperty()) continue;

            String setterName = psiFunction.getName();

            if (setterName == null) continue;

            if (setterName.equals(fieldName)) {

                AtomicInteger response = new AtomicInteger();
                ApplicationManager.getApplication().invokeLater(() -> {
                    response.set(Messages.showYesNoDialog(
                            psiFunction.getProject(),
                            "<html><body>" +
                                    psiFunction.getText() + "<br><br>" +
                                    "</body></html>",
                            "Can this function be used as a setter for the field </b> \"" + fieldName + "\"?",
                            Messages.getQuestionIcon()
                    ));
                });
                if (response.get() == Messages.NO) {
                    return true;
                }

            }
        }
        return false;

    }

    /**
     * Checks if the given class has a conflicting getter for the given field.
     * If a getter for the field already exists, the user is asked if the getter can be used for the field.
     *
     * @param psiClass  the class to check
     * @param fieldName the field to check
     * @return true if the class has a conflicting getter, false otherwise
     */
    private boolean hasConflictingGetter(TypeScriptClass psiClass, String fieldName) {

        for (JSFunction psiFunction : psiClass.getFunctions()) {
            if (!psiFunction.isGetProperty()) continue;

            String getterName = psiFunction.getName();

            if (getterName == null) continue;

            if (getterName.equals(fieldName)) {
                AtomicInteger response = new AtomicInteger();
                ApplicationManager.getApplication().invokeLater(() -> {
                    response.set(Messages.showYesNoDialog(
                            psiFunction.getProject(),
                            "<html><body>" +
                                    psiFunction.getText() + "<br><br>" +
                                    "</body></html>",
                            "Can this function be used as a getter for the field </b> \"" + fieldName + "\"?",
                            Messages.getQuestionIcon()
                    ));

                });
                if (response.get() == Messages.NO) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns whether the quick fix should start in a write action.
     *
     * @return false since the quick fix uses a dialog to gather user input
     */
    @Override
    public boolean startInWriteAction() {
        // This quick fix uses a dialog to gather user input, so it should not start in a write action
        return false;
    }

    private static class DefaultValues {
        private static final String UNDEFINED = "undefined";
        private static final String STRING = "\"\"";
        private static final String NUMBER = "0";
        private static final String BOOLEAN = "false";
        private static final String ANY = "undefined";

        public static String getDefaultValue(Property property) {
            for (String type : property.getTypes()) {
                switch (type) {
                    case "string":
                        return STRING;
                    case "number":
                        return NUMBER;
                    case "boolean":
                        return BOOLEAN;
                    case "any":
                        return ANY;
                    case "undefined":
                        return UNDEFINED;
                    default:
                        break;
                }
            }

            AtomicReference<String> input = new AtomicReference<>();
            ApplicationManager.getApplication().invokeLater(() -> {
                do {
                    input.set(Messages.showInputDialog(
                            "<html>Please enter a (default) initialization for <b>" + property.getName() +
                                    " : " + property.getTypesAsString() + "</b>:<br></html>",
                            "",
                            Messages.getQuestionIcon()
                    ));
                } while (input.get() == null || input.get().isEmpty());
            });
            return input.get();

        }
    }


}
