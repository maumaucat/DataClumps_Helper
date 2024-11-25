import util.Index
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.diagnostic.Logger

class PostStartupActivity : ProjectActivity {


    override suspend fun execute(project: Project) {
        Index.resetIndex(project)
    }
}
