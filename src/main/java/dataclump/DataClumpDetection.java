package dataclump;

import com.intellij.lang.javascript.psi.ecma6.*;
import com.intellij.lang.javascript.psi.ecmal4.JSClass;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiElement;
import evoluation.DiagnosticTool;
import util.*;
import com.intellij.codeInspection.*;
import com.intellij.lang.javascript.psi.JSElementVisitor;
import com.intellij.lang.javascript.psi.JSParameterList;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import Settings.DataClumpSettings;

import java.util.*;

/**
 * The Data Clump Detection Inspection.
 * A data clump is a group of fields or parameters that are always used together. This inspection
 * detects data clumps in classes and functions and marks them as problems. The user can then apply
 * the Data Clump Refactoring to refactor the data clumps.
 */
public class DataClumpDetection extends LocalInspectionTool {


    /**
     * Build the visitor for the inspection
     *
     * @param holder     the problems holder
     * @param isOnTheFly if the inspection is on the fly
     * @return the visitor
     */
    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new JSElementVisitor() {

            /**
             * Visit the parameter list of a function and detect data clumps. Also update the index.
             * This function is called automatically whenever a parameter list is visited (edited/open).
             *
             * @param parameterList the parameter list
             */
            @Override
            public void visitJSParameterList(@NotNull JSParameterList parameterList) {
                CodeSmellLogger.info("Visiting Parameter List: " + parameterList.getText());
                CodeSmellLogger.info("Visiting Parameter List: valid " + parameterList.isValid());

                TypeScriptFunction psiFunction = PsiUtil.runReadActionWithResult(() -> PsiTreeUtil.getParentOfType(parameterList, TypeScriptFunction.class));
                CodeSmellLogger.info("Visiting Parameter List: Function " + psiFunction);
                CodeSmellLogger.info("Visiting Parameter List: Function valid " + psiFunction.isValid());
                assert psiFunction != null;
                if (!PsiUtil.runReadActionWithResult(psiFunction::isValid))
                    CodeSmellLogger.error("Invalid Function: " + psiFunction, new Exception());

                // Skip constructors
                if (PsiUtil.runReadActionWithResult(psiFunction::isConstructor)) return;
                // Update the index
                Index.updateFunction(psiFunction);
                // Detect data clumps if the number of parameters is greater than the required minimum
                List<Parameter> parameters = Index.getFunctionsToParameters().get(psiFunction);
                if (parameters != null && parameters.size() >= Objects.requireNonNull(DataClumpSettings.getInstance().getState()).minNumberOfProperties) {
                    detectDataClump(psiFunction, holder, false);
                }
                super.visitJSParameterList(parameterList);
            }

            /**
             * Visit the class and detect data clumps. Also update the index.
             * This function is called automatically whenever a class is visited (edited/open).
             *
             * @param TypeScriptClass the class
             */
            @Override
            public void visitTypeScriptClass(@NotNull TypeScriptClass TypeScriptClass) {
                CodeSmellLogger.info("Visiting Class: " + TypeScriptClass.getName());
                CodeSmellLogger.info("Visiting Class: valid " + TypeScriptClass.isValid());

                // Update the index
                Index.updateClass(TypeScriptClass);
                // Detect data clumps if the number of properties is greater than the required minimum
                List<Classfield> classfields = Index.getClassesToClassFields().get(TypeScriptClass);
                if (classfields != null && classfields.size() >= Objects.requireNonNull(DataClumpSettings.getInstance().getState()).minNumberOfProperties) {
                    detectDataClump(TypeScriptClass, holder, false);
                }
                super.visitTypeScriptClass(TypeScriptClass);
            }

            /**
             * Visit the interface and detect data clumps. Also update the index.
             * This function is called automatically whenever an interface is visited (edited/open).
             *
             * @param typeScriptInterface the interface
             */
            @Override
            public void visitTypeScriptInterface(@NotNull TypeScriptInterface typeScriptInterface) {
                CodeSmellLogger.info("Visiting Interface: " + typeScriptInterface.getName());
                CodeSmellLogger.info("Visiting Interface: valid " + typeScriptInterface.isValid());

                Index.updateClass(typeScriptInterface);

                List<Classfield> classfields = Index.getClassesToClassFields().get(typeScriptInterface);
                if (classfields != null && classfields.size() >= Objects.requireNonNull(DataClumpSettings.getInstance().getState()).minNumberOfProperties) {
                    detectDataClump(typeScriptInterface, holder, false);
                }
                super.visitTypeScriptInterface(typeScriptInterface);
            }
        };
    }

    /**
     * Detect data clumps in the current element
     *
     * @param currentElement the current element
     * @param holder         the problems holder
     * @param report         if the data clumps should be reported to the full analysis
     */
    public void detectDataClump(PsiElement currentElement, ProblemsHolder holder, boolean report) {

        long start = 0;
        if (DiagnosticTool.DIAGNOSTIC_MODE) {
            start = System.nanoTime();
        }

        HashMap<PsiElement, List<Property>> potentialDataClumps = new HashMap<>();

        if (currentElement instanceof JSClass currentClass) {
            potentialDataClumps = calculatePotentialDataClumpsForClass(currentClass);
        } else if (currentElement instanceof TypeScriptFunction currentFunction) {
            potentialDataClumps = calculatePotentialDataClumpsForFunction(currentFunction);
        }

        processPotentialDataClumps(potentialDataClumps, holder, currentElement, start, report);
    }

    /**
     * Process the potential data clumps and mark them as data clumps if they meet the required conditions
     *
     * @param potentialDataClumps the potential data clumps
     * @param holder              the problems holder
     * @param currentElement      the current element
     * @param start               the start time of the detection
     * @param report              if the data clumps should be reported to the full analysis
     */
    private void processPotentialDataClumps(HashMap<PsiElement, List<Property>> potentialDataClumps, ProblemsHolder holder, PsiElement currentElement, long start, boolean report) {

        List<Property> currentElementsProperties = new ArrayList<>();

        if (currentElement instanceof JSClass currentClass) {
            currentElementsProperties = new ArrayList<>(Index.getClassesToClassFields().get(currentClass));
        } else if (currentElement instanceof TypeScriptFunction currentFunction) {
            currentElementsProperties = new ArrayList<>(Index.getFunctionsToParameters().get(currentFunction));
        }

        for (PsiElement otherElement : potentialDataClumps.keySet()) {
            List<Property> matchingProperties = potentialDataClumps.get(otherElement);
            // Check if the number of matching properties is greater than the required minimum
            if (matchingProperties.size() >= Objects.requireNonNull(DataClumpSettings.getInstance().getState()).minNumberOfProperties) {

                // report the data clump to the full analysis if required
                if (report) {
                    FullAnalysis.report(currentElement, otherElement, matchingProperties);
                    return;
                }

                // mark the data clump as a problem
                for (Property property : currentElementsProperties) {
                    if (matchingProperties.contains(property)) {

                        PsiElement dataClumpElement;
                        if (currentElement instanceof JSClass currentClass) {
                            dataClumpElement = PsiUtil.getPsiField(currentClass, property.getName());
                        } else {
                            TypeScriptFunction currentFunction = (TypeScriptFunction) currentElement;
                            dataClumpElement = PsiUtil.getPsiParameter(currentFunction, property.getName());
                        }

                        assert dataClumpElement != null;

                        String currentElementType = currentElement instanceof JSClass ? "class" : "function";
                        String otherElementType = otherElement instanceof JSClass ? "class" : "function";

                        String description = "Data Clump between " + currentElementType + " " + PsiUtil.getQualifiedName(currentElement) +
                                " and " + otherElementType + " " + PsiUtil.getQualifiedName(otherElement) +
                                ". Matching Properties " + matchingProperties + ".";

                        if (canRefactor(currentElement) && canRefactor(otherElement)) {
                            holder.registerProblem(dataClumpElement,
                                    description,
                                    new DataClumpRefactoring(currentElement, otherElement, new ArrayList<>(matchingProperties)));
                        } else {
                            holder.registerProblem(dataClumpElement, description +
                                    " This data clump can not be refactored automatically."
                            );
                        }

                    }
                }

                if (DiagnosticTool.DIAGNOSTIC_MODE) {
                    long end = System.nanoTime();
                    long time = end - start;
                    DiagnosticTool.addMeasurement(new DiagnosticTool.DetectionMeasurement(Index.getProject(), time, ReportFormat.getDataClumpsTypeContext(currentElement, otherElement, matchingProperties)));
                }
            }
        }

    }

    /**
     * Check if the element can be refactored automatically. If the element is part of an interface or
     * if the function is overridden, it can not be refactored.
     *
     * @param element the element
     * @return true if the element can be refactored, false otherwise
     */
    private boolean canRefactor(PsiElement element) {
        // if the element is part of an interface, it can not be refactored
        // functions that are overridden can not be refactored since the overridden functions would be affected
        if (element instanceof TypeScriptFunction function) {
            if (isOverwritten(function)) return false;
            JSClass containingClass = PsiUtil.runReadActionWithResult(() -> PsiTreeUtil.getParentOfType(element, JSClass.class));
            return !(containingClass instanceof TypeScriptInterface);
        } else return !(element instanceof TypeScriptInterface);
    }

    /**
     * Check if the function is overridden by another function
     *
     * @param function the function
     * @return true if the function is overridden by another function, false otherwise
     */
    private boolean isOverwritten(TypeScriptFunction function) {
        List<JSClass> classesWithFunction = Index.getFunctionNamesToClasses().get(function.getName());
        if (classesWithFunction == null) return false;
        for (JSClass psiClass : classesWithFunction) {
            TypeScriptFunction otherFunction = PsiUtil.runReadActionWithResult(() -> (TypeScriptFunction) psiClass.findFunctionByName(function.getName()));
            if (otherFunction != null && isOverriding(function, otherFunction)) return true;
        }
        return false;
    }

    /**
     * Calculate the potential data clumps for a class
     *
     * @param currentClass the current class
     * @return the potential data clumps for the class. The key is the other class or function
     * and the value is the matching properties with the current class
     */
    private HashMap<PsiElement, List<Property>> calculatePotentialDataClumpsForClass(JSClass currentClass) {

        HashMap<PsiElement, List<Property>> potentialDataClumps = new HashMap<>();

        // iterate over the class fields of the current class
        for (Classfield classfield : Index.getClassesToClassFields().get(currentClass)) {
            if (!checkField(currentClass, classfield)) continue;

            // iterate over the classes that have the same class field
            for (JSClass otherClass : new ArrayList<>(Index.getPropertiesToClasses().get(classfield))) {

                if (!check(currentClass, otherClass)) continue;

                List<Classfield> classfieldList = Index.getClassesToClassFields().get(otherClass);
                if (classfieldList.contains(classfield)) {
                    Classfield otherClassfield = classfieldList.get(classfieldList.indexOf(classfield));
                    // make sure the fields match and are not only equal
                    if (!checkField(otherClass, otherClassfield) || !classfield.matches(otherClassfield)) continue;

                    if (!potentialDataClumps.containsKey(otherClass)) {
                        potentialDataClumps.put(otherClass, new ArrayList<>());
                    }
                    potentialDataClumps.get(otherClass).add(classfield);
                }
            }

            if (!Index.getPropertiesToFunctions().containsKey(classfield)) continue;
            // iterate over the functions that have the same class field
            for (TypeScriptFunction otherFunction : new ArrayList<>(Index.getPropertiesToFunctions().get(classfield))) {
                if (!check(currentClass, otherFunction)) continue;
                if (!potentialDataClumps.containsKey(otherFunction)) {
                    potentialDataClumps.put(otherFunction, new ArrayList<>());
                }
                potentialDataClumps.get(otherFunction).add(classfield);
            }
        }
        return potentialDataClumps;
    }

    /**
     * Calculate the potential data clumps for a function
     *
     * @param currentFunction the current function
     * @return the potential data clumps for the function. The key is the other class or function
     * and the value is the matching properties with the current function
     */
    private HashMap<PsiElement, List<Property>> calculatePotentialDataClumpsForFunction(TypeScriptFunction currentFunction) {

        HashMap<PsiElement, List<Property>> potentialDataClumps = new HashMap<>();

        for (Parameter parameter : Index.getFunctionsToParameters().get(currentFunction)) {
            for (TypeScriptFunction otherFunction : new ArrayList<>(Index.getPropertiesToFunctions().get(parameter))) {
                if (!check(currentFunction, otherFunction)) continue;
                if (!potentialDataClumps.containsKey(otherFunction)) {
                    potentialDataClumps.put(otherFunction, new ArrayList<>());
                }
                potentialDataClumps.get(otherFunction).add(parameter);
            }

            if (!Index.getPropertiesToClasses().containsKey(parameter)) continue;
            for (JSClass otherClass : new ArrayList<>(Index.getPropertiesToClasses().get(parameter))) {
                if (!check(currentFunction, otherClass)) continue;

                Classfield classfield = Index.getMatchingClassFieldForClass(otherClass, parameter);
                assert classfield != null;
                if (!checkField(otherClass, classfield)) continue;

                if (!potentialDataClumps.containsKey(otherClass)) {
                    potentialDataClumps.put(otherClass, new ArrayList<>());
                }
                potentialDataClumps.get(otherClass).add(classfield);
            }
        }
        return potentialDataClumps;
    }

    private boolean checkField(JSClass psiClass, Classfield classfield) {
        if (PsiUtil.runReadActionWithResult(classfield::isStatic)) return false;

        PsiElement psiField = PsiUtil.getPsiField(psiClass, classfield);
        assert psiField != null;

        return !isOverriding(psiClass, psiField);
    }

    /**
     * Check if the two elements should be compared for data clumps
     *
     * @param element1 the first element
     * @param element2 the second element
     * @return true if the elements should be compared, false otherwise
     */
    private boolean check(PsiElement element1, PsiElement element2) {

        if (!PsiUtil.runReadActionWithResult(element1::isValid)) {
            Index.removeElement(element2);
            return false;
        }
        if (!PsiUtil.runReadActionWithResult(element2::isValid)) {
            Index.removeElement(element2);
            return false;
        }
        if (element1 == element2) return false;

        boolean check = true;
        if (element1 instanceof TypeScriptFunction) {
            check = !isOverriding((TypeScriptFunction) element1);
        }
        if (element2 instanceof TypeScriptFunction) {
            check = check && !isOverriding((TypeScriptFunction) element2);
        }

        return check;
    }

    /**
     * Check if the function is overriding another function
     *
     * @param function the function
     * @return true if the function is overriding another function, false otherwise
     */
    private boolean isOverriding(TypeScriptFunction function) {
        JSClass containingClass = PsiUtil.runReadActionWithResult(() -> PsiTreeUtil.getParentOfType(function, JSClass.class));
        if (containingClass == null) return false;

        List<JSClass> hierarchy = resolveHierarchy(containingClass);
        if (hierarchy.isEmpty()) return false;
        for (JSClass psiClass : hierarchy) {
            if (psiClass == containingClass) continue;
            if (PsiUtil.runReadActionWithResult(()->psiClass.findFunctionByName(function.getName()) != null)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if the two functions are overriding each other or the same function in the hierarchy
     *
     * @param function1 the first function
     * @param function2 the second function
     * @return true if the functions are overriding each other, false otherwise
     */
    private boolean isOverriding(TypeScriptFunction function1, TypeScriptFunction function2) {

        if (!Objects.equals(function1.getName(), function2.getName())) return false;
        JSClass containingClass1 = PsiUtil.runReadActionWithResult(() -> PsiTreeUtil.getParentOfType(function1, JSClass.class));
        if (containingClass1 == null) return false;
        JSClass containingClass2 = PsiUtil.runReadActionWithResult(() -> PsiTreeUtil.getParentOfType(function2, JSClass.class));
        if (containingClass2 == null) return false;
        if (containingClass1 == containingClass2) return false;

        List<JSClass> commonClasses = new ArrayList<>(getCommonClassesInHierarchy(containingClass1, containingClass2));
        for (JSClass commonClass : commonClasses) {
            if (PsiUtil.runReadActionWithResult(() -> commonClass.findFunctionByName(function1.getName()) != null)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if the field is overriding another field of an interface
     *
     * @param psiClass   the class
     * @param psiElement the field
     * @return true if the field is overriding another field of an interface, false otherwise
     */
    private boolean isOverriding(JSClass psiClass, PsiElement psiElement) {
        List<JSClass> hierarchy =
                resolveHierarchy(psiClass);
        if (hierarchy.isEmpty()) return false;

        for (JSClass superClass : hierarchy) {
            if (superClass == psiClass) continue;
            if (superClass instanceof TypeScriptInterface && PsiUtil.runReadActionWithResult(() -> superClass.findFieldByName(PsiUtil.getName(psiElement))) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the common classes in the hierarchy of the two classes
     *
     * @param class1 the first class
     * @param class2 the second class
     * @return the common classes in the hierarchy of the two classes
     */
    private List<JSClass> getCommonClassesInHierarchy(JSClass class1, JSClass class2) {
        if (class1 == null || class2 == null) return new ArrayList<>();

        List<JSClass> superClasses1 = resolveHierarchy(class1);
        List<JSClass> superClasses2 = resolveHierarchy(class2);

        superClasses1.retainAll(superClasses2);

        return superClasses1;
    }

    /**
     * Resolve the hierarchy of a class
     *
     * @param tsClass the class
     * @return the hierarchy of the class
     */
    private List<JSClass> resolveHierarchy(JSClass tsClass) {

        List<JSClass> superClasses = new ArrayList<>();

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            superClasses.add(tsClass);
            List<JSClass> extendedClasses = new ArrayList<>();
            List<JSClass> implementedInterfaces = new ArrayList<>();

            ApplicationManager.getApplication().runReadAction(() -> {
                extendedClasses.addAll(Arrays.asList(tsClass.getSuperClasses()));
                implementedInterfaces.addAll(Arrays.asList(tsClass.getImplementedInterfaces()));
            });

            for (JSClass psiClass : extendedClasses) {
                superClasses.addAll(resolveHierarchy(psiClass));
            }

            for (JSClass psiClass : implementedInterfaces) {
                superClasses.addAll(resolveHierarchy(psiClass));
            }

        });

        ArrayList<JSClass> copyResult = new ArrayList<>(superClasses);
        copyResult.removeIf(Objects::isNull);
        return copyResult;
    }

}