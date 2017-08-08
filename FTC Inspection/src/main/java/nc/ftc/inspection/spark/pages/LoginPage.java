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
	
    
    public static Route handleLoginPost = (Request request, Response response) -> {
        Map<String, Object> model = new HashMap<>();
        User user = UsersDAO.authenticate(getQueryUsername(request), getQueryPassword(request));
        if (user == null) {
            model.put("authenticationFailed", true);
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
	
    public static Route handleLogoutPost = (Request request, Response response) -> {
    	request.session().removeAttribute("currentUser");
    	request.session().removeAttribute("sessionToken");
        request.session().attribute("loggedOut", true);
        response.redirect(Path.Web.LOGIN);
        return null;
    };
}
