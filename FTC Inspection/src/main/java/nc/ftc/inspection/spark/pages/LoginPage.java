package nc.ftc.inspection.spark.pages;

import java.util.HashMap;
import java.util.Map;

import nc.ftc.inspection.AuthenticationManager;
import nc.ftc.inspection.dao.UsersDAO;
import nc.ftc.inspection.model.User;
import nc.ftc.inspection.spark.util.Path;
import spark.Request;
import spark.Response;
import spark.Route;
import static nc.ftc.inspection.spark.util.ViewUtil.render;
import static nc.ftc.inspection.spark.util.RequestUtil.*;

public class LoginPage {


	public static Route serveLoginPage = (Request request, Response response) -> {
		Map<String, Object> model = new HashMap<>();
		model.put("loggedOut", removeSessionAttrLoggedOut(request));
		model.put("loginRedirect", removeSessionAttrLoginRedirect(request));
		return render(request, model, Path.Template.LOGIN);
	};

	public static Route servePasswordChangePage = (Request request, Response response) -> {
		Map<String, Object> model = new HashMap<>();
		model.put("changePW", true);
		return render(request, model, Path.Template.LOGIN);
	};

	public static Route serveCreateAccountPage = (Request request, Response response) -> {
		Map<String, Object> model = new HashMap<>();
		return serveCreateAccountPage(request, response, model);
	};
	
	public static Object serveCreateAccountPage(Request request, Response response, Map<String, Object> model) {
		try {
			model.put("newUserCount", Integer.parseInt(request.params(":id")));
		} catch (Exception e) {
			model.put("newUserCount", 1);
		}
		return render(request, model, Path.Template.CREATE_ACCOUNT);
	}

	public static Route handlePasswordChangePost = (Request request, Response response) -> {
		Map<String, Object> model = new HashMap<>();
		model.put("changePW", true);
		User user = UsersDAO.authenticate(getQueryUsername(request), getQueryPassword(request));
		if (user == null) {
			User temp = UsersDAO.getUser(getQueryUsername(request));
			//if you are a higher level than someone then you can change their password
			User admin = AuthenticationManager.getCurrentUser(request);
			if (!(admin != null && admin.is(User.ADMIN) && admin.outRanks(temp))) {
				model.put("authenticationFailed", true);
				model.put("username", getQueryUsername(request));
				model.put("reason", "The given username and password are invalid");
				return render(request, model, Path.Template.LOGIN);
			}
			user = temp;
		}
		if (request.queryParams("newPassword1") == null || !request.queryParams("newPassword1").equals(request.queryParams("newPassword2"))) {
			model.put("authenticationFailed", true);
			model.put("reason", "The passwords entered do not match");
			model.put("username", getQueryUsername(request));
			return render(request, model, Path.Template.LOGIN);
		}
		model.put("authenticationSucceeded", true);
		UsersDAO.updatePassword(user.getUsername(), getQueryPassword(request), request.queryParams("newPassword1"));
		user = UsersDAO.authenticate(getQueryUsername(request), request.queryParams("newPassword1"));
		request.session().attribute("sessionToken", AuthenticationManager.getNewSession(user));
		request.session().attribute("currentUser", user.getUsername());
		if (getQueryLoginRedirect(request) != null) {
			response.redirect(getQueryLoginRedirect(request));
		}
		return render(request, model, Path.Template.LOGIN);
	};
	
	public static Route handleLoginPost = (Request request, Response response) -> {
		if (request.queryParams("newPassword1") != null || request.queryParams("newPassword2") != null) {
			return handlePasswordChangePost.handle(request, response);
		}
		Map<String, Object> model = new HashMap<>();
		User user = UsersDAO.authenticate(getQueryUsername(request), getQueryPassword(request));
		if (user == null) {
			model.put("authenticationFailed", true);
			model.put("username", getQueryUsername(request));
			return render(request, model, Path.Template.LOGIN);
		}
		if (!user.hasChangedPw()) {
			model.put("changePW", true);
			model.put("username", user.getUsername());
			model.put("authenticationFailed", true);
			model.put("reason","You must change your password from the default.");
			return render(request, model, Path.Template.LOGIN);
		}
		model.put("authenticationSucceeded", true);
		request.session().attribute("sessionToken", AuthenticationManager.getNewSession(user));
		request.session().attribute("currentUser", user.getUsername());
		if (getQueryLoginRedirect(request) != null) {
			response.redirect(getQueryLoginRedirect(request));
		}
		return render(request, model, Path.Template.LOGIN);
	};

	public static Route handleCreateAccountPost = (Request request, Response response) -> {
		int i = 1; 
		int success = 0;
		try {
			while (true) {
				String username = request.queryParams("username" + i);
				String realname = request.queryParams("realname" + i);
				String typeString = request.queryParams("type" + i);
				int type = User.NONE;
				switch (typeString) {
				case "team":
					type = User.TEAM;
					break;
				case "volunteer":
					type = User.VOLUNTEER;
					break;
				case "key":
					type = User.KEY_VOLUNTEER;
					break;
				}
				//if the username was left blank this will fail
				if (UsersDAO.addUser(username, username, realname, type)) {
					success++;
				}
				i++;
			}
		} catch (Exception e) {
			//this happens when we loop past the last one
		}
		Map<String, Object> model = new HashMap<>();
		model.put("success", success);
		return serveCreateAccountPage(request, response, model);
	};

	public static Route handleLogoutPost = (Request request, Response response) -> {
		request.session().removeAttribute("currentUser");
		request.session().removeAttribute("sessionToken");
		request.session().attribute("loggedOut", true);
		response.redirect(Path.Web.LOGIN);
		return null;
	};
}
