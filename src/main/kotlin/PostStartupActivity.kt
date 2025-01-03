import com.intellij.openapi.project.DumbService
import util.Index
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.jgoodies.common.base.Objects
import dataclump.FullAnalysis
import evoluation.DiagnosticTool
import util.CodeSmellLogger

/**
 * This class is used to execute some code after the project is loaded.
 */
class PostStartupActivity : ProjectActivity {

    /**
     * This method is called after the project is loaded. It is used to reset the index.
     */
    override suspend fun execute(project: Project) {
        val dumbService = DumbService.getInstance(project)

        // Ensure that Index.resetIndex is executed after indexing
        dumbService.runWhenSmart {

            Index.resetIndex(project)

            CodeSmellLogger.info("Plugin 11 loaded")

            FullAnalysis.run("C:\\Users\\ms\\Documents\\Uni\\Bachlorarbeit\\Messungen\\fullAnalysis.json")

        }
    }
}
