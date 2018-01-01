package nc.ftc.inspection.spark.pages;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.commons.collections.bag.SynchronizedSortedBag;
import org.apache.commons.io.IOUtils;
import org.apache.http.Consts;
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
import nc.ftc.inspection.Update;
import nc.ftc.inspection.dao.ConfigDAO;
import nc.ftc.inspection.model.Remote;
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
	
}
