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
import java.io.FileReader;
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
     * The paths to the files where the measurements are stored.
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

    /**
     * Adds a new measurement to the JSON file.
     * @param newMeasurement the new measurement to be added
     */
    public static void addMeasurement(DetectionMeasurement newMeasurement) {
        writeToFile(FILE_PATH_DETECTION, newMeasurement);
    }

    /**
     * Adds a new measurement to the JSON file.
     * @param newMeasurement the new measurement to be added
     */
    public static void addMeasurement(FullAnalysisMeasurement newMeasurement) {
        writeToFile(FILE_PATH_FULL_ANALYSIS, newMeasurement);
    }

    /**
     * Adds a new measurement to the JSON file.
     * @param newMeasurement the new measurement to be added
     */
    public static void addMeasurement(FullAnalysisFileMeasurement newMeasurement) {
        writeToFile(FILE_PATH_FULL_ANALYSIS, newMeasurement);
    }

    /**
     * Adds a new measurement to the JSON file.
     * @param newMeasurement the new measurement to be added
     */
    public static void addMeasurement(IndexMeasurement newMeasurement) {
        writeToFile(FILE_PATH_INDEX, newMeasurement);
    }


    /**
     * Writes the given measurement to the file at the given path.
     *
     * @param path the path to the file
     * @param newMeasurement the measurement to be written to the file
     * @param <T> the type of the mesurement
     */
    public static <T> void writeToFile(String path, T newMeasurement) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        File file = new File(path);
        JsonArray jsonArray;

        try {
            // if the file already exists, read the JSON-Array from it
            if (file.exists() && file.length() > 0) {
                try (FileReader reader = new FileReader(file)) {
                    JsonElement jsonElement = JsonParser.parseReader(reader);
                    // if the file is not an array, create a new one and add the existing element
                    if (jsonElement.isJsonArray()) {
                        jsonArray = jsonElement.getAsJsonArray();
                    } else {
                        jsonArray = new JsonArray();
                        jsonArray.add(jsonElement);
                    }
                }
            } else { // if the file does not exist, create a new JSON-Array
                jsonArray = new JsonArray();
            }

            // add the new measurement to the JSON-Array
            jsonArray.add(gson.toJsonTree(newMeasurement));

            // write the JSON-Array back to the file
            try (FileWriter writer = new FileWriter(file)) {
                gson.toJson(jsonArray, writer);
            }
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

    /**
     * Represents a detection measurement. (Time needed for the detection of a data clump)
     */
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
}
