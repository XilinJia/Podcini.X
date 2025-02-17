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
