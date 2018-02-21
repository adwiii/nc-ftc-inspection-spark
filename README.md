# nc-ftc-inspection-spark
--*Version 2.3 Change Log* --

**Deleting Users** - There is now an option to delete users on the "Manage Users" page.

**Video Overlay Default Color** - There is now an "Event Settings" page (accessible by an admin below Event Management on th eevent home page). This page allows the setting of a default color for the video overlay. NOTE: If using an event created before this update, the value can only be set once. Setting it multiple times will cause unpredictable behavior (It will randomly choose one of the set values). 

**Match Control Page Safeties** - The control flow of the match control page has been improved to disable buttons that would cause illegal states. This fix handles the abort match case and re-allows access to the manual randomization pages before match start. The state of the control page is reset appropriately when a new match is loaded. The page also presents the user with a confirm dialog if they attempt to leave the page at time that would leave the server in an illegal state. (After randomization and before commit.)

**Score Tracker Auto Loading** - To prevent randomization desynchronization between score trackers and the server, the score tracker's page does not load until match start. As of now, if the match is aborted AND RANDOMIZATION IS CHANGED, the score tracker MUST REFRESH. This issue will be addressed soon.

**Logging Improvements** Log files are now handles internally instead of via batch redirects, so they will populate as the program executes instead of at termination.

**Deletion of Quals/Elims Data** - The Event Settings page mentioned above provides a way to delete all data from elims or quals. This is a very dangerous operation that should only be performed if a faulty match schedule is uploaded or incorrect alliances generated. (This feature is not fully tested yet) 

**Versioning and Copyright** - We have added a version label and licensing to the bottom of pages and to the code. 


--*Version 2.2 Change Log* --

**Video Overlay Display** - The Video Overlay shows the bottom portion of the field display, with some modifications to include the team numbers, timer, event name, and current match. The parking and jewel graphics swap to balancing and relics upon the first scored relic or the beginning of endgame, whichever occurs first. The timer is shown in a progress bar above the scores. The color is only shown during match play. The Match Preview, Randomization, Match Result, Alliance Selection, and Timeout pages show full-screen. The color of the video portion can be customized by editing the URL. Change the value of the color parameter to the 6-character hex encoding of whatever color you'd like:
http://localhost/event/example/field/?ad=true&overlay=true&color=FF00FF   

**Removing and Deleting Events** - When logged in as an admin, use the drop-down "Admin" menu to get to the Server Config page. From there, go to the Server Data Management Page. This page has options to remove or delete events. There is a description on the difference between the removing and deleting on the page.

**Importing and Exporting Users** - The same Server Data Management page has options to import and export user data. This allows the transfer of users between server instances. Click the "Export Users" button to download a "users.dat" file. Transfer that file to the other computer and use the file selector to select that file to import. The users will have the same username, privileges, and passwords as on the originating server.

**Importing Team List** - When creating events, there is now an option to select a teams.txt file to add teams. This appears during the "setup" phase only. After uploading, use the link to access the add/remove teams page to check that all teams were successfully imported.

**Control Page Stabilization** - To help prevent the server from entering illegal states, we have added some lockout features to the Match Control Page. Once a match is randomized, the control page is forced to the scores tab, and no other tab can be opened. The lock is released upon score commit or match abort. There are still a few ways to enter illegal states that we know of and will continue working on fixes for: 1. After Abort Match, you can load another match, and skip the preview phase. 2. After match commit, you can use the external randomization entry to randomize the next match, also skipping the preview phase. The current fix has made it so that once the match is randomized, you cannot use the external randomization to re-randomize. We are working to fix that as well.

**Legal Score Limits** -  We have added the same score sanity checks that are in the FIRST official software: no more than 24 glyphs, 8 rows, 6 columns, 2 ciphers, 4 jewels, 2 relics, 2 keys, etc. These have been added to both the score tracker pages and the match control page. Keep in mind, even the FIRST official software allows for some illegal states, such as scoring more keys than auto glyphs, or the two alliances combining to score more than the 4 jewels on the field. While the in-match input for these prevents this from happening, the review page still allows for these states to occur.

**Pit Display Flicker Fix** - We've fixed the flicker effect on the Pit Display and are working on adding it to the queueing display. 
