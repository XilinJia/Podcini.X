package ac.mdiq.podcini.net.download

import ac.mdiq.podcini.R
import androidx.annotation.StringRes

/** Utility class for Download Errors.  */
/** Get machine-readable code.  */
enum class DownloadError(val code: Int, @StringRes val res:  Int) {
    SUCCESS(0, R.string.download_successful),
    ERROR_PARSER_EXCEPTION(1, R.string.download_error_parser_exception),
    ERROR_UNSUPPORTED_TYPE(2, R.string.download_error_unsupported_type),
    ERROR_CONNECTION_ERROR(3, R.string.download_error_connection_error),
    ERROR_MALFORMED_URL(4, R.string.download_error_malformed_url),
    ERROR_IO_ERROR(5, R.string.download_error_io_error),
    ERROR_FILE_EXISTS(6, R.string.download_error_file_exist),
    ERROR_DOWNLOAD_CANCELLED(7, R.string.download_canceled_msg),
    ERROR_DEVICE_NOT_FOUND(8, R.string.download_error_device_not_found),
    ERROR_HTTP_DATA_ERROR(9, R.string.download_error_http_data_error),
    ERROR_NOT_ENOUGH_SPACE(10, R.string.download_error_insufficient_space),
    ERROR_UNKNOWN_HOST(11, R.string.download_error_unknown_host),
    ERROR_REQUEST_ERROR(12, R.string.download_error_request_error),
    ERROR_DB_ACCESS_ERROR(13, R.string.download_error_db_access),
    ERROR_UNAUTHORIZED(14, R.string.download_error_unauthorized),
    ERROR_FILE_TYPE(15, R.string.download_error_file_type),
    ERROR_FORBIDDEN(16, R.string.download_error_forbidden),
    ERROR_IO_WRONG_SIZE(17, R.string.download_error_wrong_size),
    ERROR_IO_BLOCKED(18, R.string.download_error_blocked),
    ERROR_UNSUPPORTED_TYPE_HTML(19, R.string.download_error_unsupported_type_html),
    ERROR_NOT_FOUND(20, R.string.download_error_not_found),
    ERROR_CERTIFICATE(21, R.string.download_error_certificate),
    ERROR_MISC(50, R.string.download_error_misc);

    companion object {
        /** Return DownloadError from its associated code.  */
        fun fromCode(code: Int): DownloadError {
            return DownloadError.entries.firstOrNull { it.code == code } ?: throw IllegalArgumentException("unknown code: $code")
        }
    }
}
