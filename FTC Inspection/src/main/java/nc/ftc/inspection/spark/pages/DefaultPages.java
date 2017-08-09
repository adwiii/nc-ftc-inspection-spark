package nc.ftc.inspection.spark.pages;

import java.util.HashMap;

import org.eclipse.jetty.http.HttpStatus;

import nc.ftc.inspection.spark.util.Path;
import spark.Request;
import spark.Response;
import spark.Route;
import static nc.ftc.inspection.spark.util.ViewUtil.render;

public class DefaultPages {

	public static Route indexPage = (req, res) -> {
		HashMap<String, Object> map = new HashMap<String, Object>();
		map.put("currTime", System.currentTimeMillis());
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
}
