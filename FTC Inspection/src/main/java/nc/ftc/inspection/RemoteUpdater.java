package nc.ftc.inspection;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import com.google.gson.Gson;

import nc.ftc.inspection.dao.ConfigDAO;
import nc.ftc.inspection.model.Remote;

public class RemoteUpdater extends Thread {
	
	private static RemoteUpdater instance = new RemoteUpdater();
	private volatile boolean shutdown = false;
	private List<Remote> remotes;
	private Queue<Update> queue = new LinkedList<>();
	
	private RemoteUpdater() {
		remotes = ConfigDAO.getRemotes();
		remotes.add(new Remote("localhost/update/", "empty"));
		Map<String, String> map = new HashMap<>();
		map.put(":ay", "column");
		queue.add(new Update("test10", 1, map, 1, 3, 2, false, "hello"));
		start();
	}
	
	public static RemoteUpdater getInstance() {
		return instance;
	}
	
	public void shutdown() {
		shutdown = true;
		synchronized(this) {
			this.notify();
		}
	}
	
	public synchronized void enqueue(Update update) {
		queue.offer(update);
	}
	
	
	
	public void run() {
		while(!shutdown) {
			if(remotes.size() == 0) {
				synchronized(this) {
					try {
						this.wait();
					} catch (InterruptedException e) {
						//interrupt to kill
					}
				}
			} else {
				//empty queue into a POST object & send it to host.
				List<NameValuePair> form = new ArrayList<>();
				form.add(new BasicNameValuePair("key", "key"));
				Gson gson = new Gson();
				List<Update> updates = new ArrayList<>(queue.size() + 2);
				synchronized(this) {
					while(!queue.isEmpty()) {
						updates.add(queue.poll());
					}
				}
				form.add(new BasicNameValuePair("updates", gson.toJson(updates)));
				for(Remote r : remotes) {
					try {
						r.sendPOST(form);
						System.out.println("Sent");
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				synchronized(this) {
					try {
						this.wait(10000); //up this after testing
					} catch (InterruptedException e) {
					}
				}
			}
		}
	}

}
