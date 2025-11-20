package ac.mdiq.podcini.net.download

class DownloadStatus(
         val state: Int,
         val progress: Int) {

    enum class State {
        UNKNOWN,
        QUEUED,
        RUNNING,
        COMPLETED,     // Both successful and not successful
        INCOMPLETE
    }
}
