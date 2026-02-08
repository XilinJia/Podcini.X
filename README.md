# Podcini.X

<img width="100" src="https://raw.githubusercontent.com/xilinjia/podcini/main/images/icon 256x256.png" align="left" style="margin-right:15px"/>

An open source podcast instrument, attuned to Puccini ![Puccini](./images/Puccini.jpg), adorned with pasticcini ![pasticcini](./images/pasticcini.jpg) and aromatized with porcini ![porcini](./images/porcini.jpg), invites your harmonious heartbeats.

### Rendezvous chez:

[<img src="./images/external/getItGithub.png" alt="Get it on GitHub" height="50">](https://github.com/XilinJia/Podcini.X/releases/latest)
[<img src="./images/external/getItIzzyOnDroid.png" alt="IzzyOnDroid" height="50">](https://apt.izzysoft.de/fdroid/index/apk/ac.mdiq.podcini.X)
[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" alt="Get it on F-Droid" height="50">](https://f-droid.org/packages/ac.mdiq.podcini.X/)
<!-- [<img src="https://www.openapk.net/images/openapk-badge.png" alt="Get it on OpenApk" height="50">](https://www.openapk.net/podcini-x-podcast-instrument/ac.mdiq.podcini.X/ ) -->
<!-- [<img src="https://www.androidfreeware.net/images/androidfreeware-badge.png" alt="Get it on AndroidFreeware" height="50">](https://www.androidfreeware.net/download-podcini-x-podcast-instrument-apk.html) -->


[//]: # ([<img src="./images/external/amazon.png" alt="Amazon" height="40">]&#40;https://www.amazon.com/%E8%B4%BE%E8%A5%BF%E6%9E%97-Podcini-R/dp/B0D9WR8P13&#41;)


### A fork of [Podcini.R](<https://github.com/XilinJia/Podcini>) as of Jan 13 2025, this project inherits all functionalities of Podcini.R only with the capability of accessing to Youtube stripped off.

## Notable features

1. Is fully based on Jetpack Compose, using `media3` with `AudioOffloadMode` enabled (nicer to device battery).
2. Features multiple, natural and circular play queues associable with any podcast.
3. Features volume hierarchies each of which can contain sub-volumes and feeds.
4. Presents synthetic podcasts and allows episodes to be separately shelved.
5. Allows setting tags, todos, notes/comments, 5-level rating, and 12-level play state on every episode.
6. Boasts sophisticated sorting, filtering and searching on episodes and podcasts.
7. Supports sleep and auto play timers.
8. Handles auto-download or auto-enqueue governed by policy and limit settings of individual feed (podcast).
9. Supports spaced repetition of repeat episodes: auto-download or auto-enqueue them at preset intervals specified in the feed.
10. Caches streamed audio for seamless local rewind and replay.
11. Features audio clips recording and position marking on any episodes for better review.
12. Allows linking/relating multiple episodes for better grouping.
13. Is capable to preserve important episodes when a podcast is unsubscribed.
14. Spotlights sending/receiving feeds and subscriptions catalogue across devices.
15. Offers Readability and Text-to-Speech for RSS contents.
16. Supports auto-backups, customized media folder and importing DB from other apps

### Note:

#### For Podcini to show up on car's HUD with Android Auto, please read AnroidAuto.md for instructions.
#### If you need to cast to an external speaker or screen, you should install the "play" apk, not the "free" apk, that's about the difference between the two.

Podcini.X requests for permission for unrestricted background activities for uninterrupted background play of a playlist.  For more see [this issue](https://github.com/XilinJia/Podcini.X/issues/88)

If you intend to sync through a server, NextCloud server has been tested, but caution is advised. 

## Usage and notable features description 
<details> <summary>Click to expand</summary>

### Quick start

* On a fresh install of Podcini, do any of the following to get started enjoying the power of Podcini:
  * Open the drawer by right-swipe from the left edge of the phone
  * Tap "Add Podcast", in the new view, enter any key words to search for desired podcasts, see "Online feed" section below
  * Or, from the drawer -> Settings -> Import/Export, tap either "Import AntennaPod DB" or "Import Podcast Addict DB" to import the DB you have exported from the other apps.
  * Or, from the drawer -> Settings -> Import/Export, tap OPML import to import your opml file containing a set of podcast

Note, if you already have subscriptions in Podcini, importing the OPML file or the DB from the other apps will not erase your existing data in Podcini, except if the imported DB contains common podcasts you have in Podcini, the imported data will overwrite on those in the existing subscriptions in Podcini.
  
### Podcast (Feed)

* A podcast/feed can be subscribed online or loaded from a local directory with media files
* Every feed (podcast) can be associated with a queue allowing downloaded media to be added to the queue
* In addition to subscribed podcasts, synthetic podcasts can be created and work as subscribed podcasts but with extra features:
  * episodes can be copied/moved to any synthetic podcast
  * episodes from online feeds can be shelved into any synthetic podcasts without having to subscribe to the online feed
* FeedDetails screen has two views: FeedInfo and FeedEpisodes, which can be toggled by tapping on the cover image
* FeedInfo view offers a link for direct search of feeds related to author
* In FeedInfo view, one can enter personal comments/notes under "My opinion" for the feed
* A rating of Trash, Bad, OK, Good, Super can be set on any feed
* on action bar of FeedEpisodes view there is a direct access to the associated Queue, if any
* Long-press filter button in FeedEpisodes view enables/disables filters without changing filter settings
* Podcast's settings can be accessed in from the menu
* "Prefer streaming over download" is now on setting of individual feed
* Added audio type setting (Speech, Music, Movie) for improved audio processing
* RSS feeds with no playable media can be subscribed and read/listened (via TTS)
* there are two ways to access TTS: from the action bar of EpisodeHome view, and on the list of FeedEspiosdes view
  * the former plays the TTS instantly on the text available, and regardless of whether the episode as playable media or not, and the app can't control the playing except for play/pause
  * the latter does not play anything, instead, it constructs an audio file (like download) to be played as a normal media and the speed/rewind/forward can be controlled in Podcini

### Volume

* A volume is a container that can contain various number of feeds and various number of sub-volumes
* It can be used to organize similar feeds
* It can be loaded from a local directory tree when importing local feeds
  
### Episode

* New share notes menu option on various episode views
* there is a new rating system for every episode: Trash, Bad, OK, Good, Super
* there is a new play state system: Unspecified, Building, New, Unplayed, Later, Soon, Queue, Progress, Again, Forever, Skipped, Played, Passed, Ignored
  	* among which Unplayed, Later, Soon, Queue, Again, Forever, Skipped, Played, Passed, Ignored are settable by the user
	* when an episode is started to play, its state is set to Progress
	* when an episode is manually set to Queue, it's added to the queue according to the associated queue setting of the feed
	* when an episode is added to a queue, its state is set to Queue, when it's removed from a queue, the state (if lower than Skipped) is set to Skipped
	* when an episode is set to Again or Later, a due time can be specified
* in EpisodeInfo view, one can enter personal items:
  * comments/notes under "My opinion" for the episode
  * Todo with note and due time with timer
  * tags
* New episode home view with two display modes: webpage or reader
* In episode, in addition to "description" there is a new "transcript" field to save text (if any) fetched from the episode's website

### Podcast list

* Subscriptions page by default has a list layout and can be opted for a grid layout for the podcasts subscribed
* An all new sorting dialog and mechanism for Subscriptions based on title, date, time and count combinable with other criteria
* An all new way of filtering for both podcasts and episodes with expanded criteria.
  * some multi-factor criteria options are hidden by default, tap on the criteria to show the options.
* in Subscriptions list, click on cover image of a feed opens the FeedInfo/FeedEpisodes
* New and efficient ways of click and long-click operations on both podcast and episode lists:
  * click on title area opens the podcast/episode
  * long-press on title area automatically enters in selection mode
  * options to select all above or below are shown action bar together with Select All
  * operation options are prompted for the selected (single or multiple)
  * in episodes lists, click on an episode image brings up the FeedInfo view
* Downward swipe triggered feeds update
  * in Subscriptions view, all feeds are updated
  * in FeedEpisodes view, only the single feed is updated
* Local search for feeds or episodes can be separately specified on title, author (feed only), description (including transcript in episodes), and comment (My opinion)

### Episode list

* Episode lists appears in various screens: Queues (including bins), Facets, FeedEpisodes, OnlineFeed, etc.
* On most such lists, an episode can be played/streamed by pressing the action button on the episode
* when playing/streaming an episode from screen other than Queues, a sub-list of episodes are added to the virtual queue for better tracking
* The action buttons are normally formed automatically, but they allow to be customized in a feed settings.
* For play or stream, three actions are supported: normal (play next when one is finished), One (only play one episode), or Repeat (repeating the one episode)
* Long-press on the action button on any episode list brings up more options
* An all new sorting dialog and mechanism for Subscriptions based on title, date, time and count combinable with other criteria
* An all new way of filtering for both podcasts and episodes with expanded criteria.
  * some multi-factor criteria options are hidden by default, tap on the criteria to show the options.
* FeedEpisodes has the option to show larger image on the list by changing the "Use wide layout" setting of the feed
* Facets view provides easy access to various filters:
  * AllEpisodes, History and Download
  * New, Planned (for Soon and Later), Repeats (for Again and Forever), Liked (for Good and Super)
  * in each of the filtered views, related feeds can be shown
* Episodes list is shown in views of Facets, FeedEpisodes, and OnlineEpisodes
* New and efficient ways of click and long-click operations on both podcast and episode lists:
  * click on title area opens the podcast/episode
  * long-press on title area automatically enters in selection mode
  * options to select all above or below are shown action bar together with Select All
  * operation options are prompted for the selected (single or multiple)
  * in episodes lists, click on an episode image brings up the FeedInfo view
* Episodes lists supports swipe actions
  * Left and right swipe actions on lists now have telltales and can be configured on the spot
  * Swipe actions are brought to perform anything on the multi-select menu, and there is a Combo swipe action
  * Playing an episode at a specified future time can be set with a swipe action
* Downward swipe triggered feeds update
  * in Queues screen, the associated feeds are updated
  * in FeedEpisodes view, only the single feed is updated
* in episode list view, if episode has no media, TTS button is shown for fetching transcript (if not exist) and then generating audio file from the transcript. TTS audio files are playable in the same way as local media (with speed setting, pause and rewind/forward)
* Deleting and updating feeds are performed promptly
* Local search for feeds or episodes can be separately specified on title, author (feed only), description (including transcript in episodes), and comment (My opinion)

### Queues

* Multiple queues can be used: 5 queues are provided by default, user can rename or add up to 15 queues
  * on app startup, the most recently updated queue is set to active queue
  * any episodes can be easily added/moved to the active or any designated queues
  * any queue can be associated with any podcast for customized playing experience
* Every queue is circular: if the final item in queue finished, the first item in queue (if exists) will get played
* Every queue has a bin (accessible from the top bar of Queues view) containing past episodes removed from the queue, useful for further review and handling
* Feed associated queue can be set to None, in which case:
  * the episodes in the feed are not automatically added to any queue, instead FeedEpisodes view forms a natural queue on their own
  * when playing an episode in FeedEpisodes view, the next episode to play is determined in such a way:
    * if the currently playing episode had been (manually) added to the active queue, then it's the next in the queue
    * else if "prefer streaming" is set, it's the next unplayed (or Again and Forever) episode in the natural queue based on the current filter and sort order
    * else it's the next downloaded unplayed (or Again and Forever) episode
* There is a button on the top bar of the Queues view to show associated feeds 
* Otherwise, episode played from a list other than the queue is a one-off play, unless the episode is on the active queue, in which case, the next episode in the queue will be played

### Player

* More convenient player control displayed on all pages
* Player UI (button row) is horizontally swipable: to the left hides the player to the drawer (tap the teaser image at bottom of drawer to restore), to the right brings up the sleep timer dialog
* the cover image in Player UI
  * Tap opens the detailed info for the episode plus user set items
  * Long-press opens the Feed details
* Playback speed setting has been straightened up, three speed can be set separately or combined: current audio, podcast, and global
* There are two mechanisms in updating playback progress (configurable in Settings): every 5 seconds or adaptively at the interval of 2 percent of the media duration
* Volume adaptation control is added to player detailed view to set for current media and it takes precedence over that in feed settings
* The speedometer shows the current play speed, 
  * on tap, shows a dialog where various attributes can be set 
  on long-press, shows volume adaptation settings
* The Record button, when tapped, starts/ends recording, when long-pressed, records a timestamp marker, both of which can be accessed from Play detailed view
* Added preference "Fast Forward Speed" and "Fast Skip Speed" under "Playback" in settings with default value of 0.0, dialog allows setting a number between 0.0 and 10.0
* The Rewind button rewinds on tap by the number of seconds customizable, on long-press restarts the current media
* The Forward button forwards on tap by the number of seconds customizable
  * on long-press, if the user customize "Fast Skip Speed" to a value greater than 0.1
    * plays at the set speed,
    * long-press again restores the normal play speed
* The Skip" button on the player
  * long-press moves to the next episode
  * by default, single tap does nothing
  * if the user customize "Fast Skip Speed" to a value greater than 0.1, it behaves in the following way:
    * single tap during play, the set speed is used to play the current audio
    * single tap again, the original play speed resumes
    * single tap not during play has no effect
* Added preference "Fallback Speed" under "Playback" in settings with default value of 0.0, dialog allows setting a float number (capped between 0.0 and 1.5)
* if the user customizes "Fallback speed" to a value greater than 0.1, long-press the Play button during play enters the fallback mode and plays at the set fallback speed, single tap exits the fallback mode
* streamed media somewhat equivalent to downloaded media
  * there is a streaming cache, so mostly, Rewind/Forward on streaming simply operates from the cache
  * enabled episode description on player detailed view
  * enabled intro- and end- skipping
  * mark as played when finished
  * streamed media is added to queue and is resumed after restart
* There are three modes for playing video: fullscreen, window and audio-only, they can be switched seamlessly in video player
* Video player automatically switch to audio when app invisible or when switching to other views in the app.
* when video mode is set to audio only, click on image on audio player on a video episode brings up the normal player detailed view
* Episodes played to 95% of the full duration is considered completely played

### Online feed

* Upon any online search (by Add podcast), there appear a list of online feeds related to searched key words
  * a webpage address is accepted as a search term
* Long-press on a feed in online feed list prompts to subscribe it straight out.
* Press on a feed opens Online feed view for info or episodes of the feed and opting to subscribe the feed
* Online feed info display is handled in similar ways as any local feed, and offers options to subscribe or view episodes
* Online feed episodes can be freely played (streamed) without a subscription
* Online feed episodes can be selectively reserved into synthetic podcasts without subscribing to the feed

### Instant (or Wifi) sync

* Ability to sync between devices on the same wifi network without a server (experimental feature)
* It syncs the play states (position and played) of episodes that exist in both devices (ensure to refresh first) and that have been played (completed or not)
* So far, every sync is a full sync, no sync for subscriptions and media files

### Automation

* Auto refresh (feed updates) can be set with an interval in hours. Start time is "now" unless it's separately set
  * Note these timing are not guaranteed to be exact. Android has interests in controlling them.
* Auto download algorithm is based on settings in individual feed.
  * When auto download is enabled in the global Settings, by default, all undownloaded episodes in queues are candidates for download
    * whether or which queues are included in auto-download can be configures in Settings
  * Auto downloads run after feed refresh, scheduled or manual
  * Auto-downloading of episodes in any feed need to be separately enabled in the feed settings.
  * Each feed has its own limit (Episode cache) for number of episodes downloaded, this limit rules in combination of the global limit for the app.
  * Each feed can have its own download policy 
    * Only new: only new items at refresh time are download candidates.
      * without Replace, if old downloaded items (fulfilling the allowed cache) have not been played, new items will not be downloaded.
      * with Replace, new items will replace old downloaded items
    * Newest: the newest items (not necessarily new) are downloaded
    * Oldest: the oldest items are downloaded
    * Current filter and sort: the items to be downloaded depend on the current filtering and sorting criteria set in FeedDetails screen
      * the current filtering and sorting criteria are copied so, once set, future changes won't affect auto-download behavior
  * Those marked as Soon can be separately enabled, and once enabled, takes precedence over normal policies
  * After auto download run, episodes with New status in the feed is changed to Unplayed.
  * In auto download feed setting, inclusive and exclusive filters can be set (if needed) 
    * there are now separate dialogs for inclusive and exclusive filters where filter tokens can be specified independently
    * on exclusive dialog, there are optional check boxes "Exclude episodes shorter than" and "Mark excluded episodes played"
* Auto enqueue algorithm is based on settings in individual feed.
  * Auto enqueue run after feed refresh, scheduled or manual
  * Auto-enqueuing of episodes in any feed need to be separately enabled in the feed settings.
  * Each feed has its own limit (Episode cache) for number of episodes enqueued, this limit rules in combination of the global limit for the app.
  * Each feed can have its own enqueue policy 
    * Only new: only new items at refresh time are enqueue candidates.
      * without Replace, if old enqueued items (fulfilling the allowed cache) have not been played, new items will not be enqueued.
      * with Replace, new items will replace old enqueued items
    * Newest: the newest items (not necessarily new) are enqueued
    * Oldest: the oldest items are enqueued
    * Current filter and sort: the items to be enqueued depend on the current filtering and sorting criteria set in FeedDetails screen
      * the current filtering and sorting criteria are copied so, once set, future changes won't affect auto-enqueue behavior
  * Those marked as Soon can be separately enabled, and once enabled, takes precedence over normal policies
 * After auto-enqueue run, episodes with New status in the feed is changed to Unplayed.
  * In auto-enqueue feed setting, inclusive and exclusive filters can be set (if needed) 
    * there are now separate dialogs for inclusive and exclusive filters where filter tokens can be specified independently
    * on exclusive dialog, there are optional check boxes "Exclude episodes shorter than" and "Mark excluded episodes played"
* Sleep timer has a new option of "To the end of episode"

### Statistics

* Statistics compiles the media that's been played during a specified period and for today
* There are 2 numbers regarding played time: duration and time spent
  * time spent is simply time spent playing a media, so play speed, rewind and forward can play a role
  * Duration shows differently under 2 settings: "including marked as play" or not
  * In the former, it's the full duration of a media that's been ever started playing played
  * In the latter case, it's the max position reached in a media

### Security and reliability

* Disabled `usesCleartextTraffic`, so that all content transmission is more private and secure
* there are three sets of loggings: episodes downloaded, contents shared to Podcini, and contents removed from Podcini (either feeds or individual episodes in synthetic feeds) 
* in Import/Export settings, there is a new Combo Import/Export
	* it handles Preferences, Database, and Media files combined or selectively
	* all are saved to "Podcini-Backups-(date)" directory under the directory you pick
	* on import, Media files have to be done after the DB is imported (the option is disabled when importing DB is selected)
	* individual import/export functions for Preferences, Database, and Media files are removed
	* if in case one wants to import previously exported Preferences, Database, or Media files, 
		* manually create a directory named "Podcini-Backups"
		* copy the previous .realm file into the above directory
		* copy the previous directories "Podcini-Prefs" and/or "Podcini-MediaFiles" into the above directory
		* no need to copy all three, only the ones you need
		* then do the combo import
* There is an option to turn on auto backup in Settings->Import/Export
  * if turned on, one needs to specify interval (in hours), a folder, and number of copies to keep
  * then Preferences and DB are backed up in sub-folder named "Podcini-AudoBackups-(date)"
  * backup time is on the next resume of Podcini after interval hours from last backup time
  * to restore, use Combo restore 
* Folder for downloaded media can be customized
  	* the use of customized folder can be changed or reset
	* folder in SD card should also work (someone try it out)
	* upon change, downloaded media files are moved from the previous folder to the new folder
	* export and reconcile should also work with customized folder
* Play history/progress can be separately exported/imported as Json files (once needed when migrating from Podcini 5 with a different DB. now it doesn't seem to provide much benefit if one export/import the DB).
* Reconcile feature (accessed from Downloads in Episodes view) is added to ensure downloaded media files are in sync with specs in DB
* Podcasts can be selectively exported from Subscriptions view
* There is a setting to disable/enable auto backup of OPML files to Google
* Upon re-install of Podcini, the OPML file previously backed up to Google is not imported automatically but based on user confirmation.

For more details of the changes, see the [Changelog](changelog.md)

</details>

## Screenshots

### Settings
<img src="./images/1_drawer.jpg" width="238" /> <img src="./images/2_setting.jpg" width="238" /> <img src="./images/2_setting01.jpg" width="238" /> 

<img src="./images/2_setting1.jpg" width="238" /> <img src="./images/2_setting2.jpg" width="238" /> 

### Subscriptions
<img src="./images/3_subscriptions2.jpg" width="238" /> <img src="./images/3_subscriptions1.jpg" width="238" /> <img src="./images/3_subscriptions0.jpg" width="238" />

### Podcast
<img src="./images/5_podcast_0.jpg" width="238" /> <img src="./images/5_podcast_1.jpg" width="238" />

### Podcast settings
<img src="./images/5_podcast_setting.jpg" width="238" /> <img src="./images/5_podcast_setting1.jpg" width="238" /> 

### Episode and player details
<img src="./images/6_episode.jpg" width="238" /> <img src="./images/6_player_details.jpg" width="238" /> 

### Multiple queues and episodes easy access
<img src="./images/4_queue.jpg" width="238" /> <img src="./images/4_episodes.jpg" width="238" />

### Usage customization
<img src="./images/8_speed.jpg" width="238" /> <img src="./images/8_swipe_setting.jpg" width="238" /> <img src="./images/8_swipe_setting1.jpg" width="238" /> <img src="./images/8_swipe_actions.jpg" width="238" /> <img src="./images/8_multi_selection.jpg" width="238" />

### Get feeds online
<img src="./images/9_feed_search.jpg" width="238" /> <img src="./images/9_online_feed_info.jpg" width="238" /> <img src="./images/91_online_episodes.jpg" width="238" />

### Android Auto
<img src="./images/92_Auto_list.png" width="238" /> <img src="./images/92_Auto_player.png" width="238" />

## Links

* [Changelog](changelog.md)
* [Privacy Policy](PrivacyPolicy.md)
* [Contributors](CONTRIBUTORS.md)
* [Contributing](CONTRIBUTING.md)
<!-- * [Translation (Transifex)](https://app.transifex.com/xilinjia/podcini/dashboard/) -->
* [Translation (Crowdin)](https://crowdin.com/project/podcinix)

## License

Podcini.X, same as the project it was forked from, is licensed under the GNU General Public License (GPL-3.0).
You can find the license text in the LICENSE file.

## Copyright

New files and contents in the project are copyrighted in 2024 by Xilin Jia and related contributors.

## Licenses and permissions

[Licenses and permissions](Licenses_and_permissions.md)
