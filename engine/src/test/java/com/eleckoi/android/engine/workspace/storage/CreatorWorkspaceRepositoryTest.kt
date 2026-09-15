package com.eleckoi.android.engine.workspace.storage

import com.eleckoi.android.engine.agent.api.AgentPermissionMode
import com.eleckoi.android.engine.workspace.model.CreatorConversationTimelineItem
import com.eleckoi.android.engine.workspace.model.CreatorConversationTimelineKind
import com.eleckoi.android.engine.workspace.model.CreatorWorkspace
import com.eleckoi.android.engine.workspace.model.CreatorWorkspaceRootAccess
import com.eleckoi.android.foundation.serialization.ElecKoiPrettyJson
import com.eleckoi.android.foundation.storage.ElecKoiDataException
import java.io.File
import java.nio.file.Files
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeNoException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CreatorWorkspaceRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `create starts empty and survives repository reload`() = runBlocking {
        val root = temporaryFolder.newFolder("creator_workspaces")
        val repository = repository(root)

        val created = repository.create(name = "Galgame 测试", linkedCharacterId = "character-1")

        assertTrue(created.files.isEmpty())
        assertEquals("character-1", created.linkedCharacterId)

        val reloaded = repository(root).get(created.id)
        assertNotNull(reloaded)
        assertEquals(created.id, reloaded?.id)
        assertTrue(File(root, "catalog.json").isFile)
        assertTrue(File(root, "workspaces/${created.id}/manifest.json").isFile)
    }

    @Test
    fun `creator workspace mounts one writable primary and multiple scoped references`() = runBlocking {
        val root = temporaryFolder.newFolder("creator-character-roots")
        val repository = repository(root)
        val workspace = repository.create("多角色创作")

        val withReference = repository.attachCharacterRoot(
            workspaceId = workspace.id,
            characterId = "reference-1",
            alias = "参考角色",
        )
        assertEquals(null, withReference.primaryCharacterRootId)
        assertEquals(CreatorWorkspaceRootAccess.ReadOnly, withReference.characterRoots.single().access)

        val withPrimary = repository.attachCharacterRoot(
            workspaceId = workspace.id,
            characterId = "primary-1",
            alias = "主角色",
            makePrimary = true,
        )
        assertEquals("primary-1", withPrimary.linkedCharacterId)
        assertEquals("character:primary-1", withPrimary.primaryCharacterRootId)
        assertEquals(2, withPrimary.characterRoots.size)
        assertEquals(
            CreatorWorkspaceRootAccess.ReadWrite,
            withPrimary.characterRoots.single { it.characterId == "primary-1" }.access,
        )

        val writableReference = repository.setCharacterRootAccess(
            workspaceId = workspace.id,
            rootId = "character:reference-1",
            access = CreatorWorkspaceRootAccess.ReadWrite,
        )
        assertEquals(
            CreatorWorkspaceRootAccess.ReadWrite,
            writableReference.characterRoots.single { it.characterId == "reference-1" }.access,
        )

        val switched = repository.setPrimaryCharacterRoot(workspace.id, "character:reference-1")
        assertEquals("reference-1", switched.linkedCharacterId)
        assertEquals(
            CreatorWorkspaceRootAccess.ReadOnly,
            switched.characterRoots.single { it.characterId == "primary-1" }.access,
        )

        val detached = repository.detachCharacterRoot(workspace.id, "character:reference-1")
        assertEquals(null, detached.primaryCharacterRootId)
        assertEquals(null, detached.linkedCharacterId)
        assertEquals(listOf("primary-1"), detached.characterRoots.map { it.characterId })
    }

    @Test
    fun `creator media assets stay private and survive repository reload`() = runBlocking {
        val root = temporaryFolder.newFolder("creator-media-assets")
        val repository = repository(root)
        val workspace = repository.create("图片创作")
        val source = temporaryFolder.newFile("source.png").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4))
        }

        val imported = repository.importCreatorMediaAsset(
            workspaceId = workspace.id,
            assetId = "media-1",
            extension = "png",
            source = source,
        )

        assertTrue(imported.isFile)
        assertEquals(source.readBytes().toList(), imported.readBytes().toList())
        assertTrue(repository.listFiles(workspace.id).isEmpty())
        assertEquals(listOf("media-1"), repository.listCreatorMediaAssetFiles(workspace.id).map { it.nameWithoutExtension })
        assertNotNull(repository(root).creatorMediaAssetFile(workspace.id, "media-1"))

        repository.deleteCreatorMediaAsset(workspace.id, "media-1")
        assertEquals(null, repository.creatorMediaAssetFile(workspace.id, "media-1"))
    }

    @Test
    fun `one character keeps one persistent story workspace`() = runBlocking {
        val root = temporaryFolder.newFolder("character-workspace")
        val repository = repository(root)

        val workspace = repository.ensureCharacterWorkspace("character-1", "角色 · 剧情小说")
        val sameWorkspace = repository.ensureCharacterWorkspace("character-1", "重命名不应新建")

        assertEquals(workspace.id, sameWorkspace.id)
        assertEquals("character-1", workspace.linkedCharacterId)
        assertTrue(workspace.characterOwned)
        assertEquals(1, repository.list().size)
        assertTrue(repository.listFiles(workspace.id).isEmpty())
        assertTrue(File(root, "characters/character-1/剧情小说/project").isDirectory)
        assertFalse(File(root, "workspaces/${workspace.id}").exists())
        assertEquals(
            "characters/character-1/剧情小说/project",
            repository.runtimeProjectPath(workspace),
        )

        val reloaded = repository(root).list()
        assertEquals(listOf(workspace.id), reloaded.map { it.id })
    }

    @Test
    fun `new character container does not create any mode workspace`() = runBlocking {
        val root = temporaryFolder.newFolder("empty-character-container")
        val repository = repository(root)

        repository.ensureCharacterContainer("character-1")

        val container = File(root, "characters/character-1")
        assertTrue(container.isDirectory)
        assertTrue(container.listFiles().orEmpty().isEmpty())
        assertTrue(repository.list().isEmpty())
    }

    @Test
    fun `missing character workspace directory is rejected`() {
        val root = temporaryFolder.newFolder("missing-character-workspace")
        val paths = WorkspacePathGuard(root)
        paths.initialize()
        val unsupported = CreatorWorkspace(
            id = "retired-workspace",
            name = "已停用工作区",
            linkedCharacterId = "character-1",
            characterOwned = true,
            createdAt = "2026-08-28T00:00:00Z",
            updatedAt = "2026-08-28T00:00:00Z",
        )

        assertFalse(paths.isSafeWorkspaceDirectory(unsupported))
    }

    @Test
    fun `write updates catalog and file listing`() = runBlocking {
        val root = temporaryFolder.newFolder("write")
        val repository = repository(root)
        val workspace = repository.create("可编辑项目")

        val updated = repository.writeText(workspace.id, "scenes/opening.js", "export const ready = true")

        assertTrue("scenes/opening.js" in updated.files)
        assertEquals("export const ready = true", repository.readText(workspace.id, "scenes/opening.js"))
        assertEquals(updated.files, repository.listFiles(workspace.id).map { it.path })
    }

    @Test
    fun `writing identical workspace text does not rewrite file or manifests`() = runBlocking {
        val root = temporaryFolder.newFolder("write-identical")
        val repository = repository(root)
        val workspace = repository.create("幂等写入")
        val first = repository.writeText(workspace.id, "index.html", "<main>same</main>")
        val projectFile = File(root, "workspaces/${workspace.id}/project/index.html")
        val manifestFile = File(root, "workspaces/${workspace.id}/manifest.json")
        projectFile.setLastModified(1_000L)
        manifestFile.setLastModified(2_000L)

        val second = repository.writeText(workspace.id, "index.html", "<main>same</main>")

        assertEquals(first, second)
        assertEquals(1_000L, projectFile.lastModified())
        assertEquals(2_000L, manifestFile.lastModified())
    }

    @Test
    fun `delete path removes only the requested workspace file`() = runBlocking {
        val root = temporaryFolder.newFolder("delete-path")
        val repository = repository(root)
        val workspace = repository.create("聊天历史")
        repository.writeText(workspace.id, "资料/one.md", "one")
        repository.writeText(workspace.id, "资料/two.md", "two")

        val updated = repository.deletePath(workspace.id, "资料/one.md")

        assertFalse(updated.files.contains("资料/one.md"))
        assertTrue(updated.files.contains("资料/two.md"))
        assertEquals("two", repository.readText(workspace.id, "资料/two.md"))
    }

    @Test
    fun `listing reconciles files written by the DSH runtime into manifest and catalog`() = runBlocking {
        val root = temporaryFolder.newFolder("runtime-reconcile")
        val repository = repository(root)
        val workspace = repository.create("运行时写入项目")
        val project = File(root, "workspaces/${workspace.id}/project")
        val generated = File(project, "index.html")
        generated.writeText("<main>generated</main>")

        val files = repository.listFiles(workspace.id)
        val reloaded = repository(root).get(workspace.id)

        assertTrue(files.any { it.path == "index.html" })
        assertTrue(reloaded?.files?.contains("index.html") == true)
        assertEquals(files.sumOf { it.sizeBytes }, reloaded?.totalBytes)
    }

    @Test
    fun `one workspace keeps conversation metadata without duplicating DSH timelines`() = runBlocking {
        val root = temporaryFolder.newFolder("conversations")
        val repository = repository(root)
        val workspace = repository.create("多对话项目")

        val second = repository.createConversation(workspace.id, "检查界面")
        val secondId = requireNotNull(second.activeConversationId)
        repository.saveConversationTimeline(
            workspace.id,
            secondId,
            listOf(
                CreatorConversationTimelineItem(
                    id = "message-1",
                    kind = CreatorConversationTimelineKind.User,
                    text = "检查侧栏",
                ),
            ),
        )

        val reloaded = repository(root).get(workspace.id)
        assertEquals(2, reloaded?.conversations?.size)
        assertTrue(reloaded?.conversations?.first { it.id == secondId }?.timeline?.isEmpty() == true)

        val afterDelete = repository(root).deleteConversation(workspace.id, secondId)
        assertEquals(1, afterDelete.conversations.size)
        assertEquals(workspace.id, afterDelete.activeConversationId)
    }

    @Test
    fun `permission mode survives reload and is shared by every workspace conversation`() = runBlocking {
        val root = temporaryFolder.newFolder("conversation-permission")
        val repository = repository(root)
        val workspace = repository.create("权限持久化")
        repository.createConversation(workspace.id, "第二个任务")

        repository.saveWorkspacePermissionMode(
            workspaceId = workspace.id,
            permissionMode = AgentPermissionMode.FullAccess,
        )

        val reloaded = requireNotNull(repository(root).get(workspace.id))
        assertEquals(AgentPermissionMode.FullAccess, reloaded.permissionMode)

        val third = repository.createConversation(workspace.id, "第三个任务")
        assertEquals(AgentPermissionMode.FullAccess, third.permissionMode)

        val separateWorkspace = repository.create("独立的新项目")
        assertEquals(AgentPermissionMode.AskForApproval, separateWorkspace.permissionMode)
    }

    @Test
    fun `delete workspace removes catalog entry and private files`() = runBlocking {
        val root = temporaryFolder.newFolder("delete-workspace")
        val repository = repository(root)
        val workspace = repository.create("待删除项目")

        repository.delete(workspace.id)

        assertEquals(null, repository.get(workspace.id))
        assertFalse(File(root, "workspaces/${workspace.id}").exists())
        assertFalse(repository(root).list().any { it.id == workspace.id })
    }

    @Test
    fun `workspace manifest recovers a damaged catalog`() = runBlocking {
        val root = temporaryFolder.newFolder("recovery")
        val repository = repository(root)
        val workspace = repository.create("恢复测试")
        File(root, "catalog.json").writeText("{ damaged")

        val recovered = repository(root).get(workspace.id)

        assertEquals(workspace.id, recovered?.id)
        assertTrue(File(root, "catalog.json").readText().contains(workspace.id))
    }

    @Test
    fun `catalog recovery reports a catalog write failure`() {
        val root = temporaryFolder.newFolder("recovery-write-failure")
        val paths = WorkspacePathGuard(root)
        paths.initialize()
        val workspace = CreatorWorkspace(
            id = "recovered-workspace",
            name = "恢复失败测试",
            createdAt = "2026-07-14T12:00:00Z",
            updatedAt = "2026-07-14T12:00:00Z",
        )
        val workspaceDirectory = File(paths.workspacesRoot, workspace.id)
        assertTrue(File(workspaceDirectory, WorkspacePathGuard.ProjectDirectoryName).mkdirs())
        AtomicWorkspaceFileStore().writeJson(
            File(workspaceDirectory, WorkspacePathGuard.ManifestFileName),
            ElecKoiPrettyJson.encodeToString(workspace),
        )

        val error = assertThrows(ElecKoiDataException::class.java) {
            WorkspaceCatalogStore(paths, AtomicWorkspaceFileStore()) { _, _ ->
                error("simulated catalog write failure")
            }.catalog()
        }

        assertTrue(error.message.orEmpty().contains("无法写回索引"))
        assertEquals("simulated catalog write failure", error.cause?.message)
    }

    @Test
    fun `project path traversal is rejected`() = runBlocking {
        val root = temporaryFolder.newFolder("traversal")
        val repository = repository(root)
        val workspace = repository.create("安全项目")

        val error = runCatching {
            repository.writeText(workspace.id, "../outside.txt", "bad")
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(!File(root, "outside.txt").exists())
    }

    @Test
    fun `checkpoint copies current project into bounded snapshot area`() = runBlocking {
        val root = temporaryFolder.newFolder("checkpoint")
        val repository = repository(root)
        val workspace = repository.create("快照项目")
        repository.writeText(workspace.id, "script.js", "window.version = 2")

        val checkpoint = repository.checkpoint(workspace.id, "第二版")

        val snapshot = File(
            root,
            "workspaces/${workspace.id}/checkpoints/${checkpoint.id}/project/script.js",
        )
        assertTrue(snapshot.isFile)
        assertEquals("window.version = 2", snapshot.readText())
        assertEquals(checkpoint.id, repository.get(workspace.id)?.latestCheckpointId)
    }

    @Test
    fun `checkpoints can be listed and restore replaces the complete project`() = runBlocking {
        val root = temporaryFolder.newFolder("restore")
        val repository = repository(root)
        val workspace = repository.create("可恢复项目")
        repository.writeText(workspace.id, "script.js", "window.version = 1")
        val first = repository.checkpoint(workspace.id, "AI 修改前")
        repository.writeText(workspace.id, "script.js", "window.version = 2")
        repository.writeText(workspace.id, "generated.txt", "later file")
        val second = repository.checkpoint(workspace.id, "第二版")

        assertEquals(listOf(second.id, first.id), repository.listCheckpoints(workspace.id).map { it.id })

        val restored = repository.restoreCheckpoint(workspace.id, first.id)

        assertEquals("window.version = 1", repository.readText(workspace.id, "script.js"))
        assertFalse("generated file must be removed by full restore", "generated.txt" in restored.files)
        assertFalse(File(root, "workspaces/${workspace.id}/project/generated.txt").exists())
        assertEquals(first.id, restored.latestCheckpointId)
        assertEquals(restored.files, repository.get(workspace.id)?.files)
    }

    @Test
    fun `empty project checkpoint remains restorable`() = runBlocking {
        val root = temporaryFolder.newFolder("restore-empty")
        val repository = repository(root)
        val workspace = repository.create("空项目")
        val project = File(root, "workspaces/${workspace.id}/project")
        val checkpoint = repository.checkpoint(workspace.id, "清空后")
        repository.writeText(workspace.id, "generated.txt", "later file")

        val restored = repository.restoreCheckpoint(workspace.id, checkpoint.id)

        assertTrue(project.isDirectory)
        assertTrue(restored.files.isEmpty())
        assertTrue(repository.listFiles(workspace.id).isEmpty())
        assertFalse(File(project, "generated.txt").exists())
    }

    @Test
    fun `restore validates checkpoint quota before replacing live project`() = runBlocking {
        val root = temporaryFolder.newFolder("restore-quota")
        val repository = repository(root)
        val workspace = repository.create("损坏快照")
        repository.writeText(workspace.id, "script.js", "safe-current")
        val checkpoint = repository.checkpoint(workspace.id, "修改前")
        val snapshotFile = File(
            root,
            "workspaces/${workspace.id}/checkpoints/${checkpoint.id}/project/script.js",
        )
        snapshotFile.writeText("x".repeat(CreatorWorkspaceRepository.MaxSingleFileBytes.toInt() + 1))

        val error = runCatching {
            repository.restoreCheckpoint(workspace.id, checkpoint.id)
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertEquals("safe-current", repository.readText(workspace.id, "script.js"))
    }

    @Test
    fun `failed directory exchange rolls the original project back`() = runBlocking {
        val root = temporaryFolder.newFolder("restore-rollback")
        var moveCount = 0
        val repository = repository(
            root = root,
            moveDirectory = { source, destination ->
                moveCount += 1
                if (moveCount == 2) error("simulated exchange failure")
                require(source.renameTo(destination))
            },
        )
        val workspace = repository.create("回滚项目")
        repository.writeText(workspace.id, "script.js", "before")
        val checkpoint = repository.checkpoint(workspace.id, "修改前")
        repository.writeText(workspace.id, "script.js", "must-survive")

        val error = runCatching {
            repository.restoreCheckpoint(workspace.id, checkpoint.id)
        }.exceptionOrNull()

        assertEquals("simulated exchange failure", error?.message)
        assertEquals("must-survive", repository.readText(workspace.id, "script.js"))
        assertTrue(File(root, "workspaces/${workspace.id}/project").isDirectory)
    }

    @Test
    fun `unsafe checkpoint id cannot escape workspace`() = runBlocking {
        val root = temporaryFolder.newFolder("restore-traversal")
        val repository = repository(root)
        val workspace = repository.create("安全恢复")
        repository.writeText(workspace.id, "marker.txt", "safe")

        val error = runCatching {
            repository.restoreCheckpoint(workspace.id, "../outside")
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertEquals("safe", repository.readText(workspace.id, "marker.txt"))
    }

    @Test
    fun `project root symbolic link is rejected`() = runBlocking {
        val root = temporaryFolder.newFolder("project-root-link")
        val repository = repository(root)
        val workspace = repository.create("符号链接项目")
        repository.writeText(workspace.id, "marker.txt", "safe")
        val workspaceRoot = File(root, "workspaces/${workspace.id}")
        val project = File(workspaceRoot, "project")
        val realProject = File(workspaceRoot, "project-real")
        assertTrue(project.renameTo(realProject))
        createSymbolicLinkOrSkip(project, realProject)

        val error = runCatching { repository.checkpoint(workspace.id, "不安全") }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(File(realProject, "marker.txt").isFile)
    }

    @Test
    fun `symbolic checkpoints root cannot redirect snapshot writes`() = runBlocking {
        val root = temporaryFolder.newFolder("checkpoint-root-link")
        val repository = repository(root)
        val workspace = repository.create("快照链接项目")
        val outside = File(root, "outside-checkpoints").apply { mkdirs() }
        val checkpoints = File(root, "workspaces/${workspace.id}/checkpoints")
        createSymbolicLinkOrSkip(checkpoints, outside)

        val error = runCatching { repository.checkpoint(workspace.id, "不安全") }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(outside.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `symbolic checkpoint staging cannot redirect writes or cleanup`() = runBlocking {
        val root = temporaryFolder.newFolder("checkpoint-staging-link")
        val repository = repository(root)
        val workspace = repository.create("快照临时链接项目")
        val outside = File(root, "outside-staging").apply { mkdirs() }
        val marker = File(outside, "marker.txt").apply { writeText("must survive") }
        val staging = File(root, "workspaces/${workspace.id}/.checkpoint-test-2")
        createSymbolicLinkOrSkip(staging, outside)

        val error = runCatching { repository.checkpoint(workspace.id, "不安全") }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(marker.isFile)
        assertEquals("must survive", marker.readText())
    }

    @Test
    fun `directory depth beyond shared limit is rejected`() = runBlocking {
        val root = temporaryFolder.newFolder("directory-depth")
        val repository = repository(root)
        val workspace = repository.create("深目录项目")
        var directory = File(root, "workspaces/${workspace.id}/project")
        repeat(CreatorWorkspaceRepository.MaxDirectoryDepth) { index ->
            directory = File(directory, "level-$index")
        }
        assertTrue(directory.mkdirs())
        File(directory, "boundary.txt").writeText("allowed")
        assertTrue(repository.listFiles(workspace.id).any { it.path.endsWith("boundary.txt") })
        val tooDeep = File(directory, "overflow").apply { mkdir() }
        File(tooDeep, "deep.txt").writeText("too deep")

        val error = runCatching { repository.listFiles(workspace.id) }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun `filesystem entries beyond shared limit are rejected`() = runBlocking {
        val root = temporaryFolder.newFolder("filesystem-entries")
        val repository = repository(root)
        val workspace = repository.create("多目录项目")
        val project = File(root, "workspaces/${workspace.id}/project")
        repeat(CreatorWorkspaceRepository.MaxFilesystemEntries) { index ->
            assertTrue(File(project, "empty-$index").mkdir())
        }
        assertTrue(repository.listFiles(workspace.id).isEmpty())
        assertTrue(File(project, "overflow").mkdir())

        val error = runCatching { repository.listFiles(workspace.id) }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun `checkpoint trimming never follows symbolic links`() = runBlocking {
        val root = temporaryFolder.newFolder("checkpoint-trim-link")
        val repository = repository(root)
        val workspace = repository.create("裁剪快照项目")
        val first = repository.checkpoint(workspace.id, "第一版")
        val outside = File(root, "must-survive").apply { mkdirs() }
        val marker = File(outside, "marker.txt").apply { writeText("safe") }
        val linkedOutside = File(
            root,
            "workspaces/${workspace.id}/checkpoints/${first.id}/project/linked-outside",
        )
        createSymbolicLinkOrSkip(linkedOutside, outside)

        repeat(CreatorWorkspaceRepository.MaxCheckpointCount) { version ->
            repository.writeText(workspace.id, "version.txt", version.toString())
            repository.checkpoint(workspace.id, "版本 $version")
        }

        assertFalse(File(root, "workspaces/${workspace.id}/checkpoints/${first.id}").exists())
        assertTrue(marker.isFile)
        assertEquals(CreatorWorkspaceRepository.MaxCheckpointCount, repository.listCheckpoints(workspace.id).size)
    }

    @Test
    fun `single file size limit is enforced before disk write`() = runBlocking {
        val root = temporaryFolder.newFolder("limit")
        val repository = repository(root)
        val workspace = repository.create("限制测试")

        val error = runCatching {
            repository.writeText(
                workspace.id,
                "too-large.txt",
                "x".repeat(CreatorWorkspaceRepository.MaxSingleFileBytes.toInt() + 1),
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(!File(root, "workspaces/${workspace.id}/project/too-large.txt").exists())
    }

    private fun repository(
        root: File,
        moveDirectory: ((File, File) -> Unit)? = null,
    ): CreatorWorkspaceRepository {
        var id = 0
        val now = { Instant.parse("2026-07-14T12:00:00Z").plusSeconds(id.toLong()) }
        val newId = { "test-${++id}" }
        return if (moveDirectory == null) {
            CreatorWorkspaceRepository(root = root, now = now, newId = newId)
        } else {
            CreatorWorkspaceRepository(
                root = root,
                now = now,
                newId = newId,
                moveDirectory = moveDirectory,
            )
        }
    }

    private fun createSymbolicLinkOrSkip(link: File, target: File) {
        val error = runCatching {
            Files.createSymbolicLink(link.toPath(), target.toPath().toAbsolutePath())
        }.exceptionOrNull()
        if (error != null) assumeNoException(error)
    }
}
