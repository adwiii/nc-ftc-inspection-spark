package nc.ftc.inspection.spark.pages;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.collections.bag.SynchronizedSortedBag;

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
    	try {
        	model.put("newUserCount", Integer.parseInt(request.params(":id")));
    	} catch (Exception e) {
    		model.put("newUserCount", 1);
    	}
    	return render(request, model, Path.Template.CREATE_ACCOUNT);
    };
    
    public static Route handleLoginPost = (Request request, Response response) -> {
        Map<String, Object> model = new HashMap<>();
        User user = UsersDAO.authenticate(getQueryUsername(request), getQueryPassword(request));
        if (user == null) {
            model.put("authenticationFailed", true);
            model.put("username", getQueryUsername(request));
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
    
    public static Route handlePasswordChangePost = (Request request, Response response) -> {
    	Map<String, Object> model = new HashMap<>();
    	model.put("changePW", true);
    	User user = UsersDAO.authenticate(getQueryUsername(request), getQueryPassword(request));
        if (user == null) {
        	User temp = UsersDAO.getUser(getQueryUsername(request));
        	//if you are
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
	
    public static Route handleCreateAccountPost = (Request request, Response response) -> {
    	int i = 1; 
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
    		
    	}
    };
    
    public static Route handleLogoutPost = (Request request, Response response) -> {
    	request.session().removeAttribute("currentUser");
    	request.session().removeAttribute("sessionToken");
        request.session().attribute("loggedOut", true);
        response.redirect(Path.Web.LOGIN);
        return null;
    };
}
