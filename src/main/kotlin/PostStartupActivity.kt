import com.intellij.openapi.project.DumbService
import util.Index
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class PostStartupActivity : ProjectActivity {


    override suspend fun execute(project: Project) {
        val dumbService = DumbService.getInstance(project)

        // Ensure that Index.resetIndex is executed after indexing
        dumbService.runWhenSmart {
            Index.resetIndex(project)
        }
    }
}
