package listener;

import com.intellij.lang.javascript.psi.ecma6.TypeScriptClass;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptFunction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import util.Index;

import java.util.Collection;
import java.util.List;

/**
 * Listener for file changes in order to update the index.
 */
public class FileChangeListener implements BulkFileListener {

    /**
     * Update the index after a file change.
     * @param events the file events
     */
    @Override
    public void after(@NotNull List<? extends @NotNull VFileEvent> events) {

        for (VFileEvent event : events) {

            VirtualFile file = event.getFile();
            if (file != null && file.getName().endsWith(".ts")) {

                PsiManager manager = PsiManager.getInstance(Index.getProject());
                PsiFile psiFile = manager.findFile(file);
                if (psiFile == null || !psiFile.isValid()) continue;

                // iterate all functions in file -> update index
                for (TypeScriptFunction psiFunction : PsiTreeUtil.findChildrenOfType(psiFile, TypeScriptFunction.class)) {
                    Index.updateFunction(psiFunction);
                }

                Collection<PsiElement> allClasses = List.of(PsiTreeUtil.collectElements(psiFile, element ->
                        element instanceof TypeScriptClass
                ));

                // iterate all classes in file -> update index
                for (PsiElement psiElement : allClasses) {
                    TypeScriptClass psiClass = (TypeScriptClass) psiElement;
                    Index.updateClass(psiClass);
                }
            }
        }

        BulkFileListener.super.after(events);
    }
}
