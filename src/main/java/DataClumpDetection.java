import com.intellij.lang.javascript.psi.ecmal4.JSClass;
import com.intellij.psi.PsiElement;
import util.*;
import com.intellij.codeInspection.*;
import com.intellij.lang.javascript.psi.JSElementVisitor;
import com.intellij.lang.javascript.psi.JSParameterList;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptClass;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptFunction;
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

                TypeScriptFunction psiFunction = PsiTreeUtil.getParentOfType(parameterList, TypeScriptFunction.class);

                assert psiFunction != null;

                // Skip constructors
                if (psiFunction.isConstructor()) return;
                // Update the index
                Index.updateFunction(psiFunction);
                // Detect data clumps if the number of parameters is greater than the required minimum
                if (parameterList.getParameters().length >= Objects.requireNonNull(DataClumpSettings.getInstance().getState()).minNumberOfProperties) {
                    detectDataClump(psiFunction, holder);
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

                // Update the index
                Index.updateClass(TypeScriptClass);
                // Detect data clumps if the number of properties is greater than the required minimum
                if (PsiUtil.getClassfields(TypeScriptClass).size() >= Objects.requireNonNull(DataClumpSettings.getInstance().getState()).minNumberOfProperties) {
                    detectDataClump(TypeScriptClass, holder);
                }
                super.visitTypeScriptClass(TypeScriptClass);
            }
        };
    }

    /**
     * Detect data clumps in the current element
     *
     * @param currentElement the current element
     * @param holder         the problems holder
     */
    public void detectDataClump(PsiElement currentElement, ProblemsHolder holder) {

        HashMap<PsiElement, List<Property>> potentialDataClumps = new HashMap<>();

        if (currentElement instanceof TypeScriptClass currentClass) {
            potentialDataClumps = calculatePotentialDataClumpsForClass(currentClass);
        } else if (currentElement instanceof TypeScriptFunction currentFunction) {
            potentialDataClumps = calculatePotentialDataClumpsForFunction(currentFunction);
        }

        processPotentialDataClumps(potentialDataClumps, holder, currentElement);
    }

    /**
     * Process the potential data clumps and mark them as data clumps if they meet the required conditions
     *
     * @param potentialDataClumps the potential data clumps
     * @param holder              the problems holder
     * @param currentElement      the current element
     */
    private void processPotentialDataClumps(HashMap<PsiElement, List<Property>> potentialDataClumps, ProblemsHolder holder, PsiElement currentElement) {

        List<Property> currentElementsProperties = new ArrayList<>();

        if (currentElement instanceof TypeScriptClass currentClass) {
            currentElementsProperties = new ArrayList<>(Index.getClassesToClassFields().get(currentClass));
        } else if (currentElement instanceof TypeScriptFunction currentFunction) {
            currentElementsProperties = new ArrayList<>(Index.getFunctionsToParameters().get(currentFunction));
        }

        for (PsiElement otherElement : potentialDataClumps.keySet()) {
            List<Property> matchingProperties = potentialDataClumps.get(otherElement);
            // Check if the number of matching properties is greater than the required minimum
            if (matchingProperties.size() >= Objects.requireNonNull(DataClumpSettings.getInstance().getState()).minNumberOfProperties) {
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

                        holder.registerProblem(dataClumpElement, "Data Clump with " + PsiUtil.getName(otherElement) +
                                ". Matching Properties " + matchingProperties + ".", new DataClumpRefactoring(currentElement, otherElement, new ArrayList<>(matchingProperties)));

                    }
                }
            }
        }

    }

    /**
     * Calculate the potential data clumps for a class
     *
     * @param currentClass the current class
     * @return the potential data clumps for the class. The key is the other class or function
     * and the value is the matching properties with the current class
     */
    private HashMap<PsiElement, List<Property>> calculatePotentialDataClumpsForClass(TypeScriptClass currentClass) {

        HashMap<PsiElement, List<Property>> potentialDataClumps = new HashMap<>();

        for (Classfield classfield : Index.getClassesToClassFields().get(currentClass)) {

            for (TypeScriptClass otherClass : new ArrayList<>(Index.getPropertiesToClasses().get(classfield))) {

                if (!check(currentClass, otherClass)) continue;
                List<Classfield> classfieldList = Index.getClassesToClassFields().get(otherClass);
                if (classfieldList.contains(classfield) && classfieldList.get(classfieldList.indexOf(classfield)).matches(classfield)) {
                    if (!potentialDataClumps.containsKey(otherClass)) {
                        potentialDataClumps.put(otherClass, new ArrayList<>());
                    }
                    potentialDataClumps.get(otherClass).add(classfield);
                }
            }

            if (!Index.getPropertiesToFunctions().containsKey(classfield)) continue;
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
            for (TypeScriptClass otherClass : new ArrayList<>(Index.getPropertiesToClasses().get(parameter))) {
                if (!check(currentFunction, otherClass)) continue;

                Classfield classfield = Index.getMatchingClassFieldForClass(otherClass, parameter);
                if (!potentialDataClumps.containsKey(otherClass)) {
                    potentialDataClumps.put(otherClass, new ArrayList<>());
                }
                potentialDataClumps.get(otherClass).add(classfield);
            }
        }
        return potentialDataClumps;
    }

    /**
     * Check if the two elements should be compared for data clumps
     *
     * @param element1 the first element
     * @param element2 the second element
     * @return true if the elements should be compared, false otherwise
     */
    private boolean check(PsiElement element1, PsiElement element2) {

        if (!element1.isValid()) {
            Index.removeElement(element2);
            return false;
        }
        if (!element2.isValid()) {
            Index.removeElement(element2);
            return false;
        }

        if (element1 == element2) return false;

        if (element1 instanceof TypeScriptClass class1 && element2 instanceof TypeScriptClass class2) {
            return checkClasses(class1, class2);
        }
        if (element1 instanceof TypeScriptFunction function1 && element2 instanceof TypeScriptFunction function2) {
            return checkFunctions(function1, function2);
        }
        if (element1 instanceof TypeScriptClass TypeScriptClass && element2 instanceof TypeScriptFunction function) {
            return checkClassFunctionPair(function, TypeScriptClass);
        }
        if (element1 instanceof TypeScriptFunction function && element2 instanceof TypeScriptClass TypeScriptClass) {
            return checkClassFunctionPair(function, TypeScriptClass);
        }

        CodeSmellLogger.error("Invalid Elements for Data Clump Detection: " + element1 + " " + element2, new IllegalArgumentException());
        return false;
    }

    public boolean checkClasses(TypeScriptClass TypeScriptClass1, TypeScriptClass TypeScriptClass2) {
        if (Objects.requireNonNull(DataClumpSettings.getInstance().getState()).includeFieldsInSameHierarchy) {
            return true;
        } else {
            return !inSameHierarchy(TypeScriptClass1, TypeScriptClass2);
        }
    }

    /**
     * Check if the class and function should be compared for data clumps
     *
     * @param function        the function
     * @param TypeScriptClass the class
     * @return true if the class and function should be compared, false otherwise
     */
    private boolean checkClassFunctionPair(TypeScriptFunction function, TypeScriptClass TypeScriptClass) {
        TypeScriptClass functionClass = PsiTreeUtil.getParentOfType(function, TypeScriptClass.class);
        return !inSameHierarchy(functionClass, TypeScriptClass);
    }

    /**
     * Check if the two functions should be compared for data clumps
     *
     * @param function1 the first function
     * @param function2 the second function
     * @return true if the functions should be compared, false otherwise
     */
    private boolean checkFunctions(TypeScriptFunction function1, TypeScriptFunction function2) {
        return !isOverridden(function1, function2);
    }

    /**
     * Check if the one function is overridden by the other function
     *
     * @param function1 the first function
     * @param function2 the second function
     * @return true if the first function is overridden by the second function, false otherwise
     */
    private static boolean isOverridden(TypeScriptFunction function1, TypeScriptFunction function2) {
        JSClass class1 = PsiTreeUtil.getParentOfType(function1, JSClass.class);
        JSClass class2 = PsiTreeUtil.getParentOfType(function2, JSClass.class);

        if (class1 == null || class2 == null | class1 == class2 || !Objects.equals(function1.getName(), function2.getName()))
            return false;

        List<JSClass> common = getCommonClassesInHierarchy(class1, class2);
        for (JSClass commonClass : common) {
            if (commonClass.findFunctionByName(function1.getName()) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if the two classes are in the same hierarchy
     *
     * @param class1 the first class
     * @param class2 the second class
     * @return true if the classes are in the same hierarchy, false otherwise
     */
    private static boolean inSameHierarchy(TypeScriptClass class1, TypeScriptClass class2) {
        return !getCommonClassesInHierarchy(class1, class2).isEmpty();
    }

    /**
     * Get the common classes in the hierarchy of the two classes
     *
     * @param class1 the first class
     * @param class2 the second class
     * @return the common classes in the hierarchy of the two classes
     */
    private static List<JSClass> getCommonClassesInHierarchy(JSClass class1, JSClass class2) {
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
    private static List<JSClass> resolveHierarchy(JSClass tsClass) {

        List<JSClass> superClasses = new ArrayList<>();
        superClasses.add(tsClass);

        for (JSClass psiClass : tsClass.getSuperClasses()) {
            superClasses.addAll(resolveHierarchy(psiClass));
        }

        for (JSClass psiClass : tsClass.getImplementedInterfaces()) {
            superClasses.addAll(resolveHierarchy(psiClass));
        }

        return superClasses;
    }

}