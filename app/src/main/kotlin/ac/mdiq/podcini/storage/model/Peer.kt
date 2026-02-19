package ac.mdiq.podcini.storage.model

import io.github.xilinjia.krdb.types.EmbeddedRealmObject
import io.github.xilinjia.krdb.types.annotations.Index

class Peer: EmbeddedRealmObject {
    @Index
    var id: Long = 0

    var uid: String = ""

    var name: String = ""

    var nickname: String = ""
}