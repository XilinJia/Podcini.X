package ac.mdiq.podcini.net.feed.parser.media.vorbis

class VorbisCommentReaderException : Exception {
    constructor(message: String?) : super(message)

    constructor(message: Throwable?) : super(message)

    companion object {
        private const val serialVersionUID = 1L
    }
}
