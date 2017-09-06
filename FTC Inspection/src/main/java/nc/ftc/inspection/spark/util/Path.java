package nc.ftc.inspection.spark.util;


public class Path {

    // The @Getter methods are needed in order to access
    // the variables from Velocity Templates
    public static class Web {
        public static final String INDEX = "/index/";
        public static final String LOGIN = "/login/";
        public static final String CHANGE_PW = "/changepw/";
        public static final String LOGOUT = "/logout/";
        public static final String ERROR_403 = "/error403/";
        public static final String IP_PAGE = "/ip/";
        public static final String CREATE_ACCOUNT_SIMPLE = "/create/account/";
        public static final String CREATE_ACCOUNT = CREATE_ACCOUNT_SIMPLE + ":id/";
        public static final String CREATE_EVENT = "/create/event/";
        public static final String MANAGE_EVENT = "/event/:event/manage/";
        public static final String EDIT_FORM = "/event/:event/edit/";
		public static final String INSPECT = "/event/:event/inspect/:form/"; //the inspection form page
		public static final String INSPECT_SELECT = "/event/:event/select/:form"; //page to select teams to inspect
        public static final String INSPECT_ITEM = "/event/:event/inspect/:form/";
        public static final String NEW_TEAM = "/teams/all/"; // POST to add new team
        public static final String EDIT_TEAM = "/teams/all/"; //PUT to edit team
        public static final String MASTER_TEAM_LIST = "/teams/all/"; //GET for list of teams
        public static final String EVENT_STATUS = "/event/:event/status/data"; //Endpoint to get data
        public static final String EVENT_STATUS_PAGE = "/event/:event/status/"; //page to view table
		public static final String ALL = "*";
        
		
        public String getIndex() {
        	return INDEX;
        }
        public String getConsole() {
        	return "";
        }
        public String getInspection() {
        	return "";
        }
        public String getLogout() {
        	return LOGOUT;
        }
        public String getLogin() {
        	return LOGIN;
        }
    }

    public static class Template {
        public final static String INDEX = "/velocity/index/index.vm";
        public final static String LOGIN = "/velocity/index/login.vm";
        public final static String BOOKS_ALL = "/velocity/book/all.vm";
        public static final String BOOKS_ONE = "/velocity/book/one.vm";
        public static final String NOT_FOUND = "/velocity/notFound.vm";
        public static final String IP_PAGE = "/velocity/index/ip.vm";
        public static final String ERROR_403 = "/velocity/403.vm";
        public static final String CREATE_ACCOUNT = "/velocity/users/createAccount.vm";
        public static final String CREATE_EVENT = "/velocity/event/createEvent.vm";
        public static final String MANAGE_EVENT = "/velocity/event/manageEvent.vm";
        public static final String EDIT_FORM = "/velocity/event/editForm.vm";//just renders form for now
        public static final String INSPECT = "/velocity/event/inspect.vm";
    }

}