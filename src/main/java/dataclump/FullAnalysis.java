package dataclump;

import Settings.DataClumpSettings;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
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
import util.PsiUtil;

import java.io.File;
import java.io.IOException;
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
        Index.addIndexBuildListener(() -> run(resultPath));

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

        // check if the index is built before running the analysis
        if (!Index.isIndexBuilt()) {
            CodeSmellLogger.error("Index not built", new IllegalStateException());
            return;
        }

        Project project = Index.getProject();

        // run the analysis in a background task, so the UI is not blocked
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Analyzing") {

            @Override
            public void run(@NotNull ProgressIndicator progressIndicator) {

                long startTime = 0;
                if (DiagnosticTool.DIAGNOSTIC_MODE) {
                    startTime = System.nanoTime();
                }

                CodeSmellLogger.info("Running full analysis");

                PsiManager manager = PsiManager.getInstance(project);

                // Run the read action to collect TypeScript files
                Collection<VirtualFile> typescriptFiles = new ArrayList<>();
                ApplicationManager.getApplication().runReadAction(() -> {
                    typescriptFiles.addAll(FileTypeIndex.getFiles(TypeScriptFileType.INSTANCE, GlobalSearchScope.projectScope(project)));
                });

                int numberOfFiles = typescriptFiles.size();
                int count = 1;

                // all data clump problems
                List<DataClumpProblem> dataClumpProblems = new ArrayList<>();

                // iterate all TypeScript files in the project
                for (VirtualFile virtualFile : typescriptFiles) {

                    long startTimeFile = 0;
                    if (DiagnosticTool.DIAGNOSTIC_MODE) {
                        startTimeFile = System.nanoTime();
                    }

                    CodeSmellLogger.info("Analyzing file: " + virtualFile.getName() + " (" + count + "/" + numberOfFiles + ")"
                            + " - " + virtualFile.getLength());

                    // read the psiFile in a read action
                    final PsiFile[] psiFileWrap = new PsiFile[1]; // Using an array to make it effectively final
                    ApplicationManager.getApplication().runReadAction(() -> {
                        psiFileWrap[0] = manager.findFile(virtualFile);
                    });
                    PsiFile psiFile = psiFileWrap[0];

                    // collect all functions in the file in a read action
                    Collection<TypeScriptFunction> functions = new ArrayList<>();
                    ApplicationManager.getApplication().runReadAction(() -> {
                        functions.addAll(PsiTreeUtil.findChildrenOfType(psiFile, TypeScriptFunction.class));
                    });

                    // iterate all functions in the file and collect the data clump problems
                    for (TypeScriptFunction psiElement : functions) {
                        ProblemsHolder problemsHolder = DataClumpUtil.invokeInspection(psiElement);
                        assert problemsHolder != null;
                        collectProblems(problemsHolder, dataClumpProblems, virtualFile);
                    }

                    // collect all classes in the file in a read action
                    Collection<PsiElement> allClasses = new ArrayList<>();
                    ApplicationManager.getApplication().runReadAction(() -> {
                        allClasses.addAll(List.of(PsiTreeUtil.collectElements(psiFile, element ->
                                element instanceof TypeScriptClass
                        )));
                    });

                    // iterate all classes in the file and collect the data clump problems
                    for (PsiElement psiElement : allClasses) {
                        ProblemsHolder problemsHolder = DataClumpUtil.invokeInspection(psiElement);
                        assert problemsHolder != null;
                        collectProblems(problemsHolder, dataClumpProblems, virtualFile);
                    }

                    // collect all interfaces in the file in a read action
                    Collection<PsiElement> allInterfaces = new ArrayList<>();
                    ApplicationManager.getApplication().runReadAction(() -> {
                        allInterfaces.addAll(List.of(PsiTreeUtil.collectElements(psiFile, element ->
                                element instanceof TypeScriptInterface
                        )));
                    });

                    // iterate all interfaces in the file and collect the data clump problems
                    for (PsiElement psiElement : allInterfaces) {
                        ProblemsHolder problemsHolder = DataClumpUtil.invokeInspection(psiElement);
                        assert problemsHolder != null;
                        collectProblems(problemsHolder, dataClumpProblems, virtualFile);
                    }

                    count++;
                    if (DiagnosticTool.DIAGNOSTIC_MODE) {
                        long endTimeFile = System.nanoTime();
                        long durationFile = (endTimeFile - startTimeFile);
                        DiagnosticTool.addMeasurement(new DiagnosticTool.FullAnalysisFileMeasurement(virtualFile.getName(), durationFile));
                    }
                }

                // general info about the analysis
                HashMap<String, String> settings = new HashMap<>();
                settings.put("DIAGNOSTIC_MODE", String.valueOf(DiagnosticTool.DIAGNOSTIC_MODE));
                settings.put("MIN_NUMBER_OF_PROPERTIES", String.valueOf(Objects.requireNonNull(DataClumpSettings.getInstance().getState()).minNumberOfProperties));
                settings.put("INCLUDE_MODIFIERS_IN_DETECTION", String.valueOf(Objects.requireNonNull(DataClumpSettings.getInstance().getState()).includeModifiersInDetection));


                // write all data clump problems and the general information to a JSON file
                writeToFile(dataClumpProblems,
                        new GeneralInformation(
                                project.getName(),
                                DiagnosticTool.getCurrentDateTime(),
                                settings,
                                numberOfFiles,
                                dataClumpProblems.size())
                        , resultPath);

                if (DiagnosticTool.DIAGNOSTIC_MODE) {
                    long endTime = System.nanoTime();
                    long duration = (endTime - startTime);
                    DiagnosticTool.addMeasurement(new DiagnosticTool.FullAnalysisMeasurement(project, duration));
                }
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
    private static void collectProblems(ProblemsHolder problemsHolder, List<DataClumpProblem> dataClumpProblems, VirtualFile file) {

        // iterate all problems in the ProblemsHolder
        if (problemsHolder.hasResults()) {
            for (ProblemDescriptor problem : problemsHolder.getResults()) {
                String problemDescription = problem.getDescriptionTemplate();

                // extract the names and properties from the description
                Pattern pattern = Pattern.compile("Data Clump between (.*?) (.*?) and (.*?) (.*?)\\. Matching Properties \\[(.*?)]");
                Matcher matcher = pattern.matcher(problemDescription);

                if (matcher.find()) {
                    String type1 = matcher.group(1);
                    String name1 = matcher.group(2);
                    String type2 = matcher.group(3);
                    String name2 = matcher.group(4);
                    String[] properties = matcher.group(5).split(",\\s*");

                    List<String> propertyList = Arrays.asList(properties);

                    // Create a new DataClumpProblem and add it to the list if it is not already contained

                    HashSet<String> files = new HashSet<>();
                    files.add(PsiUtil.runReadActionWithResult(file::getName));
                    int lineNumber = PsiUtil.runReadActionWithResult(problem::getLineNumber) + 1;
                    HashSet<Integer> lines = new HashSet<>();
                    lines.add(lineNumber);
                    DataClumpProblem dataClumpProblem = new DataClumpProblem(files, lines, name1, type1, name2, type2, propertyList);

                    if (!dataClumpProblems.contains(dataClumpProblem)) {
                        dataClumpProblems.add(dataClumpProblem);
                    } else {
                        int index = dataClumpProblems.indexOf(dataClumpProblem);
                        dataClumpProblems.get(index).addFile(file.getName());
                        dataClumpProblems.get(index).addLine(lineNumber);
                    }

                }
            }
        }

    }

    /**
     * Writes the data clump problems and the general information to a JSON file.
     * The JSON file is saved at the given result path.
     *
     * @param problems   the list of data clump problems found in the analysis
     * @param info       the general information of the analysis
     * @param resultPath the path to save the results to
     */
    private static void writeToFile(List<DataClumpProblem> problems, GeneralInformation info, String resultPath) {
        Map<String, Object> jsonData = new HashMap<>();
        jsonData.put("generalInfo", info);
        jsonData.put("dataClumpProblems", problems);

        ObjectMapper objectMapper = new ObjectMapper();

        try {
            objectMapper.writeValue(new File(resultPath), jsonData);
        } catch (IOException e) {
            CodeSmellLogger.error("Could not write filepath: " + resultPath, e);
        }
    }

    /**
     * A record representing a data clump problem between two elements and the matching properties.
     */
    private record DataClumpProblem(Set<String> files,
                                    Set<Integer> lines,
                                    String element1,
                                    String type1,
                                    String element2,
                                    String type2,
                                    List<String> dataClump) {

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

        public void addFile(String file) {
            files.add(file);
        }

        public void addLine(int line) {
            lines.add(line);
        }
    }

    /**
     * A record representing the general information of the full analysis.
     */
    private record GeneralInformation(String project, String timeOfMeasurement, HashMap<String, String> settings,
                                      int numberOfFiles, int numberOfDataClumps) {
    }


}
