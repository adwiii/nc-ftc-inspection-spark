package nc.ftc.inspection.spark.util;

import nc.ftc.inspection.Server;

public class Path {

    // The @Getter methods are needed in order to access
    // the variables from Velocity Templates
    public static class Web {
    	public static final String DEFAULT = "/";
        public static final String INDEX = "/index/";
        public static final String LOGIN = "/login/";
        public static final String USER_PAGE = "/me/";
        public static final String CHANGE_PW = "/changepw/";
        public static final String LOGOUT = "/logout/";
        public static final String ERROR_403 = "/error403/";
        public static final String IP_PAGE = "/ip/";
        public static final String CREATE_ACCOUNT_SIMPLE = "/create/account/";
        public static final String CREATE_ACCOUNT = CREATE_ACCOUNT_SIMPLE + ":id/";
        public static final String CREATE_EVENT = "/create/event/";
        public static final String MANAGE_EVENT = "/event/:event/manage/";
        public static final String MANAGE_EVENT_TEAMS = "/event/:event/manage/teams/";
        public static final String SET_STATUS = "/event/:event/manage/status/";
        public static final String EVENT_HOME = "/event/:event/home/";
        public static final String EVENT_SIMPLE = "/event/:event/";
        public static final String EDIT_PERMISSIONS = "/users/manage/";
        
        
        public static final String EDIT_FORM = "/event/:event/edit/";
        public static final String INSPECT_HOME = "/event/:event/inspect/";
		public static final String INSPECT = "/event/:event/inspect/:form/"; //the inspection form page
		//public static final String INSPECT_SELECT = "/event/:event/select/:form"; //page to select teams to inspect
        public static final String INSPECT_ITEM = "/event/:event/inspect/:form/";
        public static final String INSPECT_NOTE = "/event/:event/inspect/:form/note/";
        public static final String INSPECT_SIG = "/event/:event/inspect/:form/sig/";
        public static final String INSPECT_STATUS = "/event/:event/inspect/:form/status/";
        public static final String INSPECT_OVERRIDE = "/event/:event/inspect/:form/override/";
        public static final String INSPECT_TEAM_HOME = "/event/:event/inspect/team/:team/";
        public static final String INSPECT_TEAM_FORM = "/event/:event/inspect/team/:team/:form/";
        public static final String TEAM_INFO = "/event/:event/teams/info/";
        
        public static final String NEW_TEAM = "/teams/all/"; // POST to add new team
        //TODO change so as to not have duplicate constants?
        public static final String EDIT_TEAM = "/teams/all/"; //PUT to edit team
        public static final String MASTER_TEAM_LIST = "/teams/all/"; //GET for list of teams
        public static final String EVENT_STATUS = "/event/:event/status/data/"; //Endpoint to get data
        public static final String EVENT_STATUS_PAGE = "/event/:event/status/"; //page to view table
        public static final String UPLOAD_SCHEDULE = "/event/:event/manage/scheduleupload/";
        public static final String SCHEDULE = "/event/:event/schedule/";
        public static final String SCHEDULE_STATUS = "/event/:event/schedule/status/";
        //TODO have a subdir for scoring/match stuff under /event?
        public static final String RANDOMIZE = "/event/:event/randomize/";
        public static final String RERANDOMIZE = "/event/:event/rerandomize/";
        public static final String HEAD_REF = "/event/:event/headref/";
        public static final String REF = "/event/:event/ref/:alliance/";// --> /test/ref/red/   ?
        public static final String GET_RANDOM = "/event/:event/random/";
        //Updating vs submitting. updating is during the match. submit is after
        //need to be separate b/c both PUT and POST used on updating
        public static final String SCORE_BREAKDOWN = "/event/:event/scorebreakdown/";
        public static final String SCORE = "/event/:event/score/:alliance/"; 
        public static final String SCORE_AUTO = "/event/:event/score/:alliance/auto/"; 
        public static final String EDIT_SCORE = "/event/:event/score/edit/:alliance/"; // control page edit
        public static final String EDIT_SCORE_HOME = "/event/:event/editmatch/"; 
        public static final String BOTH_SCORE = "/event/:event/score/";
        public static final String COMMIT_SCORES = "/event/:event/scorecommit/";
        public static final String SUBMIT_SCORE = "/event/:event/score/:alliance/submit/";
        public static final String MATCH_CONTROL = "/event/:event/control/";
        public static final String LOCKOUT_REFS = "/event/:event/control/lockout/";
        public static final String MATCH_PREVIEW = "/event/:event/time/preview/";
        public static final String SHOW_PREVIEW = "/event/:event/display/preview/";
        public static final String SHOW_RESULTS = "/event/:event/display/results/";
        public static final String SHOW_RESULTS_OLD = "/event/:event/display/postold/";
        public static final String SHOW_MATCH = "/event/:event/display/match/";
        public static final String START_MATCH = "event/:event/time/start/";
        public static final String PAUSE_MATCH = "event/:event/time/pause/";
        public static final String RESUME_MATCH = "event/:event/time/resume/";
        public static final String RESET_MATCH = "event/:event/time/reset/";
        public static final String GET_MATCH = "event/:event/match/";
        public static final String LOAD_MATCH = "event/:event/match/load/:match/";
        public static final String WAIT_FOR_REFS = "event/:event/match/status/reviewcomplete/";
        public static final String WAIT_FOR_MATCH_END = "event/:event/match/status/end/";
        public static final String GET_TIMER_COMMANDS = "/event/:event/time/command/";
        public static final String GET_DISPLAY_COMMANDS = "/event/:event/display/command/";
        public static final String TIMEOUT_COMMAND = "/event/:event/timeout/:cmd/";
        public static final String GET_POST_RESULTS_INFO = "/event/:event/display/resultdata/";
        public static final String GET_SELECTION_INFO = "/event/:event/display/selectiondata/";
        public static final String GET_FULL_SCORESHEET = "/event/:event/match/:match/scoresheet/";
        public static final String GET_ALLIANCE_BREAKDOWN = "/event/:event/match/:match/scoresheet/:alliance/";
        public static final String PIT_DISPLAY = "/event/:event/pit/";
        
        //get gets the page, PUT returns new score breakdown, and POST commits.
        public static final String EDIT_MATCH_SCORE = "/event/:event/match/:match/edit/";
        public static final String GET_MATCH_FULL = "/event/:event/match/:match/full/";
        public static final String GET_MATCH_INFO = "/event/:event/match/:match/info/";
        
        public static final String MATCH_RESULTS = "event/:event/results/";
        public static final String MATCH_RESULTS_SIMPLE = "event/:event/results/simple/";
        public static final String MATCH_RESULTS_DETAILS = "event/:event/results/details/";
        public static final String RANKINGS = "/event/:event/rankings/";
        
        public static final String AUDIENCE_DISPLAY = "event/:event/audience/";
        public static final String FIELD_DISPLAY  ="/event/:event/field/";
        
        
        public static final String SELECTION = "/event/:event/selection/select/";
        public static final String START_SELECTION = "/event/:event/selection/start/";
        public static final String CLEAR_SELECTION = "/event/:event/selection/clear/";
        public static final String UNDO_SELECTION = "/event/:event/selection/undo/";
        public static final String SAVE_SELECTION = "/event/:event/selection/save/";
        
        public static final String REMOTE_POST = "/update/";
        public static final String SERVER_CONFIG = "/config/";
        public static final String VERIFY = "/config/verify/";
        public static final String CLIENT_KEYS = "/config/keys/";
        public static final String REMOTE_KEYS = "/config/remotes/";
        public static final String DATA_DOWNLOAD = "/config/remotes/dd/";
        public static final String DATA_DOWNLOAD_GLOBAL = "/config/remotes/ddglobal/";
        public static final String DATA_DOWNLOAD_EVENT = "/config/remotes/ddevent/";
        public static final String PING = "/ping/";
        
        public static final String UPLOAD_ALLIANCES = "/event/:event/manage/allianceupload/";
        
        //restarting teleop vs restarting match??
        public static final String ABORT_MATCH = "event/:event/time/abort/"; 
		public static final String ALL = "*";
        
		
        public String getIndex() {
        	return INDEX;
        }
        public String getConsole() {
        	return "";
        }
        public String getInspection() {
        	if (Server.defaultEventCode == null) {
        		return "/event/inspect/";//TODO fix this	
        	}
        	return "/event/" + Server.defaultEventCode + "/inspect/";
        }
        public String getLogout() {
        	return LOGOUT;
        }
        public String getLogin() {
        	return LOGIN;
        }
        public String getUserPage() {
        	return USER_PAGE;
        }
        public String getEditPermissions() {
        	return EDIT_PERMISSIONS;
        }
    }

    public static class Template {
        public final static String INDEX = "/velocity/index/index.vm";
        public final static String LOGIN = "/velocity/index/login.vm";
        public static final String USER_PAGE = "/velocity/users/user_page.vm";
        public final static String BOOKS_ALL = "/velocity/book/all.vm";
        public static final String BOOKS_ONE = "/velocity/book/one.vm";
        public static final String NOT_FOUND = "/velocity/notFound.vm";
        public static final String IP_PAGE = "/velocity/index/ip.vm";
        public static final String ERROR_403 = "/velocity/403.vm";
        public static final String CREATE_ACCOUNT = "/velocity/users/createAccount.vm";
        public static final String CREATE_EVENT = "/velocity/event/createEvent.vm";
        public static final String MANAGE_EVENT = "/velocity/event/manageEvent.vm";
        public static final String EDIT_FORM = "/velocity/event/editForm.vm";//just renders form for now
        public static final String INSPECT_HOME = "/velocity/event/inspect_index.vm";
        public static final String INSPECT = "/velocity/event/inspect.vm";
        public static final String STATUS_PAGE = "/velocity/event/status.vm";
        public static final String STATUS_PAGE_PROJECTOR = "/velocity/event/statusProjector.vm";
        public static final String PIT_DISPLAY = "/velocity/event/pit.vm";
        public static final String PIT_DISPLAY_PROJECTOR = "/velocity/event/pitProjector.vm";
        public static final String SCHEDULE_PAGE = "/velocity/event/schedule.vm";
        public static final String HEAD_REF = "/velocity/event/headRef.vm"; 
        public static final String REF_PRE_RANDOM = "/velocity/event/ref_preRandom.vm"; 
        public static final String REF_AUTO = "/velocity/event/ref_auto.vm";
        public static final String REF_AUTO_REVIEW = "/velocity/event/ref_autoReview.vm";
        public static final String REF_TELEOP = "/velocity/event/ref_teleop.vm";
        public static final String REF_REVIEW = "/velocity/event/ref_review.vm";
        public static final String REF_POST_SUBMIT = "/velocity/event/ref_postSubmit.vm";
        public static final String CONTROL = "/velocity/event/control.vm";
        public static final String MATCH_RESULT = "/velocity/event/results.vm";
        public static final String MATCH_RESULT_SIMPLE = "/velocity/event/resultsSimple.vm";
		public static final String MATCH_RESULT_DETAIL = "/velocity/event/resultsDetail.vm";
		public static final String AUDIENCE_DISPLAY = "/velocity/event/audienceMatch.vm";
		public static final String INSPECTION_TEAM_SELECT = "/velocity/event/teamSelect.vm";
		public static final String BINARY_INSPECTION_PAGE = "/velocity/event/binaryInspection.vm";
		public static final String INSPECTION_OVERRIDE_PAGE = "/velocity/event/inspectOverride.vm";
		public static final String INSPECT_TEAM_HOME = "/velocity/event/teamInspectHome.vm";
		public static final String TEAM_INFO = "/velocity/event/teamInfo.vm";
		public static final String FIELD_DISPLAY = "/velocity/event/fieldDisplay.vm";
		public static final String EDIT_SCORE_HOME = "/velocity/event/editScoreHome.vm";
		public static final String EDIT_MATCH_SCORE = "/velocity/event/editMatch.vm";
		public static final String RANKINGS = "/velocity/event/rankings.vm";
		public static final String MANAGE_EVENT_TEAMS = "/velocity/event/manageEventTeams.vm";
		public static final String UPLOAD_SCHEDULE = "/velocity/event/uploadSchedule.vm";
		public static final String EVENT_HOME= "/velocity/event/eventHome.vm";
        public static final String EDIT_PERMISSIONS = "/velocity/users/managePermissions.vm";
        public static final String FULL_SCORESHEET = "/velocity/event/readOnlyScore.vm";
        public static final String ALLIANCE_BREAKDOWN = "/velocity/event/readOnlyBreakdown.vm";
        public static final String CLIENT_KEYS = "/velocity/keys.vm";
        public static final String REMOTE_KEYS = "/velocity/remotes.vm";
        public static final String SERVER_CONFIG = "/velocity/serverConfig.vm";
        public static final String UPLOAD_ALLIANCES = "/velocity/event/uploadAlliances.vm";
        
    }

}