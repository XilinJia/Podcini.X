package ac.mdiq.podcini.storage.utils

import ac.mdiq.podcini.PodciniApp.Companion.getAppContext
import ac.mdiq.podcini.R
import ac.mdiq.podcini.config.settings.DatabaseTransporter
import ac.mdiq.podcini.storage.database.appPrefs
import ac.mdiq.podcini.storage.database.upsertBlk
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Loge
import ac.mdiq.podcini.utils.dateStampFilename
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

const val autoBackupDirName = "Podcini-AutoBackups"

fun autoBackup() {
    val TAG = "autoBackup"
    val context = getAppContext()

    val isAutoBackup = appPrefs.autoBackup
    if (!isAutoBackup) return
    val uriString = appPrefs.autoBackupFolder
    if (uriString.isNullOrBlank()) {
        Loge(TAG, context.getString(R.string.auto_backup_folder_not_specified))
        return
    }

    Logd("autoBackup", "in autoBackup directory: $uriString")
    suspend fun deleteDirectoryAndContents(directory: UnifiedFile): Boolean {
        if (directory.isDirectory()) {
            directory.listChildren().forEach { file ->
                if (file.isDirectory()) deleteDirectoryAndContents(file)
                Logd(TAG, "deleting ${file.name}")
                try { file.delete() } catch (e: Throwable) {
                    Loge(TAG, "deleteDirectoryAndContents: failed to delete ${file.name} ${e.message}")
                }
            }
        }
        try { return  directory.delete() } catch (e: Throwable) { Loge(TAG, "deleteDirectoryAndContents: failed to delete ${directory.name} ${e.message}") }
        return false
    }

    val curTime = nowInMillis()
    if ((curTime - appPrefs.autoBackupTimeStamp) / 10000 > appPrefs.autoBackupIntervall)
//    if ((curTime - appPrefs.autoBackupTimeStamp) / 3600000 > appPrefs.autoBackupIntervall)
        CoroutineScope(Dispatchers.IO).launch {
            val uri = uriString.toSafeUri()
            val permissions = context.contentResolver.persistedUriPermissions.find { it.uri == uri }
            if (permissions != null && permissions.isReadPermission && permissions.isWritePermission) {
                val chosenDir = uri.toUF()
                if (chosenDir.exists()) {
                    val backedupDirs = mutableListOf<UnifiedFile>()
                    try {
                        chosenDir.listChildren().forEach { file ->
                            Logd(TAG, "file: $file")
                            if (file.isDirectory() && file.name.startsWith(autoBackupDirName, ignoreCase = true)) backedupDirs.add(file)
                        }
                        Logd(TAG, "backupDirs: ${backedupDirs.size}")
                        val limit = appPrefs.autoBackupLimit
                        if (backedupDirs.size >= limit) {
                            backedupDirs.sortBy { it.name }
                            for (i in 0..(backedupDirs.size - limit)) deleteDirectoryAndContents(backedupDirs[i])
                        }
                        val dirName = dateStampFilename("$autoBackupDirName-%s")
                        val exportSubDir = chosenDir.createDirectory(dirName)
                        val realmFile = exportSubDir.createFile("application/octet-stream", "backup.realm")
                        DatabaseTransporter().exportToUri(realmFile)
                        upsertBlk(appPrefs) { it.autoBackupTimeStamp = curTime }
                    } catch (e: Exception) { Loge("autoBackup", "Error backing up ${e.message}") }
                } else Loge("autoBackup", context.getString(R.string.auto_backup_folder_not_available))
            } else Loge("autoBackup", "Uri permissions are no longer valid")
        }
}