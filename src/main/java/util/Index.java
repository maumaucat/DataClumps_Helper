package util;

import com.intellij.lang.javascript.TypeScriptFileType;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptClass;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptFunction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.HashMap;

public class Index {

    private static HashMap<String, TypeScriptFunction> psiFunctions;

    private static HashMap<String, TypeScriptClass> psiClasses;

    private static final Logger LOG = Logger.getInstance(Index.class);

    public static HashMap<String, TypeScriptFunction> getPsiFunctions() {
        return psiFunctions;
    }

    public static HashMap<String, TypeScriptClass> getPsiClasses() {
        return psiClasses;
    }

    public static void addPsiFunction(TypeScriptFunction function) {
        psiFunctions.put(function.getQualifiedName(), function);
    }

    public static void addPsiClass(TypeScriptClass cls) {
        psiClasses.put(cls.getQualifiedName(), cls);
    }

    public static void updatePsiFunction(String oldQualifiedName, TypeScriptFunction newFunction) {
        psiFunctions.remove(oldQualifiedName);
        addPsiFunction(newFunction);
    }

    public static void updatePsiClass(String oldQualifiedName, TypeScriptClass newClass) {
        psiClasses.remove(oldQualifiedName);
        addPsiClass(newClass);
    }

    public static void removePsiFunction(String oldQualifiedName) {
        psiFunctions.remove(oldQualifiedName);
    }

    public static void removePsiClass(String oldQualifiedName) {
        psiClasses.remove(oldQualifiedName);
    }

    public static void resetIndex(Project project) {

        psiFunctions = new HashMap<>();
        psiClasses = new HashMap<>();

        // Read operations need this
        ApplicationManager.getApplication().runReadAction(() -> {

            PsiManager manager = PsiManager.getInstance(project);

            // Alle TypeScriptFiles
            for (VirtualFile virtualFile : FileTypeIndex.getFiles(TypeScriptFileType.INSTANCE, GlobalSearchScope.projectScope(project))) {

                PsiFile psiFile = manager.findFile(virtualFile);

                LOG.info("PSIFile: " + psiFile.getName());

                for (TypeScriptFunction psiFunction : PsiTreeUtil.findChildrenOfType(psiFile, TypeScriptFunction.class)) {
                    LOG.info("Function Name: " + psiFunction.getQualifiedName());
                    addPsiFunction(psiFunction);
                }

                for (TypeScriptClass psiClass : PsiTreeUtil.getChildrenOfTypeAsList(psiFile, TypeScriptClass.class)) {
                    LOG.info("Class Name: " + psiClass.getName());
                    addPsiClass(psiClass);
                }
            }
        });
    }
}

