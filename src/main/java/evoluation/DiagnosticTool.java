package evoluation;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import util.CodeSmellLogger;
import util.Property;
import util.PsiUtil;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
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
    private static String FILE_PATH;


    /**
     * Initializes the DiagnosticTool with the given project.
     *
     * @param project the project to be analyzed
     */
    public static void init(Project project) {
        DIAGNOSTIC_MODE = true;
        FILE_PATH = "\\C:\\Users\\ms\\Documents\\Uni\\Bachlorarbeit\\Messungen\\" + "measurements_" + project.getName() + "_" + getCurrentDateTime() + ".json";
    }

    /**
     * Adds a new measurement to the file.
     *
     * @param newMeasurement the measurement to be added
     */
    public static void addMeasurement(Measurement newMeasurement) {
        List<Measurement> measurements;

        Gson gson = new GsonBuilder().registerTypeAdapter(Measurement.class, new MeasurementAdapter()).setPrettyPrinting().create();
        File file = new File(FILE_PATH);

        // if the file exists, read the existing measurements
        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                Type listType = new TypeToken<List<Measurement>>() {
                }.getType();
                measurements = gson.fromJson(reader, listType);
            } catch (IOException e) {
                CodeSmellLogger.error("Could not read file: " + FILE_PATH, e);
                return;
            }
        } else {
            // otherwise, create a new list
            measurements = new ArrayList<>();
        }

        // add the new measurement to the list
        measurements.add(newMeasurement);

        // write the measurements back to the file
        try (FileWriter writer = new FileWriter(FILE_PATH)) {
            gson.toJson(measurements, writer);
        } catch (IOException e) {
            CodeSmellLogger.error("Could not write file: " + FILE_PATH, e);
        }

    }

    /**
     * Returns the current date and time in the format "yyyy-MM-dd_HH-mm-ss".
     * (Can be used in the file name)
     *
     * @return the current date and time
     */
    private static String getCurrentDateTime() {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
        return now.format(formatter);
    }

    /**
     * Represents a measurement of the plugin.
     */
    public static abstract class Measurement {
        protected final String name;
        protected final String project;
        protected final String timeOfMeasurement;
        protected final double durationInMilliSeconds;

        public Measurement(String name, String projectName, String timeOfMeasurement, double durationInMilliSeconds) {
            this.name = name;
            this.project = projectName;
            this.timeOfMeasurement = timeOfMeasurement;
            this.durationInMilliSeconds = durationInMilliSeconds;
        }
    }

    /**
     * Represents a measurement of the inspection for detection of data clumps.
     */
    public static class InspectionDetectionMeasurement extends Measurement {
        private final String element1;
        private final String element2;
        private final List<String> dataClump;

        public InspectionDetectionMeasurement(Project project, PsiElement element1, PsiElement element2, List<Property> dataClump, long durationNanoSeconds) {
            super("InspectionDetection", project.getName(), getCurrentDateTime(), durationNanoSeconds / 1000000.0);
            this.element1 = PsiUtil.getQualifiedName(element1);
            this.element2 = PsiUtil.getQualifiedName(element2);
            this.dataClump = new ArrayList<>();
            for (Property property : dataClump) {
                this.dataClump.add(property.toString());
            }
        }

        public InspectionDetectionMeasurement(String project, String timeOfMeasurement, String element1, String element2, List<String> dataClump, long durationNanoSeconds) {
            super("InspectionDetection", project, timeOfMeasurement, durationNanoSeconds / 1000000.0);
            this.element1 = element1;
            this.element2 = element2;
            this.dataClump = dataClump;
        }
    }

    /**
     * Represents a measurement of the full analysis of the project.
     */
    public static class FullAnalysisMeasurement extends Measurement {

        public FullAnalysisMeasurement(Project project, long durationNanoSeconds) {
            super("FullAnalysis", project.getName(), getCurrentDateTime(), durationNanoSeconds / 1000000.0);
        }

        public FullAnalysisMeasurement(String project, String timeOfMeasurement, long durationNanoSeconds) {
            super("FullAnalysis", project, timeOfMeasurement, durationNanoSeconds / 1000000.0);
        }
    }

    /**
     * Custom adapter for the Measurement class to serialize and deserialize it correctly.
     */
    public static class MeasurementAdapter implements JsonSerializer<Measurement>, JsonDeserializer<Measurement> {

        @Override
        public JsonElement serialize(Measurement src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("project", src.project);
            jsonObject.addProperty("timeOfMeasurement", src.timeOfMeasurement);
            jsonObject.addProperty("durationInMilliSeconds", src.durationInMilliSeconds);

            if (src instanceof InspectionDetectionMeasurement inspection) {
                jsonObject.addProperty("element1", inspection.element1);
                jsonObject.addProperty("element2", inspection.element2);
                jsonObject.add("dataClump", context.serialize(inspection.dataClump));
            }

            return jsonObject;
        }

        @Override
        public Measurement deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            CodeSmellLogger.info("Deserializing measurement: " + json.toString());

            JsonObject jsonObject = json.getAsJsonObject();
            String project = jsonObject.get("project").getAsString();
            String timeOfMeasurement = jsonObject.get("timeOfMeasurement").getAsString();
            double durationInMilliSeconds = jsonObject.get("durationInMilliSeconds").getAsDouble();

            if (jsonObject.has("element1")) {  // Inspecting if it's an InspectionDetectionMeasurement
                String element1 = jsonObject.get("element1").getAsString();
                String element2 = jsonObject.get("element2").getAsString();
                List<String> dataClump = context.deserialize(jsonObject.get("dataClump"), List.class);
                return new InspectionDetectionMeasurement(project, timeOfMeasurement, element1, element2, dataClump, (long) durationInMilliSeconds);
            } else {  // Otherwise, it's a FullAnalysisMeasurement
                double excludingWriteOps = jsonObject.get("durationInMilliSecondsExcludingWriteOperations").getAsDouble();
                return new FullAnalysisMeasurement(project, timeOfMeasurement, (long) durationInMilliSeconds);
            }
        }
    }


}
