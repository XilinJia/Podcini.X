package ac.mdiq.podcini.config.settings

import ac.mdiq.podcini.storage.database.appPrefs
import ac.mdiq.podcini.storage.database.upsertBlk
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Loge
import ac.mdiq.podcini.utils.dateStampFilename
import android.app.Activity
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException
import androidx.core.net.toUri
import ac.mdiq.podcini.storage.utils.nowInMillis

const val autoBackupDirName = "Podcini-AutoBackups"

fun autoBackup(activity: Activity) {
    val TAG = "autoBackup"

//    val prefsDirName = "Podcini-Prefs"

    val isAutoBackup = appPrefs.autoBackup
    if (!isAutoBackup) return
    val uriString = appPrefs.autoBackupFolder
    if (uriString.isNullOrBlank()) return

    Logd("autoBackup", "in autoBackup directory: $uriString")

    fun deleteDirectoryAndContents(directory: DocumentFile): Boolean {
        if (directory.isDirectory) {
            directory.listFiles().forEach { file ->
                if (file.isDirectory) deleteDirectoryAndContents(file)
                Logd(TAG, "deleting ${file.name}")
                try { file.delete() } catch (e: Throwable) { Loge(TAG, "deleteDirectoryAndContents: failed to delete ${file.name} ${e.message}")}
            }
        }
        try { return  directory.delete() } catch (e: Throwable) { Loge(TAG, "deleteDirectoryAndContents: failed to delete ${directory.name} ${e.message}")}
        return false
    }

    CoroutineScope(Dispatchers.IO).launch {
        val interval = appPrefs.autoBackupIntervall
        val lastBackupTime = appPrefs.autoBackupTimeStamp
        val curTime = nowInMillis()
        if ((curTime - lastBackupTime) / 1000 / 3600 > interval) {
            val uri = uriString!!.toUri()
            val permissions = activity.contentResolver.persistedUriPermissions.find { it.uri == uri }
            if (permissions != null && permissions.isReadPermission && permissions.isWritePermission) {
                val chosenDir = DocumentFile.fromTreeUri(activity, uri)
                if (chosenDir != null) {
                    val backupDirs = mutableListOf<DocumentFile>()
                    try {
                        if (chosenDir.isDirectory) {
                            chosenDir.listFiles().forEach { file ->
                                Logd(TAG, "file: $file")
                                if (file.isDirectory && file.name?.startsWith(autoBackupDirName, ignoreCase = true) == true) backupDirs.add(file)
                            }
                        }
                        Logd(TAG, "backupDirs: ${backupDirs.size}")
                        val limit = appPrefs.autoBackupLimit
                        if (backupDirs.size >= limit) {
                            backupDirs.sortBy { it.name }
                            for (i in 0..(backupDirs.size - limit)) deleteDirectoryAndContents(backupDirs[i])
                        }

                        val dirName = dateStampFilename("$autoBackupDirName-%s")
                        val exportSubDir = chosenDir.createDirectory(dirName) ?: throw IOException("Error creating subdirectory $dirName")
//                        val subUri: Uri = exportSubDir.uri
//                        PreferencesTransporter(prefsDirName).exportToDocument(subUri)
                        val realmFile = exportSubDir.createFile("application/octet-stream", "backup.realm")
                        if (realmFile != null) DatabaseTransporter().exportToDocument(realmFile.uri)
                        upsertBlk(appPrefs) { it.autoBackupTimeStamp = curTime }
                    } catch (e: Exception) { Loge("autoBackup", "Error backing up ${e.message}") }
                }
            } else Loge("autoBackup", "Uri permissions are no longer valid")
        }
    }
}