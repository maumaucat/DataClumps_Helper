package dataclump;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.javascript.TypeScriptFileType;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptClass;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptFunction;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptInterface;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import evoluation.DiagnosticTool;
import util.CodeSmellLogger;
import util.DataClumpUtil;
import util.Index;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FullAnalysis {

    public static void run(String resultPath) {

        long startTime = 0;
        if (DiagnosticTool.DIAGNOSTIC_MODE) {
             startTime = System.nanoTime();
        }

        if (!Index.isIndexBuilt()) {
            CodeSmellLogger.error("Index not built", new IllegalStateException());
            return;
        }

        Project project = Index.getProject();
        PsiManager manager = PsiManager.getInstance(project);

        List<DataClumpProblem> dataClumpProblems = new ArrayList<>();

        // Alle TypeScriptFiles
        for (VirtualFile virtualFile : FileTypeIndex.getFiles(TypeScriptFileType.INSTANCE, GlobalSearchScope.projectScope(project))) {
            PsiFile psiFile = manager.findFile(virtualFile);

            // iterate all functions in file
            for (TypeScriptFunction psiElement : PsiTreeUtil.findChildrenOfType(psiFile, TypeScriptFunction.class)) {
                ProblemsHolder problemsHolder = DataClumpUtil.invokeInspection(psiElement);
                assert problemsHolder != null;
                collectProblems(problemsHolder, dataClumpProblems);
            }

            // iterate all classes in file
            Collection<PsiElement> allClasses = List.of(PsiTreeUtil.collectElements(psiFile, element ->
                    element instanceof TypeScriptClass
            ));

            for (PsiElement psiElement : allClasses) {
                ProblemsHolder problemsHolder = DataClumpUtil.invokeInspection(psiElement);
                assert problemsHolder != null;
                collectProblems(problemsHolder, dataClumpProblems);
            }
            //iterate all interfaces in file
            Collection<PsiElement> allInterfaces = List.of(PsiTreeUtil.collectElements(psiFile, element ->
                    element instanceof TypeScriptInterface
            ));

            for (PsiElement psiElement : allInterfaces) {
                ProblemsHolder problemsHolder = DataClumpUtil.invokeInspection(psiElement);
                assert problemsHolder != null;
                collectProblems(problemsHolder, dataClumpProblems);
            }
        }

        long durationWithoutIO = 0;
        if (DiagnosticTool.DIAGNOSTIC_MODE) {
            long endTime = System.nanoTime();
            durationWithoutIO = (endTime - startTime) / 1000000;
        }

        writeDataClumpsToFile(dataClumpProblems, resultPath);

        if (DiagnosticTool.DIAGNOSTIC_MODE) {
            long endTime = System.nanoTime();
            long duration = (endTime - startTime) / 1000000;
            DiagnosticTool.addMeasurement(new DiagnosticTool.FullAnalysisMeasurement(project, duration, durationWithoutIO));
        }
    }

    private static void collectProblems(ProblemsHolder problemsHolder, List<DataClumpProblem> dataClumpProblems) {

        if (problemsHolder.hasResults()) {
            for (ProblemDescriptor problem : problemsHolder.getResults()) {
                String problemDescription = problem.getDescriptionTemplate();

                Pattern pattern = Pattern.compile("Data Clump between (.*?) and (.*?)\\. Matching Properties \\[(.*?)\\]");
                Matcher matcher = pattern.matcher(problemDescription);

                if (matcher.find()) {
                    // extract the names
                    String name1 = matcher.group(1);
                    String name2 = matcher.group(2);

                    // extract the properties
                    String[] properties = matcher.group(3).split(",\\s*");
                    List<String> propertyList = Arrays.asList(properties);

                    DataClumpProblem dataClumpProblem = new DataClumpProblem(name1, name2, propertyList);
                    if (!dataClumpProblems.contains(dataClumpProblem)) {
                        dataClumpProblems.add(dataClumpProblem);
                    }
                }
            }
        }

    }

    private static void writeDataClumpsToFile(List<DataClumpProblem> dataClumpProblems, String resultPath) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        List<DataClumpProblem> problems;

        // read existing measurements
        File file = new File(resultPath);
        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                Type listType = new TypeToken<List<DataClumpProblem>>() {
                }.getType();
                problems = gson.fromJson(reader, listType);
            } catch (IOException e) {
                CodeSmellLogger.error("Could not read filepath: " + resultPath, e);
                return;
            }
        } else {
            problems = new ArrayList<>();
        }

        // add new measurement
        problems.addAll(dataClumpProblems);

        // write measurements back to file
        try (FileWriter writer = new FileWriter(resultPath)) {
            gson.toJson(problems, writer);
        } catch (IOException e) {
            CodeSmellLogger.error("Could not write filepath: " + resultPath, e);
        }
    }

    private static class DataClumpProblem {
        private String element1;
        private String element2;
        private List<String> dataClump;

        public DataClumpProblem(String element1, String element2, List<String> dataClump) {
            this.element1 = element1;
            this.element2 = element2;
            this.dataClump = dataClump;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            DataClumpProblem other = (DataClumpProblem) obj;
            return ((element1.equals(other.element1) && element2.equals(other.element2)) ||
                    (element1.equals(other.element2) && element2.equals(other.element1)))
                    && dataClump.equals(other.dataClump);
        }
    }

}
