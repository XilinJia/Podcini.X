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
