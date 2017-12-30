package nc.ftc.inspection;

import static spark.Spark.*;
import static spark.debug.DebugScreen.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import javax.servlet.MultipartConfigElement;
import javax.servlet.http.Part;

import nc.ftc.inspection.dao.EventDAO;
import nc.ftc.inspection.event.Event;
import nc.ftc.inspection.model.Alliance;
import nc.ftc.inspection.model.FormRow;
import nc.ftc.inspection.model.Match;
import nc.ftc.inspection.model.Team;
import nc.ftc.inspection.model.User;
import nc.ftc.inspection.spark.pages.DefaultPages;
import nc.ftc.inspection.spark.pages.EventPages;
import nc.ftc.inspection.spark.pages.GlobalPages;
import nc.ftc.inspection.spark.pages.LoginPage;
import nc.ftc.inspection.spark.util.Filters;
import nc.ftc.inspection.spark.util.Path;

public class Server {
	public static final String DB_PATH;// = "src/main/resources/db/";
	public static final String GLOBAL_DB;// = "jdbc:sqlite:"+DB_PATH+"global.db"; 

	//maps event code to Event object for in-RAM cache of data.
	//currently only for live-scoring. May need to add inspection in the future.
	public static Map<String, Event> activeEvents = new HashMap<>();
	// If the user does not specify an event, the server will assume that this is the event they mean
	// For the local server, this should be the event it is at, and for the remote server, there should be no default
	public static String defaultEventCode = "test11";
	static{ //TODO check if were in eclipse. If not, change DB path to lib folder?
		DB_PATH = "src/main/resources/db/";
		GLOBAL_DB = "jdbc:sqlite:"+DB_PATH+"global.db"; 
	}
	public static void main(String[] args) {
		try {//idk, somethings up with gradle but this makes it work.
			Class.forName("org.sqlite.JDBC");
		} catch (ClassNotFoundException e1) {
			e1.printStackTrace();
		}
		
//		System.out.println(EventDAO.getStatus("test2"));
////		EventDAO.cre
//		EventDAO.createEventDatabase("test12");
//		EventDAO.addTeamToEvent(10, "test11");
//		EventDAO.populateStatusTables("test11");
//		
//		List<FormRow> rows = EventDAO.getForm("test2", "HW");
//		for(FormRow fr : rows){
//			System.out.println(fr);
//		}
		EventDAO.loadActiveEvents();
		
		
		port(80);
		staticFiles.location("/public");
		//TODO remove the debug screen for release?
		enableDebugScreen();
		
		before("*", Filters.addTrailingSlashesAndLowercase);		
		
		get(Path.Web.INDEX, DefaultPages.indexPage);
		get(Path.Web.LOGIN, LoginPage.serveLoginPage);
		post(Path.Web.LOGIN, LoginPage.handleLoginPost);
		post(Path.Web.LOGOUT, LoginPage.handleLogoutPost);
		get(Path.Web.IP_PAGE, DefaultPages.ipPage);
		get(Path.Web.SCHEDULE, EventPages.serveSchedulePage);
		get(Path.Web.ERROR_403, DefaultPages.error403);
		get(Path.Web.EVENT_STATUS_PAGE, EventPages.serveStatusPage);
		get(Path.Web.RANKINGS, EventPages.handleGetRankings);
		get(Path.Web.MATCH_RESULTS, EventPages.serveResultsPage);
		get(Path.Web.EVENT_HOME, EventPages.serveEventHomePage);
		//I am unsure about the ones below here
		get(Path.Web.GET_RANDOM, EventPages.handleGetRandom);
		get(Path.Web.WAIT_FOR_REFS, EventPages.handleWaitForRefs);
		get(Path.Web.WAIT_FOR_MATCH_END, EventPages.handleWaitForEnd);

		//THESE ARE GENERAL USERS BUT NO ONE SHOULD EVER SEE THEM DIRECTLY BC THEY ARE REST
		get(Path.Web.MASTER_TEAM_LIST, GlobalPages.handleTeamListGet);
		get(Path.Web.EVENT_STATUS, EventPages.handleGetStatusGet);
		//I am unsure about the ones below here
		get(Path.Web.SCORE, EventPages.handleGetScore);
		get(Path.Web.SCORE_BREAKDOWN, EventPages.handleGetScoreBreakdown);
		get(Path.Web.SCHEDULE_STATUS, EventPages.handleGetScheduleStatus);
		get(Path.Web.GET_MATCH, EventPages.handleGetCurrentMatch);
		get(Path.Web.BOTH_SCORE, EventPages.handleGetFullScore);
		get(Path.Web.GET_MATCH_FULL, EventPages.handleGetFullResult);
		get(Path.Web.GET_MATCH_INFO, EventPages.handleGetMatchInfo);
		
		//User Pages - Must be logged in
		before(Path.Web.CHANGE_PW, Filters.getAuthenticationFilter());
		get(Path.Web.CHANGE_PW, LoginPage.servePasswordChangePage);
		post(Path.Web.CHANGE_PW, LoginPage.handlePasswordChangePost);
		
		before(Path.Web.USER_PAGE, Filters.getAuthenticationFilter());
		get(Path.Web.USER_PAGE, LoginPage.serveUserPage );
		
		
		//Inspection Pages - Must be inspector
		before(Path.Web.INSPECT, Filters.getAuthenticationFilter(User.INSPECTOR));
		get(Path.Web.INSPECT, EventPages.serveInspectionPage);
		
		before(Path.Web.INSPECT_HOME, Filters.getAuthenticationFilter(User.INSPECTOR));
		get(Path.Web.INSPECT_HOME, EventPages.serveInspectionHome);
		
		//Team Inspection Pages
		before(Path.Web.INSPECT_TEAM_HOME, Filters.getAuthenticationFilter(User.TEAM));
		get(Path.Web.INSPECT_TEAM_HOME, EventPages.serveTeamInspectionHome);
		
		before(Path.Web.INSPECT_TEAM_FORM, Filters.getAuthenticationFilter(User.TEAM));
		get(Path.Web.INSPECT_TEAM_FORM, EventPages.serveInspectionPageReadOnly);
		
		//LRI Pages
		before(Path.Web.EDIT_FORM, Filters.getAuthenticationFilter(User.LI));
		get(Path.Web.EDIT_FORM, EventPages.serveFormEditPage);
		
		before(Path.Web.INSPECT_OVERRIDE, Filters.getAuthenticationFilter(User.LI));
		get(Path.Web.INSPECT_OVERRIDE, EventPages.serveInspectionOverride);
		
		
		//TODO encrypt passwords on POST
		
		//Head Ref pages
		before(Path.Web.HEAD_REF, Filters.getAuthenticationFilter(User.HEAD_REF));
		get(Path.Web.HEAD_REF, EventPages.serveHeadRef);
		
		//Ref Pages
		before(Path.Web.REF, Filters.getAuthenticationFilter(User.REF));
		get(Path.Web.REF, EventPages.serveRef);
		before(Path.Web.INSPECT_ITEM, Filters.getAuthenticationFilter(User.REF));
		post(Path.Web.INSPECT_ITEM, EventPages.handleInspectionItemPost);

		//ADMIN Pages
		before(Path.Web.EDIT_PERMISSIONS, Filters.getAuthenticationFilter(User.ADMIN));
		get(Path.Web.EDIT_PERMISSIONS, LoginPage.serveEditPermissionsPage);
		post(Path.Web.EDIT_PERMISSIONS, LoginPage.handleEditPermissionsPost);
		
		before(Path.Web.MATCH_CONTROL, Filters.getAuthenticationFilter(User.ADMIN));
		get(Path.Web.MATCH_CONTROL, EventPages.serveMatchControlPage);
		
		before(Path.Web.AUDIENCE_DISPLAY, Filters.getAuthenticationFilter(User.ADMIN));
		get(Path.Web.AUDIENCE_DISPLAY, EventPages.serveAudienceDisplay);
		
		before(Path.Web.FIELD_DISPLAY, Filters.getAuthenticationFilter(User.ADMIN));
		get(Path.Web.FIELD_DISPLAY, EventPages.serveFieldDisplay);
		
		before(Path.Web.MATCH_PREVIEW, Filters.getAuthenticationFilter(User.ADMIN));
		get(Path.Web.MATCH_PREVIEW, EventPages.handleWaitForPreview);
		post(Path.Web.MATCH_PREVIEW, EventPages.handleShowPreview);
		
		before(Path.Web.GET_TIMER_COMMANDS, Filters.getAuthenticationFilter(User.ADMIN));
		get(Path.Web.GET_TIMER_COMMANDS, EventPages.handleGetTimerCommands);
		
		before(Path.Web.GET_DISPLAY_COMMANDS, Filters.getAuthenticationFilter(User.ADMIN));
		get(Path.Web.GET_DISPLAY_COMMANDS, EventPages.handleGetDisplayCommands);
		
		before(Path.Web.EDIT_MATCH_SCORE, Filters.getAuthenticationFilter(User.ADMIN));
		get(Path.Web.EDIT_MATCH_SCORE, EventPages.handleGetEditScorePage);
		post(Path.Web.EDIT_MATCH_SCORE, EventPages.handleCommitEditedScore);
		put(Path.Web.EDIT_MATCH_SCORE, EventPages.handleGetEditedScore);
		
		before(Path.Web.MANAGE_EVENT, Filters.getAuthenticationFilter(User.ADMIN));
		get(Path.Web.MANAGE_EVENT, EventPages.serveManagePage);
		
		before(Path.Web.ADD_TEAM, Filters.getAuthenticationFilter(User.ADMIN));
		get(Path.Web.ADD_TEAM, EventPages.serveAddTeam);
		post(Path.Web.ADD_TEAM, EventPages.handleAddTeam);
		
		before(Path.Web.UPLOAD_SCHEDULE, Filters.getAuthenticationFilter(User.ADMIN));
		get(Path.Web.UPLOAD_SCHEDULE, EventPages.serveUploadSchedulePage);
		
		before(Path.Web.EDIT_SCORE_HOME, Filters.getAuthenticationFilter(User.ADMIN));
		get(Path.Web.EDIT_SCORE_HOME, EventPages.serveEditScoreHome);
		
		before(Path.Web.CREATE_ACCOUNT_SIMPLE, Filters.getAuthenticationFilter(User.ADMIN));
		get(Path.Web.CREATE_EVENT, EventPages.serveEventCreationPage);
		post(Path.Web.CREATE_EVENT, EventPages.handleEventCreationPost);
		
		before(Path.Web.CREATE_ACCOUNT_SIMPLE, Filters.getAuthenticationFilter(User.ADMIN));
		get(Path.Web.CREATE_ACCOUNT_SIMPLE, LoginPage.serveCreateAccountPage);
		post(Path.Web.CREATE_ACCOUNT_SIMPLE, LoginPage.handleCreateAccountPost);
		
		before(Path.Web.CREATE_ACCOUNT, Filters.getAuthenticationFilter(User.ADMIN));
		get(Path.Web.CREATE_ACCOUNT, LoginPage.serveCreateAccountPage);
		post(Path.Web.CREATE_ACCOUNT, LoginPage.handleCreateAccountPost);
		
		before(Path.Web.NEW_TEAM, Filters.getAuthenticationFilter(User.ADMIN));
		post(Path.Web.NEW_TEAM, GlobalPages.handleNewTeamPost);
		put(Path.Web.EDIT_TEAM, GlobalPages.handleNewTeamPost);
		
		
		//headref?
		post(Path.Web.RANDOMIZE, EventPages.handleRandomizePost);
		post(Path.Web.RERANDOMIZE, EventPages.handleReRandomizePost);
		
		//admin?
		post(Path.Web.UPLOAD_SCHEDULE, "multipart/form-data", EventPages.handleScheduleUpload);
		post(Path.Web.START_MATCH, EventPages.handleStartMatch);
		post(Path.Web.PAUSE_MATCH, EventPages.handlePauseMatch);
		post(Path.Web.RESUME_MATCH, EventPages.handleResumeMatch);
		post(Path.Web.SUBMIT_SCORE, EventPages.handleScoreSubmit);
		post(Path.Web.COMMIT_SCORES, EventPages.handleScoreCommit);
		post(Path.Web.SCORE, EventPages.handleTeleopSubmit);
		post(Path.Web.SCORE_AUTO, EventPages.handleAutoSubmit);
		post(Path.Web.LOAD_MATCH, EventPages.handleLoadMatch);
		post(Path.Web.SHOW_PREVIEW, EventPages.handleShowPreviewCommand);
		post(Path.Web.SHOW_MATCH, EventPages.handleShowMatch);
		post(Path.Web.LOCKOUT_REFS, EventPages.handleLockoutRefs);
		put(Path.Web.SCORE, EventPages.handleScoreUpdate);
		put(Path.Web.EDIT_SCORE, EventPages.handleControlScoreEdit);
		post(Path.Web.SET_STATUS, EventPages.handleSetStatus);
		post(Path.Web.RANKINGS, EventPages.handleRecalcRankings);
		
		//ref?
		put(Path.Web.INSPECT_NOTE, EventPages.handleNote);
		put(Path.Web.INSPECT_SIG, EventPages.handleSig);
		put(Path.Web.INSPECT_STATUS, EventPages.handleFormStatus);
		
		get(Path.Web.GET_POST_RESULTS_INFO, EventPages.handlePostResultData);
		post(Path.Web.SHOW_RESULTS, EventPages.handleShowResults);
		
		get(Path.Web.ALL, DefaultPages.notFound);
		
		after("*", Filters.addGzipHeader);
		
		/* TODO might want to record a snapshot of thread and ram count every 30 se or so
		 * and keep like 20 minutes of data
		 * have a page that shows the graph, the names of all the current threads & maybe their state?
		 * need to do some timing on responses - those random ~8s times on updateScore is concerning.
		Thread t = new Thread() {
			public void run() {
				while(true) {
					try {
						Thread.sleep(60000);
					}catch(Exception e) {
						
					}
					int threads = Thread.activeCount();
					Runtime r = Runtime.getRuntime();
					long free = r.freeMemory();
					long total = r.totalMemory();
					long ram = total - free;
					double perc = 100.0 * ((double)ram) / ((double)total);
					System.out.println(threads +" threads");
					System.out.println("RAM: "+(ram / 1024 / 1024) + "/" + (total/1024/1024)+ " MB ("+perc+"%)");
					System.out.println("****************");
				}
			}
		};
		t.start();
		*/
	}
}

