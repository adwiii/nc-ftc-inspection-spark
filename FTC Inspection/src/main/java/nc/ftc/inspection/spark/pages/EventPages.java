package nc.ftc.inspection.spark.pages;

import nc.ftc.inspection.dao.EventDAO;
import nc.ftc.inspection.model.Event;
import nc.ftc.inspection.model.FormRow;
import nc.ftc.inspection.spark.util.Path;
import static nc.ftc.inspection.spark.util.ViewUtil.render;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
		Event event = EventDAO.getEvent(eventCode);
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
		return EventDAO.getStatus(event);
	};
	
}
