package nc.ftc.inspection.spark.pages;

import nc.ftc.inspection.Server;
import nc.ftc.inspection.dao.EventDAO;
import nc.ftc.inspection.dao.GlobalDAO;
import nc.ftc.inspection.event.ADState;
import nc.ftc.inspection.event.Event;
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
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.Set;
import java.util.stream.Collectors;

import javax.servlet.MultipartConfigElement;
import javax.servlet.http.Part;

import org.apache.commons.collections.bag.SynchronizedSortedBag;

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
		String event = request.params("event");
		Map<String, Object> model = new HashMap<String, Object>();
		model.put("event", event);//TODO get the event name from db
		String[] columns = new String[]{"hw", "sw", "fd", "sc", "ci"};
		model.put("headers", columns);
		model.put("teams", EventDAO.getStatus(event));
		return render(request, model, Path.Template.STATUS_PAGE);
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
			Part p = request.raw().getPart("file");
			List<Match> matches = new ArrayList<>();
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
			//	System.out.println(match+":"+red1+(r1S == 1 ? "*":"")+","+red2+(r2S == 1 ? "*":"")+","+blue1+(b1S == 1 ? "*":"")+","+blue2+(b2S == 1 ? "*":""));
			}
			}catch(Exception e){
				e.printStackTrace();
			}
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
			e.getCurrentMatch().setStatus(MatchStatus.AUTO);
			return "{\"rand\":\"" + e.getCurrentMatch().randomize() +"\"}";
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
			case AUTO_REVIEW: //if already submitted, load teleop. (Only matters for first ref to submit)
				//Alliance a = e.getCurrentMatch().getAlliance(alliance);
				//TODO use alliance.isInReview() to serve a waiting page that waits until both refs enter review phase.
				a.setInReview(true);//in case refreshed.
				template = a.scoreSubmitted() ? Path.Template.REF_TELEOP : Path.Template.REF_AUTO_REVIEW;
				break;
			case TELEOP:
				template = Path.Template.REF_TELEOP;
				break;
			case REVIEW:
				template = a.scoreSubmitted() ? Path.Template.REF_POST_SUBMIT : Path.Template.REF_REVIEW;
				a.setInReview(true);//in case refreshed.
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
				return null;
			}
			if(event.getCurrentMatch() == null){
				response.status(500);
				return null;
			}
			if(event.getCurrentMatch().isRandomized()){
				return "{\"rand\":\"" + event.getCurrentMatch().getRandomization() +"\"}";
			}
			//Not yet randomized. Wait until it is.
			//TODO some form of timeout? half an hour? - just put in .wait(ms) call
			synchronized(MatchStatus.AUTO){
				MatchStatus.AUTO.wait();
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
				if(status == MatchStatus.AUTO || status == MatchStatus.AUTO_REVIEW) {
					Server.activeEvents.get(e).getCurrentMatch().updateJewels();
				}
				Server.activeEvents.get(e).getCurrentMatch().getAlliance(request.params("alliance")).calculateGlyphs();
				Server.activeEvents.get(request.params("event")).getCurrentMatch().updateNotify();
			}
			return s;
		};
		
		//POST that is done by hitting "review" button. always saves the info, but returns
		//error if not ready for review phase.
		//could change this to long poll in the future.
		public static Route handleScoreFullUpate = (Request request, Response response) -> {
			String res = updateScores(request, response);
			if(res.equals("OK")) {
				//If not in review phase, dont return 200. That way client knows not to load
				//review page yet.
				String e = request.params("event");
				
				MatchStatus status = Server.activeEvents.get(e).getCurrentMatch().getStatus();
				if(status == MatchStatus.AUTO || status == MatchStatus.AUTO_REVIEW) {
					Server.activeEvents.get(e).getCurrentMatch().updateJewels();
				}
				System.out.println(status);
				if(!status.isReview()) {
					Server.activeEvents.get(request.params("event")).getCurrentMatch().updateNotify();
					response.status(409);
					return "Not ready to review.";
				}	
				Match match = Server.activeEvents.get(e).getCurrentMatch();
				match.getAlliance(request.params("alliance")).setInReview(true);
				Server.activeEvents.get(request.params("event")).getCurrentMatch().getAlliance(request.params("alliance")).calculateGlyphs();
//				if(status == MatchStatus.AUTO) {
//					match.calculateEndAuto();
//				}
				Server.activeEvents.get(request.params("event")).getCurrentMatch().updateNotify();
			}
			return res;
			
		};
		
		public static Route handleScoreSubmit = (Request request, Response response) ->{
			//TODO if not in REVIEW status, reject.
			//TODO handle submit after teleop started for auto.
			//so maybe dont reject?
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
				response.status(500);
				return "Not in review phase!";
			}
			if(res.equals("OK") ){ 
				//if not both refs in review - dont allow
				if(!event.getCurrentMatch().isInReview()) {
					response.status(409);
					return "Red & Blue must both be in review";
				}
				event.getCurrentMatch().getAlliance(alliance).setSubmitted(true);
//				TODO commented this out recently, if stuff breaks maybe this
				//event.getCurrentMatch().getAlliance(alliance).setInReview(false);
				
				//both alliances scores submitted -> go to teleop or pre-commit
				//Front end needs to say submitted until post-commit (after teleop).
				if(event.getCurrentMatch().scoreSubmitted()){
					event.getCurrentMatch().getRed().setInReview(false);
					event.getCurrentMatch().getBlue().setInReview(false);
					
					if(event.getCurrentMatch().getStatus() == MatchStatus.AUTO_REVIEW){

						System.out.println("TELEOP TIME!");
						event.getCurrentMatch().setStatus(MatchStatus.TELEOP);
						event.getCurrentMatch().clearSubmitted();
					} else if(event.getCurrentMatch().getStatus() == MatchStatus.REVIEW){
						System.out.println("AUTO TIME!");
						event.getCurrentMatch().setStatus(MatchStatus.PRE_COMMIT);
						synchronized (MatchStatus.PRE_COMMIT) {
							MatchStatus.PRE_COMMIT.notifyAll();
						}
					}
				}
				Server.activeEvents.get(request.params("event")).getCurrentMatch().updateNotify();
			} else{
				response.status(500);
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
			if(match.getStatus() == MatchStatus.AUTO){
				match.setStatus(MatchStatus.AUTO_REVIEW);
			}
			if(match.getStatus() == MatchStatus.TELEOP){
				match.setStatus(MatchStatus.REVIEW);
			}
			return "OK";
		};

		public static Route handleScoreCommit = (Request request, Response response) ->{
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
			if(EventDAO.commitScores(event, e.getCurrentMatch())){
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
			if(e.getCurrentMatch() == null){
				response.status(500);
				return "No match loaded";
			}
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
			//TODO fix this an dmake it not suck!
			String res = "{";
			res += "\"number\":" + m.getNumber()+",";
			res += "\"red1\":"+m.getRed().getTeam1()+",";
			res += "\"red2\":"+m.getRed().getTeam2()+",";
			res += "\"blue1\":"+m.getBlue().getTeam1()+",";
			res += "\"blue2\":"+m.getBlue().getTeam2() +",";
			res += "\"red1Name\":\""+GlobalDAO.getTeamName(m.getRed().getTeam1())+"\",";
			res += "\"red2Name\":\""+GlobalDAO.getTeamName(m.getRed().getTeam2())+"\",";
			res += "\"blue1Name\":\""+GlobalDAO.getTeamName(m.getBlue().getTeam1())+"\",";
			res += "\"blue2Name\":\""+GlobalDAO.getTeamName(m.getBlue().getTeam2())+"\"";
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
				synchronized(MatchStatus.PRE_COMMIT) {
					MatchStatus.PRE_COMMIT.wait();
				}
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
		
		public static Route serveAudienceDisplay = (Request request, Response response) ->{
			return render(request, new HashMap<String, Object>(), Path.Template.AUDIENCE_DISPLAY);
		};
		
		public static Route handleWaitForPreview = (Request request, Response response) ->{
			synchronized(ADState.PREVIEW) {
				ADState.PREVIEW.wait();
			}
			return "OK";
		};
		public static Route handleShowPreview = (Request request, Response response) ->{
			synchronized(ADState.PREVIEW) {
				ADState.PREVIEW.notifyAll();
			}
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
		
		
}
