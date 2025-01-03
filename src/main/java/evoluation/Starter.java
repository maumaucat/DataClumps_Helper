package evoluation;

import com.intellij.ide.AppLifecycleListener;
import com.intellij.ide.GeneralSettings;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame;
import org.jetbrains.annotations.NotNull;
import util.CodeSmellLogger;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Objects;

public class Starter implements AppLifecycleListener {


    @Override
    public void appStarted() {
        CodeSmellLogger.info("App started");
        CodeSmellLogger.info("Headless " + ApplicationManager.getApplication().isHeadlessEnvironment());

        if (Objects.equals(System.getProperty("dataclump.diagnostic.tool"),"true")) {
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
