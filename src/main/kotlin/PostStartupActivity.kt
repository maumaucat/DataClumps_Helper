import Util.Index
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.diagnostic.Logger

class PostStartupActivity : ProjectActivity {

    private val logger = Logger.getInstance(PostStartupActivity::class.java)

    override suspend fun execute(project: Project) {
        logger.info("HIER WIRD DIE PROJECT ACTIVITY AUSGEFÜHRT, DAS IST TOLL!!!")
        Index.resetIndex(project)
    }
}
