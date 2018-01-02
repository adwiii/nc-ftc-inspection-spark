package nc.ftc.inspection.model;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import org.apache.http.Consts;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;

import nc.ftc.inspection.RemoteUpdater;
import nc.ftc.inspection.dao.ConfigDAO;

public class Remote {
	String host;
	String key;
	String event;
	Queue<List<NameValuePair>> failed = new LinkedList<>();
	public Remote(String h, String k, String event) {
		this.host = h;
		this.key = k;
		this.event = event;
	}
	public String getHost() {
		return host;
	}
	public String getEvent() {
		return event;
	}
	private void send(List<NameValuePair> form) throws IOException {
		form.add(new BasicNameValuePair("k", key));
		form.add(new BasicNameValuePair("e", event));
		HttpPost post = new HttpPost((host.startsWith("http://") ? host : ("http://"+host))+"/update/");
		UrlEncodedFormEntity entity = new UrlEncodedFormEntity(form, Consts.UTF_8);
		post.setEntity(entity);
		try(CloseableHttpClient client = HttpClients.createMinimal()){
			HttpResponse resp = client.execute(post);
			if(resp.getStatusLine().getStatusCode() == 403) {
				//Our Key is Bad! remove this Remote from the list and delete my key from the db
				System.err.println("Invalid key for "+host+" for event "+event+"! Deleteing entry!");
				ConfigDAO.deleteRemote(host, event);
				RemoteUpdater.getInstance().removeRemote(this);
				this.failed.clear();;
			}
		}	
	}
	
	public void sendPOST(List<NameValuePair> form) {
		try {
			while(!failed.isEmpty()) {
				try {
					send(failed.peek());
					failed.poll();
				}catch(IOException e) {
					if(e.getMessage().contains("Connection refused: connect")) {
						//still no conn, throw up to outer catch and append the new list to the failed queue.
						throw e;
					} else {
						//failed for a different reason. Allow loop to continue.
						e.printStackTrace();
						failed.poll();
					}
				}
			}
			send(form);
		}catch(IOException e) {
			if(e.getMessage().contains("Connection refused: connect")) {
				System.err.println("No connection to remote "+host);
				failed.offer(form);
			} else {
				e.printStackTrace();
			}
		}
	}
	public int saveFailureQueue() {
		return failed.size();
	}

}
