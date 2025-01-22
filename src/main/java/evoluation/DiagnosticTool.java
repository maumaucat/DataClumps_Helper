package evoluation;

import com.google.gson.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import dataclump.FullAnalysis;
import org.jetbrains.annotations.NotNull;
import util.*;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
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
     * The paths to the files where the measurements are stored.
     */
    private static String FILE_PATH_DETECTION;
    private static String FILE_PATH_FULL_ANALYSIS;
    private static String FILE_PATH_INDEX;
    private static String FILE_PATH_REFACTORING;


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
        FILE_PATH_REFACTORING = resultPath + "\\refactoringMeasurements_" + project.getName() + "_" + getCurrentDateTime() + ".json";

        Index.addIndexBuildListener(() -> FullAnalysis.run(resultPath + "\\fullAnalysis_" + project.getName() + "_" + getCurrentDateTime() + ".json"));
    }

    /**
     * Adds a new measurement to the JSON file.
     *
     * @param newMeasurement the new measurement to be added
     */
    public static void addMeasurement(DetectionMeasurement newMeasurement) {
        writeToFile(FILE_PATH_DETECTION, newMeasurement);
    }

    /**
     * Adds a new measurement to the JSON file.
     *
     * @param newMeasurement the new measurement to be added
     */
    public static void addMeasurement(FullAnalysisMeasurement newMeasurement) {
        writeToFile(FILE_PATH_FULL_ANALYSIS, newMeasurement);
    }

    /**
     * Adds a new measurement to the JSON file.
     *
     * @param newMeasurement the new measurement to be added
     */
    public static void addMeasurement(FullAnalysisFileMeasurement newMeasurement) {
        writeToFile(FILE_PATH_FULL_ANALYSIS, newMeasurement);
    }

    /**
     * Adds a new measurement to the JSON file.
     *
     * @param newMeasurement the new measurement to be added
     */
    public static void addMeasurement(IndexMeasurement newMeasurement) {
        writeToFile(FILE_PATH_INDEX, newMeasurement);
    }

    /**
     * Adds a new measurement to the JSON file.
     *
     * @param newMeasurement the new measurement to be added
     */
    public static void addMeasurement(RefactoringMeasurement newMeasurement) {
        writeToFile(FILE_PATH_REFACTORING, newMeasurement);
    }

    /**
     * Writes the given measurement to the file at the given path.
     *
     * @param path           the path to the file
     * @param newMeasurement the measurement to be written to the file
     * @param <T>            the type of the mesurement
     */
    public synchronized static <T> void writeToFile(String path, @NotNull T newMeasurement) {

        List<Object> data = null;
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        // Read the existing data from the file if it exists
        File file = new File(path);
        if (file.exists() && file.length() > 0) {
            try (FileReader reader = new FileReader(file)) {
                Type listType = new TypeToken<List<Object>>() {
                }.getType();
                data = gson.fromJson(reader, listType);
            } catch (IOException e) {
                CodeSmellLogger.error("Error reading file: " + path, e);
            }
        }

        // Create a new list if the file does not exist or is empty
        if (data == null) {
            data = new ArrayList<>();
        }

        // Add the new measurement to the list
        data.add(newMeasurement);

        // Write the list to the file
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(data, writer);
        } catch (IOException e) {
            CodeSmellLogger.error("Error writing file: " + path, e);
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

    /**
     * Represents a detection measurement. (Time needed for the detection of a data clump)
     */
    public static class DetectionMeasurement {
        String measurementType = "Detection";
        String project;
        String timeOfMeasurement;
        double durationInMilliSeconds;
        ReportFormat.DataClumpTypeContext dataClump;


        public DetectionMeasurement(Project project, long durationNanoSeconds, ReportFormat.DataClumpTypeContext dataClump) {
            this.project = project.getName();
            this.timeOfMeasurement = getCurrentDateTime();
            this.durationInMilliSeconds = durationNanoSeconds / 1000000.0;
            this.dataClump = dataClump;
        }
    }

    /**
     * Represents a full analysis measurement. (Time needed for the full analysis of a project)
     */
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

    /**
     * Represents a full analysis file measurement. (Time needed for the full analysis of a single file)
     */
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

    /**
     * Represents an index measurement. (Time needed for the index build)
     */
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

    /**
     * Represents a refactoring measurement. (Time needed for the refactoring of a data clump)
     */
    public static class RefactoringMeasurement {
        String measurementType = "Refactoring";
        String project;
        String timeOfMeasurement;
        double durationInMilliSeconds;
        ReportFormat.DataClumpTypeContext dataClump;

        public RefactoringMeasurement(Project project, long durationNanoSeconds, ReportFormat.DataClumpTypeContext dataClump) {
            this.project = project.getName();
            this.timeOfMeasurement = getCurrentDateTime();
            this.durationInMilliSeconds = durationNanoSeconds / 1000000.0;
            this.dataClump = dataClump;
        }

    }
}
