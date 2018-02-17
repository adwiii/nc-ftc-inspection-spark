/*
 * Copyright (c) 2016-2018 Thomas Barnette and Trey Woodlief
 * All Rights Reserved.
 */

package nc.ftc.inspection.spark.pages;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections.bag.SynchronizedSortedBag;

import com.google.gson.Gson;

import nc.ftc.inspection.AuthenticationManager;
import nc.ftc.inspection.dao.UsersDAO;
import nc.ftc.inspection.model.SimpleUser;
import nc.ftc.inspection.model.User;
import nc.ftc.inspection.spark.util.Path;
import spark.Request;
import spark.Response;
import spark.Route;
import static nc.ftc.inspection.spark.util.ViewUtil.render;
import static spark.Spark.halt;
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
		List<String> roles = new ArrayList<String>(User.editableRoles);
		Collections.reverse(roles);
		model.put("types", roles);
		return render(request, model, Path.Template.CREATE_ACCOUNT);
	}

	public static Route serveUserPage = (Request request, Response response) -> {
		Map<String, Object> model = new HashMap<>();
		return render(request, model, Path.Template.USER_PAGE);
	};

	public static Route handlePasswordChangePost = (Request request, Response response) -> {
		Map<String, Object> model = new HashMap<>();
		model.put("changePW", true);
		boolean other = false;
		User user = UsersDAO.authenticate(getQueryUsername(request), getQueryPassword(request));
		if (user == null) {
			User temp = UsersDAO.getUser(getQueryUsername(request));
			//if you are a higher level than someone then you can change their password
			User admin = AuthenticationManager.getCurrentUser(request);
			if (!(admin != null && ((admin.is(User.ADMIN) && !temp.is(User.ADMIN))||(admin.is(User.SYSADMIN) && !temp.is(User.SYSADMIN))))) {
				model.put("authenticationFailed", true);
				model.put("username", getQueryUsername(request));
				model.put("reason", "The given username and password are invalid");
				return render(request, model, Path.Template.LOGIN);
			}
			user = temp;
			other = true; //we are not updating our own password
		}
		if (request.queryParams("newPassword1") == null || !request.queryParams("newPassword1").equals(request.queryParams("newPassword2"))) {
			model.put("authenticationFailed", true);
			model.put("reason", "The passwords entered do not match");
			model.put("username", getQueryUsername(request));
			return render(request, model, Path.Template.LOGIN);
		}
		model.put("authenticationSucceeded", true);
		if (!UsersDAO.updatePassword(user.getUsername(), getQueryPassword(request), request.queryParams("newPassword1"), other)) {
			//the change failed
			model.put("authenticationFailed", true);
			model.put("username", getQueryUsername(request));
			model.put("reason", "Could not update password");
			return render(request, model, Path.Template.LOGIN);
		}
		if (!other) { //if we are updating someone else's password, then don't update the session
			user = UsersDAO.authenticate(getQueryUsername(request), request.queryParams("newPassword1"));
			request.session().attribute("sessionToken", AuthenticationManager.getNewSession(user));
			request.session().attribute("currentUser", user.getUsername());
		}
		if (getQueryLoginRedirect(request) != null) {
			response.redirect(getQueryLoginRedirect(request));
			halt();
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
		String currentSessionToken = request.session().attribute("sessionToken");
		if (currentSessionToken != null) {
			AuthenticationManager.setUser(currentSessionToken, user);
		} else {
			request.session().attribute("sessionToken", AuthenticationManager.getNewSession(user));
		}
		request.session().attribute("currentUser", user.getUsername());
		if (getQueryLoginRedirect(request) != null) {
			response.redirect(getQueryLoginRedirect(request));
			halt();
		}
		response.redirect(Path.Web.INDEX);
		halt();
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
				if (User.editableRoles.contains(typeString)) {
					type = User.valMap.get(typeString);
				}
				//if the username was left blank this will fail
				if (UsersDAO.addUser(username.toLowerCase(), username.toLowerCase(), realname, type)) {
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
		halt();
		return null;
	};

	public static Route serveEditPermissionsPage = (Request request, Response response) -> {
		Map<String, Object> model = new HashMap<>();
		model.put("possibleRoles", User.editableRoles);
		List<SimpleUser> users = User.getEditableUsers();
		model.put("editableUsers", users);
		Map<String, List<String>> userRoleMap = new HashMap<String, List<String>>();
		for (SimpleUser user : users) {
			userRoleMap.put(user.username, user.getPermissionsList());
		}
		Gson gson = new Gson();
		model.put("userRoleMap", gson.toJson(userRoleMap));
		return render(request, model, Path.Template.EDIT_PERMISSIONS);
	};

	public static Route handleEditPermissionsPost = (Request request, Response response) -> {
		//TODO add more specific error handling
		try {
			String roleString = request.queryParams("role");
			int role = User.valMap.get(roleString);
			if (role == User.SYSADMIN) {
				throw new IllegalArgumentException("Cannot set SYSADMIN role");
			}
			Gson gson = new Gson();
			String[] changedUsers = request.queryParamsValues("changedUsers[]");
			boolean add = Boolean.parseBoolean(request.queryParams("add"));
			if (add) {
				for (String username : changedUsers) {
					UsersDAO.addRole(username, role);
					//if the user is currently logged in, we want to make sure that we update their permissions
					AuthenticationManager.addUserPermission(username, role);
				}
			} else {
				for (String username : changedUsers) {
					UsersDAO.removeRole(username, role);
					//if the user is currently logged in, we want to make sure that we update their permissions
					AuthenticationManager.removeUserPermission(username, role);
				}
			}
			return "OK";
		} catch (Exception e) {
			e.printStackTrace();
			response.status(400);
			return "Unable to edit permissions";
		}
	};
	
	public static Route handleDeleteUsers = (Request request, Response response)->{
		String[] changedUsers = request.queryParamsValues("changedUsers[]");
		boolean success = true;
		for(String user : changedUsers) {
			if(!UsersDAO.deleteUser(user))success = false;
		}
		if(!success) {
			response.status(500);
			return "An error occurred";
		}
		return "OK";
	};

}
