package listener;

import com.intellij.lang.javascript.psi.ecma6.TypeScriptClass;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptFunction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiTreeChangeEvent;
import com.intellij.psi.PsiTreeChangeListener;
import org.jetbrains.annotations.NotNull;
import util.Index;

public class PsiChangeListener implements PsiTreeChangeListener {

    private static final Logger LOG = Logger.getInstance(PsiChangeListener.class);

    @Override
    public void beforeChildAddition(@NotNull PsiTreeChangeEvent psiTreeChangeEvent) {
        LOG.info("beforeChildAddition");
    }

    @Override
    public void beforeChildRemoval(@NotNull PsiTreeChangeEvent psiTreeChangeEvent) {
        LOG.info("beforeChildRemoval");
        if (psiTreeChangeEvent.getChild() instanceof TypeScriptClass) {
            Index.removePsiClass(((TypeScriptClass) psiTreeChangeEvent.getChild()).getQualifiedName());
        } else if (psiTreeChangeEvent.getChild() instanceof TypeScriptFunction) {
            Index.removePsiFunction(((TypeScriptFunction) psiTreeChangeEvent.getChild()).getQualifiedName());
        }
    }

    @Override
    public void beforeChildReplacement(@NotNull PsiTreeChangeEvent psiTreeChangeEvent) {
        LOG.info("beforeChildReplacement");
    }

    @Override
    public void beforeChildMovement(@NotNull PsiTreeChangeEvent psiTreeChangeEvent) {
        LOG.info("beforeChildMovement");
    }

    @Override
    public void beforeChildrenChange(@NotNull PsiTreeChangeEvent psiTreeChangeEvent) {
        LOG.info("beforeChildrenChange");
    }

    @Override
    public void beforePropertyChange(@NotNull PsiTreeChangeEvent psiTreeChangeEvent) {
        LOG.info("beforePropertyChange");
    }

    @Override
    public void childAdded(@NotNull PsiTreeChangeEvent psiTreeChangeEvent) {
        LOG.info("childAdded");
        if (psiTreeChangeEvent.getChild() instanceof TypeScriptClass) {
            Index.addPsiClass((TypeScriptClass) psiTreeChangeEvent.getChild());
        } else if (psiTreeChangeEvent.getChild() instanceof TypeScriptFunction) {
            Index.addPsiFunction((TypeScriptFunction) psiTreeChangeEvent.getChild());
        }
    }

    @Override
    public void childRemoved(@NotNull PsiTreeChangeEvent psiTreeChangeEvent) {
        LOG.info("childRemoved");
    }

    @Override
    public void childReplaced(@NotNull PsiTreeChangeEvent psiTreeChangeEvent) {
        LOG.info("childReplaced");
    }

    @Override
    public void childrenChanged(@NotNull PsiTreeChangeEvent psiTreeChangeEvent) {
        LOG.info("childrenChanged");
        if (psiTreeChangeEvent.getOldChild() instanceof TypeScriptClass && psiTreeChangeEvent.getNewChild() instanceof TypeScriptClass) {
            Index.updatePsiClass(((TypeScriptClass) psiTreeChangeEvent.getOldChild()).getQualifiedName(), (TypeScriptClass) psiTreeChangeEvent.getNewChild());
        } else if (psiTreeChangeEvent.getOldChild() instanceof TypeScriptFunction && psiTreeChangeEvent.getNewChild() instanceof TypeScriptFunction) {
            Index.updatePsiFunction(((TypeScriptFunction) psiTreeChangeEvent.getOldChild()).getQualifiedName(), (TypeScriptFunction) psiTreeChangeEvent.getNewChild());
        }
    }

    @Override
    public void childMoved(@NotNull PsiTreeChangeEvent psiTreeChangeEvent) {
        LOG.info("childMoved");
    }

    @Override
    public void propertyChanged(@NotNull PsiTreeChangeEvent psiTreeChangeEvent) {
        LOG.info("propertyChanged");
    }
}
