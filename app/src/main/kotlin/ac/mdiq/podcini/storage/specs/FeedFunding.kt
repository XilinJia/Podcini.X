package ac.mdiq.podcini.storage.specs

class FeedFunding( var url: String?, var content: String?) {
    override fun equals(other: Any?): Boolean {
        if (other == null || other.javaClass != this.javaClass) return false
        val funding = other as FeedFunding
        if (url == null && funding.url == null && content == null && funding.content == null) return true
        if (url != null && url == funding.url && content != null && content == funding.content) return true

        return false
    }

    override fun hashCode(): Int {
        return (url + FUNDING_TITLE_SEPARATOR + content).hashCode()
    }

    companion object {
        const val FUNDING_ENTRIES_SEPARATOR: String = "\u001e"
        const val FUNDING_TITLE_SEPARATOR: String = "\u001f"


        fun extractPaymentLinks(payLinks: String?): MutableList<FeedFunding> {
            if (payLinks.isNullOrBlank()) return arrayListOf()

            // old format before we started with PodcastIndex funding tag
            val funding = mutableListOf<FeedFunding>()
            if (!payLinks.contains(FUNDING_ENTRIES_SEPARATOR) && !payLinks.contains(FUNDING_TITLE_SEPARATOR)) {
                funding.add(FeedFunding(payLinks, ""))
                return funding
            }
            val list = payLinks.split(FUNDING_ENTRIES_SEPARATOR.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            if (list.isEmpty()) return arrayListOf()

            for (str in list) {
                val linkContent = str.split(FUNDING_TITLE_SEPARATOR.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                if (linkContent[0].isBlank()) continue

                val url = linkContent[0]
                var title = ""
                if (linkContent.size > 1 && linkContent[1].isNotBlank()) title = linkContent[1]
                funding.add(FeedFunding(url, title))
            }
            return funding
        }

        fun getPaymentLinksAsString(fundingList: MutableList<FeedFunding>?): String? {
            val result = StringBuilder()
            if (fundingList == null) return null

            for (fund in fundingList) {
                result.append(fund.url).append(FUNDING_TITLE_SEPARATOR).append(fund.content)
                result.append(FUNDING_ENTRIES_SEPARATOR)
            }
            return result.toString().removeSuffix(FUNDING_ENTRIES_SEPARATOR)
        }
    }
}