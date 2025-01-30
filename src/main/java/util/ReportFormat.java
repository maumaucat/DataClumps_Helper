package util;

import com.google.gson.annotations.SerializedName;
import com.google.rpc.Code;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptField;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptFunction;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptParameter;
import com.intellij.lang.javascript.psi.ecmal4.JSClass;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class is used to create the JSON format for the data clumps report.
 * It is using the format defined by <a href="https://github.com/FireboltCasters/data-clumps-type-context?tab=readme-ov-file">...</a>
 */
public class ReportFormat {

    /**
     * Creates the context for the data clumps report
     *
     * @param fromElement the element from which the data clump originates
     * @param toElement   the element to which the data clump leads
     * @param variables   the variables that are part of the data clump
     * @return the context for the data clumps report
     */
    public static DataClumpTypeContext getDataClumpsTypeContext(PsiElement fromElement, PsiElement toElement, List<Property> variables) {

        // get the containing files of the two elements and the project for the PsiDocumentManager later
        PsiFile fromContainingFile = PsiUtil.runReadActionWithResult(fromElement::getContainingFile);
        PsiFile toContainingFile = PsiUtil.runReadActionWithResult(toElement::getContainingFile);
        Project project = PsiUtil.runReadActionWithResult(fromElement::getProject);

        // for each variable create a context for the from and to file and add it to the dataClumpsVariables map
        Map<String, ReportFormat.DataClumpsVariableFromContext> dataClumpsVariables = new HashMap<>();
        for (Property property : variables) {

            PsiElement fromVariable;
            PsiElement toVariable;

            // get the PsiElement in the two files that represent the property
            if (fromElement instanceof JSClass) {
                fromVariable = PsiUtil.getPsiField((JSClass) fromElement, (Classfield) property);
            } else {
                fromVariable = PsiUtil.getPsiParameter((TypeScriptFunction) fromElement, property);
            }
            if (toElement instanceof JSClass) {
                 toVariable = PsiUtil.getPsiField((JSClass) toElement, (Classfield) property);
            } else {
                toVariable = PsiUtil.getPsiParameter((TypeScriptFunction) toElement, property);
            }
            assert fromVariable != null;
            assert toVariable != null;

            // get the document of the two files and use it to get the position of the PsiElements
            PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);
            Document fromDocument = PsiUtil.runReadActionWithResult(() -> psiDocumentManager.getDocument(fromContainingFile));
            Document toDocument = PsiUtil.runReadActionWithResult(() -> psiDocumentManager.getDocument(toContainingFile));
            assert fromDocument != null;
            assert toDocument != null;


            final int formStartLine = PsiUtil.runReadActionWithResult(() -> fromDocument.getLineNumber(fromVariable.getTextRange().getStartOffset()));
            final int fromEndLine = PsiUtil.runReadActionWithResult(() -> fromDocument.getLineNumber(fromVariable.getTextRange().getEndOffset()));

            ReportFormat.Position fromPosition = new ReportFormat.Position(
                    formStartLine + 1,
                    PsiUtil.runReadActionWithResult(() -> fromVariable.getTextRange().getStartOffset()) -
                            PsiUtil.runReadActionWithResult(() -> fromDocument.getLineStartOffset(formStartLine)),
                    fromEndLine + 1,
                    PsiUtil.runReadActionWithResult(() -> fromVariable.getTextRange().getEndOffset()) -
                            PsiUtil.runReadActionWithResult(() -> fromDocument.getLineStartOffset(fromEndLine))
            );

            final int toStartLine = PsiUtil.runReadActionWithResult(() -> toDocument.getLineNumber(toVariable.getTextRange().getStartOffset()));
            final int toEndLine = PsiUtil.runReadActionWithResult(() -> toDocument.getLineNumber(toVariable.getTextRange().getEndOffset()));

            ReportFormat.Position toPosition = new ReportFormat.Position(
                    toStartLine + 1,
                    PsiUtil.runReadActionWithResult(() -> toVariable.getTextRange().getStartOffset()) -
                            PsiUtil.runReadActionWithResult(() -> toDocument.getLineStartOffset(toStartLine)),
                    toEndLine + 1,
                    PsiUtil.runReadActionWithResult(() -> toVariable.getTextRange().getEndOffset()) -
                            PsiUtil.runReadActionWithResult(() -> toDocument.getLineStartOffset(toEndLine))
            );

            // get the modifiers of the two variables including the visibility
            String[] modifiers = new String[0];
            if (fromVariable instanceof TypeScriptField fromField) {
                modifiers = PsiUtil.getModifiersIncludingVisibility(fromField).toArray(new String[0]);
            } else if (fromVariable instanceof TypeScriptParameter fromParameter) {
                modifiers = PsiUtil.getModifiersIncludingVisibility(fromParameter).toArray(new String[0]);
            }

            // create the context for the toVariable in the to file
            ReportFormat.DataClumpsVariableToContext toContext = new ReportFormat.DataClumpsVariableToContext(
                    PsiUtil.getQualifiedName(toElement) + "." + PsiUtil.getName(toVariable),
                    PsiUtil.getName(toVariable),
                    property.getTypesAsString(),
                    modifiers,
                    toPosition
            );

            // get the modifiers of the two variables including the visibility
            modifiers = new String[0];
            if (toVariable instanceof TypeScriptField toField) {
                modifiers = PsiUtil.getModifiersIncludingVisibility(toField).toArray(new String[0]);
            } else if (toVariable instanceof TypeScriptParameter toParameter) {
                modifiers = PsiUtil.getModifiersIncludingVisibility(toParameter).toArray(new String[0]);
            }

            // create the context for the fromVariable in the from file
            ReportFormat.DataClumpsVariableFromContext dataClumpVariable = new ReportFormat.DataClumpsVariableFromContext(
                    PsiUtil.getQualifiedName(fromElement) + "." + PsiUtil.getName(fromVariable),
                    PsiUtil.getName(fromVariable),
                    property.getTypesAsString(),
                    modifiers,
                    fromPosition,
                    1,
                    toContext
            );

            dataClumpsVariables.put(property.getName(), dataClumpVariable);
        }

        // determine the type of the data clump
        String dataClumpType;
        if (fromElement instanceof TypeScriptFunction && toElement instanceof TypeScriptFunction) {
            dataClumpType = "parameters_to_parameters";
        } else if (fromElement instanceof TypeScriptFunction || toElement instanceof TypeScriptFunction) {
            dataClumpType = "parameters_to_fields";
        } else {
            dataClumpType = "fields_to_fields";
        }

        // create the context for the data clump

        return new DataClumpTypeContext(
                "data_clump",
                PsiUtil.getQualifiedName(fromElement) + "-" + PsiUtil.getQualifiedName(toElement),
                1.0,
                PsiUtil.runReadActionWithResult(() -> fromElement.getContainingFile().getVirtualFile().getPath()),
                fromElement instanceof JSClass ? PsiUtil.getName(fromElement) : null,
                fromElement instanceof JSClass ? PsiUtil.getQualifiedName(fromElement) : null,
                fromElement instanceof TypeScriptFunction ? PsiUtil.getName(fromElement) : null,
                fromElement instanceof TypeScriptFunction ? PsiUtil.getQualifiedName(fromElement) : null,
                PsiUtil.runReadActionWithResult(() -> toElement.getContainingFile().getVirtualFile().getPath()),
                toElement instanceof JSClass ? PsiUtil.getName(toElement) : null,
                toElement instanceof JSClass ? PsiUtil.getQualifiedName(toElement) : null,
                toElement instanceof TypeScriptFunction ? PsiUtil.getName(toElement) : null,
                toElement instanceof TypeScriptFunction ? PsiUtil.getQualifiedName(toElement) : null,
                dataClumpType,
                dataClumpsVariables
        );

    }

    /**
     * This type encapsulates the context of multiple data clumps. It includes the report's version,
     * the options used during the data clump analysis, and a dictionary mapping keys to data clump contexts.
     *
     * @param reportVersion   the version of the context format or the tooling.
     * @param detector        the options used during the data clump analysis.
     * @param dataClumps      a dictionary mapping keys to data clump contexts.
     * @param reportTimestamp the timestamp when the report was created.
     * @param targetLanguage  the language or framework the detector designed for.
     * @param reportSummary   an overall summary of the report
     * @param projectInfo     information about the project or codebase the report was generated for.
     */
    public record DataClumpsTypeContext(@SerializedName("report_version") String reportVersion,
                                        DataClumpsDetectorContext detector,
                                        Map<String, DataClumpTypeContext> dataClumps,

                                        @SerializedName("report_timestamp") String reportTimestamp,

                                        @SerializedName("target_language") String targetLanguage,

                                        ReportSummary reportSummary, ProjectInfo projectInfo) {
    }

    /**
     * This type encapsulates the summary of the data clumps report.
     *
     * @param amountDataClumps                        the total amount of data clumps detected.
     * @param amountFilesWithDataClumps               the amount of files with data clumps.
     * @param amountClassesOrInterfacesWithDataClumps the amount of classes or interfaces with data clumps.
     * @param amountMethodsWithDataClumps             the amount of methods with data clumps.
     * @param fieldsToFieldsDataClump                 the amount of fields to fields data clumps.
     * @param parametersToFieldsDataClump             the amount of parameters to fields data clumps.
     * @param parametersToParametersDataClump         the amount of parameters to parameters data clumps.
     * @param additional                              any additional information or summary.
     */
    public record ReportSummary(@SerializedName("amount_data_clumps") int amountDataClumps,

                                @SerializedName("amount_files_with_data_clumps") int amountFilesWithDataClumps,
                                @SerializedName(" amount_classes_or_interfaces_with_data_clumps") int amountClassesOrInterfacesWithDataClumps,
                                @SerializedName("amount_methods_with_data_clumps") int amountMethodsWithDataClumps,
                                @SerializedName("fields_to_fields_data_clump") int fieldsToFieldsDataClump,
                                @SerializedName("parameters_to_fields_data_clump") int parametersToFieldsDataClump,
                                @SerializedName("parameters_to_parameters_data_clump") int parametersToParametersDataClump,

                                String additional) {

    }

    /**
     * This type encapsulates the information about the project or codebase the report was generated for.
     *
     * @param projectUrl                  the URL of the project or codebase.
     * @param projectName                 the name of the project or codebase.
     * @param projectVersion              the version of the project or codebase.
     * @param projectCommitHash           the commit hash of the project or codebase.
     * @param projectTag                  the tag of the project or codebase.
     * @param projectCommitDate           the commit date of the project or codebase.
     * @param numberOfFiles               the total amount of files in the project or codebase.
     * @param numberOfClassesOrInterfaces the total amount of classes or interfaces in the project or codebase.
     * @param numberOfMethods             the total amount of methods in the project or codebase.
     * @param numberOfDataFields          the total amount of data fields in the project or codebase.
     * @param numberOfMethodParameters    the total amount of method parameters in the project or codebase.
     * @param additional                  any additional information about the project or codebase.
     */
    public record ProjectInfo(@SerializedName("project_url") String projectUrl,
                              @SerializedName("project_name") String projectName,
                              @SerializedName("project_version") String projectVersion,
                              @SerializedName("project_commit_hash") String projectCommitHash,
                              @SerializedName("project_tag") String projectTag,
                              @SerializedName("project_commit_date") String projectCommitDate,
                              @SerializedName("number_of_files") int numberOfFiles,
                              @SerializedName("number_of_classes_or_interfaces") int numberOfClassesOrInterfaces,
                              @SerializedName("number_of_methods") int numberOfMethods,
                              @SerializedName("number_of_data_fields") int numberOfDataFields,
                              @SerializedName("number_of_method_parameters") int numberOfMethodParameters,

                              String additional) {
    }

    /**
     * This type holds the configuration options for a specific detector during data clump analysis.
     *
     * @param name    the name of the detector used in the analysis.
     * @param url     the url of the detector used in the analysis.
     * @param version the version of the detector used in the analysis.
     * @param options The threshold value or metric that defines a data clump for the detector
     */
    public record DataClumpsDetectorContext(String name,
                                            String url,
                                            String version,
                                            HashMap<String, String> options) {
    }

    /**
     * This type represents the context in which a data clump exists.
     *
     * @param type                     The type of the context, in this case always 'data_clump'.
     * @param key                      a unique identifier for the data clump context.
     * @param probability              the probability of the data clump.
     * @param fromFilePath             the file path of the file from which the data clump originates.
     * @param fromClassOrInterfaceName the name of the class or interface from which the data clump originates.
     * @param fromClassOrInterfaceKey  a unique identifier for the class or interface from which the data clump originates.
     * @param fromMethodName           the name of the method from which the data clump originates.
     * @param fromMethodKey            a unique identifier for the method from which the data clump originates.
     * @param toFilePath               the file path of the file to which the data clump leads.
     * @param toClassOrInterfaceName   the name of the class or interface to which the data clump leads.
     * @param toClassOrInterfaceKey    a unique identifier for the class or interface to which the data clump leads.
     * @param toMethodName             the name of the method to which the data clump leads.
     * @param toMethodKey              a unique identifier for the method to which the data clump leads.
     * @param dataClumpType            The specific type of data clump: 'fields_to_fields', 'parameters_to_fields', or 'parameters_to_parameters'.
     * @param dataClumpData            a dictionary mapping keys to data clumps parameter from context.
     */
    public record DataClumpTypeContext(String type,
                                       String key,
                                       Double probability,
                                       @SerializedName("from_file_path") String fromFilePath,
                                       @SerializedName("from_class_or_interface_name") String fromClassOrInterfaceName,
                                       @SerializedName("from_class_or_interface_key") String fromClassOrInterfaceKey,
                                       @SerializedName("from_method_name") String fromMethodName,
                                       @SerializedName("from_method_key") String fromMethodKey,
                                       @SerializedName("to_file_path") String toFilePath,
                                       @SerializedName("to_class_or_interface_name") String toClassOrInterfaceName,
                                       @SerializedName("to_class_or_interface_key") String toClassOrInterfaceKey,
                                       @SerializedName("to_method_name") String toMethodName,
                                       @SerializedName("to_method_key") String toMethodKey,
                                       @SerializedName("data_clumps") String dataClumpType,
                                       Map<String, DataClumpsVariableFromContext> dataClumpData) {
    }

    /**
     * This type represents a parameter from the context in which a data clump exists.
     *
     * @param key         a unique identifier for the parameter.
     * @param name        the name of the parameter.
     * @param type        the data type of the parameter.
     * @param modifiers   the modifiers applied to the parameter, e.g. 'public', 'private', 'static'.
     * @param position    the position of the parameter in the file.
     * @param probability the probability of the parameter being part of the data clump.
     * @param toVariable  the context of the parameter to which the data clump leads.
     */
    public record DataClumpsVariableFromContext(String key, String name, String type, String[] modifiers,
                                                Position position, double probability,
                                                @SerializedName("to_variable") DataClumpsVariableToContext toVariable) {
    }

    /**
     * This type represents a parameter in the destination context matching a data clump.
     *
     * @param key       a unique identifier for the parameter.
     * @param name      the name of the parameter.
     * @param type      the data type of the parameter.
     * @param modifiers the modifiers applied to the parameter, e.g. 'public', 'private', 'static'.
     * @param position  the position of the parameter in the file.
     */
    public record DataClumpsVariableToContext(String key, String name, String type, String[] modifiers,
                                              Position position) {
    }

    /**
     * This type represents a position in a source code
     *
     * @param startLine   the line where the position starts
     * @param startColumn the column where the position starts
     * @param endLine     the line where the position ends
     * @param endColumn   the column where the position ends
     */
    public record Position(int startLine, int startColumn, int endLine, int endColumn) {
    }
}
