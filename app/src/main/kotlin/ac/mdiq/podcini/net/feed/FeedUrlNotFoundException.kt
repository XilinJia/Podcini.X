package ac.mdiq.podcini.net.feed

import java.io.IOException

class FeedUrlNotFoundException( val artistName: String,  val trackName: String) : IOException() {
    override val message: String
        get() = "Result does not specify a feed url"
}