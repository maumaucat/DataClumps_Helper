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

public class DataClumpDetection extends LocalInspectionTool {

    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new JSElementVisitor() {

            @Override
            public void visitJSParameterList(@NotNull JSParameterList parameterList) {
                TypeScriptFunction psiFunction = PsiTreeUtil.getParentOfType(parameterList, TypeScriptFunction.class);
                if (psiFunction.isConstructor()) return;
                Index.updateFunction(psiFunction);
                if (parameterList.getParameters().length >= DataClumpSettings.getInstance().getState().minNumberOfProperties) {
                    detectDataClump(psiFunction, holder);
                    //detectDataClumpForFunction(psiFunction, holder);
                }
                super.visitJSParameterList(parameterList);
            }

            @Override
            public void visitTypeScriptClass(@NotNull TypeScriptClass TypeScriptClass) {
                Index.updateClass(TypeScriptClass);
                if (PsiUtil.getClassfields(TypeScriptClass).size() >= DataClumpSettings.getInstance().getState().minNumberOfProperties) {
                    //detectDataClumpForClass(TypeScriptClass, holder);
                    detectDataClump(TypeScriptClass, holder);
                }
                super.visitTypeScriptClass(TypeScriptClass);
            }
        };
    }

    public void detectDataClump(PsiElement currentElement, ProblemsHolder holder) {

        HashMap<PsiElement, List<Property>> potentialDataClumps = new HashMap<>();

        if (currentElement instanceof TypeScriptClass currentClass) {
            potentialDataClumps = calculatePotentialDataClumpsForClass(currentClass);
        } else if (currentElement instanceof TypeScriptFunction currentFunction) {
            potentialDataClumps = calculatePotentialDataClumpsForFunction(currentFunction);
        }

        processPotentialDataClumps(potentialDataClumps, holder, currentElement);
    }

    private void processPotentialDataClumps(HashMap<PsiElement, List<Property>> potentialDataClumps, ProblemsHolder holder, PsiElement currentElement) {

        List<Property> currentElementsProperties = new ArrayList<>();

        if (currentElement instanceof TypeScriptClass currentClass) {
            currentElementsProperties = new ArrayList<>(Index.getClassesToClassFields().get(currentClass));
        } else if (currentElement instanceof TypeScriptFunction currentFunction) {
            currentElementsProperties = new ArrayList<>(Index.getFunctionsToParameters().get(currentFunction));
        }

        for (PsiElement otherElement : potentialDataClumps.keySet()) {
            List<Property> matchingProperties = potentialDataClumps.get(otherElement);
            if (matchingProperties.size() >= DataClumpSettings.getInstance().getState().minNumberOfProperties) {
                CodeSmellLogger.info("Detected DataClump");
                // mark the properties as a data clump
                for (Property property : currentElementsProperties) {
                    if (matchingProperties.contains(property)) {

                        PsiElement dataClumpElement = null;
                       if (currentElement instanceof JSClass currentClass) {
                           dataClumpElement = PsiUtil.getPsiField(currentClass, property.getName());
                       } else {
                           TypeScriptFunction currentFunction = (TypeScriptFunction) currentElement;
                           dataClumpElement = PsiUtil.getPsiParameter(currentFunction, (Parameter) property);
                       }

                        holder.registerProblem(dataClumpElement, "Data Clump with " + PsiUtil.getName(otherElement) +
                               ". Matching Properties " + matchingProperties + ".", new DataClumpRefactoring(currentElement, otherElement, new ArrayList<>(matchingProperties)));

                    }
                }
            }
        }

    }


    private HashMap<PsiElement, List<Property>> calculatePotentialDataClumpsForClass(TypeScriptClass currentClass) {

        HashMap<PsiElement, List<Property>> potentialDataClumps = new HashMap<>();

        for (Classfield classfield : Index.getClassesToClassFields().get(currentClass)) {

            for (TypeScriptClass otherClass : Index.getPropertiesToClasses().get(classfield)) {

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
            for (TypeScriptFunction otherFunction : Index.getPropertiesToFunctions().get(classfield)) {
                if (!check(currentClass, otherFunction)) continue;
                if (!potentialDataClumps.containsKey(otherFunction)) {
                    potentialDataClumps.put(otherFunction, new ArrayList<>());
                }
                potentialDataClumps.get(otherFunction).add(classfield);
            }
        }
        return potentialDataClumps;
    }

    private HashMap<PsiElement, List<Property>> calculatePotentialDataClumpsForFunction(TypeScriptFunction currentFunction) {

        HashMap<PsiElement, List<Property>> potentialDataClumps = new HashMap<>();

        for (Parameter parameter : Index.getFunctionsToParameters().get(currentFunction)) {
            for (TypeScriptFunction otherFunction : Index.getPropertiesToFunctions().get(parameter)) {
                if (!check(currentFunction, otherFunction)) continue;
                if (!potentialDataClumps.containsKey(otherFunction)) {
                    potentialDataClumps.put(otherFunction, new ArrayList<>());
                }
                potentialDataClumps.get(otherFunction).add(parameter);
               }

            if (!Index.getPropertiesToClasses().containsKey(parameter)) continue;
            for (TypeScriptClass otherClass : Index.getPropertiesToClasses().get(parameter)) {
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
            return true;
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

    private boolean checkClassFunctionPair(TypeScriptFunction function, TypeScriptClass TypeScriptClass) {
        TypeScriptClass functionClass = PsiTreeUtil.getParentOfType(function, TypeScriptClass.class);
        return !inSameHierarchy(functionClass, TypeScriptClass);
    }

    private boolean checkFunctions(TypeScriptFunction function1, TypeScriptFunction function2) {
        return !isOverridden(function1, function2);
    }

    private static boolean isOverridden(TypeScriptFunction function1, TypeScriptFunction function2) {
        JSClass class1 = PsiTreeUtil.getParentOfType(function1, JSClass.class);
        JSClass class2 = PsiTreeUtil.getParentOfType(function2, JSClass.class);

        if (class1 == null || class2 == null | class1 == class2 || !function1.getName().equals(function2.getName())) return false;

        List<JSClass> common = getCommonClassesInHierarchy(class1, class2);
        for (JSClass commonClass : common) {
            if (commonClass.findFunctionByName(function1.getName()) != null) {
                return true;
            }
        }
        return false;

    }

    private static boolean inSameHierarchy(TypeScriptClass class1, TypeScriptClass class2) {
        return !getCommonClassesInHierarchy(class1, class2).isEmpty();
    }

    private static List<JSClass> getCommonClassesInHierarchy(JSClass class1, JSClass class2) {
        if (class1 == null || class2 == null) return new ArrayList<>();

        List<JSClass> superClasses1 = resolveHierarchy(class1);
        List<JSClass> superClasses2 = resolveHierarchy(class2);

        superClasses1.retainAll(superClasses2);

        return superClasses1;
    }

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