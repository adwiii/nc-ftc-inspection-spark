package nc.ftc.inspection.spark.pages;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.eclipse.jetty.http.HttpStatus;

import nc.ftc.inspection.dao.EventDAO;
import nc.ftc.inspection.model.EventData;
import nc.ftc.inspection.spark.util.Path;
import spark.Request;
import spark.Response;
import spark.Route;
import static spark.Spark.halt;
import static nc.ftc.inspection.spark.util.ViewUtil.render;

public class DefaultPages {

	public static Route indexPage = (req, res) -> {
		HashMap<String, Object> map = new HashMap<String, Object>();
		map.put("currTime", System.currentTimeMillis());
		List<EventData> events = EventDAO.getEvents();
		Collections.sort(events);
		List<EventData> todayEvents = new ArrayList<EventData>();
		List<EventData> nextWeekEvents = new ArrayList<EventData>();
		Calendar todayBegin = Calendar.getInstance();
		todayBegin.set(Calendar.HOUR_OF_DAY, 23);
		todayBegin.set(Calendar.MINUTE, 59);
		todayBegin.add(Calendar.DATE, -1);
		Calendar todayEnd = Calendar.getInstance();
		todayEnd.set(Calendar.HOUR_OF_DAY, 23);
		todayEnd.set(Calendar.MINUTE, 59);
		Calendar nextWeek = (Calendar) todayEnd.clone();
		nextWeek.add(Calendar.DATE, 7);
		for (EventData event : events) {
			if (event.getDate() == null) continue;
			Calendar check = Calendar.getInstance();
			check.setTimeInMillis(event.getDate().getTime());
			if (todayBegin.before(check) && todayEnd.after(check)) {
				todayEvents.add(event);
			} else if (todayEnd.before(check) && nextWeek.after(check)) {
				nextWeekEvents.add(event);
			}
		}
		map.put("today", todayEvents.size() > 0);
		map.put("todayEvents", todayEvents);
		map.put("nextWeek", nextWeekEvents.size() > 0);
		map.put("nextWeekEvents", nextWeekEvents);
		map.put("allEvents", events);
		return render(req, map , Path.Template.INDEX)	;
	};

	public static Route ipPage = (req, res) -> {
		return render(req, Path.Template.IP_PAGE);
	};

	public static Route notAcceptable = (Request request, Response response) -> {
		response.status(HttpStatus.NOT_ACCEPTABLE_406);
		return "No suitable content found. Please specify either 'html/text' or 'application/json'.";
	};

	public static Route notFound = (Request request, Response response) -> {
		response.status(HttpStatus.NOT_FOUND_404);
		return render(request, Path.Template.NOT_FOUND);
	};

	public static Route error403 = (Request req, Response resp) -> {
		resp.status(HttpStatus.FORBIDDEN_403);
		return render(req, Path.Template.ERROR_403);
	};

	public static Route forwardTo(String newAddress) {
		return(Request req, Response resp) -> {
			resp.redirect(newAddress);
			halt();
			return "";
		};
	}
}
