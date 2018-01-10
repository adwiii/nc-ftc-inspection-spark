package nc.ftc.inspection.spark.util;

import org.apache.velocity.app.*;
import org.apache.velocity.runtime.parser.node.MathUtils;

import nc.ftc.inspection.Server;
import nc.ftc.inspection.model.User;
import spark.*;
import spark.template.velocity.*;

import java.io.File;
import java.util.*;

import static nc.ftc.inspection.spark.util.RequestUtil.*;

public class ViewUtil {

    // Renders a template given a model and a request
    // The request is needed to check the user session for language settings
    // and to see if the user is logged in
    public static String render(Request req, Map<String, Object> model, String templatePath) {
    	User user = getSessionCurrentUser(req);
    	if (user != null) {
    		model.put("currentUser", user.getUsername());
        	model.put("rolesList", user.getPermissionsList());
        	model.put("roles", user.getPermissions());
        	model.put("SYSADMIN",user.is(User.SYSADMIN));
        	model.put("ADMIN",user.is(User.ADMIN));
        	model.put("KEY_VOLUNTEER",user.is(User.KEY_VOLUNTEER));
        	model.put("HEAD_REF",user.is(User.HEAD_REF));
        	model.put("REF",user.is(User.REF));
        	model.put("LI",user.is(User.LI));
        	model.put("INSPECTOR",user.is(User.INSPECTOR));
        	model.put("VOLUNTEER",user.is(User.VOLUNTEER));
        	model.put("TEAM",user.is(User.TEAM));
        	model.put("GENERAL",user.is(User.GENERAL));
    	} else {
    		model.put("currentUser", null);
    		model.put("rolesList", new ArrayList<String>());
    		model.put("roles", 0);
    	}
    	String sysEvent = req.params("event");
    	if (sysEvent != null) {
    		model.put("sysEvent", sysEvent);
    		if((new File(Server.publicDir.getPath() + "/img/" + sysEvent + ".png")).exists()) {
    			model.put("sysEventImage", "/img/" + sysEvent + ".png");
    		}
    	}
        model.put("currentPath", req.pathInfo());
        model.put("request", req);
        boolean mobile = req.userAgent().toLowerCase().contains("mobile") || req.userAgent().toLowerCase().contains("android");
        //System.out.println(mobile + ": " + req.userAgent());
        model.put("mobile", mobile);
        model.put("WebPath", new Path.Web()); // Access application URLs from templates
        model.put("time", System.currentTimeMillis());
        model.put("Math", Math.class);
        model.put("String", String.class);
        model.put("MathUtils", MathUtils.class);
        return velocityEngine().render(new ModelAndView(model, templatePath));
    }

    private static VelocityTemplateEngine strictVelocityEngine() {
        VelocityEngine configuredEngine = new VelocityEngine();
        configuredEngine.setProperty("runtime.references.strict", true);
        configuredEngine.setProperty("resource.loader", "class");
        configuredEngine.setProperty("class.resource.loader.class", "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");
        return new VelocityTemplateEngine(configuredEngine);
    }
    private static VelocityTemplateEngine velocityEngine() {
        VelocityEngine configuredEngine = new VelocityEngine();
        configuredEngine.setProperty("runtime.references.strict", false);
        configuredEngine.setProperty("resource.loader", "class");
        configuredEngine.setProperty("class.resource.loader.class", "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");
        return new VelocityTemplateEngine(configuredEngine);
    }

	public static Object render(Request req, String index) {
		return render(req, new HashMap<>(), index);
	}
}