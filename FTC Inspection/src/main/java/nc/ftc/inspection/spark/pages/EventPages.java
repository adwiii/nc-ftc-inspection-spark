package nc.ftc.inspection.spark.pages;

import nc.ftc.inspection.Server;
import nc.ftc.inspection.dao.EventDAO;
import nc.ftc.inspection.event.Event;
import nc.ftc.inspection.model.Alliance;
import nc.ftc.inspection.model.EventData;
import nc.ftc.inspection.model.FormRow;
import nc.ftc.inspection.model.Match;
import nc.ftc.inspection.model.MatchStatus;
import nc.ftc.inspection.model.Team;
import nc.ftc.inspection.spark.util.Path;
import static nc.ftc.inspection.spark.util.ViewUtil.render;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.stream.Collectors;

import javax.servlet.MultipartConfigElement;
import javax.servlet.http.Part;

import org.apache.commons.collections.bag.SynchronizedSortedBag;
import org.omg.CORBA.PRIVATE_MEMBER;

import java.awt.Point;
import java.sql.Date;

import spark.QueryParamsMap;
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
		EventData event = EventDAO.getEvent(eventCode);
		model.put("eventName", event.getName());
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
	
	public static Route serveInspectionPage = (Request request, Response response) ->{
		Map<String, Object> model = new HashMap<>();
		String eventCode = request.params("event");
		String formID = request.params("form").toUpperCase();
		String team = request.queryParams("team");
		String teams = request.queryParams("teams");
		//if both provided, use the "teams" param.
		if(teams == null && team != null){
			teams = team;
		}
		if(teams == null){
			return "";
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
		System.out.println(max);
		model.put("max", max);
		model.put("form", form);
		model.put("teams", teamList);
		model.put("headerColor", "#E6B222");
		return render(request, model, Path.Template.INSPECT);
	};
	
	public static Route handleInspectionItemPost = (Request request, Response response) ->{
		String event = request.params("event");
		String form = request.queryParams("form");
		int team =  Integer.parseInt(request.queryParams("team"));
		int itemIndex = Integer.parseInt(request.queryParams("index"));
		boolean status = Boolean.parseBoolean(request.queryParams("state"));
		response.status(EventDAO.setFormStatus(event, form,team, itemIndex, status) ? 200 : 500);
		return request.queryParams("state");
		//Client perspective:
		//If 200 & state matches, we good. If 200 & state wrong, timing issue, do nothing
		//if 500, failed.
	};
	
	public static Route handleGetStatusGet = (Request request, Response response) -> {
		String event = request.params("event");
		System.out.println("hi");

		//TODO get which columns are enabled.
		String[] columns = new String[]{"hw", "sw", "fd", "sc", "ci"};
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
				template = a.scoreSubmitted() ? Path.Template.REF_TELEOP : Path.Template.REF_AUTO_REVIEW;
				break;
			case TELEOP:
				template = Path.Template.REF_TELEOP;
				break;
			case REVIEW:
				template = Path.Template.REF_REVIEW;
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
		
		public static Route handleGetCurrentMatch = (Request request, Response response) ->{
			return null;
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
			return updateScores(request, response);
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
				if(!Server.activeEvents.get(e).getCurrentMatch().getStatus().isReview()) {
					response.status(409);
					return "Not ready to review.";
				}	
				Match match = Server.activeEvents.get(e).getCurrentMatch();
				match.getAlliance(request.params("alliance")).setInReview(true);
				match.calculateEndAuto();
			}
			return res;
			
		};
		
		public static Route handleScoreSubmit = (Request request, Response response) ->{
			//TODO if not in REVIEW status, reject.
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
				event.getCurrentMatch().getAlliance(alliance).setSubmitted(true);
				event.getCurrentMatch().getAlliance(alliance).setInReview(false);
				//both alliances scores submitted -> go to teleop or pre-commit
				//Front end needs to say submitted until post-commit (after teleop).
				if(event.getCurrentMatch().scoreSubmitted()){
					
					if(event.getCurrentMatch().getStatus() == MatchStatus.AUTO_REVIEW){

						System.out.println("TELEOP TIME!");
						event.getCurrentMatch().setStatus(MatchStatus.TELEOP);
						event.getCurrentMatch().clearSubmitted();
					} else if(event.getCurrentMatch().getStatus() == MatchStatus.REVIEW){
						System.out.println("AUTO TIME!");
						event.getCurrentMatch().setStatus(MatchStatus.PRE_COMMIT);
					}
				}
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
			return null;
		};
		public static Route serveMatchControlPage = (Request request, Response response) ->{
			return render(request, new HashMap<String, Object>(), Path.Template.CONTROL);
		};
}
