package nc.ftc.inspection.spark.util;

import nc.ftc.inspection.AuthenticationManager;
import nc.ftc.inspection.model.User;
import spark.*;

public class Filters {

    // If a user manually manipulates paths and forgets to add
    // a trailing slash, redirect the user to the correct path
    public static Filter addTrailingSlashesAndLowercase = (Request request, Response response) -> {
        if (!request.pathInfo().endsWith("/")) {
            response.redirect(request.pathInfo().toLowerCase() + "/");
        } else {
        	response.redirect(request.pathInfo().toLowerCase());
        }
    };
    
    /**
     * This returns a filter that checks to see if the current user has the needed level to access this page
     * @param level the minimum level needed to access this page
     * @return A filter to check this page
     */
    public static Filter getAuthenticationFilter(int level) {
    	return (Request request, Response response) -> {
    		int user = AuthenticationManager.getUserType(request.session().attribute("sessionToken"));
    		//if the user is none then we need to login
    		if (user == User.NONE) {
            	request.session().attribute("sessionToken", null); //ensure there is no sessionToken going in
                request.session().attribute("loginRedirect", request.pathInfo());
                response.redirect(Path.Web.LOGIN);
    		}
    		//if the level is lower than us then we need to redirect
    		if (level < user) {
    			response.redirect(Path.Web.ERROR_403);
    		}
    	};
    }


    // Enable GZIP for all responses
    public static Filter addGzipHeader = (Request request, Response response) -> {
        response.header("Content-Encoding", "gzip");
    };

}
