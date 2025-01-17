package evoluation;

import com.google.gson.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import dataclump.FullAnalysis;
import util.CodeSmellLogger;
import util.Index;
import util.Property;
import util.PsiUtil;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * This class is used to store time measurements of the plugin in a JSON file.
 */
public class DiagnosticTool {

    /**
     * If this flag indicates whether the time measurements are currently being recorded.
     */
    public static boolean DIAGNOSTIC_MODE = false;
    /**
     * The path to the file where the measurements are stored.
     */
    private static String FILE_PATH_DETECTION;
    private static String FILE_PATH_FULL_ANALYSIS;
    private static String FILE_PATH_INDEX;


    /**
     * Initializes the DiagnosticTool with the given project.
     *
     * @param project the project to be analyzed
     */
    public static void init(Project project) {
        String resultPath = System.getProperty("dataclump.resultpath");

        if (resultPath == null || resultPath.isEmpty()) {
            CodeSmellLogger.error("Invalid result path: " + resultPath, new IllegalArgumentException());
            return;
        }
        DIAGNOSTIC_MODE = true;
        FILE_PATH_DETECTION = resultPath + "\\detectionMeasurements_" + project.getName() + "_" + getCurrentDateTime() + ".json";
        FILE_PATH_FULL_ANALYSIS = resultPath + "\\fullAnalysisMeasurements_" + project.getName() + "_" + getCurrentDateTime() + ".json";
        FILE_PATH_INDEX = resultPath + "\\indexMeasurements_" + project.getName() + "_" + getCurrentDateTime() + ".json";

        Index.addIndexBuildListener( () -> FullAnalysis.run(resultPath + "\\fullAnalysis_" + project.getName() + "_" + getCurrentDateTime() + ".json"));
    }

    public static void addMeasurement(DetectionMeasurement newMeasurement) {
        writeToFile(FILE_PATH_DETECTION, newMeasurement);
    }

    public static void addMeasurement(FullAnalysisMeasurement newMeasurement) {
        writeToFile(FILE_PATH_FULL_ANALYSIS, newMeasurement);
    }

    public static void addMeasurement(FullAnalysisFileMeasurement newMeasurement) {
        writeToFile(FILE_PATH_FULL_ANALYSIS, newMeasurement);
    }

    public static void addMeasurement(IndexMeasurement newMeasurement) {
        writeToFile(FILE_PATH_INDEX, newMeasurement);
    }


    public static <T> void writeToFile(String path, T newMeasurement) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        File file = new File(path);
        try (FileWriter writer = new FileWriter(path, true)) {
            if (file.exists() && file.length() > 0) {
                writer.write(",");
            }
            gson.toJson(newMeasurement, writer);

        } catch (IOException e) {
            CodeSmellLogger.error("Could not write to file: " + path, e);
        }
    }

    /**
     * Returns the current date and time in the format "yyyy-MM-dd_HH-mm-ss".
     * (Can be used in the file name)
     *
     * @return the current date and time
     */
    public static String getCurrentDateTime() {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
        return now.format(formatter);
    }

    public static class DetectionMeasurement {
        String measurementType = "Detection";
        String project;
        String timeOfMeasurement;
        double durationInMilliSeconds;
        String element1;
        String element2;
        List<String> dataClump;

        public DetectionMeasurement(Project project, PsiElement element1, PsiElement element2, List<Property> dataClump, long durationNanoSeconds) {
            this.project = project.getName();
            this.timeOfMeasurement = getCurrentDateTime();
            this.durationInMilliSeconds = durationNanoSeconds / 1000000.0;
            this.element1 = PsiUtil.getQualifiedName(element1);
            this.element2 = PsiUtil.getQualifiedName(element2);
            this.dataClump = new ArrayList<>();
            for (Property property : dataClump) {
                this.dataClump.add(property.toString());
            }
        }
    }

    public static class FullAnalysisMeasurement {
        String measurementType = "FullAnalysis";
        String project;
        String timeOfMeasurement;
        double durationInMilliSeconds;

        public FullAnalysisMeasurement(Project project, long durationNanoSeconds) {
            this.project = project.getName();
            this.timeOfMeasurement = getCurrentDateTime();
            this.durationInMilliSeconds = durationNanoSeconds / 1000000.0;
        }

    }

    public static class FullAnalysisFileMeasurement {
        String measurementType = "FullAnalysisFile";
        String file;
        String timeOfMeasurement;
        double durationInMilliSeconds;

        public FullAnalysisFileMeasurement(String file, long durationNanoSeconds) {
            this.file = file;
            this.timeOfMeasurement = getCurrentDateTime();
            this.durationInMilliSeconds = durationNanoSeconds / 1000000.0;
        }

    }

    public static class IndexMeasurement {
        String measurementType = "Index";
        String project;
        String timeOfMeasurement;
        double durationInMilliSeconds;

        public IndexMeasurement(Project project, long durationNanoSeconds) {
            this.project = project.getName();
            this.timeOfMeasurement = getCurrentDateTime();
            this.durationInMilliSeconds = durationNanoSeconds / 1000000.0;
        }
    }
}
