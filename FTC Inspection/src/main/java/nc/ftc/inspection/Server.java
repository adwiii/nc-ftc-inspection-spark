package nc.ftc.inspection;

import static spark.Spark.*;
import static spark.debug.DebugScreen.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;

import nc.ftc.inspection.dao.UsersDAO;
import nc.ftc.inspection.model.User;
import nc.ftc.inspection.spark.pages.DefaultPages;
import nc.ftc.inspection.spark.pages.LoginPage;
import nc.ftc.inspection.spark.util.Filters;
import nc.ftc.inspection.spark.util.Path;
import nc.ftc.inspection.spark.util.ViewUtil;
import spark.template.velocity.*;

import spark.Route;

public class Server {
	public static final String GLOBAL_DB = "jdbc:sqlite:src/main/resources/db/global.db"; 
	
	public static void main(String[] args) {
		try {//idk, somethings up with gradle but this makes it work.
			Class.forName("org.sqlite.JDBC");
		} catch (ClassNotFoundException e1) {
			e1.printStackTrace();
		}
		
		
		port(80);
		staticFiles.location("/public");
		//TODO remove the debug screen for release?
		enableDebugScreen();
		
		before("*", Filters.addTrailingSlashesAndLowercase);
		
		before(Path.Web.CREATE_ACCOUNT, Filters.getAuthenticationFilter(User.ADMIN));
		before(Path.Web.CREATE_ACCOUNT_SIMPLE, Filters.getAuthenticationFilter(User.ADMIN));
		
		
		get(Path.Web.INDEX, DefaultPages.indexPage);
		get(Path.Web.LOGIN, LoginPage.serveLoginPage);
		get(Path.Web.IP_PAGE, DefaultPages.ipPage);
		get(Path.Web.CHANGE_PW, LoginPage.servePasswordChangePage);
		get(Path.Web.CREATE_ACCOUNT_SIMPLE, LoginPage.serveCreateAccountPage);
		get(Path.Web.CREATE_ACCOUNT, LoginPage.serveCreateAccountPage);
		
		//TODO make change password/new user page
		//TODO encrypt passwords on POST
		get(Path.Web.ERROR_403, DefaultPages.error403);
		
		post(Path.Web.LOGIN, LoginPage.handleLoginPost);
		post(Path.Web.LOGOUT, LoginPage.handleLogoutPost);
		post(Path.Web.CHANGE_PW, LoginPage.handlePasswordChangePost);
		post(Path.Web.CREATE_ACCOUNT, LoginPage.handleCreateAccountPost);
		post(Path.Web.CREATE_ACCOUNT_SIMPLE, LoginPage.handleCreateAccountPost);
		
		get(Path.Web.ALL, DefaultPages.notFound);
		
		after("*", Filters.addGzipHeader);
	}
}

