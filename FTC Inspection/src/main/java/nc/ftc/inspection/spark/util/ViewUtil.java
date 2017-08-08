package nc.ftc.inspection.spark.util;

import org.apache.velocity.app.*;
import spark.*;
import spark.template.velocity.*;
import java.util.*;

import static nc.ftc.inspection.spark.util.RequestUtil.*;

public class ViewUtil {

    // Renders a template given a model and a request
    // The request is needed to check the user session for language settings
    // and to see if the user is logged in
    public static String render(Request req, Map<String, Object> model, String templatePath) {
        model.put("currentUser", getSessionCurrentUser(req));
        model.put("WebPath", new Path.Web()); // Access application URLs from templates
        return strictVelocityEngine().render(new ModelAndView(model, templatePath));
    }

    private static VelocityTemplateEngine strictVelocityEngine() {
        VelocityEngine configuredEngine = new VelocityEngine();
        configuredEngine.setProperty("runtime.references.strict", true);
        configuredEngine.setProperty("resource.loader", "class");
        configuredEngine.setProperty("class.resource.loader.class", "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");
        return new VelocityTemplateEngine(configuredEngine);
    }

	public static Object render(Request req, String index) {
		return render(req, new HashMap<>(), index);
	}
}