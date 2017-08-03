package nc.ftc.inspection;

import static spark.Spark.*;
import static spark.debug.DebugScreen.*;

public class Server {
	public static void main(String[] args) {
		port(80);
		staticFiles.location("/public");
		
		get("*", (req, res) -> "this is different");
	}
}

