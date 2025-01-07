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
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import evoluation.DiagnosticTool;
import org.jetbrains.annotations.NotNull;
import util.CodeSmellLogger;
import util.DataClumpUtil;
import util.Index;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class is an action that performs a full analysis of the project and saves the results in a JSON file.
 * The analysis detects data clumps between functions, classes, and interfaces and saves the elements and the
 * matching properties in the JSON file.
 */
public class FullAnalysis extends AnAction {

    /**
     * Called when the action is performed. It opens a file chooser dialog and lets the user choose a
     * directory to save the results then runs the full analysis.
     *
     * @param event the action event
     */
    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {

        // open file chooser dialog and let the user choose a directory
        FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
        descriptor.setTitle("Choose a Directory to Save the Results");
        descriptor.setDescription("The results will be saved in a JSON file in the selected directory");
        VirtualFile dir = FileChooser.chooseFile(descriptor, event.getProject(), null);

        // if no directory was selected, show an info message and cancel the operation
        if (dir == null) {
            Messages.showInfoMessage("No directory selected", "Info");
            return;
        }

        // ensure that the Index is built before running the analysis
        String resultPath = dir.getPath() + "/full_analysis_" + Objects.requireNonNull(event.getProject()).getName() + ".json";
        Index.addIndexBuildListener(() -> {
            run(resultPath);
        });

    }

    /**
     * Runs the full analysis and saves the results in a JSON file.
     * The analysis detects data clumps by running the DataClumpInspection
     * on all functions, classes, and interfaces in the project.
     * Depending on the Index to be built before running the analysis.
     *
     * @param resultPath the path to save the results to
     */
    public static void run(String resultPath) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {

            long startTime = 0;
            if (DiagnosticTool.DIAGNOSTIC_MODE) {
                startTime = System.nanoTime();
            }

            // check if the index is built before running the analysis
            if (!Index.isIndexBuilt()) {
                CodeSmellLogger.error("Index not built", new IllegalStateException());
                return;
            }

            CodeSmellLogger.info("Running full analysis");

            Project project = Index.getProject();
            PsiManager manager = PsiManager.getInstance(project);

            ApplicationManager.getApplication().runReadAction(() -> {

                int numberOfFiles = FileTypeIndex.getFiles(TypeScriptFileType.INSTANCE, GlobalSearchScope.projectScope(project)).size();
                int numberOfCurrentFile = 1;
                // iterate all TypeScript files in the project
                for (VirtualFile virtualFile : FileTypeIndex.getFiles(TypeScriptFileType.INSTANCE, GlobalSearchScope.projectScope(project))) {


                    List<DataClumpProblem> dataClumpProblems = new ArrayList<>();

                    CodeSmellLogger.info("Analyzing file: " + virtualFile.getName() + " (" + numberOfCurrentFile + "/" + numberOfFiles + ")");
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

                    // log DataClumpProblems for the file
                    if (!dataClumpProblems.isEmpty()) {
                        writeToFile(dataClumpProblems, virtualFile, resultPath);
                    }
                    numberOfCurrentFile++;
                }
            });

            if (DiagnosticTool.DIAGNOSTIC_MODE) {
                long endTime = System.nanoTime();
                long duration = (endTime - startTime) / 1000000;
                DiagnosticTool.addMeasurement(new DiagnosticTool.FullAnalysisMeasurement(project, duration));
            }
        });
    }

    /**
     * Collects the data clump problems from the ProblemsHolder and adds them to the list of data clump problems.
     * The data clump problems are extracted from the description of the ProblemDescriptors.
     *
     * @param problemsHolder    the ProblemsHolder containing the data clump problems
     * @param dataClumpProblems the list of data clump problems to add the new problems to
     */
    private static void collectProblems(ProblemsHolder problemsHolder, List<DataClumpProblem> dataClumpProblems) {

        // iterate all problems in the ProblemsHolder
        if (problemsHolder.hasResults()) {
            for (ProblemDescriptor problem : problemsHolder.getResults()) {
                String problemDescription = problem.getDescriptionTemplate();

                // extract the names and properties from the description
                Pattern pattern = Pattern.compile("Data Clump between (.*?) and (.*?)\\. Matching Properties \\[(.*?)\\]");
                Matcher matcher = pattern.matcher(problemDescription);

                if (matcher.find()) {
                    // extract the names
                    String name1 = matcher.group(1);
                    String name2 = matcher.group(2);

                    // extract the properties
                    String[] properties = matcher.group(3).split(",\\s*");
                    List<String> propertyList = Arrays.asList(properties);

                    // create a new DataClumpProblem and add it to the list if it is not already contained
                    DataClumpProblem dataClumpProblem = new DataClumpProblem(name1, name2, propertyList);
                    if (!dataClumpProblems.contains(dataClumpProblem)) {
                        dataClumpProblems.add(dataClumpProblem);
                    }
                }
            }
        }

    }

    /**
     * Writes the data clump problems to a JSON file.
     *
     * @param dataClumpProblems the list of data clump problems to write to the file
     * @param resultPath        the path to save the results to
     */
    private static void writeToFile(List<DataClumpProblem> dataClumpProblems, VirtualFile file, String resultPath) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        FilesProblem newProblem = new FilesProblem(file.getName(), dataClumpProblems);

        try (FileWriter writer = new FileWriter(resultPath, true)) {
            if (new File(resultPath).length() > 0) {
                writer.write(",");
            }
            gson.toJson(newProblem, writer);

        } catch (IOException e) {
            CodeSmellLogger.error("Could not write filepath: " + resultPath, e);
        }
    }

    /**
     * A record representing a data clump problem between two elements and the matching properties.
     */
    private record DataClumpProblem(String element1, String element2, List<String> dataClump) {

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            // a DataClumpProblem is equal to another if the elements and the data clump are equal
            // regardless of the order of the elements and the properties are equal
            DataClumpProblem other = (DataClumpProblem) obj;
            return ((element1.equals(other.element1) && element2.equals(other.element2)) ||
                    (element1.equals(other.element2) && element2.equals(other.element1)))
                    && dataClump.equals(other.dataClump);
        }
    }

    private record FilesProblem(String file, List<DataClumpProblem> dataClumpProblems) {
    }

}
