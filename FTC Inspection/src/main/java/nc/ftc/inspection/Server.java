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
//		EventDAO.createEventDatabase("test11");
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
		
		before(Path.Web.CREATE_ACCOUNT, Filters.getAuthenticationFilter(User.ADMIN));
		before(Path.Web.CREATE_ACCOUNT_SIMPLE, Filters.getAuthenticationFilter(User.ADMIN));
		
		
		get(Path.Web.INDEX, DefaultPages.indexPage);
		get(Path.Web.LOGIN, LoginPage.serveLoginPage);
		get(Path.Web.IP_PAGE, DefaultPages.ipPage);
		get(Path.Web.CHANGE_PW, LoginPage.servePasswordChangePage);
		get(Path.Web.CREATE_EVENT, EventPages.serveEventCreationPage);
		get(Path.Web.CREATE_ACCOUNT_SIMPLE, LoginPage.serveCreateAccountPage);
		get(Path.Web.CREATE_ACCOUNT, LoginPage.serveCreateAccountPage);
		get(Path.Web.USER_PAGE, LoginPage.serveUserPage );
		
		get(Path.Web.EDIT_FORM, EventPages.serveFormEditPage);
		get(Path.Web.INSPECT, EventPages.serveInspectionPage);
		get(Path.Web.INSPECT_HOME, EventPages.serveInspectionHome);
		get(Path.Web.INSPECT_TEAM_HOME, EventPages.serveTeamInspectionHome);
		get(Path.Web.INSPECT_TEAM_FORM, EventPages.serveInspectionPageReadOnly);
		get(Path.Web.INSPECT_OVERRIDE, EventPages.serveInspectionOverride);
		//TODO make change password/new user page
		//TODO encrypt passwords on POST
		get(Path.Web.ERROR_403, DefaultPages.error403);
		get(Path.Web.MASTER_TEAM_LIST, GlobalPages.handleTeamListGet);
		get(Path.Web.EVENT_STATUS_PAGE, EventPages.serveStatusPage);
		get(Path.Web.EVENT_STATUS, EventPages.handleGetStatusGet);
		get(Path.Web.SCHEDULE, EventPages.serveSchedulePage);
		get(Path.Web.HEAD_REF, EventPages.serveHeadRef);
		get(Path.Web.REF, EventPages.serveRef);
		get(Path.Web.GET_RANDOM, EventPages.handleGetRandom);

		get(Path.Web.SCORE, EventPages.handleGetScore);
		get(Path.Web.MATCH_CONTROL, EventPages.serveMatchControlPage);
		get(Path.Web.SCORE_BREAKDOWN, EventPages.handleGetScoreBreakdown);
		get(Path.Web.SCHEDULE_STATUS, EventPages.handleGetScheduleStatus);
		get(Path.Web.GET_MATCH, EventPages.handleGetCurrentMatch);
		get(Path.Web.BOTH_SCORE, EventPages.handleGetFullScore);
		get(Path.Web.WAIT_FOR_REFS, EventPages.handleWaitForRefs);
		
		get(Path.Web.MATCH_RESULTS, EventPages.serveResultsPage);
		get(Path.Web.AUDIENCE_DISPLAY, EventPages.serveAudienceDisplay);
		get(Path.Web.FIELD_DISPLAY, EventPages.serveFieldDisplay);
		
		get(Path.Web.MATCH_PREVIEW, EventPages.handleWaitForPreview);
		get(Path.Web.GET_TIMER_COMMANDS, EventPages.handleGetTimerCommands);
		
		
		post(Path.Web.LOGIN, LoginPage.handleLoginPost);
		post(Path.Web.LOGOUT, LoginPage.handleLogoutPost);
		post(Path.Web.CHANGE_PW, LoginPage.handlePasswordChangePost);
		post(Path.Web.CREATE_EVENT, EventPages.handleEventCreationPost);
		post(Path.Web.CREATE_ACCOUNT, LoginPage.handleCreateAccountPost);
		post(Path.Web.CREATE_ACCOUNT_SIMPLE, LoginPage.handleCreateAccountPost);
		post(Path.Web.INSPECT_ITEM, EventPages.handleInspectionItemPost);
		post(Path.Web.NEW_TEAM, GlobalPages.handleNewTeamPost);
		
		post(Path.Web.UPLOAD_SCHEDULE, "multipart/form-data", EventPages.handleScheduleUpload);
		post(Path.Web.RANDOMIZE, EventPages.handleRandomizePost);
		post(Path.Web.RERANDOMIZE, EventPages.handleReRandomizePost);
		post(Path.Web.START_MATCH, EventPages.handleStartMatch);
		post(Path.Web.SUBMIT_SCORE, EventPages.handleScoreSubmit);
		post(Path.Web.COMMIT_SCORES, EventPages.handleScoreCommit);
		post(Path.Web.SCORE, EventPages.handleTeleopSubmit);
		post(Path.Web.SCORE_AUTO, EventPages.handleAutoSubmit);
		post(Path.Web.LOAD_MATCH, EventPages.handleLoadMatch);
		
		post(Path.Web.MATCH_PREVIEW, EventPages.handleShowPreview);
		
		put(Path.Web.EDIT_TEAM, GlobalPages.handleNewTeamPost);
		put(Path.Web.SCORE, EventPages.handleScoreUpdate);
		put(Path.Web.INSPECT_NOTE, EventPages.handleNote);
		put(Path.Web.INSPECT_SIG, EventPages.handleSig);
		put(Path.Web.INSPECT_STATUS, EventPages.handleFormStatus);
		
		
		get(Path.Web.ALL, DefaultPages.notFound);
		
		after("*", Filters.addGzipHeader);
	}
}

