package listener;

import com.intellij.lang.javascript.psi.JSType;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptClass;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptField;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptFunction;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptParameter;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiTreeChangeEvent;
import com.intellij.psi.PsiTreeChangeListener;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import util.ClassField;
import util.Index;
import util.Parameter;

public class PsiChangeListener /*implements PsiTreeChangeListener*/ {

   /* private static final Logger LOG = Logger.getInstance(PsiChangeListener.class);

    @Override
    public void beforeChildAddition(@NotNull PsiTreeChangeEvent psiTreeChangeEvent) {
    }

    @Override
    public void beforeChildRemoval(@NotNull PsiTreeChangeEvent psiTreeChangeEvent) {
        //LOG.info("beforeChildRemoval");
        /*PsiElement element = psiTreeChangeEvent.getChild();
        if (element instanceof TypeScriptParameter) {
            TypeScriptFunction psiFunction = PsiTreeUtil.getParentOfType(element, TypeScriptFunction.class);
            Index.removeParameterForFunction(psiFunction, (TypeScriptParameter) element);
        }  else if (element instanceof TypeScriptField) {
            TypeScriptClass psiClass = PsiTreeUtil.getParentOfType(element, TypeScriptClass.class);
            Index.removeFieldForClass(psiClass, (TypeScriptField) element);
        }
    }

    @Override
    public void beforeChildReplacement(@NotNull PsiTreeChangeEvent psiTreeChangeEvent) {
        //LOG.info("beforeChildReplacement");
        /*PsiElement element = psiTreeChangeEvent.getOldChild();
        if (element instanceof TypeScriptParameter) {
            TypeScriptFunction psiFunction = PsiTreeUtil.getParentOfType(element, TypeScriptFunction.class);
            Index.removeParameterForFunction(psiFunction, (TypeScriptParameter) element);
        } else if (element instanceof TypeScriptField) {
            TypeScriptClass psiClass = PsiTreeUtil.getParentOfType(element, TypeScriptClass.class);
            Index.removeFieldForClass(psiClass, (TypeScriptField) element);
        }
    }

    @Override
    public void beforeChildMovement(@NotNull PsiTreeChangeEvent psiTreeChangeEvent) {
        //LOG.info("beforeChildMovement");
        /*PsiElement element = psiTreeChangeEvent.getChild();
        if (element instanceof TypeScriptParameter) {
            TypeScriptFunction old_psiFunction = PsiTreeUtil.getParentOfType(psiTreeChangeEvent.getOldParent(), TypeScriptFunction.class);
            Index.removeParameterForFunction(old_psiFunction, (TypeScriptParameter) element);
            TypeScriptFunction new_psiFunction = PsiTreeUtil.getParentOfType(psiTreeChangeEvent.getNewParent(), TypeScriptFunction.class);
            Index.addNewParameterForFunction(new_psiFunction, (TypeScriptParameter) element);
        } else if (element instanceof TypeScriptField) {
            TypeScriptClass old_psiClass = PsiTreeUtil.getParentOfType(psiTreeChangeEvent.getOldParent(), TypeScriptClass.class);
            Index.removeFieldForClass(old_psiClass, (TypeScriptField) element);
            TypeScriptClass new_psiClass = PsiTreeUtil.getParentOfType(psiTreeChangeEvent.getNewParent(), TypeScriptClass.class);
            Index.addNewFieldForClass(new_psiClass, (TypeScriptField) element);
        }
    }

    @Override
    public void beforeChildrenChange(@NotNull PsiTreeChangeEvent psiTreeChangeEvent) {
        //TODO called with the whole file :<
        LOG.info("beforeChildrenChange");
        LOG.info("parent:" + psiTreeChangeEvent.getParent().toString());
    }

    @Override
    public void beforePropertyChange(@NotNull PsiTreeChangeEvent psiTreeChangeEvent) {
        //TODO called almost never?
        LOG.info("beforePropertyChange");
        PsiElement element = psiTreeChangeEvent.getChild();
        LOG.info("element: " + element.getText());

        if (element instanceof TypeScriptParameter) {
            LOG.info("Element propertyName: " + psiTreeChangeEvent.getPropertyName());
            Index.printParametersToFunctions();

            TypeScriptFunction function = PsiTreeUtil.getParentOfType(element, TypeScriptFunction.class);
            if (psiTreeChangeEvent.getPropertyName().equals("name")) {
                String old_name = (String) psiTreeChangeEvent.getOldValue();
                String new_name = (String) psiTreeChangeEvent.getNewValue();
                Index.removeParameterForFunction(function, new Parameter(old_name, ((TypeScriptParameter) element).getJSType()));
                Index.addNewParameterForFunction(function, new Parameter(new_name, ((TypeScriptParameter) element).getJSType()));
            } else if (psiTreeChangeEvent.getPropertyName().equals("jsType")) {
                JSType old_type = (JSType) psiTreeChangeEvent.getOldValue();
                JSType new_type = (JSType) psiTreeChangeEvent.getNewValue();
                Index.removeParameterForFunction(function, new Parameter(((TypeScriptParameter) element).getName(), old_type));
                Index.addNewParameterForFunction(function, new Parameter(((TypeScriptParameter) element).getName(), new_type));
            }
            Index.printParametersToFunctions();
        } else if (element instanceof TypeScriptField) {
            LOG.info("Element propertyName: " + psiTreeChangeEvent.getPropertyName());
            Index.printClassFieldsToClasses();



            TypeScriptClass psiClass = PsiTreeUtil.getParentOfType(element, TypeScriptClass.class);
            if (psiTreeChangeEvent.getPropertyName().equals("name")) {
                String old_name = (String) psiTreeChangeEvent.getOldValue();
                String new_name = (String) psiTreeChangeEvent.getNewValue();
                Index.removeFieldForClass(psiClass, new ClassField(old_name,((TypeScriptField) element).getJSType(),((TypeScriptField) element).getAccessType().toString()));
                Index.addNewFieldForClass(psiClass, new ClassField(new_name, ((TypeScriptField) element).getJSType(),((TypeScriptField) element).getAccessType().toString()));
            } else if (psiTreeChangeEvent.getPropertyName().equals("jsType")) {
                JSType old_type = (JSType) psiTreeChangeEvent.getOldValue();
                JSType new_type = (JSType) psiTreeChangeEvent.getNewValue();
                Index.removeFieldForClass(psiClass, new ClassField(((TypeScriptField) element).getName(),old_type,((TypeScriptField) element).getAccessType().toString()));
                Index.addNewFieldForClass(psiClass, new ClassField(((TypeScriptField) element).getName(), new_type,((TypeScriptField) element).getAccessType().toString()));
            } else if (psiTreeChangeEvent.getPropertyName().equals("accessType")) {
                String old_access = psiTreeChangeEvent.getOldValue().toString();
                String new_access = psiTreeChangeEvent.getNewValue().toString();
                Index.removeFieldForClass(psiClass, new ClassField(((TypeScriptField) element).getName(),((TypeScriptField) element).getJSType(),old_access));
                Index.addNewFieldForClass(psiClass, new ClassField(((TypeScriptField) element).getName(), ((TypeScriptField) element).getJSType(),new_access));
            }
            Index.printClassFieldsToClasses();
        }
    }

    @Override
    public void childAdded(@NotNull PsiTreeChangeEvent psiTreeChangeEvent) {

        //TODO Useless since only added if it has a type but called for the first letter of a parameter
        LOG.info("Child added " + psiTreeChangeEvent.getChild());
        PsiElement element = psiTreeChangeEvent.getChild();

        if (element instanceof TypeScriptParameter) {
            LOG.info("childAdded: " + psiTreeChangeEvent.getChild());
            Index.printParametersToFunctions();
            TypeScriptFunction psiFunction = PsiTreeUtil.getParentOfType(element, TypeScriptFunction.class);
            Index.addNewParameterForFunction(psiFunction, (TypeScriptParameter) element);
            Index.printParametersToFunctions();

        }
        if (element instanceof TypeScriptField) {
            LOG.info("childAdded: " + psiTreeChangeEvent.getChild());
            Index.printClassFieldsToClasses();
            TypeScriptClass psiClass = PsiTreeUtil.getParentOfType(element, TypeScriptClass.class);
            Index.addNewFieldForClass(psiClass, (TypeScriptField) element);
            Index.printClassFieldsToClasses();
        }
    }

    @Override
    public void childRemoved(@NotNull PsiTreeChangeEvent psiTreeChangeEvent) {
    }

    @Override
    public void childReplaced(@NotNull PsiTreeChangeEvent psiTreeChangeEvent) {

    }

    @Override
    public void childrenChanged(@NotNull PsiTreeChangeEvent psiTreeChangeEvent) {
    }

    @Override
    public void childMoved(@NotNull PsiTreeChangeEvent psiTreeChangeEvent) {
    }

    @Override
    public void propertyChanged(@NotNull PsiTreeChangeEvent psiTreeChangeEvent) {
        LOG.info("PropertyChanged");
        PsiElement element = psiTreeChangeEvent.getElement();
        LOG.info("element: " + element.getText());
    }
    */
}
