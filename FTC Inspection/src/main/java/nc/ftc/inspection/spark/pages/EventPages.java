package nc.ftc.inspection.spark.pages;

import nc.ftc.inspection.Server;
import nc.ftc.inspection.Update;
import nc.ftc.inspection.dao.EventDAO;
import nc.ftc.inspection.dao.GlobalDAO;
import nc.ftc.inspection.event.ADState;
import nc.ftc.inspection.event.Display;
import nc.ftc.inspection.event.DisplayCommand;
import nc.ftc.inspection.event.Event;
import nc.ftc.inspection.event.TimerCommand;
import nc.ftc.inspection.model.Alliance;
import nc.ftc.inspection.model.EventData;
import nc.ftc.inspection.model.FormRow;
import nc.ftc.inspection.model.Match;
import nc.ftc.inspection.model.MatchResult;
import nc.ftc.inspection.model.MatchStatus;
import nc.ftc.inspection.model.Team;
import nc.ftc.inspection.model.Team.FormIndex;
import nc.ftc.inspection.spark.util.Path;
import static nc.ftc.inspection.spark.util.ViewUtil.render;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.Set;
import java.util.stream.Collectors;

import javax.servlet.MultipartConfigElement;
import javax.servlet.http.Part;

import org.apache.commons.collections.bag.SynchronizedSortedBag;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.sql.Date;

import spark.Request;
import spark.Response;
import spark.Route;

public class EventPages {
	public static Route serveEventCreationPage = (Request request, Response response) -> {	
		return render(request, Path.Template.CREATE_EVENT);
	};
	
	public static Route handleEventCreationPost = (Request request, Response response) -> {
		Map<String, Object> model = new HashMap<>();
		String code = request.queryParams("eventCode");
		String name = request.queryParams("eventName");
		String eventDate = request.queryParams("eventDate");
		Date date = null;
		try {
			date = new Date(EventDAO.EVENT_DATE_FORMAT.parse(eventDate).getTime());	
		} catch (Exception e) {
			model.put("success", 0);
			model.put("resp", "Could not create event, it appears the date is not formatted correctly");
			model.put("eventCode", code);
			model.put("eventName", name);
			model.put("eventDate", eventDate);
			return render(request, model, Path.Template.CREATE_EVENT);
		}
		boolean success = EventDAO.createEvent(code, name, date);
		
		
		if (success) {
			model.put("success", 1);
			model.put("resp", "Event successfully created");
		} else {
			model.put("success", 0);
			model.put("resp", "Could not create event, please check the information and try again");
			model.put("eventCode", code);
			model.put("eventName", name);
			model.put("eventDate", eventDate);
		}
		
		return render(request, model, Path.Template.CREATE_EVENT);
	};
	
	public static Route serveEventManagementPage = (Request request, Response response) -> {
		Map<String, Object> model = new HashMap<>();
		String eventCode = request.params("event");
		//TODO handle an event that is not here
		Event event = Server.activeEvents.get(eventCode);
		if (event == null) {
			model.put("eventName", "Unknown Event");
		} else {
			model.put("eventName", event.getData().getName());
		}
//		model.pu
		return render(request, model, Path.Template.MANAGE_EVENT);
	};
	
	public static Route serveFormEditPage = (Request request, Response response) ->{
		
		Map<String, Object> model = new HashMap<>();
		String eventCode = request.params("event");
		String formID = request.queryParams("form");
		List<FormRow> form = EventDAO.getForm(eventCode, formID);
		int max = 0;
		for(FormRow fr : form){
			max = Math.max(max, fr.getItems().length);
		}
		model.put("max", max);
		model.put("form", form);
		model.put("headerColor", "#E6B222");
		return render(request, model, Path.Template.EDIT_FORM);
	};
	
	
	private static String renderTeamSelect(Request request, Response response, String eventCode, String formID) {
		Map<String, Object> map = new HashMap<>();
		map.put("form", formID.toLowerCase());
		map.put("eventCode", eventCode);
		Event e = Server.activeEvents.get(eventCode);
		String eventName = "Unknown Event";
		if (e != null) {
			eventName = e.getData().getName();
		} else {
			map.put("teams", EventDAO.getStatus(eventCode, formID));
		}
		map.put("eventName", eventName);
		
		if(formID.equals("SC")||formID.equals("CI")) {
			//render the boolean page (which is equivalent to the LRI page, without comments)
			return render(request, map, Path.Template.BINARY_INSPECTION_PAGE);
		}
		
		return render(request, map, Path.Template.INSPECTION_TEAM_SELECT);
	}
	
	private static String inspectionPage(Request request, Response response, boolean readOnly) {
		Map<String, Object> model = new HashMap<>();
		String eventCode = request.params("event");
		String formID = request.params("form").toUpperCase();
		String team = request.queryParams("team");
		String teams = request.queryParams("teams");
		//if both provided, use the "teams" param.
		if(teams == null && team != null){
			teams = team;
		}
		//check for team read-only page
		if(teams == null) {
			teams = request.params("team");
		}
		if(teams == null){
			//render the team select page for the specified form
			return renderTeamSelect(request, response, eventCode, formID);
		}
		String[] s = teams.split(",");
		int[] teamList = new int[s.length];
		for(int i = 0; i < s.length; i++){
			teamList[i] = Integer.parseInt(s[i]);
		}
		List<FormRow> form = EventDAO.getForm(eventCode, formID, teamList);
		int max = 0;
		for(FormRow fr : form){
			max = Math.max(max, fr.getItems().length);
		}
		String[] notes = EventDAO.getFormComments(eventCode, formID, teamList);
		String[] sigs = EventDAO.getSigs(eventCode, formID, teamList);
		
		System.out.println(Arrays.toString(notes));
		model.put("readOnly", readOnly);
		model.put("max", max);
		model.put("form", form);
		model.put("formID", formID);
		model.put("teams", teamList);
		model.put("notes", notes);
		model.put("sigs", sigs);
		model.put("headerColor", "#F57E25");
		return render(request, model, Path.Template.INSPECT);
	}
	
	public static Route serveInspectionPage = (Request request, Response response) ->{
		return inspectionPage(request, response, false);
	};
	public static Route serveInspectionPageReadOnly = (Request request, Response response) ->{
		return inspectionPage(request, response, true);
	};
	
	public static Route handleInspectionItemPost = (Request request, Response response) ->{
		String event = request.params("event");
		String form = request.queryParams("form");
		int team =  Integer.parseInt(request.queryParams("team"));
		int itemIndex = Integer.parseInt(request.queryParams("index"));
		boolean status = Boolean.parseBoolean(request.queryParams("state"));
		response.status(EventDAO.setFormStatus(event, form,team, itemIndex, status) ? 200 : 500);
		EventDAO.setTeamStatus(event, form, team, 1);//IN PROGRESS TODO CONSTANT
		return request.queryParams("state");
		//Client perspective:
		//If 200 & state matches, we good. If 200 & state wrong, timing issue, do nothing
		//if 500, failed.
	};
	
	public static Route handleGetStatusGet = (Request request, Response response) -> {
		String event = request.params("event");
		//TODO get which columns are enabled.
		String[] columns = new String[]{"ci", "sc", "hw", "sw", "fd"};
		return EventDAO.getStatus(event, columns).stream().map(Team::toStatusString).collect(Collectors.toList());
	};
	
	public static Route serveStatusPage = (Request request, Response response) -> {
		Map<String, Object> model = new HashMap<String, Object>();
		String event = request.params("event");
		String projString = request.queryParams("proj");
		boolean proj = (projString == null) ? false : Boolean.parseBoolean(projString);
		String colsString = request.queryParams("cols");
		int cols = (colsString == null) ? 1 : Integer.parseInt(colsString);
		model.put("numCols", cols - 1);//velocity does things weird
		List<Team> teams = EventDAO.getStatus(event);
		int numTeamsPerCol = (int) Math.floor(teams.size() / (double) cols);
		int numExtra = teams.size() - numTeamsPerCol * cols;
		List<List<Team>> teamsPerCol = new ArrayList<List<Team>>();
		List<Team> cur = new ArrayList<Team>();
		int curCol = 0;
		int column = 0;
		for (int count = 0; count < teams.size(); count++) {
			cur.add(teams.get(count));
			curCol++;
			if (curCol == numTeamsPerCol + ((column < numExtra) ? 1 : 0)) {
				curCol = 0;
				column++;
				teamsPerCol.add(cur);
				cur = new ArrayList<Team>();
			}
		}
		if (cur.size() != 0) {
			teamsPerCol.add(cur);
		}
		model.put("teamsPerCol", teamsPerCol);
		model.put("event", event);//TODO get the event name from db
		String[] columns = new String[]{"hw", "sw", "fd", "sc", "ci"};
		model.put("headers", columns);

		model.put("teams", teams);
		if (proj) {
			return render(request, model, Path.Template.STATUS_PAGE_PROJECTOR);
		} else {
			return render(request, model, Path.Template.STATUS_PAGE);
		}
	};
	
	public static Route servePitPage = (Request request, Response response) -> {
		Map<String, Object> model = new HashMap<String, Object>();
		String event = request.params("event");
		String projString = request.queryParams("proj");
		boolean proj = (projString == null) ? false : Boolean.parseBoolean(projString);
		model.put("event", event);//TODO get the event name from db
		Event e = Server.activeEvents.get(event);
		if(e == null){
			response.status(500);
			return "Event not active.";
		}
	//	e.calculateRankings(); TODO make another endpoint to force recalc
		model.put("rankings", e.getRankings());
		model.put("event", e.getData().getName());
		List<MatchResult> results = EventDAO.getMatchResults(event);
		model.put("matches", results);
		if (proj) {
			return render(request, model, Path.Template.PIT_DISPLAY_PROJECTOR);
		} else {
			return render(request, model, Path.Template.PIT_DISPLAY);
		}
	};
	
	public static Route serveSchedulePage = (Request request, Response response) ->{
		String event = request.params("event");
		Map<String, Object> model = new HashMap<String, Object>();
		model.put("event", event);//TODO get the event name from db
		model.put("matches", EventDAO.getSchedule(event));
		return render(request, model, Path.Template.SCHEDULE_PAGE);
	};
	
	
	public static Route handleScheduleUpload = (Request request, Response response) ->{
			

		String location = "public";          // the directory location where files will be stored
		long maxFileSize = 100000000;       // the maximum size allowed for uploaded files
		long maxRequestSize = 100000000;    // the maximum size allowed for multipart/form-data requests
		int fileSizeThreshold = 1024;       // the size threshold after which files will be written to disk
		
		MultipartConfigElement multipartConfigElement = new MultipartConfigElement(
		     location, maxFileSize, maxRequestSize, fileSizeThreshold);
		 request.raw().setAttribute("org.eclipse.jetty.multipartConfig",
		     multipartConfigElement);
			
		 	String event = request.params("event");
		 	EventData data = EventDAO.getEvent(event);
		 	if(data.getStatus() != 3) {
		 		response.status(409);
		 		return "Not in quals phase!";
		 	}
			Part p = request.raw().getPart("file");
			List<Match> matches = new ArrayList<>();
			Set<Integer> teams = new HashSet<Integer>();
			Scanner scan = new Scanner(p.getInputStream());
			scan.useDelimiter("\\|");
			try{
			while(scan.hasNextLine()){
				scan.nextInt();
				scan.nextInt();
				int match = scan.nextInt();
				scan.nextInt();
				scan.next();
				int red1 = scan.nextInt();
				int red2 = scan.nextInt();
				scan.nextInt();
				int blue1 = scan.nextInt();
				int blue2 = scan.nextInt();
				scan.nextInt();
				scan.nextInt();
				scan.nextInt();
				scan.nextInt();
				scan.nextBoolean();
				scan.nextBoolean();
				scan.nextBoolean();
				scan.nextInt();
				scan.nextInt();
				scan.nextInt();
				scan.nextBoolean();
				scan.nextBoolean();
				scan.nextBoolean();
				int r1S = scan.nextInt();
				int r2S = scan.nextInt();
				scan.nextInt();
				int b1S = scan.nextInt();
				int b2S = scan.nextInt();
				scan.nextLine();
				Alliance red = new Alliance(red1, r1S == 1, red2, r2S == 1);
				Alliance blue = new Alliance(blue1, b1S == 1, blue2, b2S == 1);
				matches.add(new Match(match, red, blue));
				teams.add(red1);
				teams.add(red2);
				teams.add(blue1);
				teams.add(blue2);
			//	System.out.println(match+":"+red1+(r1S == 1 ? "*":"")+","+red2+(r2S == 1 ? "*":"")+","+blue1+(b1S == 1 ? "*":"")+","+blue2+(b2S == 1 ? "*":""));
			}
			}catch(Exception e){
				e.printStackTrace();
			}
			//TODO add teams in Set to event if not in it, and display that occurance
			scan.close();
			EventDAO.createSchedule(event, matches);
			response.status(200);
			return "OK";
		};
		
		public static Route handleRandomizePost = (Request request, Response response) ->{
			String event = request.params("event");
			Event e = Server.activeEvents.get(event);
			if(e.getCurrentMatch().isRandomized()){
				response.status(500);
				return "Match already randomized!";
			}
			int r = e.getCurrentMatch().randomize();
			synchronized(e.waitForRandomLock) {
				e.waitForRandomLock.notifyAll();
			}
			e.getDisplay().issueCommand(DisplayCommand.SHOW_RANDOM);
			e.getCurrentMatch().setStatus(MatchStatus.AUTO);
			return "{\"rand\":\"" + r +"\"}";
		};
		
		public static Route handleReRandomizePost = (Request request, Response response) ->{
			String event = request.params("event");
			Event e = Server.activeEvents.get(event);
			if(e == null){
				return null;
			}
			if(e.getCurrentMatch() == null){
				return null;
			}
			if(!e.getCurrentMatch().isRandomized()){
				response.status(500);
				return "Match not randomized!";
			}
			return "{\"rand\":\"" + e.getCurrentMatch().randomize() +"\"}";
		};
		
		public static Route serveHeadRef = (Request request, Response response) -> {
			return render(request, new HashMap<String, Object>(), Path.Template.HEAD_REF);
		};
		
		public static Route serveRef = (Request request, Response response) ->{
			
			Map<String, Object> model = new HashMap<>();
			String template = "";
			String alliance = request.params("alliance");
			Event e = Server.activeEvents.get(request.params("event"));
			if(e == null) {
				return "Event not active";
			}
			Match match = e.getCurrentMatch();
			if(match == null) {
				return "No active match";
			}
			model.put("alliance", alliance);
			Alliance a = match.getAlliance(alliance);
			if (match.getStatus() != MatchStatus.PRE_RANDOM) {
				model.put("rand", match.getRandomization());
			}
			if(match.getStatus().canAcceptScores()) {				
				for(String key : a.getScoreFields()) {
					model.put(key, a.getScore(key));
				}
			}
			switch(match.getStatus()){
			case PRE_RANDOM:
				template = Path.Template.REF_PRE_RANDOM;
				break;
			case AUTO:
				template = Path.Template.REF_AUTO;
//				model.put(arg0, arg1)
				break;
			case AUTO_REVIEW:
				//THIS PHASE NO LONGER EXISTS
				//if already submitted, load teleop. (Only matters for first ref to submit)
				//Alliance a = e.getCurrentMatch().getAlliance(alliance);
				//TODO use alliance.isInReview() to serve a waiting page that waits until both refs enter review phase.
				a.setInReview(true);//in case refreshed.
				template = a.autoSubmitted() ? Path.Template.REF_TELEOP : Path.Template.REF_AUTO_REVIEW;
				break;
			case TELEOP:
				template = a.autoSubmitted() ? Path.Template.REF_TELEOP : Path.Template.REF_AUTO;
				break;
			case REVIEW:
				if(!a.autoSubmitted()) {//better hurry up and do that auto
					template = Path.Template.REF_AUTO;
				} else if(a.isInReview()) {
					template = a.scoreSubmitted() ? Path.Template.REF_POST_SUBMIT : Path.Template.REF_REVIEW;
				} else {
					template = Path.Template.REF_TELEOP;
				}
				//template = a.scoreSubmitted() ? Path.Template.REF_POST_SUBMIT : Path.Template.REF_REVIEW;
				//a.setInReview(true);//in case refreshed.
				break;
			case PRE_COMMIT:
				template = Path.Template.REF_POST_SUBMIT;
				break;
			case POST_COMMIT:
				template = Path.Template.REF_PRE_RANDOM;
				break;			
			default:
				break;
			
			}
			return render(request, model, template);
			//return "";
		};
		
		public static Route handleGetRandom = (Request request, Response response) ->{
			//TODO this is the call that will long-poll / websocket to simulate a push to 
			//clients when randomization complete.
			String eventCode = request.params("event");
			Event event = Server.activeEvents.get(eventCode);
			if(event == null){
				response.status(500);
				return "";
			}
			if(event.getCurrentMatch() == null){
				response.status(500);
				return "";
			}
			if(event.getCurrentMatch().isRandomized()){
				return "{\"rand\":\"" + event.getCurrentMatch().getRandomization() +"\"}";
			}
			//Not yet randomized. Wait until it is.
			//TODO some form of timeout? half an hour? - just put in .wait(ms) call
			synchronized(event.waitForRandomLock){
				event.waitForRandomLock.wait();
			}
			return "{\"rand\":\"" + event.getCurrentMatch().getRandomization() +"\"}";
		};
		
		
		
		public static Route handleGetScore = (Request request, Response response) -> {
			String eventCode = request.params("event");
			String alliance = request.params("alliance");
			Event event = Server.activeEvents.get(eventCode);
			try{
				if(event == null){
					response.status(500);
					return "Event not active.";
				}
				Match match = event.getCurrentMatch();
				if(match == null){
					response.status(500);
					return "No match loaded.";
				}
				Alliance a = match.getAlliance(alliance);
				if(a == null){
					return "Invalid Alliance";
				}
				return "{"+String.join(",", a.getScores())+"}";
			}catch(Exception e){
				e.printStackTrace();
			}
			return "";
		};
		
		
		public static Route handleGetFullScore = (Request request, Response response) ->{
			String eventCode = request.params("event");
			Event event = Server.activeEvents.get(eventCode);
			try{
				if(event == null){
					response.status(500);
					return "Event not active.";
				}
				Match match = event.getCurrentMatch();
				if(match == null){
					response.status(500);
					return "No match loaded.";
				}
				
				String block = request.queryParams("block");
				if(block != null && Boolean.parseBoolean(block)) {
					long last = 0;
					String lastParam = request.queryParams("last");
					if(lastParam != null) {
						Match m =event.getCurrentMatch();
						last = Long.parseLong(lastParam);
						if(m.getLastUpdate() <= last) {
							synchronized(m.getUpdateLock()) {
								m.getUpdateLock().wait(10000);
							}
						}
					}
				}
				
				return event.getCurrentMatch().getFullScores();
			}catch(Exception e){
				e.printStackTrace();
			}
			return "";			
		};
		
		public static Route handleGetScoreBreakdown = (Request request, Response response) ->{
			String event = request.params("event");
			Event e = Server.activeEvents.get(event);
			if(e == null){
				response.status(500);
				return "Event not active.";
			}
			if(e.getCurrentMatch() == null){
				response.status(500);
				return "No match loaded";
			}
			String block = request.queryParams("block");
			if(block != null && Boolean.parseBoolean(block)) {
				long last = 0;
				String lastParam = request.queryParams("last");
				if(lastParam != null) {
					last = Long.parseLong(lastParam);
					Match m = e.getCurrentMatch();
					if(m.getLastUpdate() <= last) {
						synchronized(m.getUpdateLock()) {
							m.getUpdateLock().wait(10000);
						}
					}
				}
			}
			return e.getCurrentMatch().getScoreBreakdown();
		};
		
		
		
		private static String updateScores(Request request, Response response){
			String eventCode = request.params("event");
			String alliance = request.params("alliance");
			
			Event event = Server.activeEvents.get(eventCode);
			if(event == null){
				response.status(500);
				return "Event not active.";
			}
			Match match = event.getCurrentMatch();
			if(match == null){
				response.status(500);
				return "No match loaded.";
			}
			if(match.getStatus() == null){
				response.status(500);
				return "Null Status";
			}
			if(!match.getStatus().canAcceptScores()){
				response.status(500);
				if(match.refLockout)return "LOCKOUT";
				return "Match not ready for scores.";
			}
			Set<String> params = request.queryParams();
			for(String key : params){
				if(alliance.equals("red")){
					match.getRed().updateScore(key, request.queryParams(key));
				} else if(alliance.equals("blue")){
					match.getBlue().updateScore(key, request.queryParams(key));
				}
			}
			response.status(200);
			return "OK";
		}
		
		public static Route handleScoreUpdate = (Request request, Response response) -> {
			String s = updateScores(request, response);
			if(s.equals("OK")) {
				String e = request.params("event");
				MatchStatus status = Server.activeEvents.get(e).getCurrentMatch().getStatus();
			//	if(status == MatchStatus.AUTO || status == MatchStatus.AUTO_REVIEW) {
				//jewels are now recalculated through the entire match. The Review submit does NOT calculate.
					Server.activeEvents.get(e).getCurrentMatch().updateJewels();
			//	}
				Server.activeEvents.get(e).getCurrentMatch().getAlliance(request.params("alliance")).calculateGlyphs();
				Server.activeEvents.get(request.params("event")).getCurrentMatch().updateNotify();
			}
			return s;
		};
		
		//POST that is done by hitting submit auto and submit teleop ("review") button. always saves the info, but returns
		//error if not ready to change phase.
		//could change this to long poll in the future.
		//this is submitting teleop, and enter review phase
		public static Route handleTeleopSubmit = (Request request, Response response) -> {
			String res = updateScores(request, response);
			String e = request.params("event");
			if(res.equals("OK")) {
				//If not in review phase, dont return 200. That way client knows not to load
				//review page yet.
				
				
				MatchStatus status = Server.activeEvents.get(e).getCurrentMatch().getStatus();
				//this if should always be false
				if(status == MatchStatus.AUTO || status == MatchStatus.AUTO_REVIEW) {
					Server.activeEvents.get(e).getCurrentMatch().updateJewels();
				}
				System.out.println(status);
				if(!status.isReview()) {
					Server.activeEvents.get(request.params("event")).getCurrentMatch().updateNotify();
					response.status(409);
					if(Server.activeEvents.get(e).getCurrentMatch().refLockout)return "LOCKOUT";
					return "Not ready to review.";
				}	
				if(!Server.activeEvents.get(e).getCurrentMatch().autoSubmitted()) {
					//Both need to submit auto before 
					response.status(410); //TODO choose a better status code
					return "Cannot Review until both auto submitted";
				}
				Match match = Server.activeEvents.get(e).getCurrentMatch();
				match.getAlliance(request.params("alliance")).setInReview(true);
				if(match.isInReview()) {
					Server.activeEvents.get(e).getDisplay().issueCommand(DisplayCommand.STOP_SCORE_UPDATES);
				}
				Server.activeEvents.get(request.params("event")).getCurrentMatch().getAlliance(request.params("alliance")).calculateGlyphs();
//				if(status == MatchStatus.AUTO) {
//					match.calculateEndAuto();
//				}
				Server.activeEvents.get(request.params("event")).getCurrentMatch().updateNotify();
			} 
			return res;
			
		};
		
		
		//this is submitting auto
		public static Route handleAutoSubmit = (Request request, Response response) ->{
			try {
				String res = updateScores(request, response);
				String eventCode = request.params("event");
				String alliance = request.params("alliance");
				Event event = Server.activeEvents.get(eventCode);
				Match match = event.getCurrentMatch();
				if(match.getStatus() == null){
					response.status(500);
					return "Null Status";
				}
				MatchStatus status = match.getStatus();
				if(status == MatchStatus.TELEOP || status == MatchStatus.REVIEW) {
					if(match.getAlliance(alliance).autoSubmitted()) {
						//UHHH They submitted twice
						System.err.println("WE MIGHT HAVE A PROBLEM! "+alliance+" submitted auto twice!");
					}
					match.getAlliance(alliance).setAutoSubmitted(true);
				} else {
					response.status(409);
					if(match.refLockout)return "LOCKOUT";
					return "Invalid match status: "+status;
				}				
				return res;
			} catch(Exception e) {
				e.printStackTrace();
			}
			response.status(500);
			return "Error";
		};
		
		//This is for the review page
		public static Route handleScoreSubmit = (Request request, Response response) ->{
			//if not in REVIEW status, reject.
			try{
			String res = updateScores(request, response);
			String eventCode = request.params("event");
			String alliance = request.params("alliance");
			Event event = Server.activeEvents.get(eventCode);
			if(event.getCurrentMatch().getStatus() == null){
				response.status(500);
				return "Null Status";
			}
			if(!event.getCurrentMatch().getStatus().isReview()){
				/*
				//not review, but check if teleop and that alliance hasnt submitted yet
				boolean ok = false;
				if(event.getCurrentMatch().getStatus() == MatchStatus.TELEOP) {
					if(!event.getCurrentMatch().getAlliance(alliance).autoSubmitted()) {
						//allow this
						//URGENT TODO MAKE SURE THIS FLAG GETS CLEARED AT START OF MATCH!
						event.getCurrentMatch().getAlliance(alliance).setAutoSubmitted(true);
						ok = true;
					}
				}
				if(!ok) {*/
				
					response.status(500);
					if(event.getCurrentMatch().refLockout)return "LOCKOUT";
					return "Not in review phase!";
				//}
			}
			if(res.equals("OK") ){ 
				//if not both refs in review - dont allow
//				if(!event.getCurrentMatch().isInReview()) {
//					response.status(409);
//					return "Red & Blue must both be in review";
//				}
				event.getCurrentMatch().getAlliance(alliance).setSubmitted(true);
//				TODO commented this out recently, if stuff breaks maybe this
				//TODO THIS RIHT HERE!!! decide whether submitted or inReview to show review page?
				//event.getCurrentMatch().getAlliance(alliance).setInReview(false);
				
				//both alliances scores submitted -> go to teleop or pre-commit
				//Front end needs to say submitted until post-commit (after teleop).
				if(event.getCurrentMatch().scoreSubmitted()){
					event.getCurrentMatch().getRed().setInReview(false);
					event.getCurrentMatch().getBlue().setInReview(false);
					/*
					if(event.getCurrentMatch().getStatus() == MatchStatus.AUTO_REVIEW){

						System.out.println("TELEOP TIME!");
						event.getCurrentMatch().setStatus(MatchStatus.TELEOP);
						event.getCurrentMatch().clearSubmitted();
					} else if(event.getCurrentMatch().getStatus() == MatchStatus.REVIEW){
					*/
					
						System.out.println("AUTO TIME!");
						//notify score listeners before ref-done listeners.
						Server.activeEvents.get(request.params("event")).getCurrentMatch().updateNotify();
						event.getCurrentMatch().setStatus(MatchStatus.PRE_COMMIT);
						synchronized (event.waitForRefLock) {
							event.waitForRefLock.notifyAll();
						}
						return "OK";
					//}
				}
				Server.activeEvents.get(request.params("event")).getCurrentMatch().updateNotify();
			} else{
				response.status(500);
				if(event.getCurrentMatch().refLockout)return "LOCKOUT";
				return res;
			}
			return "OK";
			}catch(Exception e){
				e.printStackTrace();
				return null;
			}
		};
		
		public static Route handleStartMatch = (Request request, Response response) ->{
			String eventCode = request.params("event");
			Event event = Server.activeEvents.get(eventCode);
			if(event == null){
				response.status(500);
				return "Event not active.";
			}
			Match match = event.getCurrentMatch();
			if(match == null){
				response.status(500);
				return "No match loaded.";
			}
			if(match.getStatus() == null){
				response.status(500);
				return "Null Status";
			}
			if(match.getStatus() != MatchStatus.AUTO) {
				response.status(409);
				return "Match not ready for auto!";
			}
			event.getTimer().start();
			return "OK";
		};
		
		public static Route handlePauseMatch = (Request request, Response response) ->{
			String eventCode = request.params("event");
			Event event = Server.activeEvents.get(eventCode);
			if(event == null){
				response.status(500);
				return "Event not active.";
			}
			Match match = event.getCurrentMatch();
			if(match == null){
				response.status(500);
				return "No match loaded.";
			}
			if(match.getStatus() == null){
				response.status(500);
				return "Null Status";
			}
			if(event.getTimer().paused()) {
				response.status(200);
				return "Match already paused";
			}
			if(match.getStatus() == MatchStatus.AUTO || match.getStatus() == MatchStatus.TELEOP) {
				event.getTimer().pause();
				return "OK";
			}
			response.status(409);
			return "Match not running!";
		};
		public static Route handleResumeMatch = (Request request, Response response) ->{
			String eventCode = request.params("event");
			Event event = Server.activeEvents.get(eventCode);
			if(event == null){
				response.status(500);
				return "Event not active.";
			}
			Match match = event.getCurrentMatch();
			if(match == null){
				response.status(500);
				return "No match loaded.";
			}
			if(match.getStatus() == null){
				response.status(500);
				return "Null Status";
			}
			if(!event.getTimer().paused()) {
				response.status(409);
				return "Match not paused";
			}
			if(match.getStatus() == MatchStatus.AUTO || match.getStatus() == MatchStatus.TELEOP) {
				event.getTimer().resume();
				return "OK";
			}
			response.status(409);
			return "Match not running!";
		};
		public static Route handleResetMatch = (Request request, Response response) ->{
			//TODO make sure waitForEnd handles this properly (return error)
			//Same with waitForRefs
			return null;
		};
		
		public static Route handleLockoutRefs = (Request request, Response response) ->{
			Event e = Server.activeEvents.get(request.params("event"));
			if(e == null) {
				response.status(400);
				return "Event not active";
			}
			Match match = e.getCurrentMatch();
			if(match == null) {
				response.status(400);
				return "No active match";
			}
			if(e.getCurrentMatch().getStatus() != MatchStatus.REVIEW) {
				response.status(409);
				return "Not in review.";
			}
			e.getCurrentMatch().setStatus(MatchStatus.PRE_COMMIT);
			e.getCurrentMatch().refLockout = true;
			synchronized(e.waitForRefLock) {
				e.waitForRefLock.notifyAll();
			}
			return "OK";
		};
		

		public static Route handleControlScoreEdit = (Request request, Response response) ->{
			//Updates the score, then sends back the breakdown. DOES NOT NOTIFY LISTENERS!
			Event e = Server.activeEvents.get(request.params("event"));
			if(e == null) {
				response.status(400);
				return "Event not active";
			}
			Match match = e.getCurrentMatch();
			if(match == null) {
				response.status(400);
				return "No active match";
			}
			String alliance = request.params("alliance");
			if(e.getCurrentMatch().getStatus() == MatchStatus.PRE_COMMIT) {
				//TODO extra security here!
				
					Set<String> params = request.queryParams();
					for(String key : params){
						if(alliance.equals("red")){
							match.getRed().updateScore(key, request.queryParams(key));
						} else if(alliance.equals("blue")){
							match.getBlue().updateScore(key, request.queryParams(key));
						}
					}
					return e.getCurrentMatch().getScoreBreakdown();
				//}
			}
			response.status(409);
			return "Not in review phase!";
		};
		
		
		
		public static Route handleScoreCommit = (Request request, Response response) ->{
			//TODO add score data to the commit body!!!!
			String event = request.params("event");
			Event e = Server.activeEvents.get(event);
			
			if(e == null){
				response.status(500);
				return "Event not active.";
			}
			if(e.getCurrentMatch() == null){
				response.status(500);
				return "No match loaded";
			}
			if(e.getCurrentMatch().getStatus() == null){
				response.status(500);
				return "Null status";
			}
			if(e.getCurrentMatch().getStatus() != MatchStatus.PRE_COMMIT){
				response.status(500);
				return "Not ready to commit scores.";
			}
			
			Set<String> params = request.queryParams();
			/*Format:
			 * <alliance>_score_<scoreKey> : call updateScore
			 * <alliance>_card_<index>
			 * <alliance>_dq_<index>
			 * 
			 */
			
			Match match = e.getCurrentMatch();
			for(String key : params){
				String[] data = key.split("_");
				Alliance alliance  = match.getAlliance(data[0]);
				
				switch(data[1]) {
				//NOTE:MIRROR ANY CHANGES IN EDIT COMMIT
					case "score":
						alliance.updateScore(data[2], request.queryParams(key));				
						break;
					case "card":
						//alliance.setCard(Integer.parseInt(data[2]), Integer.parseInt(request.queryParams(key)));
						alliance.updateScore("card"+ data[2], request.queryParams(key));
						break;
					case "dq":
						//alliance.setDQ(Integer.parseInt(data[2]), Boolean .parseBoolean(request.queryParams(key)));
						alliance.updateScore("dq"+ data[2], request.queryParams(key));
						break;
				}
			}
			if(EventDAO.commitScores(event, e.getCurrentMatch())){
				
				//TODO save old rankings to get change
				//TODO save MatchResult Object to e.display
				Alliance red = match.getRed();
				Alliance blue = match.getBlue();
				Display d = e.getDisplay();
				MatchResult mr = new MatchResult(match.getNumber(), red, blue,red.getLastScore(), blue.getLastScore(), 1, red.getPenaltyPoints(), blue.getPenaltyPoints()  );

				d.lastResult = mr;
				int red1 = e.getRank(red.getTeam1());
				int red2 = e.getRank(red.getTeam2());
				int blue1 = e.getRank(blue.getTeam1());
				int blue2  = e.getRank(blue.getTeam2());;
				
				e.calculateRankings();
				//if unranked, show as improvement.
				d.red1Dif = red1 == -1 ? 1 : red1 - e.getRank(red.getTeam1());
				d.red2Dif = red2 == -1 ? 1 : red2 - e.getRank(red.getTeam2());
				d.blue1Dif = blue1 == -1 ? 1: blue1 - e.getRank(blue.getTeam1());
				d.blue2Dif = blue2 == -1 ? 1:blue2 -e.getRank(blue.getTeam2());;
				
				
				e.loadNextMatch();
			}
			return "OK";
		};
		public static Route serveMatchControlPage = (Request request, Response response) ->{
			Map<String, Object> map = new HashMap<String, Object>();
			
			return render(request, map, Path.Template.CONTROL);
		};
		
		
		
		public static Route handleGetScheduleStatus = (Request request, Response response) ->{
			String event = request.params("event");
			Event e = Server.activeEvents.get(event);
			if(e == null){
				response.status(500);
				return "Event not active.";
			}
			/*if(e.getCurrentMatch() == null){
				response.status(500);
				return "No match loaded";
			}*/
			return EventDAO.getScheduleStatusJSON(event);
		};
		
		public static Route handleLoadMatch = (Request request, Response response) ->{
			String event  = request.params("event");
			Event e = Server.activeEvents.get(event);
			if(e == null){
				response.status(500);
				return "Event not active.";
			}
			int match = 0;
			try {
				match = Integer.parseInt(request.params("match"));
			} catch(Exception e1) {
				return "Invalid match";
			}
			e.loadMatch(match);
			e.getCurrentMatch().updateNotify();
			return "";
		};
		
		public static Route handleGetCurrentMatch = (Request request, Response response) ->{
			String event = request.params("event");
			Event e = Server.activeEvents.get(event);
			if(e == null){
				response.status(500);
				return "Event not active.";
			}
			if(e.getCurrentMatch() == null){
				response.status(200);
				return "{}";
			}
			Match m = e.getCurrentMatch();
			Alliance red = m.getRed();
			Alliance blue = m.getBlue();
			//TODO fix this an dmake it not suck!
			String res = "{";
			res += "\"number\":" + m.getNumber()+",";
			res += "\"red1\":"+red.getTeam1()+",";
			res += "\"red2\":"+red.getTeam2()+",";
			res += "\"blue1\":"+blue.getTeam1()+",";
			res += "\"blue2\":"+blue.getTeam2() +",";
			res += "\"red1Name\":\""+GlobalDAO.getTeamName(red.getTeam1())+"\",";
			res += "\"red2Name\":\""+GlobalDAO.getTeamName(red.getTeam2())+"\",";
			res += "\"blue1Name\":\""+GlobalDAO.getTeamName(blue.getTeam1())+"\",";
			res += "\"blue2Name\":\""+GlobalDAO.getTeamName(blue.getTeam2())+"\",";
			res += "\"red1Rank\":"+e.getRank(red.getTeam1())+",";
			res += "\"red2Rank\":"+e.getRank(red.getTeam2())+",";
			res += "\"blue1Rank\":"+e.getRank(blue.getTeam1())+",";
			res += "\"blue2Rank\":"+e.getRank(blue.getTeam2()) +",";
			
			//for each team, if they had a card from a previous match & they got a YELLOW card, mark as 3 to display both yellow and red.
			Map<Integer, List<Integer>> cardMap = EventDAO.getCardsForTeams(event, red.getTeam1(), red.getTeam2(), blue.getTeam1(), blue.getTeam2());
			List<Integer> cardList = cardMap.get(red.getTeam1());			
			Integer t = cardList.size() > 0 ? cardList.get(0) : null;
			res += "\"red1Card\":"+(t!=null && t.intValue()<m.getNumber())+",";
			
			cardList = cardMap.get(red.getTeam2()); 
			t = cardList.size() > 0 ? cardList.get(0) : null;
			res += "\"red2Card\":"+(t!=null && t.intValue()<m.getNumber())+",";
			
			cardList = cardMap.get(blue.getTeam1());
			t = cardList.size() > 0 ? cardList.get(0) : null;
			res += "\"blue1Card\":"+(t!=null && t.intValue()<m.getNumber())+",";
			
			cardList = cardMap.get(blue.getTeam2());
			t = cardList.size() > 0 ? cardList.get(0) : null;
			res += "\"blue2Card\":"+(t!=null && t.intValue()<m.getNumber());
			
			
			res += "}";
			return res;
		};
		
		public static Route handleWaitForRefs = (Request request, Response response) ->{
			String event = request.params("event");
			Event e = Server.activeEvents.get(event);
			if(e == null){
				response.status(500);
				return "Event not active.";
			}
			if(e.getCurrentMatch() == null){
				response.status(200);
				return "{}";
			}
			
			Match m = e.getCurrentMatch();
			if(m.getStatus() == MatchStatus.PRE_COMMIT) {
				return "OK";
			} else {
				synchronized(e.waitForRefLock) {
					e.waitForRefLock.wait();
				}
			}
			//Return scores so control page can be guarenteed most recent values.			
			return e.getCurrentMatch().getFullScores();
		};
		
		public static Route handleWaitForEnd = (Request request, Response response) ->{
			String event = request.params("event");
			Event e = Server.activeEvents.get(event);
			if(e == null){
				response.status(500);
				return "Event not active.";
			}
			if(e.getCurrentMatch() == null){
				response.status(200);
				return "{}";
			}
			
			
			Match m = e.getCurrentMatch();
			if(m.getStatus() == MatchStatus.REVIEW) {
				return "OK";
			} else {
				synchronized(e.getTimer().waitForEndLock) {
					e.getTimer().waitForEndLock.wait();
				}
				//TODO if reset, return error (Match status == AUTO? or pre-randomize?)
			}
			return "OK";
		};
		
		public static Route serveResultsPage = (Request request, Response response) ->{
			String event = request.params("event");
			Event e = Server.activeEvents.get(event);
			if(e == null){
				response.status(500);
				return "Event not active.";
			}
			List<MatchResult> results = EventDAO.getMatchResults(event);
			Map<String, Object> map = new HashMap<String, Object>();
			map.put("matches", results);
			map.put("event", event); //TODO get event name from DB
			return render(request, map, Path.Template.MATCH_RESULT);
		};
		
		public static Route serveResultsSimplePage = (Request request, Response response) ->{
			String event = request.params("event");
			Event e = Server.activeEvents.get(event);
			if(e == null){
				response.status(500);
				return "Event not active.";
			}
			List<MatchResult> results = EventDAO.getMatchResults(event);
			Map<String, Object> map = new HashMap<String, Object>();
			map.put("matches", results);
			map.put("event", event); //TODO get event name from DB
			return render(request, map, Path.Template.MATCH_RESULT_SIMPLE);
		};
		
		public static Route serveAudienceDisplay = (Request request, Response response) ->{
			return render(request, new HashMap<String, Object>(), Path.Template.AUDIENCE_DISPLAY);
		};
		
		public static Route handleWaitForPreview = (Request request, Response response) ->{
			String event = request.params("event");
			Event e = Server.activeEvents.get(event);
			if(e == null){
				response.status(500);
				return "Event not active.";
			}
			synchronized(e.waitForPreviewLock) {
				e.waitForPreviewLock.wait();
			}
			return "OK";
		};
		public static Route handleShowPreview = (Request request, Response response) ->{
			//TODO the waitForPreview can probably be moved to AD instance in Event.
			String event = request.params("event");
			Event e = Server.activeEvents.get(event);
			if(e == null){
				response.status(500);
				return "Event not active.";
			}
			e.getDisplay().issueCommand(DisplayCommand.SHOW_PREVIEW);
			//is this lock still used?
			synchronized(e.waitForPreviewLock) {
				e.waitForPreviewLock.notifyAll();
			}
			return "OK";
		};
		public static Route handleShowResults = (Request request, Response response) ->{
			String event = request.params("event");
			Event e = Server.activeEvents.get(event);
			if(e == null){
				response.status(500);
				return "Event not active.";
			}
			e.getDisplay().issueCommand(DisplayCommand.SHOW_RESULT);
			
			return "OK";
		};
		
		public static Route handleShowOldResults = (Request request, Response response) ->{
			
			String event = request.params("event");
			Event e = Server.activeEvents.get(event);
			if(e == null){
				response.status(500);
				return "Event not active.";
			}
			//Set te Display's lastResult object
			int match = Integer.parseInt(request.queryParams("match"));
			Match m = EventDAO.getMatchResultFull(event, match);
			Alliance red = m.getRed();
			Alliance blue = m.getBlue();
			m.getScoreBreakdown();//force score calc
			MatchResult mr = new MatchResult(m.getNumber(), red, blue,red.getLastScore(), blue.getLastScore(), 1, red.getPenaltyPoints(), blue.getPenaltyPoints()  );
			
			Display d = e.getDisplay();
			//do not show change in rank for reposting old matches 
			d.blue1Dif = 0;
			d.blue2Dif = 0;
			d.red1Dif = 0;
			d.red2Dif = 0;
			d.lastResult = mr;
			
			
			e.getDisplay().issueCommand(DisplayCommand.SHOW_RESULT);		
			return "OK";
		};
		
		
		
		public static Route serveInspectionHome = (Request request, Response response) ->{
			Map<String, Object> map = new HashMap<>();
			String event = request.params("event");
			String eventName = "Unknown Event";
			Event eventData = Server.activeEvents.get(event);
			if (eventData != null) {
				eventName = eventData.getData().getName();
			}
			map.put("eventName", eventName);
			return render(request, map, Path.Template.INSPECT_HOME);
			
		};

		public static Route handleNote = (Request request, Response response) ->{
			String event = request.params("event");
			String form = request.params("form").toUpperCase();
			String team = request.queryParams("team");
			String note = request.queryParams("note");
			if(EventDAO.setFormComment(event, form, Integer.parseInt(team), note)) {
				return "OK";
			}
			response.status(400);
			return "";
		};

		public static Route handleSig = (Request request, Response response) ->{
			String event = request.params("event");
			String form = request.params("form").toUpperCase();
			int team = Integer.parseInt(request.queryParams("team"));
			int ind = Integer.parseInt(request.queryParams("index"));
			String sig = request.queryParams("sig");
			if(EventDAO.updateSigs(event, form, team, ind, sig)) {
				return "OK";
			}
			response.status(400);
			return "";
		};
		
		public static Route handleFormStatus = (Request request, Response response) ->{
			String event = request.params("event");
			String form = request.params("form").toLowerCase();
			int team = Integer.parseInt(request.queryParams("team"));
			int status = Integer.parseInt(request.queryParams("status"));
			if(EventDAO.setTeamStatus(event, form, team, status)) {
				return "OK";
			}
			response.status(400);
			return "";
		};

		public static Route serveTeamInspectionHome = (Request request, Response response) ->{
			Map<String, Object> map = new HashMap<>();
			String event = request.params("event");
			//TODO time this method and see how long it takes. If its taking too long, make one DAO call that returns all this data 
			//and only creates one SQL transaction instead of 9
			int teamNo = Integer.parseInt(request.params("team"));
			Team team = EventDAO.getTeamStatus(event, teamNo);
			int hwStatus = team.getStatus(Team.FormIndex.HW.index);
			int swStatus = team.getStatus(Team.FormIndex.SW.index);
			int fdStatus = team.getStatus(Team.FormIndex.FD.index);
			//only load forms if empty
			List<FormRow> hwForm = hwStatus == 1 || hwStatus == 2 ? EventDAO.getFailedItems(event, "HW", teamNo) : new ArrayList<>();
			List<FormRow> swForm = swStatus == 1 || swStatus == 2 ? EventDAO.getFailedItems(event, "SW", teamNo) : new ArrayList<>();
			List<FormRow> fdForm = fdStatus == 1 || fdStatus == 2 ? EventDAO.getFailedItems(event, "FD", teamNo) : new ArrayList<>();
			String hwNote = EventDAO.getFormComments(event, "HW", teamNo)[0];
			String swNote = EventDAO.getFormComments(event, "SW", teamNo)[0];
			String fdNote = EventDAO.getFormComments(event, "FD", teamNo)[0];
			
			map.put("team", team);
			map.put("ci", team.getStatus(Team.FormIndex.CI.index));
			map.put("hw", hwStatus);
			map.put("hwForm", hwForm);
			map.put("hwNote", hwNote);
			map.put("sw", swStatus);
			map.put("swForm", swForm);
			map.put("swNote", swNote);
			map.put("fd", fdStatus);
			map.put("fdForm", fdForm);
			map.put("fdNote", fdNote);

			map.put("headerColor", "#E6B222");
			return render(request, map, Path.Template.INSPECT_TEAM_HOME);
		};

		public static Route serveInspectionOverride = (Request request, Response response) ->{
			System.out.println("OVER");;
			String eventCode = request.params("event");
			String form = request.params("form");
			Map<String, Object> map = new HashMap<>();
			map.put("form", form.toLowerCase());
			map.put("eventCode", eventCode);
			Event e = Server.activeEvents.get(eventCode);
			String eventName = "Unknown Event";
			if (e != null) {
				eventName = e.getData().getName();
			} else {
				map.put("teams", EventDAO.getStatus(eventCode, form));
			}
			map.put("eventName", eventName);
			return render(request, map, Path.Template.INSPECTION_OVERRIDE_PAGE);
		};

		public static Route serveFieldDisplay = (Request request, Response response) ->{
			Map<String, Object> map = new HashMap<>();
			String adStr = request.queryParams("ad");
			String is43Str = request.queryParams("43");
			String fieldStr = request.queryParams("field");
			String muteStr = request.queryParams("mute");
			System.out.println("Params: "+adStr+","+is43Str+","+fieldStr+","+muteStr);
			map.put("ad", adStr == null ? false : Boolean.parseBoolean(adStr));
			map.put("is43", is43Str == null ? false : Boolean.parseBoolean(is43Str));
			map.put("mute", muteStr == null ? false : Boolean.parseBoolean(muteStr));
			map.put("field", fieldStr == null ? null : Integer.parseInt(fieldStr));
			return render(request, map, Path.Template.FIELD_DISPLAY);
		};

		//TODO request could send what it thinks the last command was, 
		//and this blocks if matches, and returns immediately if wrong.
		public static Route handleGetTimerCommands = (Request request, Response response) ->{
			String event = request.params("event");
			Event e = Server.activeEvents.get(event);
			if(e == null) {
				response.status(400);
				return "Event not active";
			}
			//TODO add block=false param to retrieve last command.
			return e.getTimer().blockForNextCommand();
		};
		public static Route handleGetDisplayCommands = (Request request, Response response) ->{
			String event = request.params("event");
			Event e = Server.activeEvents.get(event);
			if(e == null) {
				response.status(400);
				return "Event not active";
			}
			//TODO add block=false param to retrieve last command.
			return e.getDisplay().blockForNextCommand();
		};
		
		public static Route handleShowPreviewCommand = (Request request, Response response) ->{
			String event = request.params("event");
			Event e = Server.activeEvents.get(event);
			if(e == null) {
				response.status(400);
				return "Event not active";
			}
			e.getDisplay().issueCommand(DisplayCommand.SHOW_PREVIEW);
			return "OK";
		};
		public static Route handleShowMatch = (Request request, Response response) ->{
			String event = request.params("event");
			Event e = Server.activeEvents.get(event);
			if(e == null) {
				response.status(400);
				return "Event not active";
			}
			e.getDisplay().issueCommand(DisplayCommand.SHOW_MATCH);
			return "OK";
		};
		
		public static Route handleGetFullResult = (Request request, Response response) ->{
			String event = request.params("event");
			Event e = Server.activeEvents.get(event);
			if(e == null) {
				response.status(400);
				return "Event not active";
			}
			int m = Integer.parseInt(request.params("match"));
			return EventDAO.getMatchResultFull(event, m).getFullScores();
		};
		
		public static Route handleGetEditScorePage = (Request request, Response response) ->{
			HashMap<String, Object> map = new HashMap<>();
			map.put("match", Integer.parseInt(request.params("match")));
			return render(request, map, Path.Template.EDIT_MATCH_SCORE);
		};
		
		private static Match createMatchObject(Request request, Response response, int m) {			
			Set<String> params = request.queryParams();
			/*Format:
			 * <alliance>_score_<scoreKey> : call updateScore
			 * <alliance>_card_<index>
			 * <alliance>_dq_<index> 
			 */
			//team numbers shouldnt matter for this
			Alliance red = new Alliance(0,0);
			red.initializeScores();
			Alliance blue = new Alliance(0,0);
			blue.initializeScores();
			Match match = new Match(m, red, blue);
			
			for(String key : params){
				String[] data = key.split("_");
				Alliance alliance  = match.getAlliance(data[0]);
				switch(data[1]) {
					case "score":
						alliance.updateScore(data[2], request.queryParams(key));				
						break;
					case "card":
						//alliance.setCard(Integer.parseInt(data[2]), Integer.parseInt(request.queryParams(key)));
						alliance.updateScore("card"+ data[2], request.queryParams(key));
						break;
					case "dq":
						//alliance.setDQ(Integer.parseInt(data[2]), Boolean .parseBoolean(request.queryParams(key)));
						alliance.updateScore("dq"+ data[2], request.queryParams(key));
						break;
				}
			}
			return match;
		}
		
		public static Route handleGetEditedScore = (Request request, Response response) ->{
			String event = request.params("event");
			Event e = Server.activeEvents.get(event);			
			if(e == null){
				response.status(500);
				return "Event not active.";
			}
			int m = Integer.parseInt(request.params("match"));
			Match match = createMatchObject(request, response, m);
			return match.getScoreBreakdown();
		};
		public static Route handleCommitEditedScore = (Request request, Response response) ->{
			String event = request.params("event");
			Event e = Server.activeEvents.get(event);			
			if(e == null){
				response.status(500);
				return "Event not active.";
			}
			int m = Integer.parseInt(request.params("match"));
			Match match = createMatchObject(request, response, m);
			if(EventDAO.commitScores(event, match)){
				e.calculateRankings();
				return "OK";
			}
			response.status(500);
			return "PROBLEM";
		};
		
		public static Route handleGetMatchInfo = (Request request, Response response) ->{
			String event = request.params("event");
			Event e = Server.activeEvents.get(event);
			if(e == null){
				response.status(500);
				return "Event not active.";
			}
			int num = Integer.parseInt(request.params("match"));
			Match m = EventDAO.getMatch(event, num);
			String res = "{";
			res += "\"number\":" + m.getNumber()+",";
			res += "\"red1\":"+m.getRed().getTeam1()+",";
			res += "\"red2\":"+m.getRed().getTeam2()+",";
			res += "\"blue1\":"+m.getBlue().getTeam1()+",";
			res += "\"blue2\":"+m.getBlue().getTeam2();
			
			res += "}";
			return res;
		};		
		public static Route handleGetRankings = (Request request, Response response) ->{
			String event = request.params("event");
			Event e = Server.activeEvents.get(event);
			if(e == null){
				response.status(500);
				return "Event not active.";
			}
		//	e.calculateRankings(); TODO make another endpoint to force recalc
			Map<String, Object> map = new HashMap<>();
			map.put("rankings", e.getRankings());
			map.put("event", e.getData().getName());
			return render(request, map, Path.Template.RANKINGS);
		};
		
		public static Route serveManagePage = (Request request, Response response) ->{
			String code = request.params("event");
			EventData data = EventDAO.getEvent(code);
			Map<String, Object> map = new HashMap<>();		
			map.put("eventCode", code);
			map.put("eventName", data.getName());
			map.put("eventDate", data.getDate());
			map.put("status", data.getStatus());
			return render(request, map, Path.Template.MANAGE_EVENT);
		};

		public static Route handleSetStatus = (Request request, Response response) ->{
			String code = request.params("event");
			EventData data = EventDAO.getEvent(code);
			int newStatus = Integer.parseInt(request.queryParams("status"));
			System.out.println(newStatus);
			if(newStatus != data.getStatus() + 1) {
				response.status(400);
				return "CANT SKIP PHASE!";
			}
			switch(newStatus) {
			case 1:
				EventDAO.createEventDatabase(code);
				break;
			case 2:
				EventDAO.populateStatusTables(code);
				break;
			case 3:				
				System.out.println(code + " added to active events.");
				break;
			}

			EventDAO.setEventStatus(code, newStatus);
			return "OK";
		};
		
		public static Route serveAddTeam = (Request request, Response response) ->{
			return render(request, new HashMap<String, Object>(), Path.Template.MANAGE_EVENT_TEAMS);
		};
		
		public static Route handleAddTeam = (Request request, Response response) ->{
			String code = request.params("event");
			EventData data = EventDAO.getEvent(code);
			if(data.getStatus() != 1) {
				response.status(409);
				return "Not in setup phase!";
			}
			try {
			int team = Integer.parseInt(request.queryParams("team"));
			if(EventDAO.addTeamToEvent(team, code)) {
				return "OK"; 
			}
			response.status(400);
			return "Team already in event";
			}catch(Exception e) {
				response.status(400);
				return "Invalid team number";
			}
		};

		public static Route serveUploadSchedulePage = (Request request, Response response) ->{
			return render(request, new HashMap<String, Object>(), Path.Template.UPLOAD_SCHEDULE);
		};

		public static Route handleRecalcRankings  = (Request request, Response response) ->{
			String code = request.params("event");
			Event e = Server.activeEvents.get(code);
			if(e == null) {
				response.status(500);
				return "Event not active!";
			}
			e.calculateRankings();
			return "OK";
		};
		public static Route serveEventHomePage = (Request request, Response response) ->{
			Map<String, Object> map = new HashMap<String, Object>();
			String code = request.params("event");
			EventData data = EventDAO.getEvent(code);
			map.put("event", data);
			return render(request, map, Path.Template.EVENT_HOME);
		};

		public static Route serveEditScoreHome= (Request request, Response response) ->{
			return render(request, new HashMap<String, Object>(), Path.Template.EDIT_SCORE_HOME);
		};
		
		private static String json(String name, Object value) {
			return "\"" + name + "\":\"" + value.toString() + "\"";
		}
		public static Route handlePostResultData =(Request request, Response response)->{
			String code = request.params("event");
			Event e = Server.activeEvents.get(code);
			if(e == null) {
				response.status(500);
				return "Event not active!";
			}
			Display d = e.getDisplay();
			MatchResult mr = d.lastResult;
			Alliance red = mr.getRed();
			Alliance blue = mr.getBlue();
			Map<Integer, List<Integer>> cardMap = EventDAO.getCardsForTeams(code, red.getTeam1(), red.getTeam2(), blue.getTeam1(), blue.getTeam2());
			List<String> list = new ArrayList<>(20);
			
			list.add(json("number", mr.getNumber()));
			
			list.add(json("red1Dif", d.red1Dif));
			list.add(json("red2Dif", d.red2Dif));
			list.add(json("blue1Dif", d.blue1Dif));
			list.add(json("blue2Dif", d.blue2Dif));
			//value of bluePenalty is added to redScore to get redTotal!
			list.add(json("redScore", mr.getRedScore()));
			list.add(json("blueScore", mr.getBlueScore()));
			list.add(json("redPenalty", mr.getRedPenalty()));
			list.add(json("bluePenalty", mr.getBluePenalty()));
			list.add(json("redTotal", mr.getRedTotal()));
			list.add(json("blueTotal", mr.getBlueTotal()));
			list.add(json("winChar", mr.getWinChar()));
			
			list.add(json("red1", red.getTeam1()));
			list.add(json("red2", red.getTeam2()));
			list.add(json("blue1", blue.getTeam1()));
			list.add(json("blue2", blue.getTeam2()));
			
			list.add(json("red1Rank", e.getRank(red.getTeam1())));
			list.add(json("red2Rank", e.getRank(red.getTeam2())));
			list.add(json("blue1Rank", e.getRank(blue.getTeam1())));
			list.add(json("blue2Rank", e.getRank(blue.getTeam2())));
			
			int redCard1 = Integer.parseInt(red.getScore("card1").toString());
			int redCard2 = Integer.parseInt(red.getScore("card2").toString());
			int blueCard1 = Integer.parseInt(blue.getScore("card1").toString());
			int blueCard2 = Integer.parseInt(blue.getScore("card2").toString());
			
			//for each team, if they had a card from a previous match & they got a YELLOW card, mark as 3 to display both yellow and red.
			List<Integer> cardList = cardMap.get(red.getTeam1());			
			Integer t = cardList.size() > 0 ? cardList.get(0) : null;
			if(t!=null && t.intValue()<mr.getNumber() && redCard1==1)redCard1 = 3;
			
			cardList = cardMap.get(red.getTeam2()); 
			t = cardList.size() > 0 ? cardList.get(0) : null;
			if(t!=null && t.intValue()<mr.getNumber() && redCard2==1)redCard2 = 3;
			
			cardList = cardMap.get(blue.getTeam1());
			t = cardList.size() > 0 ? cardList.get(0) : null;
			if(t!=null && t.intValue()<mr.getNumber() && blueCard1==1)blueCard1 = 3;
			
			cardList = cardMap.get(blue.getTeam2());
			t = cardList.size() > 0 ? cardList.get(0) : null;
			if(t!=null && t.intValue()<mr.getNumber() && blueCard2==1)blueCard2 = 3;
			
			
			list.add(json("red1Card", redCard1 ));
			list.add(json("red2Card", redCard2 ));
			list.add(json("blue1Card", blueCard1 ));
			list.add(json("blue2Card", blueCard2 ));
			
			return "{"+String.join(",", list)+"}";
		};
		
		public static Route serveResultsDetailPage = (Request request, Response response) ->{
			String event = request.params("event");
			Event e = Server.activeEvents.get(event);
			
			if(e == null){
				response.status(500);
				return "Event not active.";
			}
			List<MatchResult> results = EventDAO.getMatchResults(event);
			Map<String, Object> map = new HashMap<String, Object>();
			map.put("matches", results);
			map.put("event", event); //TODO get event name from DB
			return render(request, map, Path.Template.MATCH_RESULT_DETAIL);
		};
		
		public static Route handleGetFullScoresheet = (Request request, Response response) -> {
			Map<String, Object> map = new HashMap<String, Object>();
			String event = request.params("event");
			Event e = Server.activeEvents.get(event);
			if(e == null) {
				return DefaultPages.notFound.handle(request, response);
			}
			int m = Integer.parseInt(request.params("match"));
			Match match = EventDAO.getMatchResultFull(event, m);//.getFullScores();
			if (match == null) {
				return DefaultPages.notFound.handle(request, response);
			}
			match.getScoreBreakdown();
			map.put("redScore", Integer.parseInt(match.redScoreBreakdown.get("score")));
			map.put("blueScore", Integer.parseInt(match.blueScoreBreakdown.get("score")));
			map.put("redBreakdown", match.redScoreBreakdown);
			map.put("blueBreakdown", match.blueScoreBreakdown);
			map.put("matchNumber", match.getNumber());
			map.put("fieldNumber", match.getNumber() % 2);
			map.put("red", match.getRed());
			map.put("blue", match.getBlue());
			int redRelic1Zone = Integer.parseInt(match.getRed().getScore("relic1Zone").toString());
			int redRelic2Zone = Integer.parseInt(match.getRed().getScore("relic2Zone").toString());
			boolean redRelic1Standing = Boolean.parseBoolean(match.getRed().getScore("relic1Standing").toString());
			boolean redRelic2Standing = Boolean.parseBoolean(match.getRed().getScore("relic2Standing").toString());
			map.put("redZone1", ((redRelic1Zone == 1) ? 1 : 0) + ((redRelic2Zone == 1) ? 1 : 0));
			map.put("redZone2", ((redRelic1Zone == 2) ? 1 : 0) + ((redRelic2Zone == 2) ? 1 : 0));
			map.put("redZone3", ((redRelic1Zone == 3) ? 1 : 0) + ((redRelic2Zone == 3) ? 1 : 0));
			map.put("redStanding", (redRelic1Standing && redRelic2Standing) ? 2 : (redRelic1Standing || redRelic2Standing) ? 1 : 0);
			map.put("redScores", match.getRed().getRawScores());
			int blueRelic1Zone = Integer.parseInt(match.getBlue().getScore("relic1Zone").toString());
			int blueRelic2Zone = Integer.parseInt(match.getBlue().getScore("relic2Zone").toString());
			boolean blueRelic1Standing = Boolean.parseBoolean(match.getBlue().getScore("relic1Standing").toString());
			boolean blueRelic2Standing = Boolean.parseBoolean(match.getBlue().getScore("relic2Standing").toString());
			map.put("blueZone1", ((blueRelic1Zone == 1) ? 1 : 0) + ((blueRelic2Zone == 1) ? 1 : 0));
			map.put("blueZone2", ((blueRelic1Zone == 2) ? 1 : 0) + ((blueRelic2Zone == 2) ? 1 : 0));
			map.put("blueZone3", ((blueRelic1Zone == 3) ? 1 : 0) + ((blueRelic2Zone == 3) ? 1 : 0));
			map.put("blueStanding", (blueRelic1Standing && blueRelic2Standing) ? 2 : (blueRelic1Standing || blueRelic2Standing) ? 1 : 0);
			map.put("blueScores", match.getBlue().getRawScores());
			return render(request, map, Path.Template.FULL_SCORESHEET);
		};
		
		public static Route handleGetAllianceBreakdown = (Request request, Response response) -> {
			Map<String, Object> map = new HashMap<String, Object>();
			String event = request.params("event");
			String alliance = request.params("alliance");
			boolean redAlliance = "red".equals(alliance);
			boolean blueAlliance = "blue".equals(alliance);
			if (!redAlliance && !blueAlliance) {
				return DefaultPages.notFound.handle(request, response);
			}
			map.put("redAlliance", redAlliance);
			map.put("blueAlliance", blueAlliance);
			Event e = Server.activeEvents.get(event);
			if(e == null) {
				return DefaultPages.notFound.handle(request, response);
			}
			int m = Integer.parseInt(request.params("match"));
			Match match = EventDAO.getMatchResultFull(event, m);//.getFullScores();
			if (match == null) {
				return DefaultPages.notFound.handle(request, response);
			}
			match.getScoreBreakdown();
			//taken from https://stackoverflow.com/questions/2779251/how-can-i-convert-json-to-a-hashmap-using-gson
			Type type = new TypeToken<Map<String, String>>(){}.getType();
			Gson gson = new Gson();
			map.put("redBreakdown", gson.fromJson(match.getScoreBreakdown(match.getRed()), type));
			map.put("blueBreakdown", gson.fromJson(match.getScoreBreakdown(match.getBlue()), type));
			map.put("matchNumber", match.getNumber());
			map.put("fieldNumber", match.getNumber() % 2);
			map.put("red", match.getRed());
			map.put("blue", match.getBlue());
			int redRelic1Zone = Integer.parseInt(match.getRed().getScore("relic1Zone").toString());
			int redRelic2Zone = Integer.parseInt(match.getRed().getScore("relic2Zone").toString());
			boolean redRelic1Standing = Boolean.parseBoolean(match.getRed().getScore("relic1Standing").toString());
			boolean redRelic2Standing = Boolean.parseBoolean(match.getRed().getScore("relic2Standing").toString());
			map.put("redZone1", ((redRelic1Zone == 1) ? 1 : 0) + ((redRelic2Zone == 1) ? 1 : 0));
			map.put("redZone2", ((redRelic1Zone == 2) ? 1 : 0) + ((redRelic2Zone == 2) ? 1 : 0));
			map.put("redZone3", ((redRelic1Zone == 3) ? 1 : 0) + ((redRelic2Zone == 3) ? 1 : 0));
			map.put("redStanding", (redRelic1Standing && redRelic2Standing) ? 2 : (redRelic1Standing || redRelic2Standing) ? 1 : 0);
			
			map.put("redScores", match.getRed().getRawScores());
			int blueRelic1Zone = Integer.parseInt(match.getBlue().getScore("relic1Zone").toString());
			int blueRelic2Zone = Integer.parseInt(match.getBlue().getScore("relic2Zone").toString());
			boolean blueRelic1Standing = Boolean.parseBoolean(match.getBlue().getScore("relic1Standing").toString());
			boolean blueRelic2Standing = Boolean.parseBoolean(match.getBlue().getScore("relic2Standing").toString());
			map.put("blueZone1", ((blueRelic1Zone == 1) ? 1 : 0) + ((blueRelic2Zone == 1) ? 1 : 0));
			map.put("blueZone2", ((blueRelic1Zone == 2) ? 1 : 0) + ((blueRelic2Zone == 2) ? 1 : 0));
			map.put("blueZone3", ((blueRelic1Zone == 3) ? 1 : 0) + ((blueRelic2Zone == 3) ? 1 : 0));
			map.put("blueStanding", (blueRelic1Standing && blueRelic2Standing) ? 2 : (blueRelic1Standing || blueRelic2Standing) ? 1 : 0);
			map.put("blueScores", match.getBlue().getRawScores());
			return render(request, map, Path.Template.ALLIANCE_BREAKDOWN);			
		};
		
		
		
}
