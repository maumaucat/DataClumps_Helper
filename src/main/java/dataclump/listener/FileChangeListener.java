package dataclump.listener;

import com.intellij.psi.PsiElementVisitor;
import dataclump.DataClumpDetection;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptClass;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptFunction;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptInterface;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import util.DataClumpUtil;
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
            if (file != null && file.isValid() && file.getName().endsWith(".ts")) {

                // only proceed if index is built
                if (!Index.isIndexBuilt()) continue;
                // get the psi file from the virtual file
                Project project = Index.getProject();
                PsiManager manager = PsiManager.getInstance(project);
                PsiFile psiFile = manager.findFile(file);
                if (psiFile == null || !psiFile.isValid()) continue;

                // iterate all functions in file -> invoke inspection
                for (TypeScriptFunction psiElement : PsiTreeUtil.findChildrenOfType(psiFile, TypeScriptFunction.class)) {
                    DataClumpUtil.invokeInspection(psiElement);
                }

                Collection<PsiElement> allClasses = List.of(PsiTreeUtil.collectElements(psiFile, element ->
                        element instanceof TypeScriptClass
                ));

                // iterate all classes in file -> invoke inspection
                for (PsiElement psiElement : allClasses) {
                    DataClumpUtil.invokeInspection(psiElement);
                }

                //iterate all interfaces in file -> invoke inspection
                Collection<PsiElement> allInterfaces = List.of(PsiTreeUtil.collectElements(psiFile, element ->
                        element instanceof TypeScriptInterface
                ));

                for (PsiElement psiElement : allInterfaces) {
                    DataClumpUtil.invokeInspection(psiElement);
                }

            }
        }
        BulkFileListener.super.after(events);
    }
}
