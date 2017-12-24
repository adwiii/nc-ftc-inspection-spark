package nc.ftc.inspection.spark.util;

import nc.ftc.inspection.Server;

public class Path {

    // The @Getter methods are needed in order to access
    // the variables from Velocity Templates
    public static class Web {
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
        
        public static final String NEW_TEAM = "/teams/all/"; // POST to add new team
        public static final String EDIT_TEAM = "/teams/all/"; //PUT to edit team
        public static final String MASTER_TEAM_LIST = "/teams/all/"; //GET for list of teams
        public static final String EVENT_STATUS = "/event/:event/status/data/"; //Endpoint to get data
        public static final String EVENT_STATUS_PAGE = "/event/:event/status/"; //page to view table
        public static final String UPLOAD_SCHEDULE = "/event/:event/scheduleUpload/";
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
        public static final String BOTH_SCORE = "/event/:event/score/";
        public static final String COMMIT_SCORES = "/event/:event/scorecommit/";
        public static final String SUBMIT_SCORE = "/event/:event/score/:alliance/submit/";
        public static final String MATCH_CONTROL = "/event/:event/control/";
        public static final String MATCH_PREVIEW = "/event/:event/time/preview/";
        public static final String START_MATCH = "event/:event/time/start/";
        public static final String PAUSE_MATCH = "event/:event/time/pause/";
        public static final String RESUME_MATCH = "event/:event/time/resume/";
        public static final String GET_MATCH = "event/:event/match/";
        public static final String LOAD_MATCH = "event/:event/match/load/:match/";
        public static final String WAIT_FOR_REFS = "event/:event/match/status/reviewcomplete/";
        public static final String GET_TIMER_COMMANDS = "/event/:event/time/command/";
        
        public static final String MATCH_RESULTS = "event/:event/results/";
        
        public static final String AUDIENCE_DISPLAY = "event/:event/audience/";
        public static final String FIELD_DISPLAY  ="/event/:event/field/";
        
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
		public static final String AUDIENCE_DISPLAY = "/velocity/event/audienceMatch.vm";
		public static final String INSPECTION_TEAM_SELECT = "/velocity/event/teamSelect.vm";
		public static final String BINARY_INSPECTION_PAGE = "/velocity/event/binaryInspection.vm";
		public static final String INSPECTION_OVERRIDE_PAGE = "/velocity/event/inspectOverride.vm";
		public static final String INSPECT_TEAM_HOME = "/velocity/event/teamInspectHome.vm";
		public static final String FIELD_DISPLAY = "/velocity/event/fieldDisplay.vm";
        
    }

}