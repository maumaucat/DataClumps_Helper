import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import evoluation.DiagnosticTool
import groovyjarjarantlr.DiagnosticCodeGenerator
import util.Index

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
        }
    }
}
