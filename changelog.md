# 8.10.3

* removed the itunes duration being 0.0 error
* in Overview tab of Statistics, "-7" and "+7" on both sides of Today

# 8.10.2

* some overhaul of Statistics screen
	* marked Played, Skipped, Passed and Ignored now are set separately, only on the topbar in tabs Overview, Subscriptions and Months
	* "include marked played" is no longer on the dates filter dialog
	* the played time of an episode marked as any of the four is the full duration, otherwise the played duration
	* "Include marked (any of the four)" only adds those episodes never started but marked as such, episodes started playing are always included
	* in Overview tab, "Today" can be changed to any previous day
* the long useless Viewbinding is turned off in build

# 8.10.1

* likely fixed itunes duration could not parse error
* fixed crash trying to delete negative number of episodes with policy Replace in auto-download

# 8.10.0

* fixed filter on Passed in episodes lists
* in episodes lists, if state is above Skipped, then if available, played dates, completion date, play state set date, and played duration are shown
* in Statistics, "Include marked Played" now includes Played, Passed, and Ignored.  Skipped is excluded.
* feed statistics now shows episodes played and marked played
* in Statistics screen, "Today" is clickable to show episodes played (and marked played if set to include) of today
* in Monthly tab of Statistics, each month bar and month text can be clicked to show episodes played (and marked played if set to include) of the month
* replaced all ArrayList with MutableList
* bumped min SDK to 26 (Android 7)
* updated some Compose dependencies

# 8.9.4

* fixed auto-download not executing since 8.9.0
* in episodes lists, fixed only selecting loaded episodes when Select all, Select all above, or Select all below is chosen

# 8.9.3

* fixed includeMarkedAsPlayed action not reloading in Statistics
* added play state Passed to distinguish from Ignored
* when episode starts playing, if its state was below Progress, or was Skipped or Again, set the state to Progress
* when episode is finished playing, if its state was below or equal Progress, or was Skipped, Passed or Ignored, set the state to Played
* fixed settings for fallback speed and fast forward speed 

# 8.9.2

* likely fixed the misbehaving "add comment" in SwipeActions
* in Search screen, the text input allows using either comma or space as delimit
	* when comma is present, the query text will be split by comma, otherwise, by space
	* example: "text1 text2 text3" search for the three texts (with OR), while "text1 text2, text3" searches for "text1 text2" OR "text3"
* avoided setting episode of state Again or Forever to Played after playing
* added SwipeAction SearchSelected allowing to search any selected text
* includeMarkedAsPlayed is an action item on the topbar of Statistics screen
* in Overview tab of Statistics, enabling includeMarkedAsPlayed does not reset the period 

# 8.9.1

* amended add comment in SwipeActions to better handle current media
* New episodes are also auto-marked as Unplayed in feeds set to auto-enqueue
* number of candidates in auto-download and auto-enqueue algorithms is reduced to number allowed for the Replace policy
* episodes with downloadUrl null or blank is stripped of auto-download candidates
* auto-download on empty queue is set for individual queues, need to be reset if set in version 8.9.0
* delete episode media no longer notifies BackupManager
* turned off toasts in http downloader on downloaded cancelled
* added some toast messages
* adjusted layout of EpisodeInfo screen
* base activity of the play app is changed back to AppCompatActivity as casting requires it

# 8.9.0

* stop toasting "feed update cancelled" messages
* adjusted layouts of FeedDetails and EpisodeInfo screens
* adjusted order of PlayStates Again, Forever and Skipped (DB migration is performed on first start)
* when shelving episodes to a synthetic feed, title, downloadUrl and link of the original feed is preserved
* in EpisodeInfo, if feed is synthetic, original feed title is shown if available, 
* when unsubscribing feeds, dialog prompts to preserve important episodes (default to true)
	* once checked, episodes rated Good or Super, having comments, or with play state set to Again or Forever are shelved to synthetic feed "Preserve Syndicate"
* added algorithms auto-download/auto-enqueue on empty queue to download/enqueue episodes in associated feeds of the queue
	* works only for policies other than "Only new" (all policies are still performed during update)
	* auto-enqueue on empty queue is enabled
	* auto-download on empty queue is optional and can be set in Settings->Downloads->Automatic downloads
* added property binLimit in each queue (defaulted to 0, unlimited, not resettable in this version)
* added ability to auto-trim bin when bin size exceeds 120% of binLimit

# 8.8.2

* straightened the filter settings in feed auto-download and auto-enqueue
* to avoid getting filtered out, added more potential candidates in auto-download and auto-enqueue algorithms
* fixed statistics screen counting not reset and not reflecting "include Marked As Played"
* amended FeedDetails screen. 
	* control buttons are moved to the topbar
	* tap on cover image to toggle between list and info
	* reduced height of header
* amended EpisodeInfo screen
	* most control buttons are moved to the topbar
	* the play/stream button is moved to the lower-right corner of the header, and is long-pressable
* ensured toast messages are properly logged
* added some more toast messages

# 8.8.1

* when setting play state on an episode, a time stamp is recorded
	* initialized playStateSetTime (for Skipped, Played, Again, Forever and Ignored) to "last played time" if available or first launch time of this version
* corrected statistics calculation for "include Marked As Played"
* topbar action filter is re-enabled in Subscriptions tab in Statistics
* clears memory on dispose of Statistics screen
* amended auto-download algorithm: NEW episodes only in auto-download enabled feeds are auto-marked as Unplayed
* fixed issue of empty text being set in auto-download exclusive filter that could prevent auto-download
* made loading feeds more efficient in Facets when Spinner is for All
* in OnlineSearch screen, the prompt (on reinstall) of restoring from OPML shows number of feeds included

# 8.8.0

* renamed screen Episodes to Facets and added showing of related feeds
* added Overview tab in Statistics screen
* restructured Statistics screen, enhanced loading efficiency when changing tabs
* tuned some toast messages

# 8.7.0

* ensure update worker is re-launched on a new version of the app
* shows confirmation dialog for adding a comment when setting episodes to Ignore
* show confirmation dialog when deleting repeat episodes
* adjusted order of Combo swipe actions
* added "add to associated queue" in swipe actions and episodes multi-select menu, and it take precedence to add to active queue
* in feed settings, enabling prefer streaming turns off auto-download of the feed and turns on global setting of prefer streaming
* likely fixed a persistence issue of prefer streaming in feed settings 
* added max duration filter for auto-download and auto-enqueue
	* now both episodes with duration shorter than min duration or longer than max duration can be excluded
* in auto-download algorithm, queue item is added for auto-download only when either the global setting or the feed setting of "prefer streaming" is false
* in episodes list, stream button is shown on an episode when prefer streaming is set both globally and in the feed
* tuned some toast messages

# 8.6.3

* cleared out most star imports
* added checks (even though it sounds unnecessary) to ensure feed update task not doubly scheduled
* reordered and tuned colors in multi-selection menus
* ComboSwipeAction menu only shows relevant actions
* added some more toast message in refresh and auto-download routines
* ensured toast messages are created in Main dispatcher
* messages in log now has time stamp

# 8.6.2

* fixed malfunctioning adding opinions
* fixed auto-download not function caused by auto-downloadable episodes being reset by auto-enqueue algorithm
* moved setting of counting played in feed settings into "Episode cache" dialog
* moved "Auto delete episode" setting in feed settings and added explanation text

# 8.6.1

* fixed auto-download setting not persisted in feed settings

# 8.6.0

* fixed EpisodeInfo opened from AudioPlayer shows behind AudioPlayer
* fixed auto-download filter duration unit mismatch issue
* in feed settings
	* rearranged some items
	* added auto-enqueue
		* requires feed associated queue not set to None
		* mutually exclusive with auto-download setting in the feed
		* works in similar ways as auto-download except that the episodes are not downloaded but only added to associated queue
		* also ruled by settings of policy and cache etc in the feed
* added AutoEnqueueAlgorithm
* in each auto-download feed, number of candidates is at max equal to the number allowed, a change from having some extras
	* also ruled by settings of policy and cache etc in the feed
* auto-download Soon is no longer a normal policy but an option taking precedence over normal policies
* cleaned AutoDownloadAlgorithm
* Runnable in Thread is converted to Coroutine in AutoDownloadAlgorithm and in PlaybackService for position server and sleep time, improving efficiency
* replaced Runnable and Consumer with Kotlin functions in most cases
* in Settings->Downloads, "Use custom media folder" is relocated to Details
* adjusted some toast messages

# 8.5.5

* avoided some screens reload due to past FeedUpdatingEvent and EpisodeDownloadEvent
* ensured SyncService to wait for FeedUpdatingEvent
* super class Worker is changed to CoroutineWorker
* rearranged and amended topbar items in AudioPlayer
	* home button now opens EpisodeInfo
* feed auto-downloaded cache set to 0 now taken as unlimited
* updated Compose dependencies

# 8.5.4

* adjusted toast categories: notifications, errors, exceptions
* replaced Android MaterialAlertdialog with Compose dialog
* disabled toasting of some ignorable errors
* avoid emitting FeedUpdatingEvent on start that causes unnecessary reload in Subscriptions screen
* avoid emitting EpisodeDownloadEvent on start 

# 8.5.3

* enhanced getNextInQueue routine
* likely fixed the nasty timeSpent problem that appears to mess up the numbers when manually switching episodes in Queues
* restored setting of prefSkipSilence, not sure how well it works though
* more printouts are stripped from release app or added to toast
* all caught exceptions are logged with toasts
* on metered network, if episode download is not set to allow, mass download shows a dialog
* some code adjustments and cleaning

# 8.5.2

* corrected title of Session logs in Logs screen
* changed default setting of prefSkipKeepsEpisode to true (more proper)
* removed lazy get for preference properties to react to changes
* removed the non-free and malfunctioning fyyd search and dependency of rxjava
* OnlineEpisodes is merged into OnlineFeed screen for better interaction
* amended info items in FeedDetails 
* amended line charts in Statistics
* amended feed statistics dialog
* cleaned out some useless strings resources

# 8.5.1

* amended some logging
* Android Toast messaging removed

# 8.5.0

* most errors are shown as toast messages
* error toasts can be toggled in Settings -> User interface -> Show error toasts (default to true)
* toast messages (error or not) are cached for the session and can be shown in Logs screen
	* this is true even "Show error toasts" is turned off
* most error logcat messages are turned off for the release app 
* added a setting to toggle logging of all debug messages in Settings -> User interface -> Print debug logs (default to false)
* updated some deprecated routines in NetworkUtils
* set refresh failure backoff criteria to 25% of refresh interval
* in Search screen, previous search term and results are preserved (for the session) in all cases
* fixed some discrepancies of the default values between the Settings and the usage
* consolidated handling of default values in preferences

# 8.4.0

* fixed feed statistics display numbers
* amended Statistics screen to use more state variables
* in Search screen, on return from EpisodeInfo, previous search term and results are preserved
* in global Auto download settings, added option to include/exclude undownloaded episodes in queues
	* the default includes all queues (a change from only active queue in prior versions)
	* the queue items for download are summed with other auto-download items before counting against the total global episode cache
* corrected handling auto-download candidates in relation to total allowed, likely improving situations with tight constraint settings
* added toast messages on auto-download results and errors

# 8.3.2

* fixed set queue lock/unlock
* removed the warning dialog whe locking the queue
* tuned the location and colors of toast messages
* added replacedCount into deletedCount to fixed Replace auto-download
* set refresh failure backoff criteria to 10% of refresh interval

# 8.3.1

* fixed duration filter text fields showing milliseconds numbers
* ensured some inter-dependencies in metered network settings
* corrected display of next update time under refresh in Downloads settings
	* note: refresh start time is not set, then every manual refresh of all podcasts resets for the auto-refresh schedule, this is the behavior since 8.1.1
* updated some Compose dependencies

# 8.3.0

* fixed empty episodes in FeedDetails when switching from FeedInfo
* adjusted colors of slider and progress bars in PlayerUI
* fixed incorrect file size of downloaded file
* fixed auto download policy dialog title
* added duration based filter for episode lists (FeedDetails and Episodes screens)
	* two duration limits can be entered: floor and ceiling (both in seconds)
	* filters can be Lower (< floor), Middle (between floor and ceiling), and/or Higher (> ceiling)
* added auto-download policy "Current filter and Sort" in FeedSettings
	* when set, the current settings of filter and sorting of the feed are copied
	* at refresh time, auto-download algorithm will determine episodes to be downloaded based on settings 
* curIndex in EpisodesScreen is stored in the AppPreferences 
* some dependencies update and bumped gradle to 8.12

# 8.2.1

* changing sort category will not change the sorting direction, for both subscriptions and episodes
* likely fixed again the out-of-bound errors in episodes lists
* ensured background color of Queues match the theme
* enabled some toast messages
* webviewData is cached in episode, saving some redundant constructions

# 8.2.0

* corrected github address in project to podcini.X
* removed obsolete listing files
* fixed next refresh time not updating issue
* added more ways to sort podcasts
	* added category Time to sort based on episodes' durations
	* added date sorting based on Played and Commented 
* re-arranged sorting criteria for episodes
* ensured background color of components match the theme
* added a buffering progress bar in PlayerUI

# 8.1.1

* fixed the small height of TopBar
* ensure refresh task is reset when changes in Metered Network Options involve refresh and auto-download
* added display of next refresh time in Settings -> Downloads
* in Settings -> Downloads, added "start time" setting for refresh, once set, the refresh interval will be based on it rather than "now"

# 8.1.0

* screens of FeedEpisodes and FeedInfo is merged into one: FeedDetails
	* switching between them is now an internal switch
* EpisodeHome icon on topbar of EpisodeInfo is changed
* EpisodeHome is changed to EpisodeText
* fixed rating icon not updating on PlayerDetailed
* tuned some colors, the pinkish color is replaced with brownish color
* remove dynamic colors setting in Preferences, not used

# 8.0.3

* made playerUI hidden when no playable
* fixed GPL warning on Settings screen
* code restructuring and cleanup
* updated Compose to 1.10.0

# 8.0.2

* fixed bottom padding to offset the player UI
* fixed auto-closing of adding opinion dialog from swipe actions
* add opinion dialog title is translatable, and shows auto save info

# 8.0.1

* some code restructuring.
* fixed prefs edit issue with StringSet

# 8.0.0

* first migration from Podcini.R, stripped away youtube stuff
