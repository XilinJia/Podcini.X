# 8.17.2

* fixed again bottom padding for Android 15
* enhanced feed refresh efficiency (full or simple) when there are many new episodes found
* restructured feed refresh routines and replaced WorkManager with Coroutine for single feed refreshes
* fixed some mal-functioning filters in Subscriptions screen
* added border for DropdownMenu's
* some dependencies update

# 8.17.1

* fixed bottom padding for Android 15
* fixed crash when setting current episode to Ignored in case of duplicates found
* disabled improper download error logs when doing full update on a feed
* enhanced feed refresh by avoiding fetchMediaSize on feed preferring streaming
* added "Clean up" menuitem in FeedDetails for cleaning duplicates
* times of last refresh and last full refresh are recorded in Feed, initialized when subscribing to the feed
* last full update date is shown in FeedDetails

# 8.17.0

* on Android 13 and above, app language can be set from System's App language settings
* when playing an episode
	* duplicate item is set to Ignored even if it was previously set to Later, Soon, Again
	* current episode is set to Ignored if a duplicate item was previously set to Forever, Skipped, Played, Passed, Ignored
* TTS is initialized upon first use in FeedDetails screen
* enabled "Refresh complete podcast" menuitem in FeedDetails for all podcast, and amended updateFeedFull (experimental: using much memory)
	* more properties of existing episodes are updated
	* existing duplicates are cleared, keeping rated, or last played, or last updated, while keeping comments
* removed Java Callable classes

# 8.16.4

* fixed pull to refresh from empty space
* fixed wrong behavior of choosing Unspecified in language box in Subscriptions screen
* fixed useless notice in Subscriptions when the list is empty
* various dependencies updates

# 8.16.3

* further enhancements in EpisodeVM for better monitoring of curEpisode
* amended ActionButtons on better getting action results

# 8.16.2

* amended color of timeLeft on PlayerUI and "Related" in EpisodeInfo
* amended effect blocks in EpisodeVM to better handle monitoring of curEpisode
* some code refactoring
* updated some dependencies

# 8.16.1

* duplicate search is also performed on playing the next item in queue
	* duplicate item will not be set to Ignored if it was previously set to Later, Soon, Again, Forever, Skipped, Played, Passed
* amended listing info in duplicate dialog
* tap on the duration/timeLeft number on PlayerUI now cycles through and persists modes: Duration, TimeLeft and TimeLeftOnSpeed
	* when on TimeLeftOnSpeed, "*-" is shown before the number, and "*" is shown on InfoBar of episodes lists
* global settings of "Adjust media info to playback speed" and "Show remaining time" are removed
* episodes sort dialog amended and sorting on View count filtered out for RSS feeds
* added episodes sorting based on views per day (if available)
* SwipeActions are centrally managed in EpisodeVM
* list of episodes are updated directly, no longer monitored from DB
* when getting new feed, title is also checked against past subscription logs
* when deleting feed, episodes are batch deleted
* in Reconcile, added algorithms to delete episodes belonging to no feed or non-existent feeds
* ensured to decode filename before comparing with file, fixed mistakenly deleting files in Reconcile 
* large code refactoring in SwipeActions and EpisodeVM

# 8.16.0

* episodes monitoring are enabled only on screens FeedDetails, Queues, Search and Facets
* fixed feed monitoring crash on first install
* Episode can be assigned with a list of related episodes
* from multi-select menu of episodes lists, selected episodes can be set mutually related
* related episodes (if any) can be shown/unrelated in dialog from EpisodeInfo and PlayerDetailed screens
* when start playing/streaming an episode from list
	* duplicate episodes are searched in all feeds
	* if found, all duplicate episodes are mutually set to be related
	* all duplicate episodes other than the current one are set to Ignored with a comment "duplicate"
* some minor code restructuring

# 8.15.1

* feeds monitoring are on a single coroutine
* episode monitoring is centrally managed and is limited to one monitor per episode and is tagged by the list
* enhanced efficiency of episode list scrolling
* replaced some mis-posting of PlayedEvent with other event
* episodes lists monitors played status, PlayedEvent not needed, removed

# 8.15.0

* long-press on image on PlayerUI opens the FeedDetails screen
* long-press on Rewind/Forward buttons no longer opens the skip settings
	* Rewind button opens volume adaptation dialog
	* Forward button opens the sleep timer
* Play button on PlayerUI does auto streaming if media is not downloaded 
* amended colors in PlayerUI
* enhanced Reconcile routine in Facets screen, more efficient and effective
* replaced all remaining SnackBars with Compose dialog
* download notification is moved to Dispatchers.IO from Default for efficiency
* app built with and targeted to Android 16
* large code cleaning and refactoring based on new IDE suggestions
* various dependencies updates

# 8.14.8

* fixed concurrent modification crash when trimming toast messages
* straightened sleep timer and added fading
* preserve states of Soon, Later, Again and Forever when auto set to Played or Skipped
* when "Adjust media info to playback speed" is turned on, only time left on PlayerUI is adjusted with speed, position and duration are not adjusted
* tapping on time left on PlayerUI is more responsive
* ensured position saving internal properly set when jumping on playing a different episode 
* some code cleaning

# 8.14.7

* amended buffering sizes to help with higher speed playback with streaming
* UI update of buffered position is in sync with that of played position, removed the unnecessary separate coroutine
* likely fixed concurrent modification crash when trimming toast messages
* some restructuring in PlaybackService
* updated media3 to 1.6.1

# 8.14.6

* fixed crash when removing feed
* when auto-enqueue an episode, set it not auto-downloadable, fixed issue of possibly enqueuing past episodes
* likely fixed empty recent feeds on the drawer

# 8.14.5

* changed navigation behavior from drawer: only Subscriptions, Queues and Facets fully clears backstack, others partially
* on feeds with auto-update disabled, auto-download/auto-enqueue with policies other than "Only new" still work
* updated with authentic Turkish, thanks to @mikropsoft
* updated some Compose dependencies

# 8.14.4

* properly release cache on destroy of PlaybackService 
* added menu item "Clear from cache" in AudioPlayer screen to remove current episode from cache
* further improvements and bug fixes in PlaybackService. improved power efficiency since 8.14.3
* in case of player error, no longer showing the daunting player error dialog, only toast messages
* player play/pause status is tracked by a state variable
* recent feed list in the drawer is expanded to 8
* straightened behaviors of navigation with default pages and open drawer
* PlayerErrorEvent and PlayEvent are no longer used and are removed

# 8.14.3

* removing curEpisode from queue when not playing will not automatically play the next episode
* in case of player error, pause on the current media (rather than starting the next)
* curEpisode is fully a lazy object updated by the DB in PlaybackService and AudioPlayer screen
* reordered items on episodes sort dialog
* cleared up getNextInQueue
* PlaybackPositionEvent is removed

# 8.14.2

* largely trimmed and restructured PlaybackService
* in case of player error, automatically start the next in queue
* replaced streamingNotAllowed notification with a Compose dialog
* in FeedSettings, active auto-download/auto-enqueue policy is shown
* in FeedSettings, added policy Discretionary, to only include those set as Soon
* in FeedSettings, when policy "Current filter and sort" is selected, the the filters and sorting are indicated
* changed the icon of Unrated

# 8.14.1

* fixed and enhance volume adaptation in PlayerDetails
* fixed play not working when current episode is null

# 8.14.0

* improved behavior when toggling between fallback and normal play on long-press the Play/Pause button
* auto trim toast messages list to 100
* amended multi-select menu colors
* amended routines in PlaybackService to accommodate media3 1.6.0
* minor amendments in getNextInQueue
* Play/Stream buttons on episodes list are more responsive
* On startup, Play button on PlayerUI plays the episode 
* upped Kotlin to 2.1.20, krdb to 3.2.6 and media3 to 1.6.0

# 8.13.6

* improved playing process of custom folder or local feed
* likely fixed mis-calculations of time spent and played duration
* tried to prevent autoplay when Playback service is restarted
* some code cleanups

# 8.13.5

* speed setting dialog from PlayerUI can also set fallback and fast-forward speeds as well as skip times for rewind and forward
* show some set values in FeedSettings screen
* straightened filters settings for auto-enqueue/auto-download in FeedSettings
* ensure update in FeedSettings screen when properties are changed
* further tweaked about timeSpent settings
* in feed update, filters out duplicate titles
* changed feed update rescheduling policy to REPLACE from UPDATE
* removed Android version check in getting storage space 
* fixed issue of playing media from custom folders
* fixed importing local feed issues
* set errors in red in Logs screen
* use common code with some number settings in Preferences 

# 8.13.3

* added language spinner Subscriptions screen if subscriptions are of more than one language
* in Subsciptions tab of Statistics, tap on the image opens FeedInfo
* ensured to call onPlayStart when playing a first episode 
* added toasting in Statistics when timeSpent is likely wrong
* in Statistics, ensured num of days count starting from 1 (rather than 0)
* fixed timeSpent being added extra value, introduced in 8.13.2
* fixed clip file not properly removed
* buttons on PlayerUI receive tap on expanded areas
* removed libs.versions.toml

# 8.13.2

* allow to add queues up to 12
* likely fixed 2 instances using the cache folder
* cleaned up unused code blocks in AudioPlayer screen
* tidied up PlayerUI layout
* rearranged statistics measures layout
* fixed content in PlayerDetail and EpisodeInfo getting reset for currently played episode

# 8.13.1

* likely fixed string index out of bound crash when doing swipe select text
* recorded clips are shown as chips in both EpisodeInfo and PlayerDetail screens, and can be played or removed
* moved Record button on PlayerUI
* long-pressed the Record button marks the current position
* marked positions are shown as chips in both EpisodeInfo and PlayerDetail screens, and can be sought to or removed
* click on the url in EpisodeInfo opens the webview of the episode
* text in both feed and episode titles in EpisodeInfo are selectable
* most text in FeedDetails are selectable
* click on the url in FeedDetails opens the webview of downloadUrl (likely the RSS page), long-click copies it to the clipboard
* feed and episode titles in PlayerDetail screens are no longer clickable/long-clickable, rather the text are selectable
* enabled episode monitoring in AudioPlayer screen
* RatingEvent is no longer needed, removed
* error messages are toasted in red color

# 8.13.0

* infobar text changes
* re-enabled bitrate in PlayerUI for non-Youtube podcasts
* enabled episode monitoring on curEpisode, likely fixed setting properties possibly getting reset
* enabled episode monitoring in EpisodeInfo screen, reduced relying on events
* episode arkwork is added to player meta data
* support of streaming back buffer is removed
* enabled disk caching for all streaming for better local rewind and replay
	* size defaulted to 100MD but can be customized in Settings->Playback with minimum 10MD
	* all recently streamed episodes are stored in the cache, first in first out
* enabled recording of audio clips, EXPERIMENTAL
	* record button on PlayerUI is functional only during playback
	* press the button to start recording and press again (or press pause) to stop recording
	* recorded clips are shown in EpisodeInfo screen, and can be played on tap
	* supposedly mp3, acc and ogg formats should work

# 8.12.2

* amended statistics in Subscriptions tab of Statistics screen
* straightened the Monthly tab of Statistics screen
* fixed improper handling getting file size in fetchSize
* when removing a feed, episodes marked Ignored are not preserved

# 8.12.1

* fixed a null pointer crash issue in EpisodeInfo when removing feed
* in Queues, topbar icons of showBin and showFeeds are mutually exclusive
* in Queues, when showing bin or feeds, backpress returns to the queue
* amended Overview of Statistics screen
* fixed total duration Int overflow issue in Statistics
* topbar including icons in Statistics are removed, all are included
* tap on the period text in Overview of Statistics opens the dates filter dialog
* further stripped datetime from brief comments in Commented view in Facets

# 8.12.0

* further tuned backpress behaviors
* Session logs is the default view in Logs screen
* more details in Statistics, step 1
* some code restructuring
* updated some dependencies

# 8.11.7

* newline character is changed to spaces in brief comments in Commented view in Facets
* fixed PlayerDetailed not opening to full screen
* Play button on PlayerUI is refreshed on resume
* straightened backpress behaviors
* disabled for now monitoring periodic feed updates: not effective
* ensured download or refresh works are dispatched with application context

# 8.11.6

* removed more references of ServiceStatusHandler in AudioPlayer
* set refreshing off when periodic feed updates terminate
* "Add comment" is on multi-select menu of episodes
* in episodes lists, added icon to indicate commented
* in Commented view in Facets, episodes are shown with brief comments
* in FeedDetails screen ensure TTS engine is initialized when some episodes don't have media

# 8.11.5

* fixed media notification not shown after recreating the player
* media player is initialized to streaming/local based on the global setting of StreamOverDownload
* replaced a couple Thread with Coroutine
* no longer toasting "No vorbis identification header found"
* class ServiceStatusHandler (former MediaController) in AudioPlayer screen appears redundant, disabled now.
* added observation of periodic feed updates
* check for SDK_INT >= 26 is removed (min SDK is 26)

# 8.11.4

* fixed bin limit setting dialog not showing in Queues
* when setting back buffer size, ensure player be recreated on next play
* filter and sort in Facets are persisted for the session
* filter on Facets is also indicated by the color of the filter button on topbar
* info bar in FeedDetails screen shows about episodes list
* media sizes of new episodes are fetched during feeds refresh
* added menu item "Fetch size" in FeedDetails to refresh media sizes
* added more measures in Overview of Statistics screen
* "Prefer low audio quality" setting in Playback Settings is disabled (not used)

# 8.11.3

* corrected auto-enqueue filter name in Subscriptions
* enabled setting of bin limit in Queues
* added streaming back buffer setting (defaulted to 5 minutes) for better local rewind
* streamlined Preferences screens

# 8.11.2

* added auto-enqueue filter in Subscriptions
* added title text filter in episodes lists
* fixed Soon not properly handled in auto-enqueue/auto-download algorithms

# 8.11.1

* straightened the behavior of filter in Statistics
* in FeedDetails screen, filtered is not shown in status bar
	* filter button on topbar is set green if filter is set otherwise text color
	* long-pressed, the filter button turns red indicating temporarily disabled, long-press again re-enabled
* in Subscriptions screen filtered is not shown in status bar
	* filter button on topbar is set green if filter is set otherwise text color
* enhanced display behavior of topbar in Facets screen

# 8.11.0

* start using self-maintained Kotlin SDK KRDB for Realm DB
* fixed crash when search selected gets a negative string index
* fixed overwritten issue of adding to queue (which could cause some episode as InQueue but not actually added to a queue)
* export OPML will not include synthetic feeds
* OnlineSearch screen shows progress dialog when restore from backup is performed
* in OnlineSearch screen, open to search by RSS address is removed (RSS address can be added to the top search bar)
* fixed Play button on PlayerUI not playing after app start
* fixed open web button on topbar of EpisodeInfo screen
* fixed mis-alignments of download and circular progress buttons
* Kotlin is upped to 2.1.10

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
