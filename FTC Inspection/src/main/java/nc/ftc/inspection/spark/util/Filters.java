/*
 * Copyright (c) 2016-2018 Thomas Barnette and Trey Woodlief
 * All Rights Reserved.
 */

package nc.ftc.inspection.spark.util;

import static spark.Spark.halt;

import nc.ftc.inspection.AuthenticationManager;
import nc.ftc.inspection.model.User;
import spark.*;

public class Filters {

    // If a user manually manipulates paths and forgets to add
    // a trailing slash, redirect the user to the correct path
    public static Filter addTrailingSlashesAndLowercase = (Request request, Response response) -> {
        if (!request.pathInfo().endsWith("/")) {
            response.redirect(request.pathInfo().toLowerCase() + "/");
            halt();
        } else {
        	//check to see if we need to go to lower case
        	if (!request.pathInfo().equals(request.pathInfo().toLowerCase())) {
        		response.redirect(request.pathInfo().toLowerCase());
        		halt();
        	}
        }
    };
    
    /**
     * This returns a filter that checks to see if the current user has the needed level to access this page
     * @param type the minimum level needed to access this page
     * @return A filter to check this page
     */
    public static Filter getAuthenticationFilter(int type) {
    	return (Request request, Response response) -> {
    		int user = AuthenticationManager.getUserType(request.session().attribute("sessionToken"));
    		//if the user is none then we need to login
    		if (user == User.NONE) {
                request.session().attribute("loginRedirect", request.pathInfo());
                response.redirect(Path.Web.LOGIN);
                halt();
    		}
    		//if the user cannot access this page, then 403
    		if ((user & type) == 0) {
    			response.redirect(Path.Web.ERROR_403);
    			halt();
    		}
    	};
    }
    
    public static Filter createSession = (Request request, Response response) -> {
    	if (AuthenticationManager.getSession(request) == null) {
    		request.session().attribute("sessionToken", AuthenticationManager.getNewSession(null));
    	}
    };
    	
    
    /**
     * This returns a filter that checks that the user is logged in
     * @return A filter to check this page for logged in
     */
    public static Filter getAuthenticationFilter() {
    	return (Request request, Response response) -> {
    		int user = AuthenticationManager.getUserType(request.session().attribute("sessionToken"));
    		//if the user is none then we need to login
    		if (user == User.NONE) {
            	request.session().attribute("sessionToken", null); //ensure there is no sessionToken going in
                request.session().attribute("loginRedirect", request.pathInfo());
                response.redirect(Path.Web.LOGIN);
    		}
    	};
    }


    // Enable GZIP for all responses
    public static Filter addGzipHeader = (Request request, Response response) -> {
        response.header("Content-Encoding", "gzip");
    };

}
