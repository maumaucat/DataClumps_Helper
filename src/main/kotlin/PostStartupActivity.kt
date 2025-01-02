import com.intellij.openapi.project.DumbService
import util.Index
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import evoluation.DiagnosticTool

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
            DiagnosticTool.init(project)
        }
    }
}
