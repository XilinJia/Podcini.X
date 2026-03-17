package ac.mdiq.podcini.storage.utils

import ac.mdiq.podcini.PodciniApp.Companion.getAppContext
import ac.mdiq.podcini.storage.database.appPrefs
import ac.mdiq.podcini.storage.database.upsert
import ac.mdiq.podcini.storage.database.upsertBlk
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Loge
import ac.mdiq.podcini.utils.Logs
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import io.ktor.http.ContentType
import io.ktor.http.Url
import io.ktor.utils.io.charsets.Charset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.io.IOException
import kotlinx.io.files.FileNotFoundException
import okio.Buffer
import okio.FileSystem
import okio.ForwardingSource
import okio.Path
import okio.Path.Companion.toPath
import okio.Sink
import okio.Source
import okio.buffer
import okio.sink
import okio.source
import java.io.File
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

/**
 * Utility functions for handling storage errors
 */
private const val TAG: String = "StorageUtils"

const val MAX_FILENAME_LENGTH: Int = 242 // limited by CircleCI

private const val MD5_HEX_LENGTH = 32

private val validChars = ("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 _-").toCharArray()

val customMediaUriString: String
    get() = if (appPrefs.useCustomMediaFolder) appPrefs.customMediaUri else ""

val fs = FileSystem.SYSTEM

val internalDir: UnifiedFile
    get() = PathFile(getAppContext().filesDir.absolutePath.toPath())

val mediaDir: UnifiedFile
    get() {
        if (customMediaUriString.isNotBlank()) {
            val d = customMediaUriString.toUF()
            if (d.isDirectory()) {
                Logd(TAG, "mediaDir: $customMediaUriString")
                return d
            } else {
                Loge(TAG, "The chosen custom media folder is not valid: ${appPrefs.customMediaUri}. Reset!")
                upsertBlk(appPrefs) {
                    it.useCustomMediaFolder = false
                    it.customMediaUri = ""
                    it.customFolderUnavailable = true
                }
            }
        }
        Logd(TAG, "mediaDir use internal media folder")
        val mediaDir_ = getAppContext().getExternalFilesDir("media") ?: throw IllegalArgumentException("Invalid media dir")
        return mediaDir_.toUF()
    }


val clipsDir: UnifiedFile
    get() {
        var dir = internalDir / "clips"
        runBlocking { if (!dir.exists()) dir = internalDir.createDirectory("clips") }
        return dir
    }

/**
 * Get the number of free bytes that are available on the external storage.
 */
val freeSpaceAvailable: Long
    get() {
        return if (customMediaUriString.isBlank()) {
            val stat = StatFs(internalDir.absPath)
            val availableBlocks = stat.availableBlocksLong
            val blockSize = stat.blockSizeLong
            availableBlocks * blockSize
        } else {
            val storageManager = getAppContext().getSystemService(Context.STORAGE_SERVICE) as StorageManager
            val appSpecificDir = getAppContext().getExternalFilesDir(null) ?: return 0L
            val uuid = storageManager.getUuidForPath(appSpecificDir)
            storageManager.getAllocatableBytes(uuid)
        }
    }

val cacheDir: UnifiedFile = internalDir / "cache"

suspend fun createCacheDir() {
    if (!cacheDir.exists()) internalDir.createDirectory("cache")
}

var tempRoottree by mutableStateOf<Uri?>(null)

val persistedTrees by lazy { getAppContext().contentResolver.persistedUriPermissions.filter { DocumentsContract.isTreeUri(it.uri) }.map { it.uri }.toMutableSet() }

fun findRootForUri(uri: Uri): Uri? {
    try {
        val childId = DocumentsContract.getDocumentId(uri)
        return persistedTrees.find { rootUri ->
            val treeId = DocumentsContract.getTreeDocumentId(rootUri)
            childId.startsWith(treeId) && rootUri.authority == uri.authority
        }
    } catch (e: Exception) { return null }
}

fun String.OKPath(): Path {
    Logd(TAG, "String.OKPath() $this")
    val uri = this.toSafeUri()
    return when (uri.scheme) {
        "file" -> uri.path?.toPath() ?: throw kotlinx.io.IOException("Invalid file URI")
        "content" -> this.toPath()
        else -> toPath()
    }
}

fun String.toSafeUri(): Uri {
    Logd(TAG, "String.toSafeUri() $this")
    return when {
        startsWith("content://") || startsWith("file://") || startsWith("http") -> this.toUri()
        startsWith("android_asset/") -> "file:///android_asset/${this.substring(14)}".toUri()
        startsWith("/") -> Uri.fromFile(File(this))
        else -> this.toUri()
    }
}

suspend fun quietlyDeleteFile(uri: Uri) {
    val file = uri.toUF()
    file.delete()
}

interface UnifiedFile {
    val name: String

    val absPath: String

    val extension: String?
        get() {
            val name = this.name
            val dot = name.lastIndexOf('.')
            if (dot <= 0 || dot == name.length - 1) return null
            return name.substring(dot + 1)
        }

    suspend fun exists(): Boolean

    suspend fun delete(): Boolean

    fun isDirectory(): Boolean
    suspend fun listChildren(): List<UnifiedFile>

    suspend fun createDirectory(name: String): UnifiedFile
    suspend fun createFile(mimeType: String, name: String): UnifiedFile

//    suspend fun createFile(uri: Uri): UnifiedFile?

    suspend fun createFile(): UnifiedFile

    fun source(): Source
    fun sink(append: Boolean = false): Sink
    suspend fun size(): Long?

    suspend fun readBytes(): ByteArray = source().buffer().use { it.readByteArray() }

    suspend fun readString(charset: Charset = Charsets.UTF_8): String = source().buffer().use { it.readString(charset) }

    suspend fun writeBytes(bytes: ByteArray) = sink(append = false).buffer().use { it.write(bytes) }

    suspend fun appendBytes(bytes: ByteArray) = sink(append = true).buffer().use { it.write(bytes) }

    suspend fun writeString(string: String, charset: Charset = Charsets.UTF_8) {
        sink(append = false).buffer().use { sink ->
            sink.writeString(string, charset)
            sink.flush()
        }
    }

    suspend fun copyTo(target: UnifiedFile) {
        source().buffer().use { src -> target.sink().buffer().use { dst -> dst.writeAll(src) } }
    }

    suspend fun moveTo(target: UnifiedFile) {
        copyTo(target)
        delete()
    }
}

class PathFile(
    val path: Path,
    val fs: FileSystem = FileSystem.SYSTEM
) : UnifiedFile {

    override val name: String
        get() = path.name

    override val absPath: String = path.toString()

    override suspend fun exists(): Boolean = fs.exists(path)

    override fun isDirectory(): Boolean = fs.metadata(path).isDirectory

    override suspend fun listChildren(): List<UnifiedFile> = fs.list(path).map { PathFile(it) }

    override suspend fun delete(): Boolean =
        try {
            fs.delete(this.path)
            true
        } catch (e: Throwable) { false }

    override suspend fun createFile(mimeType: String, name: String): UnifiedFile {
        if (!fs.exists(path)) throw IllegalStateException("Parent path does not exist: $path")
        if (!fs.metadata(path).isDirectory) throw IllegalStateException("Cannot create file under a file: $path")
        val newPath: Path = path / name
        if (!fs.exists(newPath)) fs.sink(newPath).use { }
        return PathFile(newPath, fs)
    }

    override suspend fun createFile(): UnifiedFile {
        writeString("")
        return this
    }

    override suspend fun createDirectory(name: String): UnifiedFile {
        if (!fs.exists(path) || !fs.metadata(path).isDirectory) throw IllegalStateException("Cannot create a subdirectory under a non-directory path: $path")
        val newDirPath = path / name
        fs.createDirectories(newDirPath)
        return PathFile(newDirPath, fs)
    }

    override fun source(): Source = fs.source(path)

    override fun sink(append: Boolean): Sink = if (append) fs.appendingSink(path) else fs.sink(path)

    override suspend fun size(): Long? = try { fs.metadata(path).size } catch (e: Exception) { null }
}

class ContentUriFile(
    val uri: Uri,
    val context: Context = getAppContext()
) : UnifiedFile {

    val docFile: DocumentFile? = if (isTreeRoot) DocumentFile.fromTreeUri(context, uri) else DocumentFile.fromSingleUri(context, uri)

    val isTreeRoot: Boolean
        get() {
//            Logd(TAG, "isTreeRoot contentRoot: $contentRoot")
//            Logd(TAG, "isTreeRoot uri: $uri")
            return uri in persistedTrees
//            return try { DocumentsContract.getTreeDocumentId(uri) == DocumentsContract.getTreeDocumentId(mediaDir.toAndroidUri()) } catch (e: Exception) { false }
        }

    fun queryMimeType(): String? =
        context.contentResolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }

    fun findSavedRoot(): Uri? {
        Logd(TAG, "findSavedRoot uri: $uri")
        if (tempRoottree != null && uri.toString().startsWith(tempRoottree.toString())) return tempRoottree

        persistedTrees.forEach { Logd(TAG, "saved toor: $it") }
        return persistedTrees.find { uri.toString().startsWith(it.toString()) }
    }

    override val name: String
        get() = docFile?.name ?: "unknown"

    override val absPath: String = uri.toString()

    override suspend fun exists(): Boolean {
        if (isTreeRoot) return true
        return try {
            val df = if (DocumentsContract.isDocumentUri(context, uri)) DocumentFile.fromSingleUri(context, uri) else DocumentFile.fromTreeUri(context, uri)
            df?.exists() == true
        } catch (e: FileNotFoundException) { false } catch (e: IllegalArgumentException) { false }
        return false
    }

    override suspend fun size(): Long? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (index != -1) cursor.getLong(index) else null
                    } else null
                }
        } catch (_: Exception) { null }
    }

    override fun isDirectory(): Boolean {
        Logd(TAG, "isDirectory")
        if (isTreeRoot) return true
        val projection = arrayOf(DocumentsContract.Document.COLUMN_MIME_TYPE)
        context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val mime = c.getString(0)
                return mime == DocumentsContract.Document.MIME_TYPE_DIR
            }
        }
        return false
    }

    override suspend fun listChildren(): List<UnifiedFile> {
        val rootUri = findSavedRoot() ?: uri
        Logd(TAG, "listChildren rootUri: $rootUri")
        val result = mutableListOf<Uri>()
        val parentId = if (rootUri == uri) DocumentsContract.getTreeDocumentId(rootUri) else DocumentsContract.getDocumentId(uri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(rootUri, parentId)
        context.contentResolver.query(childrenUri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val childId = cursor.getString(0)
                val childUri = DocumentsContract.buildDocumentUriUsingTree(rootUri, childId)
                if (childUri == uri) continue
                result.add(childUri)
            }
        }
        return result.map { ContentUriFile(it, context) }
    }

    override fun source(): okio.Source {
        Logd(TAG, "source")
//        val uri: Uri = docFile?.uri ?: throw IllegalStateException("Cannot open input stream for $uri")
        val inputStream = context.contentResolver.openInputStream(uri) ?: throw IllegalStateException("Cannot open input stream for $uri")
        return inputStream.source()
    }

    override fun sink(append: Boolean): okio.Sink {
        Logd(TAG, "sink")
//        val uri: Uri = docFile?.uri ?: throw IllegalStateException("Cannot open input stream for $uri")
        val mode = if (append) "wa" else "w"
        val outputStream = context.contentResolver.openOutputStream(uri, mode) ?: throw IllegalStateException("Cannot open output stream for $uri")
        return outputStream.sink()
    }

    override suspend fun delete(): Boolean {
        Logd(TAG, "delete ${docFile?.uri}")
        return docFile?.delete() ?: false
    }

    override suspend fun createFile(mimeType: String, name: String): UnifiedFile {
        Logd(TAG, "createFile")
        val rootUri = findSavedRoot() ?: uri
        Logd(TAG, "createFile rootUri: $rootUri")
        val newUri = try {
            val parentId = if (rootUri == uri) DocumentsContract.getTreeDocumentId(rootUri) else DocumentsContract.getDocumentId(uri)
            val parentUri = DocumentsContract.buildDocumentUriUsingTree(uri, parentId)
            DocumentsContract.createDocument(context.contentResolver, parentUri, mimeType, name)
        } catch (e: Exception) { throw IOException("failed creating file $name under $uri") }
        return ContentUriFile(newUri!!)
    }

    override suspend fun createFile(): UnifiedFile {
        val docId = DocumentsContract.getDocumentId(uri)
        val parentId = if (docId.contains("/")) docId.substringBeforeLast("/") else DocumentsContract.getTreeDocumentId(uri)
        val name = docId.substringAfterLast('/')
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(uri, parentId)
        val newFileUri = try { DocumentsContract.createDocument(context.contentResolver, parentUri, "application/octet-stream", name) } catch (e: Exception) { throw IOException("failed creating file at $uri") }
        return ContentUriFile(newFileUri!!)
    }

    override suspend fun createDirectory(name: String): UnifiedFile {
        Logd(TAG, "createDirectory $name $uri")
        val rootUri: Uri? = findSavedRoot() ?: uri
        val parentDocId: String = if (rootUri == uri) DocumentsContract.getTreeDocumentId(rootUri) else DocumentsContract.getDocumentId(uri)
        val parentUri: Uri = DocumentsContract.buildDocumentUriUsingTree(uri, parentDocId)
        val newUri = DocumentsContract.createDocument(context.contentResolver, parentUri, DocumentsContract.Document.MIME_TYPE_DIR, name) ?: throw IOException("error creating directory $name under $uri")
        return ContentUriFile(newUri, context)
    }
}

operator fun UnifiedFile.div(child: String): UnifiedFile = when (this) {
    is PathFile -> PathFile(this.path / child, this.fs)
    is ContentUriFile -> {
        if (DocumentsContract.isTreeUri(this.uri)) {
            val parentId = DocumentsContract.getTreeDocumentId(this.uri)?: throw IOException("getting parentId for uri failed: $uri")
            val childUri = DocumentsContract.buildDocumentUriUsingTree(this.uri, "$parentId/$child")
            ContentUriFile(childUri)
        } else throw UnsupportedOperationException("Cannot append child to document URI")
    }
    else -> throw UnsupportedOperationException("Unsupported UnifiedFile type")
}

fun UnifiedFile.parent(): UnifiedFile? = when (this) {
    is PathFile -> this.path.parent?.let { PathFile(it, fs) }
    is ContentUriFile -> this.docFile?.parentFile?.let { ContentUriFile(it.uri, context) }
    else -> null
}

fun UnifiedFile.toAndroidUri(): Uri? = when (this) {
    is PathFile -> absPath.toUri()
    is ContentUriFile -> uri
    else -> null
}

fun File.toUF(): UnifiedFile = PathFile(absolutePath.toPath())

fun Uri.toUF(): UnifiedFile {
    Logd(TAG, "Uri.toUF() $this")
    return when (scheme) {
        "file" -> PathFile(this.path!!.toPath())
        "content" -> ContentUriFile(this)
        else -> PathFile(this.toString().toPath())
    }
}

fun String.toUF(): UnifiedFile {
    Logd(TAG, "String.toUF() $this")
    val uri = try { this.toSafeUri() } catch (e: Exception) { null }
    return when (uri?.scheme) {
        null -> PathFile(this.toPath())
        "content" -> ContentUriFile(uri)
        "file" -> PathFile(uri.path!!.toPath())
        else -> error("Unsupported URI scheme: ${uri.scheme}")
    }
}

suspend fun deleteDirectoryRecursively(dir: UnifiedFile) {
    Logd(TAG, "deleteDirectoryRecursively ${dir.absPath}")
    if (dir.isDirectory()) {
        for (file in dir.listChildren()) deleteDirectoryRecursively(file)
    }
    dir.delete()
}

/**
 * Create a .nomedia file to prevent scanning by the media scanner.
 */
fun createNoMediaFile() {
    CoroutineScope(Dispatchers.IO).launch {
        val nomediaFile = mediaDir / ".nomedia"
        if (!nomediaFile.exists()) {
            try { mediaDir.createFile("", ".nomedia")
            } catch (e: Exception) {
                Logs(TAG, e, "failed creating .nomedia file")
                if (customMediaUriString.isNotBlank()) {
                    upsert(appPrefs) {
                        it.useCustomMediaFolder = false
                        it.customMediaUri = ""
                        it.customFolderUnavailable = true
                    }
                }
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

fun guessFileName(url: String, contentDisposition: String?, mimeType: String?): String {
    var filename: String? = null
    if (contentDisposition != null) filename = Regex("filename\\s*=\\s*\"?([^\";]+)\"?").find(contentDisposition)?.groupValues?.get(1)
    if (filename == null) {
        val decodedUrl = try { Url(url).encodedPath } catch (e: Exception) { url }
        val lastSegment = decodedUrl.split('/').lastOrNull { it.isNotEmpty() }
        filename = lastSegment?.substringBefore('?') ?: "downloadfile"
    }
    val extension = mimeType?.let { ContentType.parse(it).contentSubtype }
    return if (!filename.contains(".") && extension != null) "$filename.$extension" else { filename }
}

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
    } catch (e: Exception) { return null }
}

class AddLocalFolder : ActivityResultContracts.OpenDocumentTree() {
    override fun createIntent(context: Context, input: Uri?): Intent {
        return super.createIntent(context, input).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

class CountingSource(delegate: Source) : ForwardingSource(delegate) {
    var count: Long = 0
        private set

    override fun read(sink: Buffer, byteCount: Long): Long {
        val read = super.read(sink, byteCount)
        if (read > 0) count += read
        return read
    }
}
