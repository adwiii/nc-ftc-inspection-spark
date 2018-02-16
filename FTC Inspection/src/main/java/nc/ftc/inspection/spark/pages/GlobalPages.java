/*
 * Copyright (c) 2016-2018 Thomas Barnette and Trey Woodlief
 * All Rights Reserved.
 */

package nc.ftc.inspection.spark.pages;

import static nc.ftc.inspection.spark.util.ViewUtil.render;
import java.util.HashMap;
import java.util.Set;
import java.util.stream.Collectors;

import nc.ftc.inspection.dao.GlobalDAO;
import nc.ftc.inspection.spark.util.Path;
import spark.Request;
import spark.Response;
import spark.Route;

public class GlobalPages {
	public static Route handleTeamListGet = (Request request, Response response) -> {
		return GlobalDAO.getMasterTeamList().stream().map(Object::toString).collect(Collectors.toList());	
	};
	
	public static Route handleNewTeamPost = (Request request, Response response) -> {
		String name = request.queryParams("name");
		int number;
		try{
			number = Integer.parseInt(request.queryParams("number"));
		}catch(NumberFormatException e){
			response.status(400);
			return "";
		}
		response.status(GlobalDAO.addNewTeam(number, name) ? 201 : 500);
		return "";
	};
	
//	public static Route handleEditTeamPut = (Request request, Response response) -> {
//		String name = request.queryParams("name");
//		int number;
//		try{
//			number = Integer.parseInt(request.queryParams("number"));
//		}catch(NumberFormatException e){
//			response.status(400);
//			return "";
//		}
//		response.status(GlobalDAO.editTeamName(number, name) ? 201 : 500);
//		return "";
//	};

	public static Route serveFeedbackForm =(Request request, Response response) -> {
		return render(request, new HashMap<String, Object>(), Path.Template.FEEDBACK);
	};

	public static Route handleFeedback =(Request request, Response response) -> {
		String feedback = request.queryParams("feedback");
		GlobalDAO.saveFeedback(feedback);		
		return render(request, new HashMap<String, Object>(), Path.Template.FEEDBACK_THANKS);
	};
}
