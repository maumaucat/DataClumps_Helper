package dataclump;

import Settings.DataClumpSettings;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.lang.javascript.TypeScriptFileType;
import com.intellij.lang.javascript.psi.ecma6.*;
import com.intellij.lang.javascript.psi.ecmal4.JSClass;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbService;
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
import util.*;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static evoluation.DiagnosticTool.getCurrentDateTime;


/**
 * This class is an action that performs a full analysis of the project and saves the results in a JSON file.
 * The analysis detects data clumps between functions, classes, and interfaces and saves the elements and the
 * matching properties in the JSON file.
 */
public class FullAnalysis extends AnAction {

    private static HashMap<String, ReportFormat.DataClumpTypeContext> dataClumps = new HashMap<>();
    private static int amountDataClumps = 0;
    private static int fieldsToFieldsDataClump = 0;
    private static int parametersToFieldsDataClump = 0;
    private static int parametersToParametersDataClump = 0;
    private static Set<PsiFile> filesWithDataClumps = new HashSet<>();
    private static Set<PsiElement> classesOrInterfacesWithDataClumps = new HashSet<>();
    private static Set<PsiElement> methodsWithDataClumps = new HashSet<>();


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

        DumbService.getInstance(project).runWhenSmart(() -> {
            // run the analysis in a background task, so the UI is not blocked
            ProgressManager.getInstance().run(new Task.Backgroundable(project, "Analyzing") {

                @Override
                public void run(@NotNull ProgressIndicator progressIndicator) {

                    long startTime = 0;
                    if (DiagnosticTool.DIAGNOSTIC_MODE) {
                        startTime = System.nanoTime();
                    }
                    ApplicationManager.getApplication().invokeAndWait(() -> {
                        PsiManager.getInstance(project).dropPsiCaches();
                    });


                    CodeSmellLogger.info("Running full analysis");

                    PsiManager manager = PsiManager.getInstance(project);

                    // Run the read action to collect TypeScript files
                    Collection<VirtualFile> typescriptFiles = new ArrayList<>();
                    ApplicationManager.getApplication().runReadAction(() -> {
                        typescriptFiles.addAll(FileTypeIndex.getFiles(TypeScriptFileType.INSTANCE, GlobalSearchScope.projectScope(project)));
                    });

                    CodeSmellLogger.info("Found " + typescriptFiles.size() + " TypeScript files");

                    // initialize variables to store the counting information for the report
                    dataClumps = new HashMap<>();
                    amountDataClumps = 0;
                    fieldsToFieldsDataClump = 0;
                    parametersToFieldsDataClump = 0;
                    parametersToParametersDataClump = 0;
                    int numberOfClassesOrInterfaces = 0;
                    int numberOfMethods = 0;
                    int numberOfDataFields = 0;
                    int numberOfMethodParameters = 0;

                    // initialize the count for the file iteration (so the progress can be logged)
                    int count = 1;

                    DataClumpDetection inspection = new DataClumpDetection();

                    // iterate all TypeScript files in the project
                    for (VirtualFile virtualFile : typescriptFiles) {
                        CodeSmellLogger.info("Analyzing file: " + virtualFile.getName() + "(" + count + "/" + typescriptFiles.size() + ")");

                        long startTimeFile = 0;
                        if (DiagnosticTool.DIAGNOSTIC_MODE) {
                            startTimeFile = System.nanoTime();
                        }

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
                            numberOfMethods++;
                            if (!PsiUtil.runReadActionWithResult(psiElement::isValid))
                                CodeSmellLogger.error("Invalid Function: " + psiElement, new Exception());
                            // Skip constructors
                            if (PsiUtil.runReadActionWithResult(psiElement::isConstructor)) continue;

                            // Detect data clumps if the number of parameters is greater than the required minimum
                            List<Parameter> parameters = Index.getFunctionsToParameters().get(psiElement);
                            numberOfMethodParameters += parameters != null ? parameters.size() : 0;
                            if (parameters != null && parameters.size() >= Objects.requireNonNull(DataClumpSettings.getInstance().getState()).minNumberOfProperties) {
                                inspection.detectDataClump(psiElement, new ProblemsHolder(InspectionManager.getInstance(project), psiFile, false), true);
                            }
                        }

                        // collect all classes in the file in a read action
                        Collection<PsiElement> allClasses = new ArrayList<>();
                        ApplicationManager.getApplication().runReadAction(() -> {
                            allClasses.addAll(List.of(PsiTreeUtil.collectElements(psiFile, element -> element instanceof TypeScriptClass)));
                        });

                        // iterate all classes in the file and collect the data clump problems
                        for (PsiElement psiElement : allClasses) {
                            numberOfClassesOrInterfaces++;
                            List<Classfield> classfields = Index.getClassesToClassFields().get((JSClass) psiElement);
                            numberOfDataFields += classfields != null ? classfields.size() : 0;
                            if (classfields != null && classfields.size() >= Objects.requireNonNull(DataClumpSettings.getInstance().getState()).minNumberOfProperties) {
                                inspection.detectDataClump(psiElement, new ProblemsHolder(InspectionManager.getInstance(project), psiFile, false), true);
                            }

                        }

                        // collect all interfaces in the file in a read action
                        Collection<PsiElement> allInterfaces = new ArrayList<>();
                        ApplicationManager.getApplication().runReadAction(() -> {
                            allInterfaces.addAll(List.of(PsiTreeUtil.collectElements(psiFile, element -> element instanceof TypeScriptInterface)));
                        });

                        // iterate all interfaces in the file and collect the data clump problems
                        for (PsiElement psiElement : allInterfaces) {
                            numberOfClassesOrInterfaces++;
                            List<Classfield> classfields = Index.getClassesToClassFields().get((JSClass) psiElement);
                            numberOfDataFields += classfields != null ? classfields.size() : 0;
                            if (classfields != null && classfields.size() >= Objects.requireNonNull(DataClumpSettings.getInstance().getState()).minNumberOfProperties) {
                                inspection.detectDataClump(psiElement, new ProblemsHolder(InspectionManager.getInstance(project), psiFile, false), true);
                            }
                        }

                        count++;

                        if (DiagnosticTool.DIAGNOSTIC_MODE) {
                            long endTimeFile = System.nanoTime();
                            long durationFile = (endTimeFile - startTimeFile);
                            DiagnosticTool.addMeasurement(new DiagnosticTool.FullAnalysisFileMeasurement(virtualFile.getName(), durationFile));
                        }
                    }

                    // information about the settings
                    HashMap<String, String> options = new HashMap<>();
                    options.put("DIAGNOSTIC_MODE", String.valueOf(DiagnosticTool.DIAGNOSTIC_MODE));
                    options.put("MIN_NUMBER_OF_PROPERTIES", String.valueOf(Objects.requireNonNull(DataClumpSettings.getInstance().getState()).minNumberOfProperties));
                    options.put("INCLUDE_MODIFIERS_IN_DETECTION", String.valueOf(Objects.requireNonNull(DataClumpSettings.getInstance().getState()).includeModifiersInDetection));

                    // information about the detector (this Plugin)
                    ReportFormat.DataClumpsDetectorContext detector = new ReportFormat.DataClumpsDetectorContext("Data Clump Helper", null, Objects.requireNonNull(PluginManagerCore.getPlugin(PluginId.getId("de.marlena.data.clump.helper"))).getVersion(), options);

                    // summary information for the report (amount of data clumps, files, classes, methods, and fields etc)
                    ReportFormat.ReportSummary summary = new ReportFormat.ReportSummary(amountDataClumps, filesWithDataClumps.size(), classesOrInterfacesWithDataClumps.size(), methodsWithDataClumps.size(), fieldsToFieldsDataClump, parametersToFieldsDataClump, parametersToParametersDataClump, "");

                    // information about the project
                    ReportFormat.ProjectInfo projectInfo = new ReportFormat.ProjectInfo(null, project.getName(), null, null, null, null, typescriptFiles.size(), numberOfClassesOrInterfaces, numberOfMethods, numberOfDataFields, numberOfMethodParameters, "");

                    // create the context for the report
                    ReportFormat.DataClumpsTypeContext context = new ReportFormat.DataClumpsTypeContext("1.0", detector, dataClumps, getCurrentDateTime(), "TypeScript", summary, projectInfo);

                    // write the context to the file
                    writeToFile(context, resultPath);
                    CodeSmellLogger.info("Full analysis completed");

                    if (DiagnosticTool.DIAGNOSTIC_MODE) {
                        long endTime = System.nanoTime();
                        long duration = (endTime - startTime);
                        DiagnosticTool.addMeasurement(new DiagnosticTool.FullAnalysisMeasurement(project, duration));
                    }
                }
            });

        });

    }

    /**
     * Reports a data clump between two elements and the variables that are clumped. The data clump is stored in the
     * dataClumps map and the elements are stored in the corresponding sets. This method is called by the inspection if it
     * is invoked by the full analysis.
     *
     * @param fromElement the element from where the data clump is detected
     * @param toElement   the element with whom the data clump is detected
     * @param variables   the variables that are clumped (fields or parameters)
     */
    public static void report(PsiElement fromElement, PsiElement toElement, List<Property> variables) {
        amountDataClumps++;

        if (fromElement instanceof JSClass) {
            classesOrInterfacesWithDataClumps.add(fromElement);
        } else {
            methodsWithDataClumps.add(fromElement);
        }

        filesWithDataClumps.add(PsiUtil.runReadActionWithResult(fromElement::getContainingFile));
        filesWithDataClumps.add(PsiUtil.runReadActionWithResult(toElement::getContainingFile));

        ReportFormat.DataClumpTypeContext dataClumpTypeContext = ReportFormat.getDataClumpsTypeContext(fromElement, toElement, variables);

        if (dataClumpTypeContext.dataClumpType().equals("parameters_to_parameters")) {
            parametersToParametersDataClump++;
        } else if (dataClumpTypeContext.dataClumpType().equals("parameters_to_fields")) {
            parametersToFieldsDataClump++;
        } else {
            fieldsToFieldsDataClump++;
        }

        FullAnalysis.dataClumps.put(dataClumpTypeContext.key(), dataClumpTypeContext);
    }

    /**
     * Writes the context to a JSON file.
     *
     * @param context    the context to write to the file
     * @param resultPath the path to write the file to
     */
    private static void writeToFile(ReportFormat.DataClumpsTypeContext context, String resultPath) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            mapper.writeValue(new File(resultPath), context);
        } catch (IOException e) {
            CodeSmellLogger.error("Error writing to file", e);
        }
    }
}
