/*
 * Copyright (c) 2016-2018 Thomas Barnette and Trey Woodlief
 * All Rights Reserved.
 */

package nc.ftc.inspection.spark.pages;

import nc.ftc.inspection.Server;
import nc.ftc.inspection.dao.EventDAO;
import nc.ftc.inspection.dao.GlobalDAO;
import nc.ftc.inspection.event.Display;
import nc.ftc.inspection.event.DisplayCommand;
import nc.ftc.inspection.event.Event;
import nc.ftc.inspection.event.Rank;
import nc.ftc.inspection.event.TimerCommand;
import nc.ftc.inspection.model.Alliance;
import nc.ftc.inspection.model.EventData;
import nc.ftc.inspection.model.FormRow;
import nc.ftc.inspection.model.Match;
import nc.ftc.inspection.model.MatchResult;
import nc.ftc.inspection.model.MatchStatus;
import nc.ftc.inspection.model.Selection;
import nc.ftc.inspection.model.Team;
import nc.ftc.inspection.spark.util.Path;
import static nc.ftc.inspection.spark.util.ViewUtil.render;
import static spark.Spark.halt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.servlet.MultipartConfigElement;
import javax.servlet.http.Part;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.sql.Date;

import spark.Request;
import spark.Response;
import spark.Route;

public class EventPages {
	static Logger log ;
	static{
		if(!Server.redirected) {
			Server.redirectError();
		}
		log = LoggerFactory.getLogger(EventPages.class);
	}
	
	public static Route serveEventCreationPage = (Request request, Response response) -> {	
		return render(request, Path.Template.CREATE_EVENT);
	};
	public static final String eventCodePattern = "[a-z0-9_]+";
	public static Route handleEventCreationPost = (Request request, Response response) -> {
		Map<String, Object> model = new HashMap<>();
		String code = request.queryParams("eventCode");
		String name = request.queryParams("eventName");
		String eventDate = request.queryParams("eventDate");
		Date date = null;
		if (!Pattern.matches(eventCodePattern, code)) {
			model.put("success", 0);
			model.put("resp", "Could not create event, the event code must be lowercase letters, numbers, or underscore");
			model.put("eventCode", code);
			model.put("eventName", name);
			model.put("eventDate", eventDate);
			return render(request, model, Path.Template.CREATE_EVENT);
		}
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
			response.redirect(Path.Web.MANAGE_EVENT.replace(":event", code));
			halt();
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
		EventData e = EventDAO.getEvent(eventCode);
		String eventName = "Unknown Event";
		if (e != null) {
			eventName = e.getName();
			map.put("teams", EventDAO.getStatus(eventCode, formID));
		}
		map.put("eventName", eventName);
		
		if(formID.equals("SC")||formID.equals("CI")) {
			//render the boolean page (which is equivalent to the LRI page, without comments)
			return render(request, map, Path.Template.BINARY_INSPECTION_PAGE);
		}
		
		return render(request, map, Path.Template.INSPECTION_TEAM_SELECT);
	}
	
	private static String inspectionPage(Request request, Response response, boolean readOnly, String eventCode, String formID, String team, String teams, boolean plain) {
		Map<String, Object> model = new HashMap<>();
		//if both provided, use the "teams" param.
		if(teams == null && team != null){
			teams = team;
		}
		//check for team read-only page
		if(teams == null) {
			teams = team;
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
		
//		System.out.println(Arrays.toString(notes));
		model.put("readOnly", readOnly);
		model.put("max", max);
		model.put("form", form);
		model.put("formID", formID);
		model.put("teams", teamList);
		model.put("notes", notes);
		model.put("sigs", sigs);
		model.put("headerColor", "#F57E25");
		if (plain) {
			return render(request, model, Path.Template.INSPECT_PLAIN);
		} else {
			return render(request, model, Path.Template.INSPECT);
		}
	}
	
	public static Route serveInspectionPage = (Request request, Response response) ->{
		boolean plain = request.pathInfo().endsWith("plain/");
		String eventCode = request.params("event");
		String formID = request.params("form").toUpperCase();
		String team = request.queryParams("team");
		String teams = request.queryParams("teams");
		if(Server.activeEvents.get(eventCode) == null) {
			return noData(request, "Inspection");
		}
		return inspectionPage(request, response, false, eventCode, formID, team, teams, plain);
	};
	public static Route serveInspectionPageReadOnly = (Request request, Response response) ->{
		boolean plain = request.pathInfo().endsWith("plain/");
		String eventCode = request.params("event");
		String formID = request.params("form").toUpperCase();
		String team = request.params("team");
		String teams = request.queryParams("teams");
		return inspectionPage(request, response, true, eventCode, formID, team, teams, plain);
	};
	
	public static Route handleInspectionItemPost = (Request request, Response response) ->{
		String event = request.params("event");
		String form = request.queryParams("form");
		int team =  Integer.parseInt(request.queryParams("team"));
		int itemIndex = Integer.parseInt(request.queryParams("index"));
		boolean status = Boolean.parseBoolean(request.queryParams("state"));
		
		Event e = Server.activeEvents.get(event);
		if(e == null) {
			response.status(400);
			return "Event not active!";
		}
		
		response.status(e.setFormStatus(form, team, itemIndex, status) ? 200 : 500);
		/*
		response.status(EventDAO.setFormStatus(event, form,team, itemIndex, status) ? 200 : 500);
		EventDAO.setTeamStatus(event, form, team, 1);//IN PROGRESS TODO CONSTANT
		*/
		
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
		if(teams == null) {
			return noData(request, "Inspection");
		}
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
		String[] columns = new String[]{"ci", "sc", "hw", "sw", "fd"};//removed SC! hope it still works!
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
		Event e = Server.activeEvents.get(event);
		if(e == null) {
			return noData(request,"Schedule");
		}
		List<Match> schedule = e.scheduleCache.get();
		if(schedule == null) {
			schedule = EventDAO.getSchedule(event);
			e.scheduleCache.set(schedule);
		}
		
		model.put("event", event);//TODO get the event name from db
		List<Match> quals = new ArrayList<>(schedule.size());
		List<Match> elims = new ArrayList<>(10);
		for(Match m : schedule) {
			if(m.isElims()) {
				elims.add(m);
			} else {
				quals.add(m);
			}
		}
		model.put("matches", quals);
		model.put("elims", elims);
		String page = render(request, model, Path.Template.SCHEDULE_PAGE);
		return page;
		
		
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
			List<Match> playedQuals = new ArrayList<>();
			List<Match> elims = new ArrayList<>();//for uploading complete event
			List<Match> playedElims = new ArrayList<>();
			List<Alliance> alliances = new ArrayList<>(4); // for uploading complete event
			boolean started = false;
			Set<Integer> teams = new HashSet<Integer>();
			Scanner scan = new Scanner(p.getInputStream());
			scan.useDelimiter("\\|");
			try{
			while(scan.hasNextLine()){
				/*
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
				*/
				scan.nextInt(); //Division Number. We are ignoring this.
				int phase = scan.nextInt();
				int number = scan.nextInt();
				int TScount = scan.nextInt();
				long[] ts = new long[TScount];
				for(int i = 0; i < TScount; i++) {
					ts[i] = scan.nextLong();
				}
				scan.next(); //always empty
				int red1 = scan.nextInt();
				int red2 = scan.nextInt();
				int red3 = scan.nextInt();
				int blue1 = scan.nextInt();
				int blue2 = scan.nextInt();
				int blue3 = scan.nextInt();
				//1 if no-show, 2 if RED CARD
				int red1DQ = scan.nextInt();
				int red2DQ = scan.nextInt();
				int red3DQ = scan.nextInt();
				boolean red1yc = scan.nextBoolean();
				boolean red2yc = scan.nextBoolean();
				boolean red3yc = scan.nextBoolean();
				int blue1DQ = scan.nextInt();
				int blue2DQ = scan.nextInt();
				int blue3DQ = scan.nextInt();
				boolean blue1yc = scan.nextBoolean();
				boolean blue2yc = scan.nextBoolean();
				boolean blue3yc = scan.nextBoolean();
				int r1S = scan.nextInt();
				int r2S = scan.nextInt();
				scan.nextInt(); //probably red3S? which should always be 0
				int b1S = scan.nextInt();
				int b2S = scan.nextInt();
				scan.nextInt(); //probably blue3s which = 0 
				int played = scan.nextInt();
				int redJewels = scan.nextInt();
				int redAutoGlyphs = scan.nextInt();
				int redKeys = scan.nextInt();
				int redParked = scan.nextInt();
				int redGlyphs = scan.nextInt(); 
				int redRows = scan.nextInt();
				int redColumns = scan.nextInt();
				int redCiphers = scan.nextInt();
				int redZone1  = scan.nextInt();
				int redZone2 = scan.nextInt();
				int redZone3 = scan.nextInt();
				int redUpright = scan.nextInt();
				int redBalanced = scan.nextInt();

				int redMinor = scan.nextInt();
				int redMajor = scan.nextInt();
				int blueMinor = scan.nextInt();
				int blueMajor = scan.nextInt();
				
				int blueJewels = scan.nextInt();
				int blueAutoGlyphs = scan.nextInt();
				int blueKeys = scan.nextInt();
				int blueParked = scan.nextInt();
				int blueGlyphs = scan.nextInt(); 
				int blueRows = scan.nextInt();
				int blueColumns = scan.nextInt();
				int blueCiphers = scan.nextInt();
				int blueZone1  = scan.nextInt();
				int blueZone2 = scan.nextInt();
				int blueZone3 = scan.nextInt();
				int blueUpright = scan.nextInt();
				int blueBalanced = scan.nextInt();
				scan.nextLine();//ignore repeat penalty entries
				
				Match match;
				if(phase == 1) {
					Alliance red = new Alliance(red1, r1S == 1, red2, r2S == 1);
					Alliance blue = new Alliance(blue1, b1S == 1, blue2, b2S == 1);

					match = new Match(number, red, blue);
					matches.add(match);
					teams.add(red1);
					teams.add(red2);
					teams.add(blue1);
					teams.add(blue2);
				} else {
					started = true; //allow elims with no quals
					//phase 2 match 11 and 21 need to set alliances
					if(phase == 2) {
						if(number == 11) { //SF1-1
							alliances.add(new Alliance(1, red1, red2, red3));
							alliances.add(new Alliance(4, blue1, blue2, blue3));
						} 
						if(number == 21) {//SF2-1
							alliances.add(new Alliance(2, red1, red2, red3));
							alliances.add(new Alliance(3, blue1, blue2, blue3));
						}
					}
					
					
					
					//These matches will use their numbering system! need t convert before saving.
					String name = phase == 2 ? "SF-" : "F-";
					if(phase == 2) {
						name += (number/10)+"-"+(number % 10);
					} else {
						name += number;
					}
					//for now, alliance doesnt matter. after we create DB entry, get the alliance info before committing
					Alliance red = new Alliance(0);
					Alliance blue = new Alliance(0);
					match = new Match(number,red,blue,name);
					elims.add(match);
				}
				
				if(played == 1) {
					started = true;
					Alliance red = match.getRed();
					Alliance blue = match.getBlue();
					red.initializeScores();
					blue.initializeScores();
					
					//i have the number in each zone and the number standing
					//convert to by relic scores
					int red1Z = 0;
					int red2Z = 0;
					if(redZone1 > 0) {
						red1Z = 1;
						redZone1--;
					} else if(redZone2 > 0) {
						red1Z = 2;
						redZone2--;
					} else if(redZone3 > 0) {
						red1Z = 3;
						redZone3--;
					}
					if(redZone1 > 0) {
						red2Z = 1;
					} else if(redZone2 > 0) {
						red2Z = 2;
					} else if(redZone3 > 0) {
						red2Z = 3;
					}					
					
					boolean redDQ1 = red1DQ == 1;
					boolean redDQ2 = red2DQ == 1;
					int redCard1 = red1yc ?  1 : (red1DQ == 2 ? 2 : 0);
					int redCard2 = red2yc ? 1 : (red2DQ == 2 ? 2 : 0);
					red.updateScore("major", redMajor);
					red.updateScore("minor", redMinor);
					red.updateScore("autoGlyphs", redAutoGlyphs);
					red.updateScore("cryptoboxKeys", redKeys);
					red.updateScore("jewels", redJewels);
					red.updateScore("parkedAuto", redParked);
					red.updateScore("glyphs", redGlyphs);
					red.updateScore("rows", redRows);
					red.updateScore("columns", redColumns);
					red.updateScore("ciphers", redCiphers);
					red.updateScore("relic1Zone", red1Z);
					red.updateScore("relic1Standing", redUpright >= 1);
					red.updateScore("relic2Zone", red2Z);
					red.updateScore("relic2Standing", redUpright == 2);
					red.updateScore("balanced", redBalanced);
					red.updateScore("card1", redCard1);
					red.updateScore("card2", redCard2);
					red.updateScore("dq1", redDQ1);
					red.updateScore("dq2", redDQ2);
					
					//repeat for blue
					int blue1Z = 0;
					int blue2Z = 0;
					if(blueZone1 > 0) {
						blue1Z = 1;
						blueZone1--;
					} else if(blueZone2 > 0) {
						blue1Z = 2;
						blueZone2--;
					} else if(blueZone3 > 0) {
						blue1Z = 3;
						blueZone3--;
					}
					if(blueZone1 > 0) {
						blue2Z = 1;
					} else if(blueZone2 > 0) {
						blue2Z = 2;
					} else if(blueZone3 > 0) {
						blue2Z = 3;
					}					
					
					boolean blueDQ1 = blue1DQ == 1;
					boolean blueDQ2 = blue2DQ == 1;
					int blueCard1 = blue1yc ?  1 : (blue1DQ == 2 ? 2 : 0);
					int blueCard2 = blue2yc ? 1 : (blue2DQ == 2 ? 2 : 0);
					blue.updateScore("major", blueMajor);
					blue.updateScore("minor", blueMinor);
					blue.updateScore("autoGlyphs", blueAutoGlyphs);
					blue.updateScore("cryptoboxKeys", blueKeys);
					blue.updateScore("jewels", blueJewels);
					blue.updateScore("parkedAuto", blueParked);
					blue.updateScore("glyphs", blueGlyphs);
					blue.updateScore("rows", blueRows);
					blue.updateScore("columns", blueColumns);
					blue.updateScore("ciphers", blueCiphers);
					blue.updateScore("relic1Zone", blue1Z);
					blue.updateScore("relic1Standing", blueUpright >= 1);
					blue.updateScore("relic2Zone", blue2Z);
					blue.updateScore("relic2Standing", blueUpright == 2);
					blue.updateScore("balanced", blueBalanced);
					blue.updateScore("card1", blueCard1);
					blue.updateScore("card2", blueCard2);
					blue.updateScore("dq1", blueDQ1);
					blue.updateScore("dq2", blueDQ2);	
					
					if(match.isElims()) {
						playedElims.add(match);
					} else {
						playedQuals.add(match);
					}
					
				}
				
			//	System.out.println(match+":"+red1+(r1S == 1 ? "*":"")+","+red2+(r2S == 1 ? "*":"")+","+blue1+(b1S == 1 ? "*":"")+","+blue2+(b2S == 1 ? "*":""));
			}
			}catch(Exception e){
				e.printStackTrace();
			}
			//TODO add teams in Set to event if not in it, and display that occurance
			List<Team> teams2 = EventDAO.getTeams(event);
			for(Team t : teams2) {
				teams.remove(t.getNumber());
			}
			for(Integer t : teams) {
				EventDAO.addTeamToEvent(t, event);
				log.info("MISSING TEAM "+t+". AUTOMATICALLY ADDED TO EVENT!");
			}
			
			
			
			scan.close();
			EventDAO.createSchedule(event, matches);
			if(started) {
				//go through all matches that have been played and commit them
				for(Match m : playedQuals) {
					EventDAO.commitScores(event, m);
				}
				//then if alliances exist, create them
				if(alliances.size() > 0) {
					Alliance[] a = new Alliance[4];
					alliances.sort(Comparator.comparingInt(Alliance::getRank));
					a = alliances.toArray(a);
					EventDAO.createAlliances(event, a);
					//generate SF matches.
					
					List<Match> sf = new ArrayList<>(4);
					//Red=1, Blue=4
					sf.add( new Match(1, a[0], a[3], "SF-1-1"));
					//Red=2, Blue=3
					sf.add( new Match(2, a[1], a[2], "SF-2-1"));
					sf.add( new Match(3, a[0], a[3], "SF-1-2"));
					sf.add( new Match(4, a[1], a[2], "SF-2-2"));
					EventDAO.createElimsMatches(event, sf);
				}
				//then if elims have been played, commit them
				for(Match m : playedElims) {
					
					Alliance red = m.getRed();
					Alliance blue = m.getBlue();
					//convert to our numbering system.
					m.setNumber( EventDAO.getElimsMatchNumber(event, m.getName()));
					int[] ranks = EventDAO.getElimsMatchBasic(event,m.getNumber());
					red.setRank(ranks[0]);
					blue.setRank(ranks[1]);
					
					//commit scores should use match name to determine if elims
					if(EventDAO.commitScores(event, m)) {
						handleElimsUpdate(event,m);
					}
				}
				
			}
			
			//calculate rankings
			Event e = Server.activeEvents.get(event);
			if(e != null) {
				e.calculateRankings();
				e.rankingsCache.invalidate();;
				e.resultsCache.invalidate();;
				e.scheduleCache.invalidate();;
			}
			
			response.status(200);
			response.redirect("../");
			halt();
			return "OK";
		};
		
		
		
		public static Route handleRandomizePost = (Request request, Response response) ->{
			String event = request.params("event");
			Event e = Server.activeEvents.get(event);/*
			if(e.getCurrentMatch().isRandomized()){
				response.status(500);
				return "Match already randomized!";
			}
			*/
			Match m = e.isMatchStaged() ? e.getStagedMatch() : e.getCurrentMatch();
			if(m.getStatus() != MatchStatus.PRE_RANDOM && m.getStatus() != MatchStatus.AUTO) {
				response.status(500);
				log.warn("Failed Randomization: Invalid match phase ({}) for {}", m.getStatus(), event);
				return "Invalid match phase.";
			}
			int val = 0;
			try {
				val = Integer.parseInt(request.queryParams("value"));
			}catch(Exception e2) {
				//no param
			}
			
			int r = m.randomize(val);
			synchronized(e.waitForRandomLock) {
				e.waitForRandomLock.notifyAll();
			}
			log.info("Randomized {} to {}", m.getName(), r);
			e.getDisplay().issueCommand(DisplayCommand.SHOW_RANDOM);
			m.setStatus(MatchStatus.AUTO);
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
			if(e.isMatchStaged()) {
				if(!e.getStagedMatch().isRandomized()){
					response.status(500);
					return "Match not randomized!";
				}
			} else if(!e.getCurrentMatch().isRandomized()){
				response.status(500);
				return "Match not randomized!";
			}
			return "{\"rand\":\"" + e.getCurrentMatch().randomize() +"\"}";
		};
		
		public static Route serveHeadRef = (Request request, Response response) -> {
			return render(request, new HashMap<String, Object>(), Path.Template.HEAD_REF);
		};
		
		/**
		 * This endpoint serves the Score tracker page that is used to enter live scores during a match.
		 * It determines the appropriate state to set the score tracker page to: Pre-Match, Auto, Teleop, Review, or Post-Match.
		 * It also pre-fills the pages with any scores already in the system so if the page is refreshed, no data is lost or 
		 * overwritten.
		 */
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
			if (match.getStatus() == MatchStatus.AUTO) {
				model.put("timeLeftInAuto", 30 * 1000 - e.getTimer().elapsed());
			}
			if (match.getStatus() == MatchStatus.TELEOP) {
				model.put("timeLeftInTeleop", 128 * 1000 - e.getTimer().elapsed());
			}
			switch(match.getStatus()){
			case PRE_RANDOM:
				template = Path.Template.REF_PRE_RANDOM;
				break;
			case AUTO:
				//Don't serve auto until after the start of the match.
//				template = e.getTimer().isStarted() ? Path.Template.REF_AUTO : Path.Template.REF_PRE_RANDOM;
				if (e.getTimer().isStarted()) {
					template = Path.Template.REF_AUTO;
					model.put("timeLeftInAuto", Math.max(30 * 1000 - e.getTimer().elapsed(), 0));
				} else {
					template = Path.Template.REF_PRE_RANDOM;
				}
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
				if (a.autoSubmitted()) {
					template = Path.Template.REF_TELEOP;
				} else {
					template = Path.Template.REF_AUTO;
					model.put("timeLeftInAuto", Math.max(30 * 1000 - e.getTimer().elapsed(), 0));
				}
//				template = a.autoSubmitted() ? Path.Template.REF_TELEOP : Path.Template.REF_AUTO;
				break;
			case REVIEW:
				if(!a.autoSubmitted()) {//better hurry up and do that auto
					template = Path.Template.REF_AUTO;
					model.put("timeLeftInAuto", Math.max(30 * 1000 - e.getTimer().elapsed(), 0));
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
		
		public static Route serveIdleRef = (Request request, Response response) ->{
			Map<String, Object> model = new HashMap<>();
			Event e = Server.activeEvents.get(request.params("event"));
			if(e == null) {
				return "Event not active";
			}
			Match match = e.getCurrentMatch();
			if(match == null) {
				return "No active match";
			}
			model.put("match", match.getName());
			model.put("field", (match.getNumber() + 1) % 2 + 1);
			model.put("alliance", request.params("alliance").toUpperCase());
			return render(request, model, Path.Template.REF_IDLE);			
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
			Match m = event.isMatchStaged() ? event.getStagedMatch() : event.getCurrentMatch();
			if(m.isRandomized()){
				return "{\"rand\":\"" + m.getRandomization() +"\"}";
			}
			//Not yet randomized. Wait until it is.
			//TODO some form of timeout? half an hour? - just put in .wait(ms) call
			synchronized(event.waitForRandomLock){
				event.waitForRandomLock.wait();
			}
			return "{\"rand\":\"" + m.getRandomization() +"\"}";
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
			//card carry handled for active my during load, so this call is the one exception
			//that way we dont have to access the DB every time a score changes.
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
				Server.activeEvents.get(e).getCurrentMatch().updateNotify();
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
					Server.activeEvents.get(e).getDisplay().issueCommand(DisplayCommand.IN_REVIEW);
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
				event.getCurrentMatch().updateJewels();
				event.getCurrentMatch().getAlliance(request.params("alliance")).calculateGlyphs();
				event.getCurrentMatch().updateNotify();
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
					
//						System.out.println("AUTO TIME!");
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
			log.info("Starting Match!");
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
		
		public static Route getMatchTime = (Request request, Response response) -> {
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
				response.status(209); //flag that the match is paused
				return event.getTimer().elapsed();
			}
			if(match.getStatus() == MatchStatus.AUTO || match.getStatus() == MatchStatus.TELEOP) {
				response.status(200);
				return event.getTimer().elapsed();
			}
			if (match.getStatus() == MatchStatus.REVIEW) {
				response.status(200);
				return 158 * 1000; //the match is over, just return the end of match time 
			}
			response.status(409);
			return "Match not running!";
		};
		
		public static Route handleTimeoutCommand = (Request request, Response response) ->{
			String event = request.params("event");
			String cmd = request.params("cmd");
			Event e = Server.activeEvents.get(event);
			if(e == null) {
				response.status(400);
				return "Event not active";
			}
			switch(cmd.toUpperCase()) {
				case "FIELD":
					e.getTimer().issueCommand(TimerCommand.FIELD_TO);
					break;
				case "TEAM":
					e.getTimer().issueCommand(TimerCommand.TEAM_TO);
					break;
				case "END":
					e.getTimer().issueCommand(TimerCommand.END_TO);
					break;
				case "SHOW":
					e.getDisplay().issueCommand(DisplayCommand.SHOW_TO);
					break;
				default:
					response.status(400);
					return "Invalid command";
			}
			return "OK";
		};
		
		public static Route handleResetMatch = (Request request, Response response) ->{
			//TODO make sure waitForEnd handles this properly (return error)
			//Same with waitForRefs
			//re-initialize scores!
			//set match status to AUTO!
			//stop timers
			//stop livescore updates ~ send DisplayCOmmand.STOP_SCORING
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
			log.info("Aborting Match!");
			match.getRed().initializeScores();
			match.getBlue().initializeScores();
			e.getTimer().reset();
			e.getDisplay().issueCommand(DisplayCommand.STOP_SCORE_UPDATES);
			synchronized(match.getUpdateLock()) {//prevent duplication of score POSTs
				match.getUpdateLock().notifyAll();
			}
			match.setStatus(MatchStatus.AUTO);
			synchronized(e.getTimer().waitForEndLock) {
				e.getTimer().waitForEndLock.notifyAll();
			}
			
			return "OK";
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
					if(e.getCurrentMatch().isElims()) {
						e.fillCardCarry(e.getCurrentMatch());
					}
					return e.getCurrentMatch().getScoreBreakdown();
				//}
			}
			response.status(409);
			return "Not in review phase!";
		};
		
		private static void handleEndSF(String event, List<MatchResult> sf1, List<MatchResult> sf2) {
			int red1 = 0;
			int blue1 = 0;
			int red2 = 0;
			int blue2 = 0;
			for(MatchResult mr : sf1) {
				if(mr.getStatus() == 1) {
					if(mr.getWinChar() == 'R')red1++;
					if(mr.getWinChar() == 'B')blue1++;
				}
			}
			for(MatchResult mr : sf2) {
				if(mr.getStatus() == 1) {
					if(mr.getWinChar() == 'R')red2++;
					if(mr.getWinChar() == 'B')blue2++;
				}
			}
			if((red2 >= 2 || blue2 >= 2) && (red1 >=2 || blue1 >= 2)){
				//SF complete
				log.info("Semifinals complete!");
				//red = winner of SF1
				//blue = winner of SF2
				Alliance red = new Alliance(red1 >= 2 ? 1 : 4);
				Alliance blue = new Alliance(red2 >= 2 ? 2 : 3);
				int max1 = sf1.stream().mapToInt(MatchResult::getNumber).max().getAsInt();//series.stream().max(Comparator.comparingInt(MatchResult::getNumber)).get().getNumber();
				int max2 = sf2.stream().mapToInt(MatchResult::getNumber).max().getAsInt();
				int f = Math.max(max1, max2);
				//get nex number where n % field count = 1
				//assert 2 field. find ext odd number
				f++;
				if(f%2 == 0)f++;
				Match f1 = new Match(f, red, blue, "F-1" );
				Match f2 = new Match(f+2, red, blue, "F-2" );
				List<Match> finals = new ArrayList<Match>(2);
				finals.add(f1);
				finals.add(f2);
				EventDAO.createElimsMatches(event, finals);
			}
		}
		
		//call then on commit or commit edit of elims matches 
		private static void handleElimsUpdate(String event, Match match) {
			//GOTTA GET THE MATCH submitted
			String name = match.getName();
			String prefix = name.substring(0, name.lastIndexOf("-"));
			//check for series victory.
			List<MatchResult> series = EventDAO.getSeriesResults(event, prefix);
			int redWin = 0;
			int blueWin = 0;
			int unplayed = 0;
			int cancelled = 0;
			for(MatchResult mr : series) {
				if(mr.getStatus() == 1) {
					if(mr.getWinChar() == 'R')redWin++;
					if(mr.getWinChar() == 'B')blueWin++;
				}
				if(mr.getStatus() == 0) {
					unplayed++;
				}
				if(mr.getStatus() == 2) {
					cancelled++;
				}
			}
			
			//This is built to handle weird stuff, out of order, and crazy match editing.
			//if making finals matches, get highest match number and use next odd number.
			if(redWin >= 2 || blueWin >= 2) {
				//handle red advance if SF
				//if more matches scheduled, cancel them
				if(unplayed > 0) {
					//cancel all future unplayed matches.
					for(MatchResult m : series) {
						if(m.getStatus() == 0) {
							EventDAO.cancelMatch(event, m.getNumber());
						}
					}
				}
				
				if(prefix.startsWith("SF-")) {
					char sf = prefix.charAt(3);
					char otherSF = sf == '1' ? '2' : '1'; //get other SF to check if its done.
					List<MatchResult> other = EventDAO.getSeriesResults(event, "SF-"+otherSF);
					if(sf == '1') {
						handleEndSF(event, series, other);
					} else {
						handleEndSF(event, other, series);
					}
				}
			} else {
				//if more matches of status = 2, were fine
				//if more matches but cancelled, uncancel them (this would only happen if edit caused them to be cancelled)
				//if no more matches, create another match
				//add 2 to this match number for next match!
				//(really, add # of fields to the match number)
				if(unplayed == 0) {
					if(cancelled > 0) {
						//uncancel first cancelled match for this series.
						for(MatchResult mr : series) {
							if(mr.getStatus() == 2) {
								//uncancel
								EventDAO.uncancelMatch(event, mr.getNumber());
								break;
							}
						}
					} else {
						//create new match
						MatchResult last = series.stream().max(Comparator.comparingInt(MatchResult::getNumber)).get();
						int num = Integer.parseInt(name.substring(name.lastIndexOf("-")+1));
						int dif = last.getNumber() - match.getNumber();
						num += dif / 2; //this should be the last part of the name of the last match. 
						//needs to be + no fields
						Match m = new Match(last.getNumber() + 2, match.getRed(), match.getBlue(), prefix+"-"+(num+1) );
						List<Match> matches = new ArrayList<>(1);
						matches.add(m);
						log.info("Creating Match "+m.getName());
						EventDAO.createElimsMatches(event, matches);
					}
				}
				
			}
		}
		
		
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
				//TODO for elims, dont do rank check, do series record check & generate new matches or cancel matches
				if(e.getCurrentMatch().isElims()) {
					handleElimsUpdate(event, e.getCurrentMatch());	
					Alliance red = match.getRed();
					Alliance blue = match.getBlue();
					MatchResult res = new MatchResult(match.getNumber(), red, blue,  red.getLastScore(),blue.getLastScore(), 1,blue.isAllianceRedCard() ? 0 : red.getPenaltyPoints(),  red.isAllianceRedCard() ? 0 : blue.getPenaltyPoints(), match.getName());
					Display d = e.getDisplay();
					d.lastResult = res;
					//No change in rankings
					d.red1Dif = 0;
					d.red2Dif = 0;
					d.blue1Dif = 0;
					d.blue2Dif = 0;
				} else {
					Alliance red = match.getRed();
					Alliance blue = match.getBlue();
					Display d = e.getDisplay();
					MatchResult mr = new MatchResult(match.getNumber(), red, blue,red.getLastScore(), blue.getLastScore(), 1, red.getPenaltyPoints(), blue.getPenaltyPoints()  );
	
					d.lastResult = mr;
					int red1 = e.getRank(red.getTeam1());
					int red2 = e.getRank(red.getTeam2());
					int blue1 = e.getRank(blue.getTeam1());
					int blue2  = e.getRank(blue.getTeam2());;
					
					try {
						e.calculateRankings();
						//if unranked, show as improvement.
						d.red1Dif = red1 == -1 ? 1 : red1 - e.getRank(red.getTeam1());
						d.red2Dif = red2 == -1 ? 1 : red2 - e.getRank(red.getTeam2());
						d.blue1Dif = blue1 == -1 ? 1: blue1 - e.getRank(blue.getTeam1());
						d.blue2Dif = blue2 == -1 ? 1:blue2 -e.getRank(blue.getTeam2());
					} catch(Exception exc) {
						//something went wrong calculating rankings, so we don't know how they changed
						d.red1Dif = 0;
						d.red2Dif = 0;
						d.blue1Dif = 0;
						d.blue2Dif = 0;
					}
				}
				synchronized(e.waitForCommitLock) {
					e.waitForCommitLock.notifyAll();
				}
				e.getCurrentMatch().setStatus(MatchStatus.POST_COMMIT);
				if(e.isMatchStaged()) { //this will handle the case where finals match not created yet in else.
					e.loadStagedMatch();
				} else {
				e.loadNextMatch();
				}
			}
			return "OK";
		};
		public static Route serveMatchControlPage = (Request request, Response response) ->{
			Map<String, Object> map = new HashMap<String, Object>();
			log.info("Serving Match Control Page");
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
			Match m = e.isMatchStaged() ? e.getStagedMatch() : e.getCurrentMatch();
			log.info("Returning info for match " + m.getName());
			Alliance red = m.getRed();
			Alliance blue = m.getBlue();
			//TODO fix this an dmake it not suck!
			String res = "{";
			res += "\"number\":" + m.getNumber()+",";
			res += "\"name\":\"" + m.getName()+"\",";
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
			if(e.getData().getStatus() == EventData.ELIMS && m.getNumber() > 0) {//not a test match
				res += "\"red3\":"+red.getTeam3() +",";
				res += "\"blue3\":"+blue.getTeam3() +",";
				res += "\"red3Name\":\""+GlobalDAO.getTeamName(red.getTeam3()) +"\",";
				res += "\"blue3Name\":\""+GlobalDAO.getTeamName(blue.getTeam3()) +"\",";
				String prefix = m.getName().substring(0, m.getName().lastIndexOf("-"));
				List<MatchResult> series = EventDAO.getSeriesResults(event, prefix);
				int redWin = 0;
				int blueWin = 0;
				for(MatchResult mr : series) {
					if(mr.getStatus() == 1) {
						if(mr.getWinChar() == 'R')redWin++;
						if(mr.getWinChar() == 'B')blueWin++;
					}
				}				
				res += "\"redWins\":"+redWin+",";
				res += "\"blueWins\":"+blueWin+",";
				
				//elims cards
				e.fillCardCarry(m);
				res += "\"red1Card\":"+m.getRed().carriesCard()+",";
				res += "\"red2Card\":"+m.getRed().carriesCard()+",";
				res += "\"blue1Card\":"+m.getBlue().carriesCard()+",";
				res += "\"blue2Card\":"+m.getBlue().carriesCard();
				
				/*Old code- same as in fillCardCarry method.
				Map<Integer, List<Integer>> cardMap = EventDAO.getCardsElims(event);
				List<Integer> list = cardMap.get(red.getRank());
				Integer t = list.size() > 0 ? list.get(0) : null;
				res += "\"red1Card\":"+(t!=null && t.intValue()<m.getNumber())+",";
				res += "\"red2Card\":"+(t!=null && t.intValue()<m.getNumber())+",";
				list = cardMap.get(blue.getRank());
				t = list.size() > 0 ? list.get(0) : null;
				res += "\"blue1Card\":"+(t!=null && t.intValue()<m.getNumber())+",";
				res += "\"blue2Card\":"+(t!=null && t.intValue()<m.getNumber());
				*/
			} else {
				//quals cards
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
			
			}
			
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
		
		public static Route handleWaitForStart = (Request request, Response response)->{
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
				synchronized(e.getTimer().waitForStartLock) {
					e.getTimer().waitForStartLock.wait();
				}
			}
			return "OK";
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
				if(m.getStatus() == MatchStatus.AUTO) {
					response.status(409);
					return "ABORTED";
				}
			}
			return "OK";
		};
		
		public static Route handleWaitForCommit = (Request request, Response response)->{
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
			if(m.getStatus() == MatchStatus.POST_COMMIT) {
				return "OK";
			} else {
				synchronized(e.waitForCommitLock) {
					e.waitForCommitLock.wait();
				}
			}
			return "OK";
		};
		
		public static Route serveResultsPage = (Request request, Response response) ->{
			String event = request.params("event");
			Event e = Server.activeEvents.get(event);
			if(e == null){
				return noData(request, "Match");
			}
			Map<String, Object> map = new HashMap<String, Object>();
			List<MatchResult> results = e.resultsCache.get();
			if (results == null) {
				results = EventDAO.getMatchResults(event);
				e.resultsCache.set(results);
			}
			map.put("matches", results);
			map.put("event", event); //TODO get event name from DB
			map.put("eventName", e.getData().getName());
			map.put("teams", EventDAO.getTeams(event));
			return render(request, map, Path.Template.MATCH_RESULT);
		};
		
		public static Route serveTeamResultsPage = (Request request, Response response) ->{
			String event = request.params("event");
			String team = request.params("team");
			Event e = Server.activeEvents.get(event);
			if(e == null){
				return noData(request, "Match");
			}
			Map<String, Object> map = new HashMap<String, Object>();
			List<MatchResult> results = e.resultsCache.get();
			if (results == null) {
				results = EventDAO.getMatchResults(event);
				e.resultsCache.set(results);
			}
			List<MatchResult> r2 = new ArrayList<>(results);
			int target;
			try{
				target = Integer.parseInt(team);
			}catch(Exception e3) {
				response.status(400);
				return "Invalid Team";
			}
			r2.removeIf(mr->{
				Alliance a = mr.getRed();
				if(a.getTeam1() == target)return false;
				if(a.getTeam2() == target) return false;
				if(a.getTeam3() == target) return false;
				a = mr.getBlue();
				if(a.getTeam1() == target)return false;
				if(a.getTeam2() == target) return false;
				if(a.getTeam3() == target) return false;
				return true;
			});
			map.put("matches", r2);
			map.put("event", event);
			map.put("target", team);
			map.put("teamName", GlobalDAO.getTeamName(target));
			return render(request, map, Path.Template.TEAM_MATCH_RESULT);
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
			Map<String, Object> map = new HashMap<>();
			String is43Str = request.queryParams("43");
			String muteStr = request.queryParams("mute");
			String isFlip = request.queryParams("flip");
			String isNoTimer = request.queryParams("notimer");
			
			String code = request.params("event");
			
//			System.out.println("Params: "+adStr+","+is43Str+","+fieldStr+","+muteStr);
			map.put("ad", true);
			map.put("is43", is43Str == null ? false : Boolean.parseBoolean(is43Str));
			map.put("mute", muteStr == null ? false : Boolean.parseBoolean(muteStr));
			map.put("ad2", true);
			map.put("flip", isFlip == null ? false : Boolean.parseBoolean(isFlip));
			map.put("noTimer", isNoTimer == null ? false : Boolean.parseBoolean(isNoTimer));
			map.put("eventCode", code);
			return render(request, map, Path.Template.FIELD_DISPLAY);
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
			Match m = EventDAO.getMatchResultFull(event, match, e.getData().getStatus() >= EventData.ELIMS);
			Alliance red = m.getRed();
			Alliance blue = m.getBlue();
			if(m.isElims()) {
				e.fillCardCarry(m);
			}
			m.getScoreBreakdown();//force score calc
			int redPen = m.isElims() && blue.isAllianceRedCard() ? 0 : red.getPenaltyPoints();
			int bluePen = m.isElims() && red.isAllianceRedCard() ? 0 : blue.getPenaltyPoints();
			MatchResult mr = new MatchResult(m.getNumber(), red, blue,red.getLastScore(), blue.getLastScore(), 1, redPen, bluePen, m.getName()  );
			
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
			} else {
				return noData(request, "Inspection");
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
			try {
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
			}catch(Exception e) {
				return noData(request, "Inspection");
			}
		};
		
		public static Route serveTeamInfo = (Request request, Response response) ->{
			Map<String, Object> model = new HashMap<>();
			String code = request.params("event");
			List<Team> teamList = EventDAO.getTeams(code);
			model.put("teamList", teamList);
			model.put("eventCode", code);
			return render(request, model, Path.Template.TEAM_INFO);
		};

		public static Route serveInspectionOverride = (Request request, Response response) ->{
			String eventCode = request.params("event");
			String form = request.params("form");
			Map<String, Object> map = new HashMap<>();
			map.put("form", form.toLowerCase());
			map.put("eventCode", eventCode);
			Event e = Server.activeEvents.get(eventCode);
			String eventName = "Unknown Event";
			if (e != null) {
				eventName = e.getData().getName();
				map.put("teams", EventDAO.getStatus(eventCode, form));
			}  else {
				return noData(request, "Inspection");
			}
			map.put("eventName", eventName);
			return render(request, map, Path.Template.INSPECTION_OVERRIDE_PAGE);
		};

		public static Route serveFieldDisplay = (Request request, Response response) ->{
			Map<String, Object> map = new HashMap<>();
			String adStr = request.queryParams("ad");
			String is43Str = request.queryParams("43");
			String fieldStr = request.queryParams("field");
			String ad2Str = request.queryParams("ad2");
			String muteStr = request.queryParams("mute");
			String overlayStr = request.queryParams("overlay");
			String colorStr = request.queryParams("color");
			String isNoTimer = request.queryParams("notimer");
			
//			System.out.println("Params: "+adStr+","+is43Str+","+fieldStr+","+muteStr);
			String code = request.params("event");
			String eventName = "";
			if(code != null) {
				Event e = Server.activeEvents.get(code);
				if(e != null) {
					eventName = e.getData().getName();
				}
				if(colorStr == null) {
					colorStr = EventDAO.getProperty(code, "overlayDefault");
				}
			}
			
			map.put("ad", adStr == null ? false : Boolean.parseBoolean(adStr));
			map.put("is43", is43Str == null ? false : Boolean.parseBoolean(is43Str));
			map.put("mute", muteStr == null ? false : Boolean.parseBoolean(muteStr));
			map.put("field", fieldStr == null ? null : (Integer.parseInt(fieldStr)%2));
			map.put("ad2", ad2Str == null ? false : Boolean.parseBoolean(ad2Str));
			map.put("overlay", overlayStr == null ? false : Boolean.parseBoolean(overlayStr));
			map.put("noTimer", isNoTimer == null ? false : Boolean.parseBoolean(isNoTimer));
			map.put("eventCode", code);
			map.put("bgColor", colorStr);
			map.put("eventName", eventName);
			return render(request, map, Path.Template.FIELD_DISPLAY);
		};	
		
		public static Route serveOverlay = (Request request, Response response) ->{
			Map<String, Object> map = new HashMap<>();
			String adStr = request.queryParams("ad");
			String ad2Str = request.queryParams("ad2");
			String muteStr = request.queryParams("mute");
			String colorStr = request.queryParams("color");
			String isNoTimer = request.queryParams("notimer");
			
//			System.out.println("Params: "+adStr+","+is43Str+","+fieldStr+","+muteStr);
			String code = request.params("event");
			String eventName = "";
			if(code != null) {
				Event e = Server.activeEvents.get(code);
				if(e != null) {
					eventName = e.getData().getName();
				}
				if(colorStr == null) {
					colorStr = EventDAO.getProperty(code, "overlayDefault");
				}
			}
			
			map.put("ad", adStr == null ? false : Boolean.parseBoolean(adStr));
			map.put("mute", muteStr == null ? false : Boolean.parseBoolean(muteStr));
			map.put("ad2", false);
			map.put("overlay", true);
			map.put("noTimer", isNoTimer == null ? false : Boolean.parseBoolean(isNoTimer));
			map.put("eventCode", code);
			map.put("bgColor", colorStr);
			map.put("eventName", eventName);
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
			TimerCommand cmd = e.getTimer().blockForNextCommand();
			int match = -1;
			if(e.getCurrentMatch() != null) {
				match = e.getCurrentMatch().getNumber() % 2;
			}
			//TODO add block=false param to retrieve last command.
			return cmd+","+match + ","+e.getTimer().elapsed();
		};
		public static Route handleGetDisplayCommands = (Request request, Response response) ->{
			String event = request.params("event");
			Event e = Server.activeEvents.get(event);
			if(e == null) {
				response.status(400);
				return "Event not active";
			}
			//TODO add block=false param to retrieve last command.
			DisplayCommand cmd = e.getDisplay().blockForNextCommand();
			int match = -1;
			Match mat = e.isMatchStaged() ? e.getStagedMatch() : e.getCurrentMatch();
			if(mat != null) {
				match = mat.getNumber() % 2;
			}
			return cmd +","+match;
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
			if(e.isMatchStaged()) {
				e.loadStagedMatch();
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
			String match = request.params("match");
			boolean elims = match.indexOf('-') >= 0;
			int m = elims ? EventDAO.getElimsMatchNumber(event, match) : Integer.parseInt(match);
			return EventDAO.getMatchResultFull(event, m, elims).getFullScores();
		};
		
		public static Route handleGetEditScorePage = (Request request, Response response) ->{
			HashMap<String, Object> map = new HashMap<>();
			//map.put("match", Integer.parseInt(request.params("match")));
			return render(request, map, Path.Template.EDIT_MATCH_SCORE);
		};
		
		private static Match createMatchObject(Request request, Response response, int m) {			
			Set<String> params = request.queryParams();
			/*Format:
			 * <alliance>_score_<scoreKey> : call updateScore
			 * <alliance>_card_<index>
			 * <alliance>_dq_<index> 
			 */
			//team numbers shouldnt matter for this, except for elims
			int rr = 0;
			int br = 0;
			if(request.params("match").indexOf('-')>=0) {
				int[] r = EventDAO.getElimsMatchBasic(request.params("event"), m);
				rr = r[0];
				br = r[1];
			}
			Alliance red = new Alliance(0,0, rr);
			red.initializeScores();
			Alliance blue = new Alliance(0,0, br);
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
			
			EventDAO.fillRandomizationData(request.params("event"),match);
			return match;
		}
		
		public static Route handleGetEditedScore = (Request request, Response response) ->{
			String event = request.params("event");
			Event e = Server.activeEvents.get(event);			
			if(e == null){
				response.status(500);
				return "Event not active.";
			}
			String ms = request.params("match");
			boolean elims = ms.indexOf("-") >= 0;
			int m = elims ? EventDAO.getElimsMatchNumber(event, ms) : Integer.parseInt(ms);
			Match match = createMatchObject(request, response, m);
			if(elims) {
				match.setName(ms.toUpperCase());
				e.fillCardCarry(match);
			}
			return match.getScoreBreakdown();
		};
		public static Route handleCommitEditedScore = (Request request, Response response) ->{
			String event = request.params("event");
			Event e = Server.activeEvents.get(event);			
			if(e == null){
				response.status(500);
				return "Event not active.";
			}
			String ms = request.params("match");
			boolean elims = ms.indexOf("-") >= 0;
			int m = elims ? EventDAO.getElimsMatchNumber(event, ms) : Integer.parseInt(ms);
			Match match = createMatchObject(request, response, m);
			if(elims) {
				match.setName(ms.toUpperCase());
				
			}
			if(EventDAO.commitScores(event, match)){
				if(elims) {
					handleElimsUpdate(event, match);//this may do weird things!!!!
				} else {
					e.calculateRankings();
				}
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
			
			String ms = request.params("match");
			boolean elims = ms.indexOf('-') >= 0;
			int num = elims ? EventDAO.getElimsMatchNumber(event, ms) : Integer.parseInt(ms);
			Match m = EventDAO.getMatch(event, num, elims);
			String res = "{";
			res += "\"number\":" + m.getNumber()+",";
			res += "\"name\":\""+m.getName() +"\",";
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
				return noData(request, "Rankings");
			}
			Map<String, Object> map = new HashMap<>();
			List<Rank> ranks = e.rankingsCache.get();
			if(ranks == null) {
				ranks = e.getRankings();
				e.rankingsCache.set(ranks);
			}
			map.put("rankings", ranks);
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
				log.info(code + " added to active events.");
				break;
			case 6:
				String event = request.params("event");
				File dir = new File(Server.ARCHIVE_PATH + event + "/");
				if (!dir.exists()) {
					generateArchiveFiles(event, dir);
				}
				break;
			}

			EventDAO.setEventStatus(code, newStatus);
			return "OK";
		};
		
		public static Route serveAddTeam = (Request request, Response response) ->{
			Map<String, Object> model = new HashMap<String, Object>();
			String code = request.params("event");
			List<Team> teamList = EventDAO.getTeams(code);
			model.put("teamList", teamList);
			return render(request, model, Path.Template.MANAGE_EVENT_TEAMS);
		};
		
		public static Route handleAddTeam = (Request request, Response response) ->{
			String code = request.params("event");
			EventData data = EventDAO.getEvent(code);
			if(data.getStatus() < 1 || data.getStatus() > 2) {
				response.status(409);
				return "Not in setup/inspection phase!";
			}
			try {
			int team = Integer.parseInt(request.queryParams("team"));
			if(data.getStatus() == 1 ? EventDAO.addTeamToEvent(team, code) : EventDAO.addTeamLate(team, code)) {
				return "{\"team\":\"" + team + "\",\"name\":\"" + GlobalDAO.getTeamName(team) + "\"}"; 
			}
			response.status(400);
			return "Team already in event";
			}catch(Exception e) {
				response.status(400);
				return "Invalid team number";
			}
		};
		
		public static Route handleRemoveTeam = (Request request, Response response) ->{
			String code = request.params("event");
			EventData data = EventDAO.getEvent(code);
			String[] teams = request.queryParams("teams").split(",");
			if(data.getStatus() < 1) {
				response.status(409);
				return "Not yet in setup phase!";
			}
			try {
				
				for (String team : teams) {
					if(EventDAO.removeTeamFromEvent(Integer.parseInt(team), code)) {
					}
				}
				response.status(200);
				return "OK";
			}catch(Exception e) {
				response.status(400);
				return "ERROR";
			}
		};
		
		public static Route handleEditTeam = (Request request, Response response) ->{
			String code = request.params("event");
			EventData data = EventDAO.getEvent(code);
			String team = request.queryParams("team");
			String newName = request.queryParams("name");
			String newLocation = request.queryParams("location");
			if(data.getStatus() < 1) {
				response.status(409);
				return "Not yet in setup phase!";
			}
			try {
				GlobalDAO.editTeamName(Integer.parseInt(team), newName, newLocation);
				
				response.status(200);
				return "OK";
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
			String colorStr = EventDAO.getProperty(code, "overlayDefault");
			if(colorStr == null) {
				colorStr = "FF00FF";
			}
			map.put("color", colorStr);
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
			List<String> list = new ArrayList<>(20);
			
			list.add(json("number", mr.getNumber()));
			list.add(json("name", mr.getName()));
			
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
			
			Map<Integer, Team> teams = e.teamNameCache.get();
			if(teams == null) {
				List<Team> l= EventDAO.getTeams(code);
				teams = new HashMap<>();
				for(Team t : l) {
					teams.put(t.getNumber(), t);
				}
				e.teamNameCache.set(teams);
			}
			list.add(json("red1Name", teams.get(red.getTeam1()).getName()));
			list.add(json("red2Name", teams.get(red.getTeam2()).getName()));
			list.add(json("blue1Name", teams.get(blue.getTeam1()).getName()));
			list.add(json("blue2Name", teams.get(blue.getTeam2()).getName()));
			
			list.add(json("red1", red.getTeam1()));
			list.add(json("red2", red.getTeam2()));
			list.add(json("blue1", blue.getTeam1()));
			list.add(json("blue2", blue.getTeam2()));
			
			
			
			
			
			int redCard1 = Integer.parseInt(red.getScore("card1").toString());
			int redCard2 = Integer.parseInt(red.getScore("card2").toString());
			int blueCard1 = Integer.parseInt(blue.getScore("card1").toString());
			int blueCard2 = Integer.parseInt(blue.getScore("card2").toString());
			
			if(mr.isElims()) {
				//team3
				list.add(json("red3", red.getTeam3()));
				list.add(json("blue3", blue.getTeam3()));
				

				//TODO check 2-team alliances?
				Team red3 = teams.get(red.getTeam3());
				Team blue3 = teams.get(blue.getTeam3());
				list.add(json("red3Name", red3 == null ? "" : red3.getName()));
				list.add(json("blue3Name", blue3 == null ? "" : blue3.getName()));
				
				//put seeds here
				list.add(json("redRank", mr.getRed().getRank()));
				list.add(json("blueRank", mr.getBlue().getRank()));
				
				String isFDStr = EventDAO.getProperty(code, "isFinalsDivision");
				boolean isFD = false;
				if(isFDStr != null && isFDStr.equals("true")) {
					isFD = true;					
				}
				
				//get card info for elims - done by alliance, only set card1 (sent below if)
				Map<Integer, List<Integer>> cardMap = EventDAO.getCardsElims(code);
				List<Integer> cardList = cardMap.get(red.getRank());
				if(isFD && EventDAO.getProperty(code, "redCard").equals("true")) {
					cardList.add(0);
					Collections.sort(cardList); //this is incase the card is added retroactively
				}
				if(redCard1 == 1 && cardList.size()>0 && cardList.get(0) < mr.getNumber()) {
					redCard1 = 3;
					redCard2 = 3;
				}
				cardList = cardMap.get(blue.getRank());
				if(isFD && EventDAO.getProperty(code, "blueCard").equals("true")) {
					cardList.add(0);
					Collections.sort(cardList);
				}
				if(blueCard1 == 1 && cardList.size()>0 && cardList.get(0) < mr.getNumber()) {
					blueCard1 = 3;
					blueCard2 = 3;
				}
				
				
				//get series results
				String name = mr.getName();
				String prefix = name.substring(0, name.lastIndexOf("-"));
				//check for series victory.
				List<MatchResult> series = EventDAO.getSeriesResults(code, prefix);
				int redWin = 0;
				int blueWin = 0;
				for(MatchResult r : series) {
					if(r.getStatus() == 1) {
						if(r.getWinChar() == 'R')redWin++;
						if(r.getWinChar() == 'B')blueWin++;
					}
				}
				list.add(json("redWins", redWin));
				list.add(json("blueWins", blueWin));
			} else {
				Map<Integer, List<Integer>> cardMap = EventDAO.getCardsForTeams(code, red.getTeam1(), red.getTeam2(), blue.getTeam1(), blue.getTeam2());
				
				list.add(json("red1Rank", e.getRank(red.getTeam1())));
				list.add(json("red2Rank", e.getRank(red.getTeam2())));
				list.add(json("blue1Rank", e.getRank(blue.getTeam1())));
				list.add(json("blue2Rank", e.getRank(blue.getTeam2())));
				
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
				
				
			}
			
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
		
		/**
		 * match param is match name if elims
		 */
		public static Route handleGetFullScoresheet = (Request request, Response response) -> {
			String event = request.params("event");
			boolean plain = request.pathInfo().endsWith("plain/");
			String ms = request.params("match");
			String page = getFullScoresheet(request, event, ms, plain);
			if (page == null) {
				return DefaultPages.notFound.handle(request, response);
			}
			return page;
		};
		
		public static String getFullScoresheet(Request request, String event, String ms, boolean plain) {
			Map<String, Object> map = new HashMap<String, Object>();
			Event e = Server.activeEvents.get(event);
			if(e == null) {
				return null;
			}

			int m = 0;
			boolean elims = ms.indexOf('-') >= 0;
			m = elims ? EventDAO.getElimsMatchNumber(event, ms) : Integer.parseInt(ms);
			Match match = EventDAO.getMatchResultFull(event, m, elims);//.getFullScores();
			if (match == null) {
				return null;
			}
			List<Match> schedule = e.scheduleCache.get();
			if(schedule == null) {
				schedule = EventDAO.getSchedule(event);
				e.scheduleCache.set(schedule);
			}
			if (match.isElims()) {
				for (int i = 0; i < schedule.size(); i++) {
					if (schedule.get(i).getName().toLowerCase().equals(ms)) {
						if (i > 0) {
							Match prev = schedule.get(i - 1);
							if (prev.isElims()) {
								map.put("prevMatch", schedule.get(i - 1).getName().toLowerCase());
							} else {
								map.put("prevMatch", schedule.get(i - 1).getNumber());
							}
						}
						if (i < schedule.size() - 1) {
							map.put("nextMatch", schedule.get(i + 1).getName().toLowerCase());
						}
						break;
					}
				}
			} else {
				for (int i = 0; i < schedule.size(); i++) {
					if (schedule.get(i).getNumber() == m) {
						if (i > 0) {
							map.put("prevMatch", schedule.get(i - 1).getNumber());
						}
						if (i < schedule.size() - 1) {
							Match next = schedule.get(i + 1);
							if (next.isElims()) {
								map.put("nextMatch", schedule.get(i + 1).getName().toLowerCase());
							} else {
								map.put("nextMatch", schedule.get(i + 1).getNumber());
							}
						}
						break;
					}
				}
			}
			if(elims) {
				e.fillCardCarry(match);
			}
			match.getScoreBreakdown();
			map.put("redScore", Integer.parseInt(match.redScoreBreakdown.get("score")));
			map.put("blueScore", Integer.parseInt(match.blueScoreBreakdown.get("score")));
			map.put("redBreakdown", match.redScoreBreakdown);
			map.put("blueBreakdown", match.blueScoreBreakdown);
			map.put("matchNumber", match.getNumber());
			map.put("fieldNumber", (match.getNumber() +1 ) % 2 + 1);
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
			if (plain) {
				return render(request, map, Path.Template.FULL_SCORESHEET_PLAIN);
			} else {
				return render(request, map, Path.Template.FULL_SCORESHEET);
			}
		}
		
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
			String ms = request.params("match");
			int m = 0;
			boolean elims = ms.indexOf('-') >= 0;
			m = elims ? EventDAO.getElimsMatchNumber(event, ms) : Integer.parseInt(ms);
			Match match = EventDAO.getMatchResultFull(event, m, elims);//.getFullScores();
			if (match == null) {
				return DefaultPages.notFound.handle(request, response);
			}
			if(elims) {
				e.fillCardCarry(match);
			}
			match.getScoreBreakdown();
			//taken from https://stackoverflow.com/questions/2779251/how-can-i-convert-json-to-a-hashmap-using-gson
			Type type = new TypeToken<Map<String, String>>(){}.getType();
			Gson gson = new Gson();
		
			map.put("redBreakdown", gson.fromJson(match.getScoreBreakdown(match.getRed()), type));
			map.put("blueBreakdown", gson.fromJson(match.getScoreBreakdown(match.getBlue()), type));
			map.put("matchNumber", match.getNumber());
			map.put("fieldNumber", (match.getNumber() +1 ) % 2 + 1);
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

		public static Route serveAllianceUploadPage = (Request request, Response response) ->{
			return render(request, new HashMap<String, Object>(), Path.Template.UPLOAD_ALLIANCES);
		};

		public static Route handleAllianceUpload = (Request request, Response response) ->{

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
			 	if(data.getStatus() != EventData.SELECTION) {
			 		response.status(409);
			 		return "Not in selection phase!";
			 	}
				Part p = request.raw().getPart("file");
				Scanner scan = new Scanner(p.getInputStream());
				scan.useDelimiter("\\||\n");
				Alliance[] alliances = new Alliance[4];
				try{
					int rank = 0;
					while(scan.hasNextLine()){
						if(rank > 3) {
							System.err.println("More than 4 alliances not supported.");
							break;
						}
						scan.nextInt();
						//always in order?
						int alliance = scan.nextInt();
						scan.nextInt();
						int t1 = scan.nextInt();
						int t2 = scan.nextInt();
						String s  =scan.nextLine();
						int t3 = Integer.parseInt(s.substring(1));
						alliances[rank] = new Alliance(rank + 1, t1, t2, t3);
						rank++;
						
					}
				}catch(Exception e){
					e.printStackTrace();
				}
				scan.close();
				EventDAO.createAlliances(event, alliances);
				//now, generate SF1-1,2-1,1-2,and 2-2.
				List<Match> matches = new ArrayList<>(4);
				//Red=1, Blue=4
				matches.add( new Match(1, alliances[0], alliances[3], "SF-1-1"));
				//Red=2, Blue=3
				matches.add( new Match(2, alliances[1], alliances[2], "SF-2-1"));
				matches.add( new Match(3, alliances[0], alliances[3], "SF-1-2"));
				matches.add( new Match(4, alliances[1], alliances[2], "SF-2-2"));
				EventDAO.createElimsMatches(event, matches);
				response.status(200);
				response.redirect("../");
				halt();
				return "OK";
		};
		
		public static Route handleStartSelection = (Request request, Response response) ->{
			String event = request.params("event");
			Event e = Server.activeEvents.get(event);
			if(e == null) {
				response.status(400);
				return "Event not active!";
			}
			if(EventDAO.getEvent(event).getStatus() < EventData.SELECTION) {
				EventDAO.setEventStatus(event, EventData.SELECTION);
				e.getData().setStatus(EventData.SELECTION);
			}
			e.getSelectionManager().init();
			return e.getSelectionManager().getSelectionJSON();
		};
		public static Route handleSelection = (Request request, Response response) ->{
			String event = request.params("event");
			String team = request.queryParams("team");
			String op = request.queryParams("op");
			int opCode = 0;
			switch(op) {
			case "SELECT":opCode = Selection.ACCEPT;break;
			case "DECLINE":opCode = Selection.DECLINE;break;
			default:
				response.status(400);
				return "Invalid operation!";
			}
			Event e = Server.activeEvents.get(event);
			if(e == null) {
				response.status(400);
				return "Event not active!";
			}
			if(e.getData().getStatus() < EventData.SELECTION) {
				response.status(400);
				return "Not in Aliance Selection!";
			}
			try {
				e.getSelectionManager().createAndExecute(Integer.parseInt(team), opCode);
			}catch(NumberFormatException e1) {
				response.status(400);
				return "Invalid team number!";
			}
			return e.getSelectionManager().getSelectionJSON();
		};
		public static Route handleUndoSelection = (Request request, Response response) ->{
			String event = request.params("event");
			Event e = Server.activeEvents.get(event);
			if(e == null) {
				response.status(400);
				return "Event not active!";
			}
			if(e.getData().getStatus() < EventData.SELECTION) {
				response.status(400);
				return "Not in Aliance Selection!";
			}
			e.getSelectionManager().undoSelection();
			return e.getSelectionManager().getSelectionJSON();
		};
		public static Route handleClearSelection = (Request request, Response response) ->{
			String event = request.params("event");
			Event e = Server.activeEvents.get(event);
			if(e == null) {
				response.status(400);
				return "Event not active!";
			}
			if(e.getData().getStatus() < EventData.SELECTION) {
				response.status(400);
				return "Not in Aliance Selection!";
			}
			e.getSelectionManager().clearSelection();
			return e.getSelectionManager().getSelectionJSON();
		};
		public static Route handleSaveSelection = (Request request, Response response) ->{
			String event = request.params("event");
			Event e = Server.activeEvents.get(event);
			if(e == null) {
				response.status(400);
				return "Event not active!";
			}
			if(e.getData().getStatus() < EventData.SELECTION) {
				response.status(400);
				return "Not in Aliance Selection!";
			}
			e.getSelectionManager().saveSelection();
			return e.getSelectionManager().getSelectionJSON();
		};
		public static Route handleGetSelectionData = (Request request, Response response) ->{
			String event = request.params("event");
			Event e = Server.activeEvents.get(event);
			if(e == null) {
				response.status(400);
				return "Event not active!";
			}
			if(e.getData().getStatus() < EventData.SELECTION) {
				response.status(400);
				return "Not in Aliance Selection!";
			}
			return e.getSelectionManager().getSelectionJSON();
		};

		public static Route serveStats = (Request request, Response response) ->{
			String event = request.params("event");
			Event e = Server.activeEvents.get(event);
			if(e == null) {
				return noData(request, "Stats");
			}
			HashMap<String, Object> map = new HashMap<>();
			map.put("stats", e.getTeamStats());
			map.put("quals", e.getQualsStats());
			map.put("elims", e.getElimsStats());
//			map.put("jsonStats", new Gson().toJson(map));
			return render(request, map, Path.Template.STATS);
		};

		
		private static String noData(Request request, String dat) {
			HashMap<String, Object> map = new HashMap<String, Object>();
			map.put("msg", dat + " data not available at this time. Check back later!");
			return render(request, map, Path.Template.NO_DATA);
		}
		
		public static Route serveQueuePage = (Request request, Response response) ->{
			String event = request.params("event");
			Event e = Server.activeEvents.get(event);
			if(e == null) {
				return noData(request, "Queue");
			}
			List<MatchResult> cachePointer = e.resultsCache.get();
			if(cachePointer == null) {
				cachePointer = EventDAO.getMatchResults(event);
				e.resultsCache.set(cachePointer);
			}
			List<MatchResult> results = new ArrayList<>(cachePointer);
			results.removeIf(mr->mr.getStatus() > 0);
			int num = 3;
			int fields = 2;
			String numS = request.queryParams("num");
			String fieldS = request.queryParams("fields");
			if(numS != null) {
				try {
					num = Integer.parseInt(numS) + 2; //num in queue + on field + up nxt
				} catch(Exception e2) {}
			}
			if(fieldS != null) {
				try {
					fields= Integer.parseInt(fieldS);
				} catch(Exception e2) {}
			}
			
			List<ArrayList<MatchResult>> queues = new ArrayList<>(fields);
			for(int i = 0; i < fields; i++) {
				queues.add(new ArrayList<MatchResult>());
			}
			//this is inefficient, but n should always be very small.
			final int numC = num;
			for(int i = 0; queues.stream().mapToInt(q->{return q.size()<numC?1:0;}).sum() > 0 && i < results.size();i++) {
				MatchResult mr = results.get(i);
				queues.get((mr.getNumber() + 1) % fields).add(mr);
			}
			
			
//			for(int i = 0; i < fields * (num + 1) && i < results.size(); i++) {
//				MatchResult
//				queue[i % fields][i / fields] = results.get(i);
//			}
			
			Map<String, Object> map = new HashMap<>();
			map.put("queues", queues);
			map.put("num", num);
			return render(request, map, Path.Template.QUEUE_DISPLAY);
		};

		public static Route serveZipFile = (Request request, Response response) -> {
			String event = request.params("event");
			File dir = new File(Server.ARCHIVE_PATH + event + "/");
			if (!dir.exists()) {
				generateArchiveFiles(event, dir);
			}
			ServerPages.zipDirectory(dir, response);
			return "";
		};
		
		public static void generateArchiveFiles(String event, File dir) throws IOException {
			FileUtils.forceMkdir(dir);
			File scoreDir = new File(dir.getPath() + "/scoresheets/");
			FileUtils.forceMkdir(scoreDir);
			File inspectDir = new File(dir.getPath() + "/inspectionForms/");
			FileUtils.forceMkdir(inspectDir);
			File hwDir = new File(inspectDir.getPath() + "/hw/");
			FileUtils.forceMkdir(hwDir);
			File swDir = new File(inspectDir.getPath() + "/sw/");
			FileUtils.forceMkdir(swDir);
			File fdDir = new File(inspectDir.getPath() + "/fd/");
			FileUtils.forceMkdir(fdDir);
			
			List<Team> teams = EventDAO.getTeams(event);
			for (Team team : teams) {
				File hw = new File(hwDir.getPath() + "/"  + team.getNumber() + "-hw.html");
				hw.createNewFile();
				FileUtils.writeStringToFile(hw, inspectionPage(null, null, true, event, "HW", String.valueOf(team.getNumber()), null, true));
				File sw = new File(swDir.getPath() + "/"  + team.getNumber() + "-sw.html");
				sw.createNewFile();
				FileUtils.writeStringToFile(sw, inspectionPage(null, null, true, event, "SW", String.valueOf(team.getNumber()), null, true));
				File fd = new File(fdDir.getPath() + "/" + team.getNumber() + "-fd.html");
				fd.createNewFile();
				FileUtils.writeStringToFile(fd, inspectionPage(null, null, true, event, "FD", String.valueOf(team.getNumber()), null, true));
			}
			//super fake work around bc we need the event to be "active" to pull scoresheets
			Server.activeEvents.put(event, new Event(EventDAO.getEvent(event)));
			List<MatchResult> results = EventDAO.getMatchResults(event);
			for (MatchResult match : results) {
				String name;
				if (match.isElims()) {
					name = match.getName();
				} else {
					name = String.valueOf(match.getNumber());
				}
				File scoresheet = new File(scoreDir.getPath() + "/" + name + ".html");
				scoresheet.createNewFile();
				FileUtils.writeStringToFile(scoresheet, getFullScoresheet(null, event, name, true));
			}
			File img = new File(scoreDir.getPath() + "/img");
			img.mkdir();
			img = new File(scoreDir.getPath() + "/img/logo.png");
			img.createNewFile();
			File imgOrig = new File(Server.publicDir + "/img/logo.png");
			FileUtils.copyFile(imgOrig, img);
			img = new File(scoreDir.getPath() + "/img/game-logo.png");
			img.createNewFile();
			imgOrig = new File(Server.publicDir + "/img/game-logo.png");
			FileUtils.copyFile(imgOrig, img);
			Server.activeEvents.remove(event);
		}
		public static Route serveResultsNamePage = (Request request, Response response) ->{
			String event = request.params("event");
			Event e = Server.activeEvents.get(event);
			if(e == null){
				return noData(request, "Match");
			}
			Map<String, Object> map = new HashMap<String, Object>();
			List<MatchResult> results = e.resultsCache.get();
			if (results == null) {
				results = EventDAO.getMatchResults(event);
				e.resultsCache.set(results);
			}
			map.put("matches", results);
			map.put("event", event); //TODO get event name from DB
			map.put("eventName", e.getData().getName());
			Map<Integer,Team> teams;
			if((teams = e.teamNameCache.get()) == null) {
				List<Team> list = EventDAO.getTeams(event);
				teams = new HashMap<>();
				for(Team t : list) {
					teams.put(t.getNumber(), t);
				}
				e.teamNameCache.set(teams);
			}
			map.put("teams", teams);
			return render(request, map, Path.Template.MATCH_RESULT_NAME);
		};

		public static Route serveDivisonUploadPage = (Request request, Response response) ->{
			return render(request, new HashMap<String, Object>(), Path.Template.DIVISION_WINNER_UPLOAD);
		};

		public static Route handleDivisionUpload  = (Request request, Response response) ->{
			//Take team numbers and generate Finals matches:
			/*1. Create event database,
			 *2. Add teams
			 *3. Initialize event tables
			 *4.  Add event to active events.
			 *5. Generate finals matches
			 *6. Set event to "Elims"
			 *7. return redirect to manage page. Make sure elims manage page has option to edit team info
			 */
			int red1, red2, red3, blue1, blue2, blue3;
			boolean redCard, blueCard;
			try {
				red1 = Integer.parseInt(request.queryParamOrDefault("red1", "0"));
				red2 = Integer.parseInt(request.queryParamOrDefault("red2", "0"));
				red3 = Integer.parseInt(request.queryParamOrDefault("red3", "0"));
				blue1 = Integer.parseInt(request.queryParamOrDefault("blue1", "0"));
				blue2 = Integer.parseInt(request.queryParamOrDefault("blue2", "0"));
				blue3= Integer.parseInt(request.queryParamOrDefault("blue3", "0"));
				redCard = request.queryParamOrDefault("redCard", "false").equals("on");
				blueCard = request.queryParamOrDefault("blueCard", "false").equals("on");
			}catch(NumberFormatException e) {
				response.status(400);
				return "Invalid Team Numbers!";
			}
			
			String code = request.params("event");
			EventDAO.createEventDatabase(code);
			EventDAO.addTeamToEvent(red1, code);
			EventDAO.addTeamToEvent(red2, code);
			EventDAO.addTeamToEvent(red3, code);
			EventDAO.addTeamToEvent(blue1, code);
			EventDAO.addTeamToEvent(blue2, code);
			EventDAO.addTeamToEvent(blue3, code);
			
			EventDAO.setProperty(code, "isFinalsDivision", "true");
			EventDAO.setProperty(code, "redCard", Boolean.toString(redCard));
			EventDAO.setProperty(code, "blueCard", Boolean.toString(blueCard));
			Alliance red = new Alliance(1, red1, red2, red3);
			Alliance blue = new Alliance(2, blue1, blue2, blue3);
			Alliance[] alliances = new Alliance[2];
			alliances[0] = red;
			alliances[1] = blue;
			EventDAO.createAlliances(code, alliances);
			Match f1 = new Match(1, red, blue, "IF-1");
			Match f2 = new Match(2, red, blue, "IF-2");
			List<Match> matches = new ArrayList<>();
			matches.add(f1);
			matches.add(f2);
			EventDAO.createElimsMatches(code, matches);
			EventDAO.setEventStatus(code, EventData.ELIMS);
			Server.activeEvents.get(code).getData().setStatus(EventData.ELIMS);
			
			response.redirect("../teams"); 
			return "OK";
		};
		
		public static Route handleImportTeamList = (Request request, Response response) ->{
			String event = request.params("event");
			List<Team> teams = ServerPages.parseTeamFile(request, response);
			int added = GlobalDAO.addNewTeams(teams, true);
			int added2 = EventDAO.importTeamList(event, teams );
			if(added == -1 || added2 == -1) {
				response.status(500);
				return "Error adding teams";
			}
			return added +" teams added to team list, "+added2 +" teams added to event.";
		};
		
		/**
		 * Endpoint to render the Event Settings page.
		 */
		public static Route serveEventSettings = (Request request, Response response) ->{
			String event = request.params("event");
			EventData data = EventDAO.getEvent(event);
			String colorStr = EventDAO.getProperty(event, "overlayDefault");
			if(colorStr == null) {
				colorStr = "FF00FF";
			}
			Map<String, Object> map = new HashMap<>();
			map.put("code", event);
			map.put("name", data.getName());
			map.put("color", colorStr);
			map.put("phase", data.getStatus());
			return render(request, map, Path.Template.EVENT_SETTINGS);
		};
		
		/**
		 * Endpoint to set the default background color of the Overlay Display. The default color
		 * is stored in the "overlayDefault" entry in the event preferences table. This operation is
		 * not guaranteed to work on events created before v2.3.
		 * @since v2.3
		 */
		public static Route handleOverlayColorSet = (Request request, Response response)->{
			String event = request.params("event");
			String color = request.queryParams("color");
			EventDAO.setProperty(event, "overlayDefault", color);
			return "OK";
		};
		public static Route handleDeleteData = (Request request, Response response)->{
			String event = request.params("event");
			String phase = request.params("phase");
			if(phase.equals("inspection")) {
				
			} else if(phase.equals("quals")) {
				EventDAO.deleteQuals(event);
				EventDAO.setEventStatus(event, EventData.INSPECTION);
				Server.activeEvents.get(event).calculateRankings();
				Server.activeEvents.get(event).rankingsCache.invalidate();
				Server.activeEvents.get(event).resultsCache.invalidate();
				Server.activeEvents.get(event).scheduleCache.invalidate();
			} else if(phase.equals("elims")) {
				EventDAO.deleteElims(event);
				EventDAO.setEventStatus(event, EventData.QUALS);
				Server.activeEvents.get(event).resultsCache.invalidate();
				Server.activeEvents.get(event).scheduleCache.invalidate();
			}
			return "OK";
		};
		
		public static Route handleDeleteMatches = (Request request, Response response)->{
			String event = request.params("event");
			String modResult = request.params("mod");
			int mod = -1;
			if(modResult != null) {
				mod = Integer.parseInt(modResult);
			}
			EventDAO.deleteMatches(event, mod);
			Server.activeEvents.get(event).rankingsCache.invalidate();
			Server.activeEvents.get(event).resultsCache.invalidate();
			Server.activeEvents.get(event).scheduleCache.invalidate();
			return "OK";
		};
}
