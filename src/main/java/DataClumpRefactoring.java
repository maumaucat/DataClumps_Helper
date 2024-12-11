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
import com.intellij.lang.javascript.psi.impl.JSPsiElementFactory;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import util.*;

import java.util.*;


/**
 * Refactoring für Data Clumps.
 * The Data Clump code smell occurs when multiple properties are always used together.
 * This refactoring extracts the properties into a new or existing class and replaces the properties with the new class.
 */
public class DataClumpRefactoring implements LocalQuickFix {

    /**
     *  List of properties that are always used together
     */
    private final List<Property> matchingProperties;
    /**
     * The first element that contains the data clump
     */
    private final SmartPsiElementPointer<PsiElement> currentElement;
    /**
     * The second element that contains the data clump
     */
    private final SmartPsiElementPointer<PsiElement> otherElement;

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

    public DataClumpRefactoring(@NotNull PsiElement currentElement, @NotNull PsiElement otherElement, @NotNull List<Property> matchingProperties) {
        SmartPointerManager psiPointerManager = SmartPointerManager.getInstance(currentElement.getProject());
        this.matchingProperties = matchingProperties;
        this.currentElement = psiPointerManager.createSmartPsiElementPointer(currentElement);
        this.otherElement = psiPointerManager.createSmartPsiElementPointer(otherElement);
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
            assert otherElement != null;
            CodeSmellLogger.error("Invalid element type for DataClumpRefactoring: " + otherElement.getClass(), new IllegalArgumentException());
            title = "Error refactor data clump";
        }
        return title;
    }

    /**
     * This method is called when the user selects the refactoring from the context menu.
     * It opens a dialog to select the properties that should be extracted.
     * If the user selects to create a new class, a new class is created and the properties are extracted.
     * If the user selects an existing class, the properties are extracted and added to the existing class.
     *
     * @param project the current project
     * @param problemDescriptor the problem descriptor
     */
    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor problemDescriptor) {

        // show dialog to select properties
        DataClumpDialog dialog = new DataClumpDialog(this, matchingProperties, Objects.requireNonNull(currentElement.getElement()), Objects.requireNonNull(otherElement.getElement()));

        if (!dialog.showAndGet()) return;

        CodeSmellLogger.info("Refactoring DataClump between " + currentElement + " and " + otherElement);

        List<Property> selectedProperties = dialog.getProperties();

        PsiElement currentElement = this.currentElement.getElement();
        PsiElement otherElement = this.otherElement.getElement();

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

        // get optional properties
        Set<Property> optional = getOptionalProperties(selectedProperties);

        // create or use existing class
        if (dialog.shouldCreateNewClass()) {
            String className = dialog.getClassName();
            PsiDirectory targetDirectory = dialog.getDirectory();

            CodeSmellLogger.info("Creating new class with name " + className + " in " + targetDirectory);

            extractedClass = extractClass(targetDirectory, className, selectedProperties, optional);
            Index.addClass(extractedClass);
        } else {
            extractedClass = dialog.getSelectedClass();
            CodeSmellLogger.info("Using existing class " + extractedClass.getQualifiedName());


            // save the original parameters -> needed for refactoring the function calls
            List<Property> originalParameters = new ArrayList<>();
            if (extractedClass.getConstructor() != null) {
                originalParameters = getParametersAsPropertyList((TypeScriptFunction) extractedClass.getConstructor());
            }

            extractedClass = PsiUtil.makeClassExported(extractedClass);
            Index.updateClass(extractedClass); // since the class was replaced the index must be updated

            adjustConstructor(extractedClass, selectedProperties, optional);
            addGetterAndSetter(extractedClass, selectedProperties, optional);

            // refactor the function calls of the extracted class since the constructor was adjusted
            TypeScriptFunction constructor = (TypeScriptFunction) extractedClass.getConstructor();
            assert constructor != null;
            HashMap<Classfield, Parameter> definedClassfields = new HashMap<>();
            HashMap<Classfield, String> defaultValues = new HashMap<>();
            getDefaultValues(extractedClass, selectedProperties, defaultValues);
            getClassfieldDefiningParameter(constructor, new HashMap<>(), definedClassfields );
            refactorFunctionCalls((TypeScriptFunction) extractedClass.getConstructor(), originalParameters, extractedClass, definedClassfields, defaultValues);
        }

        // refactor the elements that contain the data clump
        refactorElement(currentElement, extractedClass, selectedProperties, currentDefinedClassFields, currentDefaultValues);
        refactorElement(otherElement, extractedClass, selectedProperties, otherDefinedClassFields, otherDefaultValues);

    }

    /**
     * Creates a new class with the given name in the given directory and extracts the given properties as fields.
     *
     * @param dir the directory in which the class should be created
     * @param className the name of the class
     * @param fields the properties that should be extracted as fields
     * @param optionalFields the properties that are optional
     * @return the created class
     */
    private TypeScriptClass extractClass(PsiDirectory dir, String className, List<Property> fields, Set<Property> optionalFields) {
        CodeSmellLogger.info("Extracting class...");

        List<Property> constructorFields = new ArrayList<>(fields);
        List<Property> abstractFields = new ArrayList<>();
        List<Property> declaredFields = new ArrayList<>();

        // if the option to include modifiers in the extracted class is enabled
        // the abstract and declared fields are separated from the constructor fields
        if (Objects.requireNonNull(DataClumpSettings.getInstance().getState()).includeModifiersInExtractedClass) {

            abstractFields = fields.stream().filter(property -> property instanceof Classfield && ((Classfield) property).getModifier().contains("abstract")).toList();
            declaredFields = fields.stream().filter(property -> property instanceof Classfield && ((Classfield) property).getModifier().contains("declare")).toList();

            constructorFields.removeAll(abstractFields);
            constructorFields.removeAll(declaredFields);

            abstractFields.forEach(optionalFields::remove);
            declaredFields.forEach(optionalFields::remove);
        }

        // create class
        TypeScriptClass psiClass = PsiUtil.createClass(dir, className, !abstractFields.isEmpty(), true);

        // add a constructor that defines the fields that are not abstract or declared
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
            PsiFile file = PsiFileFactory.getInstance(dir.getProject()).createFileFromText(className + ".ts", TypeScriptFileType.INSTANCE, "");
            file.add(psiClass);
            dir.add(file);
        });

        // get the virtual file of the created class and find the class in the file
        // this is necessary to get the class with the correct references
        VirtualFile virtualFile = dir.getVirtualFile().findChild(className + ".ts");
        assert virtualFile != null;
        PsiFile file = PsiManager.getInstance(dir.getProject()).findFile(virtualFile);
        TypeScriptClass extractedClass = PsiTreeUtil.findChildOfType(file, TypeScriptClass.class);


        CodeSmellLogger.info("Class extracted.");
        return extractedClass;
    }

    /**
     * Adjusts the constructor of the given class to include the extracted properties
     * and make sure all Parameter that might not have a value are optional.
     *
     * @param psiClass the class to adjust the constructor of
     * @param matchingProperties the properties that are extracted
     * @param optionalProperties the properties that are optional
     */
    private void adjustConstructor(TypeScriptClass psiClass , List<Property> matchingProperties, Set<Property> optionalProperties) {

        CodeSmellLogger.info("Adjusting constructor of " + psiClass.getQualifiedName() + "...");

        TypeScriptFunction constructor = (TypeScriptFunction) psiClass.getConstructor();

        if (constructor == null) {
            // create new constructor
            TypeScriptFunction newConstructor = PsiUtil.createConstructor(psiClass, matchingProperties, optionalProperties, new ArrayList<>(), null, Objects.requireNonNull(DataClumpSettings.getInstance().getState()).includeModifiersInDetection);
            PsiUtil.addFunctionToClass(psiClass, newConstructor);
        } else {

            // get the classfields that are defined in the constructor
            HashMap<Classfield, Parameter> definedClassfields = new HashMap<>();
            HashMap<Parameter, Classfield> definingParameters = new HashMap<>();
            getClassfieldDefiningParameter(constructor, definingParameters, definedClassfields);

            // the fields and parameters that are defined in the constructor
            List<Property> constructorFields = new ArrayList<>();
            List<Property> constructorParameter = new ArrayList<>();

            // iterate over all parameters of the constructor
            for (JSParameterListElement psiParameter : Objects.requireNonNull(constructor.getParameterList()).getParameters()) {

                // if the parameter is a classfield -> add it to the constructor fields
                if (PsiUtil.isParameterField((TypeScriptParameter) psiParameter)) {
                    Classfield field = new Classfield((TypeScriptParameter) psiParameter);
                    constructorFields.add(field);
                    // if the field is not a matching property
                    // -> add it to the optional properties since the elements that contain the data clump do not have values for this field
                    if (!matchingProperties.contains(field)) {
                        optionalProperties.add(field);
                    }
                } else {
                    // if the parameter is not a classfield -> add it to the constructor parameters
                    Parameter parameter = new Parameter((TypeScriptParameter) psiParameter);
                    constructorParameter.add(parameter);
                    if (definingParameters.get(parameter) == null) {
                        // if the parameter is not defining a classfield -> add it to the optional properties
                        // since the elements that contain the data clump do not have values for this parameter
                        optionalProperties.add(parameter);
                    } else if (!matchingProperties.contains(definingParameters.get(parameter))) {
                        // if the parameter is defining a classfield that is not a matching property
                        // -> add it to the optional properties
                        // since the elements that contain the data clump do not have values for this field
                        optionalProperties.add(definingParameters.get(parameter));
                    }
                }
            }

            // get the properties that are not defined in the constructor
            for (Property property : matchingProperties) {
                // skip abstract fields or declared fields
                if (property instanceof Classfield && (((Classfield) property).getModifier().contains("abstract") ||
                        ((Classfield) property).getModifier().contains("declare") )) continue;

                if (!definedClassfields.containsKey(property)) {
                    constructorFields.add(property);
                    optionalProperties.add(property);
                }
            }

            List<Property> allProperties = new ArrayList<>(constructorFields);
            allProperties.addAll(constructorParameter);

            // make sure no optional properties are there that are not defined in the constructor
            optionalProperties.retainAll(allProperties);

            // make all optional defined fields optional
            for (Property optionalProperty : optionalProperties) {
                PsiElement field = PsiUtil.getPsiField(psiClass, optionalProperty.getName());
                if (field instanceof TypeScriptField) {
                    PsiUtil.makeFieldOptional((TypeScriptField) field);
                }
            }

            // remove the constructor and create a new one with the adjusted parameters
            JSBlockStatement body = constructor.getBlock();

            WriteCommandAction.runWriteCommandAction(psiClass.getProject(), constructor::delete);
            TypeScriptFunction newConstructor = PsiUtil.createConstructor(psiClass, constructorFields, optionalProperties, constructorParameter, body, Objects.requireNonNull(DataClumpSettings.getInstance().getState()).includeModifiersInDetection);
            PsiUtil.addFunctionToClass(psiClass, newConstructor);

        }
    }

    /**
     * Gets for the given function the parameters mapped to the classfields they define in the function as well as
     * the classfields mapped to the parameters that define them in the function.
     *
     * @param function the function to get the defining parameters and defined classfields for
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
                if (PsiUtil.isAssignedNewValue((TypeScriptParameter) psiParameter)) continue;
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
     * @param psiClass the class to get the default values for
     * @param properties the properties to get the default values for
     * @param defaultValues the map to store the default values mapped to the properties
     */
    private void getDefaultValues(TypeScriptClass psiClass, List<Property> properties, HashMap<Classfield, String> defaultValues) {

        defaultValues.clear();

        List<Classfield> classfields = Index.getClassesToClassFields().get(psiClass);

        for (Classfield classfield : classfields)  {
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
     * Gets all the given properties that need to be optional in the extracted class.
     * These properties must be optional in the extracted class since the elements that contain the data clump do not have values for them.
     *
     * @param properties the properties to get the optional properties for
     * @return the optional properties
     */
    private Set<Property> getOptionalProperties(List<Property> properties) {
        PsiElement currentElement = this.currentElement.getElement();
        PsiElement otherElement = this.otherElement.getElement();

        Set<Property> optionalProperties = new HashSet<>();
        if (currentElement instanceof TypeScriptClass) {
            optionalProperties.addAll(getOptionalProperties(properties, (TypeScriptClass) currentElement, currentDefinedClassFields, currentDefaultValues, currentDefiningParameters));
        }
        if (otherElement instanceof TypeScriptClass) {
            optionalProperties.addAll(getOptionalProperties(properties, (TypeScriptClass) otherElement, otherDefinedClassFields, otherDefaultValues, otherDefiningParameters));
        }
        return optionalProperties;
    }

    /**
     * Checking if a property is optional for a class or not.
     * A property is optional if it is not defined in the constructor of the class or has a default value.
     * All properties that get assigned an optional property are also optional.
     *
     * @param properties the properties to check
     * @param psiClass the class to check the properties for
     * @param definedClassfields the classfields that are defined in the constructor (by the parameters) of the class
     * @param defaultValues the default values of the classfields of the class
     * @param definingParameters the parameters that define the classfields in the constructor
     * @return the optional properties
     */
    private List<Property> getOptionalProperties(List<Property> properties, TypeScriptClass psiClass, HashMap<Classfield, Parameter> definedClassfields, HashMap<Classfield, String> defaultValues, HashMap<Parameter, Classfield> definingParameters) {

        // get the properties that are not defined in the constructor or have a default value
        List<Property> optionalProperties = new ArrayList<>();
        for (Property property : properties) {
            if (!definedClassfields.containsKey(property) && !defaultValues.containsKey(property)) {
                optionalProperties.add(property);
            }
        }

        // check if the optional properties are assigned to optional properties
        boolean changed;
        do {
            changed = false;
            for (Property property : properties) {
                // is the property already an optional property
                if (optionalProperties.contains(property)) continue;
                // is the property assigned to an optional property
                PsiElement psiField = PsiUtil.getPsiField(psiClass, property.getName());
                if (psiField == null) continue;
                for (PsiReference reference : ReferencesSearch.search(psiField)) {
                    JSAssignmentExpression assignment = PsiTreeUtil.getParentOfType(reference.getElement(), JSAssignmentExpression.class);
                    if (assignment != null && Objects.requireNonNull(assignment.getLOperand()).getFirstChild() == reference
                            && assignment.getROperand() instanceof JSReferenceExpression referenceExpression) {
                        Property assignedProperty = PsiUtil.resolveProperty(referenceExpression);
                        if (assignedProperty instanceof Classfield && optionalProperties.contains(assignedProperty)) {
                            optionalProperties.add(property);
                            changed = true;
                        } else if (assignedProperty instanceof Parameter
                                && optionalProperties.contains(definingParameters.get(assignedProperty))) {
                            optionalProperties.add(property);
                            changed = true;
                        }
                    }

                }
            }
        } while (changed);

        return optionalProperties;
    }

    /**
     * Adds getter and setter to the extracted class for the given properties if they do not already exist.
     *
     * @param psiClass the class to add the getter and setter to
     * @param properties the properties to add the getter and setter for
     * @param optional the optional properties
     */
    private void addGetterAndSetter(TypeScriptClass psiClass, List<Property> properties, Set<Property> optional) {
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

                TypeScriptFunction getter = PsiUtil.createGetter(psiClass, classfield, optional.contains(property));
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
                TypeScriptFunction setter = PsiUtil.createSetter(psiClass, classfield, optional.contains(property));
                PsiUtil.addFunctionToClass(psiClass, setter);
            }
        }
    }

    /**
     * Gets the String representation of the default initialization of the extracted class
     * that uses the default values of the classfields.
     *
     * @param psiClass the class that contains the data clump
     * @param extractedClass the class that contains the extracted properties
     * @param defaultValues the default values of the classfields of the class
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
                init.append("undefined, ");
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
     * @param element the element that contains the data clump to be refactored
     * @param extractedClass the class that contains the extracted properties
     * @param properties the properties that are extracted
     * @param definedClassfields the classfields that are defined in the constructor (by the parameters) of the element
     * @param defaultValues the default values of the classfields of the element
     */
    private void refactorElement(PsiElement element, TypeScriptClass extractedClass, List<Property> properties, HashMap<Classfield, Parameter> definedClassfields, HashMap<Classfield, String> defaultValues) {

        // refactor the element depending on its type
        // if the element is the extacted class, the refactoring is already done
        if (element instanceof TypeScriptClass psiClass && !Objects.equals(psiClass.getQualifiedName(), extractedClass.getQualifiedName())) {
            addImport(element, extractedClass);
            refactorClass(psiClass, extractedClass, properties, definedClassfields, defaultValues);
            Index.updateClass(psiClass);
        } else if (element instanceof TypeScriptFunction function) {
            addImport(element, extractedClass);
            refactorFunction(function, extractedClass, properties);
            Index.updateFunction(function);
        }
    }

    /**
     * Adds an import statement for the extracted class to the element if it is not already imported.
     * @param element the element to which the import statement should be added
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
        });

    }

    /**
     * Refactors the given class that contains the data clump to use the extracted class instead of the extracted properties.
     * The extracted fields are replaced with a field of the extracted class.
     * The constructor is updated to use the extracted class as a parameter instead of the extracted properties.
     * The constructor calls are updated to use the extracted class instead of the extracted properties.
     * The field references are updated to use the extracted class instead of the extracted properties.
     *
     * @param psiClass the class that contains the data clump
     * @param extractedClass the class that contains the extracted properties
     * @param properties the properties that are extracted
     * @param definedClassfields the classfields that are defined in the constructor (by the parameters) of the class
     * @param defaultValues the default values of the classfields of the class
     */
    private void refactorClass(TypeScriptClass psiClass, TypeScriptClass extractedClass, List<Property> properties, HashMap<Classfield, Parameter> definedClassfields, HashMap<Classfield, String> defaultValues) {
        CodeSmellLogger.info("Refactoring class " + psiClass.getQualifiedName() + "...");

        String fieldName =  "my_" + Objects.requireNonNull(extractedClass.getName()).toLowerCase(); // this should be a unique name

        // add the extracted class as a field
        ES6FieldStatementImpl newFieldStatement = PsiUtil.createJSFieldStatement(
                psiClass, fieldName, extractedClass.getJSType().getTypeText(), List.of("public"), false, getDefaultInit(psiClass, extractedClass, defaultValues)
        );
        PsiUtil.addFieldToClass(psiClass, newFieldStatement);

        // update all field references by replacing the extracted properties with getter and setter calls on the extracted class
        updateFieldReferences(psiClass, properties, fieldName);

        // adjust the constructor to use the extracted class as a parameter instead of the extracted properties
        // also adjust all constructor calls to use the extracted class instead of the extracted properties
        updateConstructor(psiClass, properties, extractedClass, fieldName, definedClassfields, defaultValues);

        CodeSmellLogger.info("Class refactored.");
    }

    /**
     * Updates the field references of the given class by replacing the extracted properties
     * with getter and setter calls on the extracted class.
     * @param psiClass the class that contains the data clump
     * @param properties the properties that are extracted
     * @param fieldName the name of the field that contains the extracted class
     */
    private void updateFieldReferences(TypeScriptClass psiClass, List<Property> properties, String fieldName) {

        List<Classfield> classfields = Index.getClassesToClassFields().get(psiClass);

        // iterate over all classfields of the class
        for (Classfield classfield : classfields) {
            // if the classfield is one of the extracted properties -> refactor the references
            if (properties.contains(classfield)) {

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
                        replaceReferenceWithGetter(psiClass, reference, fieldName, classfield.getName());
                    }
                }

                // remove the classfield from the class if it is a field
                // if it is a constructor parameter it will be removed later
                if (psiField instanceof TypeScriptField) {
                    WriteCommandAction.runWriteCommandAction(psiClass.getProject(), psiField::delete);
                }
            }
        }
    }

    /**
     * Updates the constructor of the given class to use the extracted class as a parameter instead of the extracted properties.
     * The constructor calls are updated to use the extracted class instead of the extracted properties.
     *
     * @param psiClass the class that contains the data clump
     * @param properties the properties that are extracted
     * @param extractedClass the class that contains the extracted properties
     * @param fieldName the name of the field that contains the extracted class
     * @param definedClassfields the classfields that are defined in the constructor (by the parameters) of the class
     * @param defaultValues the default values of the classfields of the class
     */
    private void updateConstructor(TypeScriptClass psiClass, List<Property> properties, TypeScriptClass extractedClass, String fieldName, HashMap<Classfield, Parameter> definedClassfields, HashMap<Classfield, String> defaultValues) {

        TypeScriptFunction constructor = (TypeScriptFunction) psiClass.getConstructor();
        if (constructor == null) return;

        // save the original parameters -> needed for refactoring the function calls
        List<Property> originalParameters = getParametersAsPropertyList(constructor);

        // remove the extracted properties from the constructor and replace the references with getter calls on the extracted class
        refactorConstructorParameter(constructor, properties, definedClassfields, fieldName);

        // add the extracted class as a parameter to the constructor
        addClassAsParameter(constructor, extractedClass, fieldName);

        // refactor the function calls of the constructor since the constructor was adjusted
        refactorFunctionCalls(constructor, originalParameters, extractedClass, definedClassfields, defaultValues);


        // add initialization of the field
        JSStatement initialization = JSPsiElementFactory.createJSStatement(
                "this." + fieldName + " = " + fieldName + ";", psiClass
        );
        WriteCommandAction.runWriteCommandAction(psiClass.getProject(), () -> {
            Objects.requireNonNull(constructor.getBlock()).addAfter(initialization, constructor.getBlock().getFirstChild());
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
            WriteCommandAction.runWriteCommandAction(psiClass.getProject(), assignment.getParent()::delete);
        }
    }

    /**
     * Deletes all parameters from the constructor that are part of the extracted properties
     * or define a field that is part of the extracted properties. The references to the parameters are replaced
     * with getter calls on the extracted class.
     *
     * @param constructor the constructor to refactor
     * @param matchingProperties the properties that are extracted
     * @param definedClassfields the classfields that are defined in the constructor (by the parameters) of the class
     * @param newParameterName the name of the new parameter object
     */
    private void refactorConstructorParameter(TypeScriptFunction constructor, List<Property> matchingProperties, HashMap<Classfield, Parameter> definedClassfields, String newParameterName) {
        // iterate over all properties that are defined in the constructor
        for (Classfield property : definedClassfields.keySet()) {
            if (!matchingProperties.contains(property)) continue; // only refactor the extracted properties

            TypeScriptParameter parameter = PsiUtil.getPsiParameter(constructor, definedClassfields.get(property));
            assert parameter != null;

            // replace all references to the parameter with getter calls on the extracted class
            for (PsiReference reference : ReferencesSearch.search(parameter)) {
                replaceReferenceWithGetter(PsiTreeUtil.getParentOfType(constructor, TypeScriptClass.class), reference, newParameterName, property.getName());
            }

            // remove the parameter from the constructor
            WriteCommandAction.runWriteCommandAction(constructor.getProject(), parameter::delete);
        }
    }

    /**
     * Adds the extracted class as a parameter to the given function.
     * @param function the function to which the parameter should be added
     * @param extractedClass the class that should be added as a parameter
     * @param newParameterName the name of the new parameter
     */
    private void addClassAsParameter(TypeScriptFunction function, TypeScriptClass extractedClass, String newParameterName) {
        JSParameterList parameterList = function.getParameterList();
        assert parameterList != null;
        TypeScriptParameter newParameter = PsiUtil.createTypeScriptParameter(function, newParameterName, extractedClass.getJSType());
        PsiUtil.addParameterToParameterList(newParameter, parameterList);
    }

    /**
     * Refactors the function calls to match the new function signature after the extracted class was added as a parameter.
     *
     * @param function the function to refactor
     * @param originalParameters the original parameters of the function before the refactoring
     *                           in the form of properties in the right order.
     *                           This is the way their values will appear in the function call
     * @param extractedClass the class that was extracted
     * @param definedClassfields the classfields that are defined in the constructor (by the parameters) of the class.
     *                           Only needed if the function is a constructor.
     * @param defaultValues the default values of the classfields of the class.
     *                      Only needed if the function is a constructor.
     */
    private void refactorFunctionCalls(TypeScriptFunction function, List<Property> originalParameters, TypeScriptClass extractedClass, @Nullable HashMap<Classfield, Parameter> definedClassfields, @Nullable HashMap<Classfield, String> defaultValues) {

        List<Property> extractedParameters = getParametersAsPropertyList((TypeScriptFunction) Objects.requireNonNull(extractedClass.getConstructor()));

        for (PsiReference functionCall : ReferencesSearch.search(function)) {

            JSArgumentList argumentList = PsiTreeUtil.getNextSiblingOfType(functionCall.getElement(), JSArgumentList.class);
            assert argumentList != null;
            JSExpression[] originalArguments = argumentList.getArguments();

            // create the new argument list
            StringBuilder updatedArguments = new StringBuilder("(");
            for (JSParameterListElement parameter : function.getParameters()) {

                // if the next parameter expects the extracted class
                // -> replace the argument with a constructor call for the extracted class
                if (Objects.equals(parameter.getJSType(), extractedClass.getJSType())) {

                    updatedArguments.append("new ").append(extractedClass.getName()).append("(");
                    for (Property property : extractedParameters) {

                        if (!function.isConstructor()) { // if the function is not a constructor -> all properties where defined
                            int index = originalParameters.indexOf(property);
                            if (index == -1) {
                                CodeSmellLogger.error("Property " + property.getName() + " not found in original parameters", new IndexOutOfBoundsException());
                                continue;
                            }
                            updatedArguments.append(originalArguments[index].getText()).append(", ");
                        } else {
                            assert definedClassfields != null;
                            assert defaultValues != null;
                            if (definedClassfields.containsKey(property)) { // if the property is defined in the constructor -> use the corresponding parameter
                                int index = originalParameters.indexOf(definedClassfields.get(property));
                                updatedArguments.append(originalArguments[index].getText()).append(", ");
                            }
                            else if (defaultValues.containsKey(property)) { // if the property has a default value -> use the default value
                                updatedArguments.append(defaultValues.get(property)).append(", ");
                            } else { // if the property is not defined in the constructor and has no default value -> use undefined
                                updatedArguments.append("undefined, ");
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

    /**
     * Gets the parameters of the given function as a list of properties.
     * The order of the properties is the same as the order of the parameters.
     *
     * @param function the function to get the parameters from
     * @return the parameters of the function as a list of properties
     */
    private List<Property> getParametersAsPropertyList(TypeScriptFunction function) {
        List<Property> parameters = new ArrayList<>();
        for (JSParameterListElement parameter : function.getParameters()) {
            Property property = new Parameter((TypeScriptParameter) parameter);
            parameters.add(property);
        }
        return parameters;
    }

    /**
     * Refactors the given class that contains the data clump to use the extracted class instead of the extracted properties.
     * The extracted fields are replaced with a field of the extracted class.
     * The function calls are updated to use the extracted class instead of the extracted properties.
     *
     * @param psiFunction the function that contains the data clump
     * @param extractedClass the class that contains the extracted properties
     * @param properties the properties that are extracted
     */
    private void refactorFunction(TypeScriptFunction psiFunction, TypeScriptClass extractedClass, List<Property> properties) {
        CodeSmellLogger.info("Refactoring function " + psiFunction.getQualifiedName() + "...");

        String newParameterName = "my_" + Objects.requireNonNull(extractedClass.getName()).toLowerCase(); // this should be a unique name

        // save the original parameters -> needed for refactoring the function calls
        List<Property> originalParameters = getParametersAsPropertyList(psiFunction);

        // remove the extracted properties from the function and replace the references with getter calls on the extracted class
        refactorFunctionParameter(psiFunction, properties, newParameterName);

        // Add the extracted class as a new parameter
        addClassAsParameter(psiFunction, extractedClass, newParameterName);

        // refactor the function calls of the function since the function was adjusted
        refactorFunctionCalls(psiFunction, originalParameters, extractedClass, new HashMap<>(), new HashMap<>());

        CodeSmellLogger.info("Function refactored.");
    }

    /**
     * Deletes all parameters from the function that are part of the extracted properties
     * and replaces the references to the parameters with getter calls on the extracted class.
     *
     * @param function the function to refactor
     * @param properties the properties that are extracted
     * @param newParameterName the name of the new parameter object
     */
    private void refactorFunctionParameter(TypeScriptFunction function, List<Property> properties, String newParameterName) {

        for (JSParameterListElement parameter : function.getParameters()) {
            Parameter currentParameter = new Parameter((TypeScriptParameter) parameter);
            if (properties.contains(currentParameter)) { // only refactor the extracted properties

                // replace all references to the parameter with getter calls on the extracted class
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

    /**
     * Replaces the given assignment with a setter call on the extracted class.
     *
     * @param psiClass the class that contains the data clump
     * @param assignment the assignment to replace
     * @param fieldName the name of the field that contains the extracted class
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
        });
    }

    /**
     * Replaces the given reference with a getter call on the extracted class.
     *
     * @param psiClass the class that contains the data clump
     * @param reference the reference to replace
     * @param fieldName the name of the field that contains the extracted class
     * @param propertyName the name of the property that is referenced
     */
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

    /**
     * Gets the classes that can be used for the data clump refactoring.
     *
     * @param properties the properties that are part of the data clump
     * @return the classes that can be used for the data clump refactoring
     */
    public Set<TypeScriptClass> getUsableClasses(List<Property> properties) {
        Set<TypeScriptClass> matchingClasses = new HashSet<>();

        // Validierung: Keine Properties angegeben
        if (properties.isEmpty()) CodeSmellLogger.error("No properties specified for DataClumpRefactoring", new IllegalArgumentException());


        // Validierung: Erste Property nicht im Index
        Property firstProperty = properties.get(0);

        if (!Index.getPropertiesToClasses().containsKey(firstProperty)) return matchingClasses;

        // Potenziell passende Klassen finden
        List<TypeScriptClass> potentialClasses = Index.getPropertiesToClasses().get(firstProperty);
        for (TypeScriptClass psiClass : potentialClasses) {
            if (!psiClass.isValid() || psiClass.getName() == null) continue;
            if (PsiUtil.hasAll(psiClass, properties)) matchingClasses.add(psiClass);
        }

        // Kopie des Sets erstellen, da wir es modifizieren
        Set<TypeScriptClass> filteredClasses = new HashSet<>(matchingClasses);

        // Überprüfung der Zuweisungen für jede Klasse
        for (TypeScriptClass psiClass : matchingClasses) {
            for (Property property : properties) {
                PsiElement psiField = PsiUtil.getPsiField(psiClass, property.getName());
                assert psiField != null;

                TypeScriptFunction constructor = (TypeScriptFunction) psiClass.getConstructor();
                if (constructor == null) break;

                // Referenzen des Properties überprüfen
                for (PsiReference reference : ReferencesSearch.search(psiField)) {

                    // Sicherstellen, dass die Referenz im Konstruktor liegt
                    if (PsiTreeUtil.getParentOfType(reference.getElement(), TypeScriptFunction.class) != constructor) break;

                    // Überprüfen, ob die Referenz in einer Zuweisung verwendet wird
                    JSAssignmentExpression assignment = PsiTreeUtil.getParentOfType(reference.getElement(), JSAssignmentExpression.class);
                    if (assignment == null || Objects.requireNonNull(assignment.getLOperand()).getFirstChild() != reference) break;


                    // Überprüfung des rechten Operanden der Zuweisung
                    if (!(assignment.getROperand() instanceof JSReferenceExpression)) {
                        filteredClasses.remove(psiClass);
                        break;
                    }

                    // Aufgelöste Property überprüfen
                    Property assignedProperty = PsiUtil.resolveProperty((JSReferenceExpression) assignment.getROperand());
                    if (assignedProperty == null) {
                        filteredClasses.remove(psiClass);
                        break;
                    }

                    // Klassenfeld-Parameter-Mapping überprüfen
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
     * Returns whether the quick fix should start in a write action.
     * @return false since the quick fix uses a dialog to gather user input
     */
    @Override
    public boolean startInWriteAction() {
        // This quick fix uses a dialog to gather user input, so it should not start in a write action
        return false;
    }
}
