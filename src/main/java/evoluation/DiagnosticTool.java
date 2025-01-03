package evoluation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.google.protobuf.DescriptorProtos;
import com.google.type.DateTime;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import util.CodeSmellLogger;
import util.Parameter;
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

public class DiagnosticTool {

    public static boolean DIAGNOSTIC_MODE = false;
    private static String FILE_PATH;

    public static void init(Project project) {
        DIAGNOSTIC_MODE = true;
        FILE_PATH = "\\C:\\Users\\ms\\Documents\\Uni\\Bachlorarbeit\\Messungen\\" + "measurements_" + project.getName() + "_" + getCurrentDateTime() + ".json";
        CodeSmellLogger.info("Diagnostic Tool initialized");
    }

    public static void addMeasurement(Measurement measurement) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        List<Measurement> measurements;

        // read existing measurements
        File file = new File(FILE_PATH);
        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                Type listType = new TypeToken<List<Measurement>>() {
                }.getType();
                measurements = gson.fromJson(reader, listType);
            } catch (IOException e) {
                CodeSmellLogger.error("Could not read filepath: " + FILE_PATH, e);
                return;
            }
        } else {
            measurements = new ArrayList<>();
        }

        // add new measurement
        measurements.add(measurement);

        // write measurements back to file
        try (FileWriter writer = new FileWriter(FILE_PATH)) {
            gson.toJson(measurements, writer);
            CodeSmellLogger.info("Measurement added: " + measurement);
        } catch (IOException e) {
            CodeSmellLogger.error("Could not write filepath: " + FILE_PATH, e);
        }

    }

    private static String getCurrentDateTime() {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
        return now.format(formatter);
    }

    public static class Measurement {

        private final String timeOfMeasurement;
        private final String element1;
        private final String element2;
        private List<String> dataClump;
        private final double durationInMilliSeconds;


        public Measurement(PsiElement element1, PsiElement element2, List<Property> dataClump, long durationNanoSeconds) {


            this.timeOfMeasurement = getCurrentDateTime();

            this.element1 = PsiUtil.getQualifiedName(element1);
            this.element2 = PsiUtil.getQualifiedName(element2);

            this.dataClump = new ArrayList<>();
            for (Property property : dataClump) {
                this.dataClump.add(property.toString());
            }

            this.durationInMilliSeconds = durationNanoSeconds / 1000000.0;
        }
    }

}
