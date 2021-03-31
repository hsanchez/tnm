package gitMiners

import kotlinx.serialization.builtins.serializer
import org.eclipse.jgit.internal.storage.file.FileRepository
import org.eclipse.jgit.revwalk.RevCommit
import util.ProjectConfig
import util.UtilFunctions
import util.serialization.ConcurrentHashMapSerializer
import util.serialization.ConcurrentSkipListSetSerializer
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListSet

class ChangedFilesMiner(
    repository: FileRepository,
    neededBranches: Set<String> = ProjectConfig.DEFAULT_NEEDED_BRANCHES,
    numThreads: Int = ProjectConfig.DEFAULT_NUM_THREADS
) : GitMiner(repository, neededBranches, numThreads = numThreads) {

    private val userFilesIds = ConcurrentHashMap<Int, ConcurrentSkipListSet<Int>>()
    private val serializer = ConcurrentHashMapSerializer(
        Int.serializer(),
        ConcurrentSkipListSetSerializer(Int.serializer())
    )

    // TODO: add FilesChanges[fileId] = Set(commit1, ...)
    override fun process(currCommit: RevCommit, prevCommit: RevCommit) {
        val git = threadLocalGit.get()
        val reader = threadLocalReader.get()

        val userEmail = currCommit.authorIdent.emailAddress
        val userId = userMapper.add(userEmail)

        val changedFiles =
            reader.use {
                UtilGitMiner.getChangedFiles(currCommit, prevCommit, it, git, userMapper, fileMapper)
            }


        for (fileId in changedFiles) {
            userFilesIds.computeIfAbsent(userId) { ConcurrentSkipListSet() }.add(fileId)
        }
    }


    override fun saveToJson(resourceDirectory: File) {
        UtilFunctions.saveToJson(
            File(resourceDirectory, ProjectConfig.USER_FILES_IDS),
            userFilesIds, serializer
        )
        saveMappers(resourceDirectory)
    }
}
