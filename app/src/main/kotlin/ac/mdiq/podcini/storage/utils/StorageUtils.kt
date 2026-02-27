package ac.mdiq.podcini.storage.utils

import ac.mdiq.podcini.PodciniApp.Companion.getAppContext
import ac.mdiq.podcini.storage.database.appPrefs
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Loge
import ac.mdiq.podcini.utils.Logs
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.StatFs
import android.os.storage.StorageManager
import android.webkit.MimeTypeMap
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileNotFoundException
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.UnsupportedEncodingException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

/**
 * Utility functions for handling storage errors
 */
private const val TAG: String = "StorageUtils"

const val MAX_FILENAME_LENGTH: Int = 242 // limited by CircleCI

private const val FEED_DOWNLOADPATH = "cache/"
const val MEDIA_DOWNLOADPATH = "media/"

private const val MD5_HEX_LENGTH = 32

private val validChars = ("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 _-").toCharArray()

val customMediaUriString: String
    get() = if (appPrefs.useCustomMediaFolder) appPrefs.customMediaUri else ""

/**
 * Get the number of free bytes that are available on the external storage.
 */

val freeSpaceAvailable: Long
    get() {
        if (customMediaUriString.isBlank()) {
            val dataFolder = getDataFolder(null)
            return if (dataFolder != null) getFreeSpaceAvailable(dataFolder.absolutePath) else 0
        } else {
            val uri = customMediaUriString.toUri()
            //                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val pickedDir = DocumentFile.fromTreeUri(getAppContext(), uri)
            if (pickedDir == null || !pickedDir.isDirectory) {
                Loge("SpaceCheck", "Invalid directory URI: $customMediaUriString")
                return 0L
            }
            val storageManager = getAppContext().getSystemService(Context.STORAGE_SERVICE) as StorageManager
            val appSpecificDir = getAppContext().getExternalFilesDir(null) ?: return 0L
            val uuid = storageManager.getUuidForPath(appSpecificDir)
            return storageManager.getAllocatableBytes(uuid)
            //                }
            //                else {
            //                    fun getFilePathFromUri(uri: Uri): String? {
            //                        if ("file" == uri.scheme) return uri.path
            //                        else if ("content" == uri.scheme) {
            //                            val projection = arrayOf(MediaStore.MediaColumns.DATA)
            //                            getAppContext().contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            //                                if (cursor.moveToFirst()) {
            //                                    val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
            //                                    return cursor.getString(columnIndex)
            //                                }
            //                            }
            //                        }
            //                        return null
            //                    }
            //                    if (DocumentsContract.isDocumentUri(getAppContext(), uri)) {
            //                        val documentFile = DocumentFile.fromTreeUri(getAppContext(), uri)
            //                        if (documentFile != null && documentFile.isDirectory) {
            //                            val filePath = getFilePathFromUri(uri)
            //                            if (filePath != null) {
            //                                val statFs = StatFs(filePath)
            //                                return statFs.availableBytes
            //                            }
            //                        }
            //                    }
            //                    return 0L
            //                }
        }
    }

val feedfilePath: String
    get() = getDataFolder(FEED_DOWNLOADPATH).toString() + "/"

//    fun getDownloadFolder(): DocumentFile? {
//        val sharedPreferences = getAppContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
//        val folderUriString = sharedPreferences.getString("custom_folder_uri", null)
//        return if (folderUriString != null) {
//            val folderUri = folderUriString.toUri()
//            DocumentFile.fromTreeUri(getAppContext(), folderUri)
//        } else null
//    }

//    fun findUnusedFile(dest: File): File? {
//        // find different name
//        var newDest: File? = null
//        for (i in 1 until Int.MAX_VALUE) {
//            val newName = (getBaseName(dest.name) + "-" + i + EXTENSION_SEPARATOR + getExtension(dest.name))
//            Logd(TAG, "Testing filename $newName")
//            newDest = File(dest.parent, newName)
//            if (!newDest.exists()) {
//                Logd(TAG, "File doesn't exist yet. Using $newName")
//                break
//            }
//        }
//        return newDest
//    }

fun ensureMediaFileExists(destinationUri: Uri) {
    Logd(TAG, "destinationUri: $destinationUri ${destinationUri.scheme}")
    when (destinationUri.scheme) {
        "file" -> {
            val file = File(destinationUri.path!!)
            if (!file.exists()) {
                try {
                    file.parentFile?.mkdirs()
                    file.createNewFile()
                } catch (e: IOException) { Logs(TAG, e, "ensureMediaFileExists Unable to create file") }
            }
        }
        "content" -> {
            try { getAppContext().contentResolver.openFileDescriptor(destinationUri, "rw")?.close()
            } catch (e: FileNotFoundException) { Logs(TAG, e, "file not exist $destinationUri:")
            } catch (e: Exception) { Logs(TAG, e, "Error checking file existence:") }
        }
        else -> throw IllegalArgumentException("Unsupported URI scheme: ${destinationUri.scheme}")
    }
}

fun quietlyDeleteFile(uri: Uri) {
    when(uri.scheme) {
        "content" -> {
            try {
                val documentFile = DocumentFile.fromSingleUri(getAppContext(), uri)
                documentFile?.delete()
            } catch (e: Throwable) { Logs(TAG, e, "quietlyDeleteFile Unable to delete file") }
        }
        "file" -> {
            try {
                val file = File(uri.path!!)
                file.delete()
            } catch (e: Throwable) { Logs(TAG, e, "quietlyDeleteFile Unable to delete file") }
        }
    }
}
fun getMimeType(fileName: String): String {
    val extension = fileName.substringAfterLast('.', "").lowercase()
    val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
    return mimeType ?: when (extension) {
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "aac" -> "audio/aac"
        "mp4" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        else -> "application/octet-stream" // Default fallback for unknown types
    }
}

private fun getTypeDir(baseDirPath: String?, type: String?): File? {
    if (baseDirPath == null) return null

    val baseDir = File(baseDirPath)
    val typeDir = if (type == null) baseDir else File(baseDir, type)
    if (!typeDir.exists()) {
        if (!baseDir.canWrite()) {
            Loge(TAG, "Base dir is not writable " + baseDir.absolutePath)
            return null
        }
        if (!typeDir.mkdirs()) {
            Loge(TAG, "Could not create type dir " + typeDir.absolutePath)
            return null
        }
    }
    return typeDir
}

/**
 * Return the folder where the app stores all of its data. This method will return the standard data folder if none has been set by the user.
 * @param type The name of the folder inside the data folder. May be null when accessing the root of the data folder.
 * @return The data folder that has been requested or null if the folder could not be created.
 */
fun getDataFolder(type: String?): File? {
    var dataFolder = getTypeDir(null, type)
    if (dataFolder == null || !dataFolder.canWrite()) {
        Logd(TAG, "User data folder not writable or not set. Trying default.")
        dataFolder = getAppContext().getExternalFilesDir(type)
    }
    if (dataFolder == null || !dataFolder.canWrite()) {
        Logd(TAG, "Default data folder not available or not writable. Falling back to internal memory.")
        dataFolder = getTypeDir(getAppContext().filesDir.absolutePath, type)
    }
    return dataFolder
}

fun deleteDirectoryRecursively(dir: DocumentFile): Boolean {
    if (dir.isDirectory) {
        for (file in dir.listFiles()) if (!deleteDirectoryRecursively(file)) return false
    }
    return dir.delete()
}

/**
 * Create a .nomedia file to prevent scanning by the media scanner.
 */
fun createNoMediaFile() {
    CoroutineScope(Dispatchers.IO).launch {
        if (customMediaUriString.isNotBlank()) {
            val customUri = customMediaUriString.toUri()
            val baseDir = DocumentFile.fromTreeUri(getAppContext(), customUri) ?: return@launch
            if (baseDir.isDirectory) {
                val nomediaFile = baseDir.findFile(".nomedia")
                if (nomediaFile == null) {
                    try {
                        baseDir.createFile("text/plain", ".nomedia")
                        Logd(TAG, ".nomedia file created in $customMediaUriString")
                    } catch (e: Throwable) {
                        Logs(TAG, e, "Could not create .nomedia file in $customMediaUriString")
                    }
                }
            }
        } else {
            val f = File(getAppContext().getExternalFilesDir(null), ".nomedia")
            if (!f.exists()) {
                try {
                    f.createNewFile()
                    Logd(TAG, ".nomedia file created")
                } catch (e: IOException) { Logs(TAG, e, "Could not create .nomedia file") }
            }
        }
    }
}

private val ACCENT_MAP: Map<Char, Char> = mapOf(
    'À' to 'A','Á' to 'A','Â' to 'A','Ã' to 'A','Ä' to 'A','Å' to 'A',
    'à' to 'a','á' to 'a','â' to 'a','ã' to 'a','ä' to 'a','å' to 'a',
    'Ç' to 'C','ç' to 'c',
    'È' to 'E','É' to 'E','Ê' to 'E','Ë' to 'E',
    'è' to 'e','é' to 'e','ê' to 'e','ë' to 'e',
    'Ì' to 'I','Í' to 'I','Î' to 'I','Ï' to 'I',
    'ì' to 'i','í' to 'i','î' to 'i','ï' to 'i',
    'Ñ' to 'N','ñ' to 'n',
    'Ò' to 'O','Ó' to 'O','Ô' to 'O','Õ' to 'O','Ö' to 'O',
    'ò' to 'o','ó' to 'o','ô' to 'o','õ' to 'o','ö' to 'o',
    'Ù' to 'U','Ú' to 'U','Û' to 'U','Ü' to 'U',
    'ù' to 'u','ú' to 'u','û' to 'u','ü' to 'u',
    'Ý' to 'Y','ý' to 'y','ÿ' to 'y',
    'Æ' to 'A','æ' to 'a','Ø' to 'O','ø' to 'o',
    'Þ' to 'T','þ' to 't','ß' to 's'
)

/**
 * This method will return a new string that doesn't contain any illegal characters of the given string.
 */
fun generateFileName(input: String, replacement: Char = '-'): String {
    val buf = StringBuilder(input.length)
    for (ch in input) {
        val c = ACCENT_MAP[ch] ?: ch
        if (Character.isSpaceChar(c) && (buf.isEmpty() || Character.isSpaceChar(buf[buf.length - 1]))) continue
        if (validChars.contains(c)) buf.append(c)
    }
    val filename = buf.toString().trim { it <= ' ' }

    return when {
        filename.isEmpty() -> {
            val length = 8
            val sb = StringBuilder(length)
            for (i in 0 until length) sb.append(validChars[(Math.random() * validChars.size).toInt()])
            sb.toString()
        }
        filename.length >= MAX_FILENAME_LENGTH -> filename.take(MAX_FILENAME_LENGTH - MD5_HEX_LENGTH - 1) + "_" + md5(filename)
        else -> filename
    }
}

private fun md5(md5: String): String? {
    try {
        val md = MessageDigest.getInstance("MD5")
        val array = md.digest(md5.toByteArray(charset("UTF-8")))
        val sb = StringBuilder()
        for (b in array) sb.append(Integer.toHexString((b.toInt() and 0xFF) or 0x100).substring(1, 3))
        return sb.toString()
    } catch (e: NoSuchAlgorithmException) { return null
    } catch (e: UnsupportedEncodingException) { return null }
}

fun getFreeSpaceAvailable(path: String?): Long {
    val stat = StatFs(path)
    val availableBlocks = stat.availableBlocksLong
    val blockSize = stat.blockSizeLong
    return availableBlocks * blockSize
}

fun getTotalSpaceAvailable(path: String?): Long {
    val stat = StatFs(path)
    val blockCount = stat.blockCountLong
    val blockSize = stat.blockSizeLong
    return blockCount * blockSize
}

class AddLocalFolder : ActivityResultContracts.OpenDocumentTree() {
    override fun createIntent(context: Context, input: Uri?): Intent {
        return super.createIntent(context, input).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

class CountingInputStream2(
    private val delegate: InputStream
) : FilterInputStream(delegate) {
    var count: Int = 0
        private set

    override fun read(): Int {
        val r = super.read()
        if (r >= 0) count++
        return r
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val r = super.read(b, off, len)
        if (r > 0) count += r
        return r
    }
}