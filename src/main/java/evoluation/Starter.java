package evoluation;

import com.intellij.ide.AppLifecycleListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;

import util.CodeSmellLogger;

import java.util.Objects;

/**
 * This class is used to start the diagnostic tool if the flag is set on startup of the application.
 */
public class Starter implements AppLifecycleListener {

    /**
     * Called when the application is started.
     */
    @Override
    public void appStarted() {
        if (Objects.equals(System.getProperty("dataclump.diagnostic.tool"), "true")) {
            Project project = openProject();
            if (project != null) {
                DiagnosticTool.init(project);
            }
        }
    }

    /**
     * Opens the project that is specified in the system properties.
     *
     * @return the project that was opened or null if the project could not be opened
     */
    public Project openProject() {

        String projectPath = System.getProperty("dataclump.projectpath");
        CodeSmellLogger.info("Project path: " + projectPath);

        if (projectPath != null && !projectPath.isEmpty()) {
            try {
                ProjectManagerEx projectManager = ProjectManagerEx.getInstanceEx();
                projectManager.loadAndOpenProject(projectPath);
                Project project = projectManager.getOpenProjects()[0];
                CodeSmellLogger.info("Project loaded: " + project.getName());
                return project;
            } catch (Exception e) {
                CodeSmellLogger.error("Could not load project: " + projectPath, e);
            }
        } else {
            CodeSmellLogger.error("Invalid project path: " + projectPath, new IllegalArgumentException());
        }
        return null;
    }

}
