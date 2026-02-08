# 10.9.2

* in Settings->Network, added setting of Identifier, defaulted to "My Podcini"
* when sending to other device, receiver address no longer needs to be typed in, it's obtained from Receiver broadcast
* Added send/receive catalog feature across devices:
	* a catalog is a list of feeds info but no episodes
	* on receiving, the catalog is put in a volume with the sender's identifier
	* to receive, in Library menu, choose "Receive contents", select "Catalog" in the popup (likewise for receiving feed), tap Start
	* to send, in Library menu, choose "Send catalog", tap Send
* startup crash on property Freeze should be fixed

# 10.9.1

* persisted host and port for feed transceiving
* volumeId of a feed is no longer transferred to other device
* when freezing a feed, set it to Volume Frozen, its queue, keepUpdated, autoEnqueue, autoDownload properties are no longer changed
* when unfreezing a feed (on its return), set its volume to none
* in Facets, added Frozen view
* in Library, Show archived also shows Frozen
* persisted episodes count in feed, avoided various screen doing live queries on it

# 10.9.0

* amended colors of some popups
* fixed crash when open Search from Combo menu on PlayerDetails
* if navigate from PlayerDetails, ensure to close the full Player screen
* in DB models, removed indexing little used identifier in episode and feed, improve on DB size and write speed
* recompile tags when new feed is added
* in Library screen, ensure the total feeds number react to changes
* enabled send/receive feed feature across devices
	* on the receiver, on Library screen, from the menu, tap "Receive feed", 
		* note the ip address and the port number (port can be changed), then tap Start on the popup
	* on the sender, on FeedDetails screen, from the menu, tap "Send to device"
		* enter the ip address/port number of the receiver, tap "Send"
	* repeat with other feed if needed, while the receiver is active
	* to end, tap "Stop" on the receiver
	* once a feed is sent, it's set Frozen on the sender device
	* the feed can be sent back and forth across the devices
	* only key properties are transferred, others stay unchanged as before or default if new
	* except for unchangeables, feed properties include: 
		volumeId, customTitle, episodesLimit, lastPlayed, lastUpdateTime, lastFullUpdateTime, tags, rating, comment, commentTime
	* episodes' properties include:
		position, playState, playStateSetTime, isAutoDownloadEnabled, tags, marks, todos, rating, comment, commentTime, lastPlayedTime, repeatTime
	* on reception, the transferred properties overwrite existing ones.
	* on a return of a feed, it's unfrozen, but associated queue is None, keepUpdated, autoEnqueue, autoDownload are false

# 10.8.9

* in Search screen, added in Feeds tab associated feeds of the resulting episodes
* improved flow setups in Queues and FeedDetails screens
* in FeedDetails screen, avoid fetching episode in Info view
* fixed Search dialog not auto-close
* EpisodeInfo started from widget adopts the theme of the main app, as demanded by the color of the description text

# 10.8.8

* straightened up the code of SwipeActions
* fixed "Add to queue..." action
* likely fixed strange auto-scroll on opening queue's bin 
* amended SearchBar text styles
* in Search screen:
	* fixed episodes showing up lagging
	* enabled sorting for searched episodes
* in FindFeeds screen
	* enabled search history
	* search results are sorted by title
* amended flow setup in EpisodeInfo
* in OnlineFeed screen, ensure return to episodes view from EpisodeInfo
* replaced all topbar navBack button with a icon

# 10.8.7

* in PlayerUI, Record button behavior is changed: click on it now creates a mark, long-press on it start/stop recording
* in EpisodeInfo screen, 
	* on topbar a Combo action replaces various other actions, enabling more operations, working also from the widget
	* play status and rating are shown on the dates line
	* fixed changes not updating
* also added Combo action button on PlayerDetails screen
* changed all label of "My opinion" to "Comments"
* in both EpisodeInfo and PlayerDetails screens, Todos, Comments, Tags are not shown if empty
* Popup's adopt colors of the theme
* tuned icon colors of swipe actions
* rebuilt hashCode and equals on Episode and Feed

# 10.8.6

* tuned the header of EpisodeInfo screen, fixed media size text
* tuned title font sizes of AudioPlayer Details
* rearranged FeedDetails header
* tuned Library screen
	* on list view, removed Author and made the title in max 2 lines, and put rating icon is on the image
	* on icon view, tuned colors a bit, and fix number not visible issue
* tuned a bit on header of OnlineFeed
* corrected remove feed dialog

# 10.8.5

* fixed issue of switching to a new episode during playing

# 10.8.4

* in widget, added Remove button on every episode
* in PlayerUI popup from widget, disabled showing toasts
* likely fixed widget skip media problem when the app is off
* enhanced search reaction

# 10.8.3

* fixed search empty issue again

# 10.8.2

* fixed search empty issue
* rearranged remove feed dialog to avoid Confirm button disappearing
* fixed Related dialog showing nothing
* amended flow setups for better reactions
* fixed app crash on second open of "Add podcast"

# 10.8.1

* amended some lazy lists and fixed some list layout not starting at the top
* set popups from the large widget to use dark theme, same as the widget
* minor dependencies update

# 10.8.0

* fixed/amended "add Podcast widget" routines
* added "add Queue widget" and "add Facet widget" features
* the main feature added is a feature-rich large widget
	* the widget is never auto-refreshed
		* unless when media is played from the widget, updated at beginning of a new media in queue
	* on the top bar, tap on queue name can change queue, the Refresh icon refreshes the widget contents, and the Podcini icon opens the app
	* the list is the queue chosen (actQueue by default), tap on each item opens the EpisodeInfo, the Play button plays/streams the episode
		* when the app is off, the first tap can have a delay of a couple seconds
	* the bottom has the media control buttons. the left-most one opens the full PlayerUI
* enhanced performance of list screens
* EpisodeInfo is now an embedded screen for better performance
* tuned colors a bit in both light and dark themes for eye comfort
* removed all "?attr/action_icon_color" in drawables
* cleaned old xml styles and themes
* large refactoring and cleaning

# 10.7.0

* renamed Subscriptions screen to Library screen
* in topbar of Queues and Facets screens, added Library button in Feeds view, to open a specialized view of Library for better operations
* in Library screen
	* added "Add podcast" in menu to open FindFeeds screen
	* show "processing" when removing a volume
* in Facets screen
	* fixed not refreshing on initial opening 
	* ensure reconcile skips local feeds
* amended and fixed shortcuts with long-press on app icon
* removed SplashActivity
* some code refactoring and removed some old unused stuff

# 10.6.1

* fixed Bin in FeedDetails showing full episodes when empty
* on new install, first opens FindFeeds screen
* amended Search screen
	* search texts are persisted (capped around 20) and can be recalled
	* search options are by default hidden, tap "Setting" icon to show
* when starting a new episode, skipSilence is reset
* fixed deprecation of CountingInputStream
* amended the code checking ID3 header
* some code refactoring

# 10.6.0

* cleaned out lots of shitty context parameters being passed around
* feeds' scores are computed no more frequent than once per day, on refresh, no longer on FeedDetails screen
* added system volume Archived, used for storing unsubscribed feeds
	* Feed "Preserve syndicate" is moved to Archive volume 
* when deleting/unsubscribe a feed, the feed and its worthy episodes are archived in volume Archived
	* an archived feed can be used in the same way as a normal feed except it is not auto-updated/enqueued/downloaded etc.
	* an archived feed can be restored by setting it to a normal volume
* in Subscriptions screen
	* on first install, set queues filter to "Default", so that newly added feeds show up by default
	* amended feeds sorting
		* changed "Title" category to "Feed" to sort on various properties of the feed
			* also including Author, Rating, Score, Score Count, Last Update, Last FullUpdate, TotleDuration, Last Commented 
		* note other categories sort on computed values of the episodes chosen
	* Archived volume can be toggled on/off in the menu
	* auto scroll to previously saved position is temporarily disabled
* on remove from queue, possible loose queue entries are checked and cleared
* fixed confirmation not showing when swipe deleting episode with Again or Forever status
* ensure to renew VirQueue when previously added episodes complete 
* amended OnlineFeed screen
	* backpress from Episodes returns home (not exit)
	* episodes are cached for multiple opens
* likely fixed backpress not always behaving in Queues and FeedDetails screens
* in Facets screen
	* backpress in Feeds view returns to episodes list view
	* added Archived view showing worthy episodes from unsubscribed podcasts
* fixed adding local feed loads no episodes, introduced in 10.4.4
* likely fixed playUI showing up on launch when there is no media to be played, introduced in 10.4.2
* fixed failure connecting to a local folder in FeedDetails
* volumes created to load local folders are set to be local
	* no further feeds can be added to them
	* in Subscriptions screen, selected a local volume can be reconnected to the local folder via popup menu (if case Android permission lost)
* settings of speeds, rewind/forward seconds, skip silence are restored in Settings, with a note, just for the benefit of the descriptions
* various code refactoring and enhancements

# 10.5.0

* fixed Delete button deleting local or repeat episode before confirmation
* fixed a possible null point crash in LocalFeedUpdater
* likely fixed creating duplicate feeds in LocalFeedUpdater
* in Queues screen
	* title on the topbar no long shows size
	* improved dragging operations
* cleaned the topbar of PlayerDetails
	* added actQueue button
	* share buttons are moved to the menu
	* access to EpisodeInfo is removed (no need)
* the playerUI (botton row) can be horizontally swiped
	* swipe left hides the UI to the drawer, to show, tap the teaser image at bottom of the drawer
	* swipe right opens the sleep timer dialog
* amended Fast forward speed and added Skip forward speed
* amended PlayerUI button functions
	* long-press Rewind button restarts the current media (no longer volume adaptation settings)
	* long-press Forward button speeds forward at Fast forward speed (if set). (no longer the sleep timer)
	* press Skip button speeds forward at Skip forward speed (if set)
	* long-press Skip button, skips to the next media, but if Play/Stream Repeat, restarts the current media
	* longpress the speedometer brings up volume adaptation settings
	* rewind/forward seconds are shown above the Rewind/Forward buttons
* settings of speeds, rewind/forward seconds, skip silence are disabled in Settings, do them with the speedometer popup
* title on topbar of Facets and Subscriptions adopts same narrow style as Queues
* improved TopChart, Queues, FeedDetails screens for better returns
* removed fake language supports, and unused old xml files
* Kotlin upped to 2.3.0, krdb upped to 3.3.0
* AGP upped to 9.0.0, and amended build.gradle files accordingly
* compose version update
* some code refactoring and reorganization

# 10.4.4

* when creating a new local feed, it's set to not auto-refresh
* in Episode, downloaded is no longer a persisted property, but a delegate of fileUrl being not null
* in Facets, reconcile no longer needs to check downloaded vs file existed
* fixed crash when fetching episodes sizes on a local feed
* fixed crash when adding item to queue at position after currently playing when there is no currently playing in the queue
	* in such situation, default the position to the front of the queue
* amended LocalFeedUpdater and fixed existing items possibly not updated
* made queue name on topbar of Queues screen a bit narrower

# 10.4.3

* fixed feed full update not updating old episodes
* fixed crash on on adding to queue after feed updates
* amended Search screen for better returns
* clarified toasts on unperformed auto-downloads
* added toast for unhandled feed uri
* some code refactoring

# 10.4.2

* fixed remove volume getting interrupted
* in Subscriptions screen
	* added toggle showAllFeeds in menu, which allows to show all feeds regardless of volumes
	* added local/remote filter
* local feeds and volumes are no longer imported duplicated
* new local feeds and volumes are imported to existing volume that corresponds to the parent folder

# 10.4.1

* fixed possible failure of a full update of a feed
* in Subscriptions screen, if in a Volume, simple/full refresh will only refresh the feeds in the volume tree
* in PlayerUI, volume adaptation setting include option to set for the feed
* when playing an episode, ensure volume adaptation setting in the feed is applied 
* likely fixed playUI is position behind control bar on startup
* amended FeedsSettings a bit
* adjustments in drawer background, border, shape and spacing
* most dialogs are round-cornered
* some layout spacing adjustments

# 10.4.0

* amended actions buttons
	* rename some actions: once -> one, JustTTS -> TTS now
	* made the long-press popup vertical with icons and labels
* amended handling of feed preferred action, if is one of Play, PlayOne, PlayRepeat, download is first ensured
* fixed crash when opening episodes in onlineFeed screen
* amended OnlineSearch screen
	* past searches are preserved on return
	* after importing local feeds, a toast is shown
* tuned scrolling mechanism of returning to episodes lists
* set border colors for selectable buttons on most popups 
* multi-select UI is unified, and uses a popup in stead of floating items

# 10.3.2

* in FeedDetails screen
	* edit feed title and url are moved to feed settings
	* feed settings button on topbar is moved to the menu
* in FeedsSettings, added settings for a preferred action button for episodes lists
* enabled various options in Feeds settings for setting multiple feeds
* ensure episodes not auto-downloadable when play state is set to Ignored, Passed, etc
* tuned colors of play states
* migrated groovy files to kts and removed commit value
* some code refactoring

# 10.3.1

* in FeedDetails screen, feed image in header also react to long-click in the same way as the image on Infobar
	* if currently playing is in the list, scroll to it
	* if currently playing is not in the feed, open the feed that contains the currently playing
* in auto-enqueue/download
	* Again or Later episodes already in queue are filtered out
	* Soon episodes are not bounded by autodownloadable or downloaded status
* tuned AudioPlayer screen a bit
* in Queues screen, ensured the order of the bin in the reverse order of episodes being removed
* when trim episodes from a feed, ensure also remove from queues
* modernized Parcelable handling in DownloadRequest class
* syntax updates in build.gradle, Android gradle plugin dropped to 8.12.3
* some code cleaning and refactoring

# 10.3.0

* in the drawer
	* restored prosperity
	* set the width to be the min of 350dp and 70% of screen width
	* items are rearranged, and are vertically scrollable
	* drawer state stays on rotation
* fixed virQueue not automatically create on new install (or upgrade from old versions)
* max number of queues allowed is raised to 20
* feed's score and scoreCount are persisted
* in FeedDetails list view
	* the header is hidden when scrolling past 2 positions
	* Infobar adds the score (count) info and image of the feed
	* click on the feed image opens the feed info view
	* long-click on the feed image scroll to the currently playing, if applicable
	* improved efficiency
* some code refactoring

# 10.2.4

* in EpisodeInfo screen, the header is scrollable together with details
* in OnlineFeed screen, all contents are scrollable, and likely fixed the progress mark appearing when rotating screen
* in FeedDetails list view, the header is hidden when in landscape mode
* adjusted drawer opening routines, drawer stays closed when rotating to landscape mode
* amended routines of queues
* some code refactoring

# 10.2.3

* fixed hang on startup on a new install (or upgrade?), introduced likely in 10.0.1
* reworked/enhanced setPlayState routine
* further sped up opening of cached webvew
* made items on FeedsSettings reflect changes for single feed setting
* ensured languages filter show up even with only 1 language
* ensured some coroutine jobs are singltons
* various code refactoring and enhancements

# 10.2.2

* fixed sort order direction in Queues screen
* actQueue is live monitored from DB
* likely fixed cases where Default queue's name can be emptied
* in FeedEpisodes screen, when in select mode, header is hidden
* on episodes lists, if use feed cover, feed rating is shown on the cover
* in Queue, introduced autoSort (default to false), settable in settings
	* when set to true
		* queue is set to locked, insert position is not applicable
		* every addToQueue will perform a sort based on the preset sort order
	* when the queue is unlocked, autoSort is automatically set to false
* enhanced addToQueue routines
* some code refactoring

# 10.2.1

* corrected handling a dynamic offload settings
* fixed skip silence setting in Feeds settings
* restored bins in Queues screen

# 10.2.0

* skip silence now can be set to current, feed and global (same as for speed)
* audio offload is reset when necessary on every new media, speed change, and skip silence change
	* if speed is 1x, and silence is not skipped, request offload (if hardware supports)
	* otherwise, not offload, use CPU
	* the change can cause a hiccup during play
* in FeedsSettings, added settings for skip silence, sorting and filter 
* added volumes on grid view of Subscriptions screen

# 10.1.0

* Feed Settings now operate on single and multiple feeds
* in Subscriptions
	* create a new volume from the menu will create a new volume under the current volume
	* on multi-select menu 
		* added "Full settings" to set for selected feeds
		* cleared some less useful settings, use the "Full settings"
		* added "Add to queue (max 200)" episodes in selected feeds
	* long-press on a volume pops up the same feed multi-select menu to operate on feeds contained plus others for the volume
	* a volume's name and parent can be changed
* in speed setting dialog from PlayUI, added "edit" button to toggle the slider

# 10.0.1

* fixed crash when moving episode from one queue to another
* fixed crash when doing a full refresh of a feed
* fixed webview text dark color in dark mode
* sped up opening webvew 
* sped up all network checks
* further tuned/enhanced playback buffer and datasource cache

# 10.0.0

* reworked the ways of handling relations of feed and episodes, and of queue and episodes
	* may cause issues due to possible incomplete migrations!
* in Queues screen, systematic sorting is performed only when requested, allowing manual sorting as long as queue is unlocked
	* removed TimeInQueue, restored Random, Smart
* introduced type Volume that can contain sub-volumes or feeds, all volumes have a nullable parent volume
* all feeds have a parent volume (by default null)
* in Subscriptions, show top volumes (if any) at the top of the list, and feeds whose parent volume is null
	* from menu, a volume can be created
	* when a volume is clicked, sub-volumes and/or feeds belonging to the volume are shown
	* long-press a volume shows prompt to delete the volume
		* deleting a volume will delete all sub-volumes and feeds under it (local files stay intact)
	* in feeds multi-select menu, selected feeds can to set to a parent volume
* in Feed Settings, added setting of parent volume
* allows adding local directory tree, from "drawer->Add podcast->Advanced->Add local folder"
	* directory is traversed indefinitely
	* if a directory has only sub-directories, it's added as a volume
	* otherwise, it's added as a local feed
* fixed various issues of playing local media
* fixed updating local feeds requiring network
* fixed download failure due to DB write transactions
* in LocalMediaPlayer, added audio offload listener with toasts on whether audio is offloaded
* enhanced colors on multi-select popup menus
* some code refactoring

# 9.11.0

* made the total duration color on PlayerUI more readable
* in LocalMediaPlayer
	* loosened request for audio offload, on most devices, at least 1x speed would benefit the battery saving
	* enhanced player buffer sizes to help playing at higher speed
	* use faster but slightly imprecise SeekParameter 
* added capability of repeating a media (indefinitely)
	* during playing/streaming, on PlayerUI->Speed->More, repeat can be checked for the current media
	* in action button popup menu, "play/stream repeat" does it
	* when scheduling a media, "Repeat media" can be checked

# 9.10.2

* in Queues screen, when not in actQueue, long-press on the name on the topbar will switch to actQueue
* for resizing episodes in a feed, trimEpisode routine is made more efficient
* amended chapter loading routines
* some list properties in DB are changed to set
* in Settings->Playback, added setting of preferred languages if languages in the app is more than one
* in Feed Settings, added setting of preferred languages if languages in the feed is more than one
* adjusted order of action buttons in popup menu

# 9.10.1

* fixed possible crash when opening PlayerDetails

# 9.10.0

* in tags editing (feed or episode), fixed tags not being removed
* added basic functionalities to manage timers
	* adding a timer now supports a full calendar
	* in EpisodeInfo, added icon to add/show/edit/remove timers
		* click on it, if no timers, opens add timer dialog, otherwise, opens timer list dialog
		* long-click on it, opens add timer dialog
	* on timer list dialog, timer can be edited/removed
	* in Facets, added sub-screen for episodes with timers set
		* when the sub-screen is opened, old timers are cleared
* further merging common code blocks of EpisodeInfo and PlayerDetails
* some code refactoring

# 9.9.1

* fixed an issue of initializing curState from DB, which can 
	* cause various properties not restored, and 
	* set Default queue's name to empty - please rename it back if this happened to you
* removing curEpisode from queue when not playing will dismiss the playerUI
* in Queues screen, the name on the topbar
	* reflects actQueue and queue size
	* when long-pressed in actQueue, the list scrolls to curEpisode
* re-adjusted auto-scroll routines:
	* in FeedDetails screen
		* on first start 
			* if curEpisode belong to the feed, it scrolls to the curEpisode
			* on other feeds, it starts to the first episode based on sorting
		* if returned from EpisodeInfo, it restores the previous position
	* in Queues screen
		* on first start 
			* if curEpisode belong to the queue, it scrolls to the curEpisode
			* on other queues, it starts to the previously saved position of the queue
		* if returned from EpisodeInfo, it restores the previous position
		* not quite desired: removing an episode causes the list to scroll to curEpisode
* amended "Add to queue" popup, removed the radio buttons
* in Settings, replaces "User forum" with "What's new"
* removed useless UsageStatistics routines inherited from AntennaPod
* various dependencies update

# 9.9.0

* added Todo capabilities in EpisodeInfo and PlayerDetails screens
	* Todo can be added, edited, removed, set/unset Done
	* Todo can have optional note
	* Todo can have a due time, once set, 
		* the episode will be played at due time (hopefully Android doesn't betray the feature)
		* due time is shown (highlighted if past due)
* added a sub-screen in Facets for Todos
	* only episodes with undone todos are shown
	* title and due time if any of undone todos are shown separated with |
* getNextInQueue do instant search for curEpisode
* amended PlayerDetails to properly get current chapter index
* reworked associated queue setting for Feed, got rid of the Spinner
* in topbar of Queues, Facets and TopChartFeeds screens, replaced the spinner with a popup
* many dialogs are replaced with light-weight popups
* some code refactoring

# 9.8.8

* EpisodeInfo and PlayerDetails show and share the same cached content
* added tags and comment settings in PlayerDetails
* PlayerDetails shows chapter image, otherwise episode image, otherwise feed image
* player notification panel also shows episode image if available, otherwise the feed image
* amended the PlayerUI, duration text is now shown on the slider bar instead below it

# 9.8.7

* likely fixed crash on EpisodeInfo screen

# 9.8.6

* ensure episode descriptions persist across multiple opens in PlayerDetails 
* when description is chosen for TTS, description text is first cleaned
* added JustTTS to long-press menu to just play description or transcript without generating audio file, 
	* speed is set to feed.playSpeed and unchangeable
	* on play, a sticky dialog pops up showing the text being played and a Stop button to stop the playing
* likely fixed Subscriptions screen often jumping to Count sort
* large info are cached in EpisodeInfo screen for quicker reopen
* revert back to Android MediaMetadataRetriever routine, FFmpegMediaMetadataRetriever full library is too big
	* fixed possible download issues since 9.8.5
* fixed Playback service accessing network when playing downloaded media
* added measure to quit streaming earlier when streaming is not possible
* fixed issue of sometimes adding no episodes to Virtual queue

# 9.8.5

* made queues a globally monitored list
* all collected states from DB are life cycle aware, saving some energy
* on PlayerUI, added time spent with the position
* Android MediaMetadataRetriever routine is replaced with the more capable FFmpegMediaMetadataRetriever
* amended TTS
	* upon clicked (or from long-press menu), prompts selecting text source (description or transcript)
	* transcript involves downloading from the net, which may or may not be available
	* button turns to Cancel while generating audio file from the text (may take several minutes, run in background)
	* allows cancel processing
	* when audio file is generated, Play button is shown
* got a new StreamOnce icon
* some cleanup in Queues screen
* some code refactoring and improvements

# 9.8.4

* when playing an episode in some list, if it's already in Virtual queue, set actQueue to Virtual queue
* when playing from FeedDetails, playInSequence is set to true for the virtual queue regardless of associated queue
* in episodes list, when long-press Play or Stream button, there is a "PlayOnce" or "StreamOnce" button, for playing a single episode
* in EpisodeInfo, icon of "add to queue" is changed
* fixed issue of curQueue not updating when changing queue from the spinner
* fixed issue of possible wrong next-in-queue
* enhanced smartRemoveFromAllQueues routine
* on PlayerUI, changed display of time left, showing all of "time left" (if speed is not 1), "remaining duration", and "duration"
* elsewhere, any reference to "time left" has speed factored in
* after media is downloaded, if parsing for duration fails, only reset the duration is the original is less than 30s or longer than 5h.

# 9.8.3

* amended Queues screen to fix issues with sorting and remove-from-queue
* every queue has its own scroll-to position, persisted upon exit

# 9.8.2

* on first start, set actQueue to where curEpisode is
* fixed auto-scroll issues in Queues screen
* in episodes lists, enlarged the effective area of the action button a bit
* when adding a list to virtual queue, it starts with the played episode
* on auto refreshing, only handle feeds set to keep updated
* some code refactoring and cleaning

# 9.8.1

* fixed episodes sorting category mismatch issue
* in FeedDetails screen, when opening filter or sort dialog, the header is hidden
* in sort dialog of Subscriptions, multi-item sub-categories (under Count) are colored with selections, like on filter dialog
* tuned and cleaned some code

# 9.8.0

* in Subscriptions screen
	* reworked filter and sort routines to better sync with DB
	* fixed a long-term bug of sorting by Count of episodes play states
	* in Sort dialog, change top category does not change direction
	* added save/restore positions
	* open/close filter or sort dialog will auto-scroll to top
* in FeedDetails screen
	* in topbar, the "goto queue" icon is not shown when the feed has not associated queue
	* in topbar, changed the "open drawer" icon to navigate back (if any) otherwise open drawer
	* in menu, "update feed" and "update complete feed" will force update even the feed is set not to keep updating
	* ensured episode return to List mode
	* if curEpisode belong to the feed
		* on first start, it scrolls to the curEpisode
		* if returned from EpisodeInfo, it restores the previous position
* in OnlineFeed topbar, changed the "open drawer" icon to navigate back (if any) otherwise open drawer
* note, drawer can always be open by swiping from the left edge of the screen
* in EpisodeInfo screen topbar, the "add to queue" icon is made more explicit
* in Queues screen
	* queues modes are persisted for returning to
	* in actQueue:
		* on first start, it scrolls to the curEpisode
		* if returned from EpisodeInfo, it restores the previous position

# 9.7.0

* some rework of navigation and default screen
	* amended mandatory parameters of some screen to be optional (for safe navigation)
	* "Remember" can now remember basically any screen
	* when "Remember" is set for default screen it is not set as the root screen, only for next start
* in NavDrawer, when opening a top feed, nav tree is not cleared
* fixed Queues not remembering the last queue
* in sort dialog of Queues, put "Time in queue" at the beginning
* most button grids can be horizontally scrolled (compensating for long languages)
* monitoring of prefs for Subscriptions and Facets are moved to the screens
* volume adaptation is no longer persisted in episodes, it's a one-off setting in the AudioPlayer
* app build.gradle syntax change per Android Studio

# 9.6.2

* fixed again queue not changing issue
* made Queues, FeedDetails, and EpisodeInfo screens parameterized
* fixed queue lock mismatch in Queues
* updated code in Player screen for better integration with Compose
* episode volume adaptation is persisted
* some code refactoring

# 9.6.1

* fixed queue not changing issue

# 9.6.0

* merged and reworked OnlineSearch and OnlineResults screens
* migrated Discovery screen to TopChartFeeds, showing top lists based on selected genre and country (from Apple)
	* limit of top list set to 100, including subscribed with marks
	* if no podcasts exist for the selected country, return nothing, rather than getting the US list
* in OnlineFeed screen, added and inline row and link for "Feeds likely related to the author" for further research
* likely fixed an issue that could cause app collection of languages and tags to be set empty
* amended screen navigation with arguments
* Player use episode image regardless of image setting
* enlarged the cover image in PlayDetailed view
* fixed the image in EpisodeInfo, and added large image at the end of details, both use episode image regardless of image setting

# 9.5.1

* in EpisodeInfo screen, chapters are shown inline
* in Player Details screen, chapters are shown inline
* chapters dialog is no longer shown in EpisodeInfo and PlayerDetails screens
* in Queues screen, down-swipe is prompted with choice of just enqueue/download or refresh (including enqueue/download)
* in OnlineFeed screen, fixed the repeating language text
* fixed crash subscribing feed with url containing itunes.apple.com
* for RSS feed, only check on url for duplicate feeds, same title is allowed
* fixed crash of IndexOutOfBounds when removing the last podcast
* adjusted a bit seekTo routine

# 9.5.0

* amended search search routine to add exclude capability:
	* same as before, search terms can be separate by space (for single words), or by comma (for phrase)
	* use '-' before a word/phrase will exclude the word/phrase
	* e.g. "wordA wordB, -wordC wordD" (no need for the quote signs), searches for phrase "wordA wordB" and excludes phrase "wordC wordD"
* added Text filter in Episodes filters, usage is same as above, the results are shown in the same screen, not in Search screen
* in FeedDetails, the search button now starts the Search screen to search in all feeds
* Search screen no long search in a single feed, and no long reacts to feed list changes
* in Facets screen
	* in Commented mode, playstate and rating are shown with the comments
	* in Tagged mode, playstate and rating are shown with the tags

# 9.4.1

* when editing tags for multiple episodes, note existing tags not shown
* fixed crash when doing reconcile in Facets screen
* added tags filter in Episodes lists
* amended feeds refresh routine to filter out feeds not set to update when refreshing single or multiple feeds
* not rescheduling feeds refresh on first start

# 9.4.0

* list in Add to queue dialog is sorted
* amended logic for feeds refresh on mobile
* made feeds refresh in foreground service to avoid being killed by some system
* amended feeds refresh routines to allow multiple feeds
* in Queues screen 
	* enabled down-swipe to refresh associated feeds
	* disabled refresh all feeds in menu (not proper place to do)
* edit episodes tags can be accessed in swipe actions and multi-select menus
* some code refactoring

# 9.3.0

* on Episodes lists
	* ensure button updates when media is deleted
	* update curEpisode action button on compose
	* "Remove from active queue" is changed to "Remove from all queues" in swipe actions and multi-selection options
* moved setting of "Continuous playback" to queue's setting as playInSequence (defaulted to true)
* when playing an episode in FeedDetails list, playInSequence in the virtual queue is set to true only if the feed's associated queue is not None
* feed languages and tags are shown in FeedDetails, where tags can be edited
* in Queues screen
	* spinner list is sorted
	* ensure the spinner text reflect on actQueue
* enabled position seeking from https patterns in episode's description
* not prepare player on first start
* multiple-language feeds are supported
* tags and languages are persisted
* enabled tagging for episodes
* amended tags editing routine to work for both Feeds and Episodes
* in EpisodeInfo
	* fixed showing no media label on normal episode
	* adjusted detailed info layout
	* added tags editing for the episode
	* moved webdata processing to IO dispatcher
* added Tagged in episodes filters
* added Tagged mode in Facets screen
* fixed feed statistics summary showing 0s
* put SharePreferences properties in MainActivity to AppPreferences

# 9.2.1

* amended addInQueue routines to handle orders properly
* likely fixed order mismatch of curQueue and actQueue
* Virtual queue size set to 50 (200 unrealistic)
* in Audo-download/enqueue, when checking if episodes are in queue, skip virQueue
* in Settings->Network, added option to disable using WifiLock (as it's reported not functional on modern Android)
* added a toggle for "don't ask again for restricted background" in Settings->Interface
* background permission request dialog now sets permission directly
* cleared some useless events

# 9.2.0

* fixed crash when setting swipeactions in Queues Bin
* in Facets screen
	* each screen mode has its own sortOrder, and its own swipeActions
	* all screen modes (except Recorded) allow further filtering (using AND only)
* likely fixed the strange behaviors of back press in Queues and FeeDetails screens
* more SharePreferences properties moved to the DB
* some code refactoring

# 9.1.0

* added Virtual queue in Queues
* playing/streaming any episode in list of FeedDetails, Facets, or Search screens, the list (limited to 200 episodes) is set as the Virtual queue
* introduced ActQueue, it's the queue where the playback traverses, whereas curQueue now is the current queue on Queues screen
* playing/streaming an episode in any queue (real or virtual), that queue is set as ActQueue
* playing/streaming an episode from OnlineFeed screen sets actQueue to none, and is one off
* operation "Add to active queue" now adds to the ActQueue (not the curQueue). If it's virtual, ignored
* changed all external references to curQueue to ActQueue
* properly handle queue's sortOrder of timeInQueue descending
* moving episodes from one queue to another no longer changes the play state
* increased max number of queues to 15
* in Queues screen
	* curQueue is persisted
	* ActQueue is marked on the Spinner with ">"
	* queue isLocked is set individually for every queue, and it's only settable when queue's sortOrder is timeInQueue ascending
	* added remove queue in Settings
	* screenMode is persistent in session
	* in Feeds mode
		* added icon in top bar to access Facets showing all episodes in the associated feeds
		* added feed count on the top bar
	* back press from Settings returns to Queues
* in Subscriptions screen, added icon in top bar to access Facets showing all episodes in current feeds
* Amended auto-download/enqueue routine to
	* ensure episodes marked as InQueue are actually in queue
	* mark excluded episode as Played if set
* moved some SharePreferences properties to the DB (sorry no migrations)
* some code refactoring

# 9.0.0-1

* fixed number on episodes tab in Search screen
* in Queues screen
	* moved Rename from menu to the Settings
	* disabled setting of keepSorted, it's automatically set if sortOrder is not timeInQueue ascending

# 9.0.0

* large rework in various parts to enhance efficiency and directly syncing Compose with DB
* in FeedSettings, introduced useEpisodeImage (defaulted to false)
* in list view of FeedDetails screen:
	* episode image is shown when both useEpisodeImage in feed and prefEpisodeCover in global settings are set to true
	* if not set to use episode image, cover image is not shown on list items
	* when clicking on the episode image, EpisodeInfo screen is opened (instead of FeedDetails)
* when removing episodes from queue, if status is InQueue, set them to proper status (Unplayed or Unspecified)
* in episodes filters, added choice of AND/OR for "Join categories with" (previously always AND)
* in Facets screen
	* added Queued for all queued episodes
	* fixed Record showing no episodes
* keep queue sorted is on per queue basis, and if set, addToQueue only add to the back
	* if you sort the queue, the play order might not be updated (to be fixed later)
* in Queues screen added Settings in menu, to set about the current queue
	* keepSorted is moved from sorting dialog
	* binLimit is moved from the menu
	* EnqueueLocation (default to Back) setting moved from global Playback is now on per queue basis
	* AutoEQDLOnEmpty (default to true) and AutoDLEpisodesInQueue (default to false) moved fro global Network are now on per queue basis
* in Subscriptions screen
	* fixed selecting feed with language set to empty string
	* when swipe down, a confirmation is shown for feeds refresh
	* toggle grid/list now persists
* in Settings->Appearance, Subscriptions settings (swipe to refresh and show grid) are removed
* in Settings, Auto backup of OPML is moved to Import/Export screen
* when finished playing an episode, if the episode state is set to Again during play time, it will not be set to Played
* in auto-backup, catch exception when deleting file not exist
* in auto-download/enqueue algorithm, feed auto filters (include/exclude) are applied when querying candidates (not afterwards) 
* in Playback, not releasing wifiLock in between streaming
* Episodes sorting adds TimeInQueue and TimeOutQueue
* fixed erasing episodes not allowed in normal feed
* toast messages are not popped up when app in background, only save in Log
* episodes sorting Random and SmartShuffle are disable (to be restored later)
* much rewritten, thrown out, and refactored
* AGP upped to 8.13.1
* various dependencies update

# 8.25.2

* in Subscriptions screen, when refreshing feeds, network permission and availability is checked and if failure, toasts are posted
* for feeds refresh, network permission and availability checks are performed at the outmost routine
	* if failure, retry is scheduled if periodic task, otherwise only post toasts
* in FeedDetails screen, fixed the issue of feed refresh running twice on swipe (introduced in 8.25.1)
* in EpisodeState, added ERROR, and set color on some states
* Episodes lists shows play states in colors
* in MediaPlayer, if setSource has an exception, the episode is set to ERROR, and exit promptly without calling the exoplayer
* improved efficiency a bit
* some code refactoring

# 8.25.1

* in Queues and FeedDetails screens, back-press handling is reverted. no reliable solution now.
* subscribe to episode monitor now overwrites existing
* tuned Episodes lists routines, likely fixed issues of progress not updating properly
* fixed possible IndexOutOfBoundsException when subscribing feed with a limit size
* some code refactoring

# 8.25.0

* manual feed refresh by down-swipe in Subscriptions screen will restart auto-refresh schedule
* episodes filters added clipped, marked
* in Facets screen, clipped or marked has a separate view
* defined worthy episodes as:
	* have comments but not Ignored, or
	* Rating above Good, or
	* set to Soon, Later, Again, Forever, or
	* have recorded clips or audio marks
* added limitEpisodesCount in feed settings (defaulted to 0: unlimited), if set above 0:
	* on feed refresh (or full refresh), older episodes are erased
	* worthy episodes are not counted and are kept
	* when increased or reset to 0, feed full refresh (from menu on FeedDetails screen) will fill in additional older episodes
* in OnlineFeed screen, added limitEpisodesCount box, and ensure limit count in subscribe routine
* Erase episodes action (via swipe or multi-select) is enabled for normal feeds
* in FeedSettings,
	* ensured some items reflect changes
	* amended Episodes filter dialog for autodownload/enqueue, and added + button and hint text for "Add term"
* in auto-download/enqueue, increased the number of candidates
* in Queues and FeedDetails screens, force back press to to behave
* when setting episodes play status to Again or Later, repeat intervals can be flexibly adjusted in the dialog
* in Settings
	* renamed "Network and Downloads" to Network
	* moved Synchronization settings to Network
* ensure to catch exception of invalid url's for safety
* some code refactoring

# 8.24.0

* amended app init mechanism, likely fixed various strange issues
* fixed possible NullPointer in episodes lists on startup
* use one time request to schedule feeds refresh, more flexible and hopefully more timely and more frequent
	* Android may kill the refresh process if it takes over 10 minutes
	* in case of errors (including being killed), retry is scheduled for maximum of 3 times at interval of a quarter of the refresh interval
	* retry is only performed on feeds not refreshed previously
* amended streaming on mobile confirmation mechanism
	* it only appears when stream button on a list is clicked
	* if Once is tapped, it starts streaming the current episode, and all applicable episodes (on a queue) without further confirmations
* in Settings, renamed the Downloads screen to Network and Downloads, and shuffled some items with the Playback screen
* in Settings->Downloads
	* feeds refresh interval is now set in minutes (not hours)
		* default to 360 minutes, sorry no migration
		* not sure how frequent Android allows reliably (2 minute interval has been tested OK)
		* it should be set longer than the refresh duration which can be a few minutes it you have many feeds and a slow network
	* removed start time setting (on every interval setting, the schedule restarts)
* in FeedDetails screen
	* avoided possibly double assembling list
	* likely fixed current playing progress not updating issue
* in episodes lists, a past-due episode (Again or Later) is highlighted
* added experimental feature of scheduling playing episodes
	* in any episodes list, swipe action (also in the Combo) pop up setting for "Play episode at time"
	* the episode will be played if media is ready or streamed
	* multiple schedules are allowed
	* start time is precise as set. note, after scheduling, the app should not be force-stopped, or battery-optimized
	* currently:
		* schedule is only for the next 24 hours
		* there is no further control on it
		* at the end of the episode, it may continue onto other episodes (depending on the feed or the queue)
* restored alternative action buttons menu left out since 8.20.0
* fixed failure downloading previously partially/fully downloaded media 
* moved some disk access calls to IO dispatchers to speed up launches
* ensured shared text starts the app in the right screen
* ensures full updates action from Subscriptions menu (though not quite necessary)
* made vms in EpisodeLazyColumn immutable
* OnlineFeed screen not exits when ShowTabsDialog is dismissed
* Kotlin upped to 2.2.20, krdb to 3.2.9
* in build.gradle, replaced setting --no-version-vectors
* some code refactoring and cleaning


# 8.23.3

* fixed issue of proper screen not opening when receiving shared text multiple times
* in filter dialog of Subscriptions screen
	* multi-select categories are colored according to selections
	* reset button selects all of languages, tags and queues
* OnlineFeed screen exits when ShowTabsDialog is dismissed
* all screens now use LocalNavController

# 8.23.2

* on episodes lists, fixed action button not updated properly after download
* enhanced shared text reception: 
	* fixed shared text not properly received when the app is not started
	* plain text can include spaces, is opened in OnlineResults screen
	* feed url can be mixed with other text (possibly shared from other apps)
* made NavDrawer content update on open
* skipSilence is persisted globally
* fixed a crash when building a feed with invalid url
* some code refactoring

# 8.23.1

* fixed interval not correctly added to repeatTime
* state Later is handled similarly as state Again
* in episodes lists, when an episode is set to Again or Later, the due date is shown
* after playing, episode of Again or Later is set to Played
* in FeedDetails screen
	* starting in Info mode is sped up
	* fixed filter button color not correctly initialized in List mode

# 8.23.0

* added 4 levels of repeat intervals in Feed settings
	* defaulted to (60 minutes, 24 hours, 30 days, 52 weeks)
	* can be customized in FeedSettings screen
* added repeatTime in Episode, and set it in DB to a random number between 10 and 100 days for existing Again episodes
* when setting episodes play status to Again, a separate dialog prompts to set desired interval
	* when setting single episode, intervals in the feed setting is used, otherwise, default of (60 minutes, 24 hours, 30 days, 52 weeks) are used
* in auto-download and auto-enqueue algorithms, on every feed, Again episodes passed repeatTime are first include
	* Note, due to Android code limitations, refresh time can not be guaranteed currently
* in download routine, if an episode has already downloaded, skip
* in Queues screen, init sort order is set to null or kept
* in Subscriptions screen, tags filter is not shown if not tags are set
* some dependencies update

# 8.22.0

* in filter dialogs and Subscriptions sort dialog
	* clickable "X" is changed to "A" which toggles select all or nothing
	* "<<<", "A", ">>>" are outline buttons
* in filter dialog of Subscriptions screen, spinners of languages, queues and tags are replaced with multi-select categories
	* Note, in each category:
		* when no item is selected, feeds not set in that category are shown
		* when all items are selected, feeds not set in that category are also included
		* three categories may need reset on first startup
* in episodes lists
	* likely fixed issue of current episode not updating when list is changed
	* amended button setting mechanism when play state changes
* in Queues screen, likely fixed again back press not returning to Queues from bin
* in FeedDetails screen, likely fixed back press not returning to list from history or disabled filter
* EpisodeInfo screen directly monitors download status
* button status color is changed from Green to an intermediate color
* removed EpisodeDownloadEvent
* some code refactoring
* not doing R8 optimization, longer build time but little benefits

# 8.21.0

* changed feed auto-enqueue algorithm a bit: it checks in the associated queue (rather than all queues) for episodes of the feed. A bit behavior changes:
	* when maximum number of episodes remain in the queue, even some have changed status (partially played for instance), no new episodes are added
	* when some episodes are moved to another queue, then new episodes will be added
	* this doesn't apply to Only New with Replace policy, which behaves as before: checks enqueued episodes in all queues only for InQueue status
* enhanced download tasks observation
	* added Incomplete status
	* finished tasks are pruned from observations
* in episodes lists
	* download/cancel buttons are now handled more efficiently, eliminated list searches
	* download progress is now shown on the cancel button
* fixed tabs deprecation and horizontal scrolling in Search screen
* when setting an episode to Played, Skipped, Passed, or Ignored, it's no longer auto-downloadable
* likely fixed bottom sheet misbehavior (on startup or after keyboard dismissal)
* in Queues screen, likely fixed back press not returning to Queues from bin
* some code refactoring

# 8.20.0

* overhauled ActionButtons routines
* in episodes lists
	* ensured button update when download completes and when deleted
	* re-enabled TTS button (somehow it was omitted) on episodes without proper download url
* avoided ID3Reader throwing exception when tag header has unexpected characters (not sure why sometimes it happens), only showing error toast
* minor spacing adjustment in filter dialogs
* amended colors in sort dialogs
* cleaned out unused resource files

# 8.19.0

* in Subscriptions screen
	* languages, queues, and tags spinners are moved to the filter dialog
	* info text is shown on the title bar
	* InfoBar is removed
* on filter dialogs (podcasts and episodes)
	* rows are horizontally scrollable: some translated text can be too long
	* amended some formats and colors
* all top app bars have a bottom divider
* in ShareReceiver, when the url is null or empty, only toast error message is shown, dialog is removed
* minor Compose deprecations updates

# 8.18.1

* in episodes lists, when app returns to foreground, refresh played items
* save current sort order in Queues and Facets screens
* use new R8 proguard syntax
* gradle to 9.1.0, ADP to 8.13.0
* ndk upped to 29
* large dependencies update

# 8.18.0

* in Queues screen
	* modes of "show bin" and "show feeds" remain after viewing episodes or feeds
	* back press from Bin or Feeds on Queues screen return to queue view
* in FeedDetails screen
	* mode of "show history" remains after viewing episodes
	* back press from history returns to normal view
	* display of score is accompanied with a count (play state above Progress)
	* score is further adjusted on each episode
		* play state of Progress is included
		* in the range of (-0.5, 0.5) based on played duration vs full duration if play state is Skipped, Played, Passed, or Ignored
		* with an extra 0.5 if it's set as Again and Forever
		* so score can be somewhat above 100 or below -100
* some code cleaning

# 8.17.9

* in top action bar of FeedDetails screen
	* "go to queue" button opens the associated queue (if any)
	* "visit website" is moved to the menu
	* added "show history" button
	* re-arranged order of the buttons
* in the top menu of FeedDetails screen, Share is conditional
* in header of FeedDetailed screen, relocated the Score a bit
* likely fixed episode counts possibly showing wrong number in FeedDetailed header
* minor code restructuring
* Temporarily reverted Gradle plugin to 8.11.1, and media3 to 1.7.1

# 8.17.8

* in header of FeedDetailed screen, added Score of the feed, ranging from -100 to 100
	* average of rating codes of all episodes with play state above Progress divided by 2 and multiplied by 100
	* note: Super=2, Good=1, OK=0, Bad=-1, Trash=-2; Unrated assumed as OK
* various updates of dependencies, including media3
* gradle updated to 9.0

# 8.17.7

* added likeCount parameter for episodes (where applicable)
* Kotlin upped to 2.2.0
* krdb upped to 3.2.8
* updated gradle and some dependencies

# 8.17.6

* Rewind and Forward buttons no longer block, allow consecutive presses
* when opening an online feed, check existing feeds based on url and title
* fixed a possible  null pointer issue in episodes list
* fixed improper handling duplicates or possible crash when deleting duplicate episode
* some Compose dependencies update

# 8.17.5

* avoid auto fetch media size in EpisodeInfo when feed is set to prefer streaming
* avoid reloading feed in FeedDetails screen when screen is back on
* fixed crash when setting current episode to Ignored 
* avoid setting current episode to Ignored again if a duplicate item was previously set to Forever, Skipped, Played, Passed, Ignored
* feed cleanup also delete loose duplicate episodes if any
* updated some dependencies

# 8.17.4

* fixed possible null pointer exceptions in EpisodeInfo screen
* fixed situation when window being null in VideoPlayerActivity
* fixed paddings of VideoPlayerActivity for Android 15
* likely fixed current episode not properly set to Ignored if a duplicate item was previously set to Forever, Skipped, Played, Passed, Ignored 
* ensured to update list in Queues and FeedDetails screens when screen is back on 
* added menu item "Clear all cache" in AudioPlayer screen to remove played episodes from cache
* krdb updated to 3.2.7

# 8.17.3

* fixed space blocking when PlayUI is dismissed
* better messaging of feed operations on status bar 
* fixed current episode not properly set to Ignored if a duplicate item was previously set to Forever, Skipped, Played, Passed, Ignored
* when removing not finished episode from queue, its status is set to Passed if it's not started playing otherwise Skipped
* likely fixed crash when episode monitoring changes

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
