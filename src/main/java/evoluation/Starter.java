package evoluation;

import com.intellij.ide.AppLifecycleListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;

import util.CodeSmellLogger;

import java.util.Objects;

public class Starter implements AppLifecycleListener {


    @Override
    public void appStarted() {

        if (Objects.equals(System.getProperty("dataclump.diagnostic.tool"), "true")) {
            Project project = openProject();
            if (project != null) {
                DiagnosticTool.init(project);
            }
        }
    }

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
