/*
 * Copyright (c) 2016-2018 Thomas Barnette and Trey Woodlief
 * All Rights Reserved.
 */

package nc.ftc.inspection.spark.pages;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletException;
import javax.servlet.http.Part;

import org.apache.commons.io.IOUtils;
import org.apache.http.Consts;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;

import com.google.gson.Gson;

import nc.ftc.inspection.RemoteUpdater;
import nc.ftc.inspection.Server;
import nc.ftc.inspection.Update;
import nc.ftc.inspection.dao.ConfigDAO;
import nc.ftc.inspection.dao.EventDAO;
import nc.ftc.inspection.dao.GlobalDAO;
import nc.ftc.inspection.dao.UsersDAO;
import nc.ftc.inspection.model.Remote;
import nc.ftc.inspection.model.Team;
import nc.ftc.inspection.model.User;
import nc.ftc.inspection.spark.util.Path;
import spark.Request;
import spark.Response;
import spark.Route;


import static nc.ftc.inspection.spark.util.ViewUtil.render;

public class ServerPages {
	public static Route handleRemoteUpdatePost = (Request request, Response response)->{
		String key = request.queryParams("k");
		String event = request.queryParams("e"); //this is just to match the key
		//if an Update has no specific event, use this one
		//check master key first. Each update also checks key against specified event in params
		if(!ConfigDAO.checkKey(event, key)) {
			response.status(403);
			return "Invalid Key";
		}
		Update[] updates = new Gson().fromJson(request.queryParams("u"), Update[].class);
		for(Update u : updates) {
			if(u.e == null) {
				if(u.t == Update.GLOBAL_DB_UPDATE || u.t == Update.USER_DB_UPDATE) {
					u.e = event;
				}
			}
		}
		Thread thread = new Thread() {
			public void run() {
				for(Update u : updates) {
					u.execute(key);
				}
			}
		};
		thread.start();
		return "OK";
	};

	public static Route serveConfigPage = (Request request, Response response)->{
		//links to remote key and client key page
		Map<String, Object> map = new HashMap<String, Object>();
		return render(request, map, Path.Template.SERVER_CONFIG);
	};

	public static Route serveRemoteKeyPage = (Request request, Response response)->{
		//only show the host and the event. dont show 
		Map<String, Object> map = new HashMap<>();
		map.put("hosts", ConfigDAO.getRemotes());
		return render(request, map, Path.Template.REMOTE_KEYS);
	};

	public static Route serveClientKeyPage = (Request request, Response response)->{
		Map<String, Object> map = new HashMap<>();
		map.put("keys", ConfigDAO.getKeys());
		return render(request, map, Path.Template.CLIENT_KEYS);
	};

	private static String chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz!@#$%^&*()";
	public static Route handleGenerateKey = (Request request, Response response)->{
		String event = request.queryParams("event");
		SecureRandom rand = SecureRandom.getInstanceStrong();
		String key = "";
		for ( int i = 0; i < 10; i++ ) {
			key += chars.charAt( rand.nextInt( chars.length() ) );
		}
		if(ConfigDAO.saveKey(event, key)) {
			return key;
		}
		response.status(500);
		return "Error generating key for "+event;
	};
	public static Route handleSaveRemoteKey = (Request request, Response response)->{
		//make verify requets to host, if accepted, save it.
		String host = request.queryParams("host");
		String key = request.queryParams("key");
		String event = request.queryParams("event");
		HttpPost post = new HttpPost((host.startsWith("http://") ? host : ("http://"+host))+"/config/verify/");
		List<NameValuePair> form = new ArrayList<>(2);
		form.add(new BasicNameValuePair("key", key));
		form.add(new BasicNameValuePair("event", event));
		UrlEncodedFormEntity entity = new UrlEncodedFormEntity(form, Consts.UTF_8);
		post.setEntity(entity);
		try(CloseableHttpClient client = HttpClients.createMinimal()){
			HttpResponse resp = client.execute(post);
			response.status(resp.getStatusLine().getStatusCode());
			if( resp.getStatusLine().getStatusCode() == 200) {
				//OK to save key
				if(ConfigDAO.saveRemote(host, key, event)) {
					RemoteUpdater.getInstance().addRemote(new Remote(host, key, event));
					return "OK";
				}
				response.status(500);
				return "Error saving key";
			}
			else { //NOT OK to save key
				response.status(400);
				return IOUtils.toString(resp.getEntity().getContent());
			}
		}catch(Exception e) {
			//no conn
			if(e.getMessage().contains("Connection refused: connect")) {
				response.status(500);
				return "No connection to host";
			}
		}
		response.status(500);
		return "Error";
	};
	public static Route handleDeleteRemoteKey = (Request request, Response response)->{
		String event = request.queryParams("event");
		String host = request.queryParams("host");
		if(ConfigDAO.deleteRemote(host, event)) {
			return "OK";
		}
		response.status(400);
		return "Error";
	};
	public static Route handleDeleteClientKey = (Request request, Response response)->{
		String event = request.queryParams("event");
		if(ConfigDAO.deleteClientKey(event)) {
			return "OK";
		}
		response.status(400);
		return "Error";
	};
	public static Route handlTestRemote = (Request request, Response response)->{
		String host = request.queryParams("host");
		HttpGet get = new HttpGet((host.startsWith("http://") ? host : ("http://"+host))+"/ping/");
		try(CloseableHttpClient client = HttpClients.createMinimal()){
			HttpResponse resp = client.execute(get);
			response.status(resp.getStatusLine().getStatusCode());
			if( resp.getStatusLine().getStatusCode() == 200) {
				return "OK";
			}
			return "Error";
		}catch(Exception e) {
			//no conn
			if(e.getMessage().contains("Connection refused: connect")) {
				response.status(404);
				return "Cannot connect.";
			}
		}
		response.status(500);
		return "Error";
	};

	public static Route handleVerify = (Request request, Response response)->{
		String event = request.queryParams("event");
		String key = request.queryParams("key");
		String res = ConfigDAO.verifyClientKey(event, key);
		response.status(res.equals("OK") ? 200 : 400);
		return res;
	};

	public static Route handlePing = (Request request, Response response) ->{
		return "OK";
	};

	private static String serveFile(String name, Response response) {
		File file = new File(Server.DB_PATH+name+".db");
		response.raw().setContentType("application/octet-stream");
		response.raw().setHeader("Content-Disposition","attachment; filename="+file.getName()+".zip");
		try {

			try(ZipOutputStream zipOutputStream = new ZipOutputStream(new BufferedOutputStream(response.raw().getOutputStream()));
					BufferedInputStream bufferedInputStream = new BufferedInputStream(new FileInputStream(file)))
			{
				ZipEntry zipEntry = new ZipEntry(file.getName());

				zipOutputStream.putNextEntry(zipEntry);
				byte[] buffer = new byte[1024];
				int len;
				while ((len = bufferedInputStream.read(buffer)) > 0) {
					zipOutputStream.write(buffer,0,len);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return "";
	}

	public static String zipDirectory(File dir, Response response) {
		response.raw().setContentType("application/octet-stream");
		response.raw().setHeader("Content-Disposition","attachment; filename="+dir.getName()+".zip");
		//		Collection<File> files = FileUtils.listFiles(dir, new String[] {"html"}, true);
		String path = dir.getPath() + "\\";
		try {
			try(ZipOutputStream zipOutputStream = new ZipOutputStream(new BufferedOutputStream(response.raw().getOutputStream()));
					BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(zipOutputStream, Charset.forName("ISO-8859-1")));){
				addDir(path, dir, zipOutputStream, writer);
				writer.close();
				zipOutputStream.close();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return "";
	}

	static void addDir(String path, File dirObj, ZipOutputStream out, BufferedWriter writer) throws IOException {
		File[] files = dirObj.listFiles();
		String line;
		for (int i = 0; i < files.length; i++) {
			if (files[i].isDirectory()) {
				out.putNextEntry(new ZipEntry(files[i].getPath().replace(path, "") + "/"));
				addDir(path, files[i], out, writer);
				continue;
			}
			//		      System.out.println(files[i].getPath());
			//		      System.out.println(path);
			System.out.println(" Adding: " + files[i].getPath().replace(path, ""));
			out.putNextEntry(new ZipEntry(files[i].getPath().replace(path, "")));
			if (files[i].getName().endsWith(".png")) {
				BufferedInputStream bufferedInputStream = new BufferedInputStream(new FileInputStream(files[i]));
				byte[] buffer = new byte[1024];
				int len;
				while ((len = bufferedInputStream.read(buffer)) > 0) {
					out.write(buffer,0,len);
				}
				out.flush();
				bufferedInputStream.close();
			} else {
				BufferedReader reader = new BufferedReader(new FileReader(files[i].getPath()));
				while ((line = reader.readLine()) != null) {
					writer.append(line).append("\n");
				}
				writer.flush();
				reader.close();
			}

			out.closeEntry();
		}
	}

	/**
	 * Sends global.db to the requester
	 */
	public static Route handleDataDownloadGlobal = (Request request, Response response) ->{
		String event = request.queryParams("e");
		String key = request.queryParams("k");
		if(ConfigDAO.checkKey(event, key)) {
			return serveFile("global", response);
		}
		response.status(403);
		return "Invalid Key";
	};

	public static Route handleDataDownloadEvent = (Request request, Response response) ->{
		String event = request.queryParams("e");
		String key = request.queryParams("k");

		if(ConfigDAO.checkKey(event, key)) {
			return serveFile(event, response);
		}
		response.status(403);
		return "Invalid Key";
	};

	private static String getDB(String host, String event, String key, String db) {	
		List<NameValuePair> form = new ArrayList<NameValuePair>(2);
		form.add(new BasicNameValuePair("k", key));
		form.add(new BasicNameValuePair("e", event));		
		HttpPost post = new HttpPost((host.startsWith("http://") ? host : ("http://"+host))+"/config/remotes/dd"+(db.equals("global")?"global/":"event/"));
		UrlEncodedFormEntity entity = new UrlEncodedFormEntity(form, Consts.UTF_8);
		post.setEntity(entity);
		try(CloseableHttpClient client = HttpClients.createMinimal()){
			HttpResponse resp = client.execute(post);
			Header head = resp.getFirstHeader("Content-Disposition");
			if(head == null) {
				if(resp.getStatusLine().getStatusCode() == 403) {
					return "Invalid Key";
				}
				return "Error downloading zip file "+db+".db.zip";
			}
			String filename = head.getValue();
			filename = filename.substring(filename.indexOf("filename=")+9);
			if(resp.getStatusLine().getStatusCode() == 200) {
				BufferedInputStream in = new BufferedInputStream(resp.getEntity().getContent());
				BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(Server.DB_PATH+filename)); 
				byte[] buffer = new byte[1024];
				int len;
				while ((len = in.read(buffer)) > 0) {
					out.write(buffer,0,len);
				}
				in.close();
				out.flush();
				out.close();
				//We have the file, now delete the existing db and unzip this one.
				String name = filename.substring(0, filename.length()-4);//cut of the "-.zip"
				try {
					Files.deleteIfExists(new File(Server.DB_PATH+name).toPath());
					try {
						ZipInputStream zip = new ZipInputStream(new FileInputStream(Server.DB_PATH+filename));
						zip.getNextEntry();
						out = new BufferedOutputStream(new FileOutputStream(Server.DB_PATH+name));
						byte[] buf = new byte[1024];
						int read = 0;
						while ((read = zip.read(buf)) != -1) {
							out.write(buf, 0, read);
						}
						out.flush();
						out.close();
						zip.close();
						return "OK";
					}catch(IOException e) {
						//error unzipping db.
						return "Error unzipping "+name;
					}
				}catch(IOException e) {
					return "Error deleting existing "+name;
				}

			}
			if(resp.getStatusLine().getStatusCode() == 403) {
				return "Invalid Key";
			}
			return "Error downloading zip file "+filename;
		} catch (IOException e) {
			e.printStackTrace();
			return "Error connecting to server";
		}	

	}
	public static Route handleDataDownloadPost = (Request request, Response response) ->{
		String host = request.queryParams("host");
		String event = request.queryParams("event");
		String key = ConfigDAO.getRemoteKey(host, event);
		String resp1 = getDB(host,event, key, "global");
		String resp2 = getDB(host,event, key, event);
		if(resp1.equals("OK")) {
			if(resp2.equals("OK")) {
				return "OK";
			}
			response.status(500);
			return resp1;
		}
		response.status(500);
		if(resp2.equals("OK")) {
			return resp2;
		}
		return resp1+";"+resp2;
	};
	
	public static Route handleDeleteEventPost = (Request request, Response response)->{
		String event = request.queryParams("code");
		String op = request.queryParams("op");
		boolean res = false;
		if(op.equals( "remove")) {
			res = EventDAO.removeEvent(event);
		} else if(op.equals( "delete")) {
			res = EventDAO.deleteEvent(event);
		}
		if(!res) {
			response.status(500);
			return "FAILED";
		}		
		return "OK";
	};
	public static Route serveServerDataPage = (Request request, Response response) ->{
		return render(request, new HashMap<>(), Path.Template.SERVER_DATA_MANAGEMENT);
	};
	
	public static Route handleExportUsers = (Request request, Response response) ->{
		List<User> users = UsersDAO.getAllUsers();
		response.raw().setContentType("application/octet-stream");
		response.raw().setHeader("Content-Disposition","attachment; filename=users.dat");
		try(ObjectOutputStream outputStream = new ObjectOutputStream(new BufferedOutputStream(response.raw().getOutputStream()))){			
			outputStream.writeObject(users);			
		} catch (Exception e) {
			e.printStackTrace();
		}
		return "OK";
	};
	
	@SuppressWarnings("unchecked")
	public static Route handleImportUsers = (Request request, Response response) ->{
		List<User> newUsers;
		String location = "public";          // the directory location where files will be stored
		long maxFileSize = 100000000;       // the maximum size allowed for uploaded files
		long maxRequestSize = 100000000;    // the maximum size allowed for multipart/form-data requests
		int fileSizeThreshold = 4096;       // the size threshold after which files will be written to disk
		
		MultipartConfigElement multipartConfigElement = new MultipartConfigElement(location, maxFileSize, maxRequestSize, fileSizeThreshold);
		request.raw().setAttribute("org.eclipse.jetty.multipartConfig", multipartConfigElement);
		Part p = request.raw().getPart("file");
		try(ObjectInputStream inputStream = new ObjectInputStream(p.getInputStream())){
			newUsers = (List<User>)inputStream.readObject();
		}catch(Exception e) {
			e.printStackTrace();
			response.status(500);
			return "Corrupted users file.";
		}
		int added = UsersDAO.importUsers(newUsers);
		if(added < 0)response.status(500);
		System.out.println(added+" users imported.");
		return added + " users imported";
	};
	
	public static List<Team> parseTeamFile(Request request, Response response) throws IOException, ServletException{
		String location = "public";          // the directory location where files will be stored
		long maxFileSize = 100000000;       // the maximum size allowed for uploaded files
		long maxRequestSize = 100000000;    // the maximum size allowed for multipart/form-data requests
		int fileSizeThreshold = 4096;// the size threshold after which files will be written to disk
		
		MultipartConfigElement multipartConfigElement = new MultipartConfigElement(location, maxFileSize, maxRequestSize, fileSizeThreshold);
		request.raw().setAttribute("org.eclipse.jetty.multipartConfig",    multipartConfigElement);	
		Part p = request.raw().getPart("file");
		List<Team> teams = new ArrayList<Team>();
		Scanner scan = new Scanner(p.getInputStream());
		scan.useDelimiter("\\|");
		try{
			while(scan.hasNextLine()){
				try {
				int division = scan.nextInt();
				int team = scan.nextInt();
				String name = scan.next();
				String affiliation = scan.next();
				String city = scan.next();
				String state = scan.next();
				String country = scan.next();
				boolean advanced = scan.nextBoolean();
				scan.nextLine();
				Team t = new Team(team, name, city+","+state+", "+country);
				teams.add(t);
				}catch(Exception e) {
					e.printStackTrace();
				}
			}
		}catch(Exception e) {
			e.printStackTrace();
		}
		return teams;
	}
	
	public static Route handleImportTeams = (Request request, Response response) ->{
		List<Team> teams = parseTeamFile(request, response);
		int added = GlobalDAO.addNewTeams(teams);
		if(added == -1) {
			response.status(500);
			return "Error adding teams";
		}
		return added +" teams added.";
	};

}
