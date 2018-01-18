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
import nc.ftc.inspection.dao.EventDAO;
import nc.ftc.inspection.model.Remote;

public class RemoteUpdater extends Thread {
	
	private static RemoteUpdater instance = new RemoteUpdater();
	private volatile boolean shutdown = false;
	private List<Remote> remotes;
	private Queue<Update> queue = new LinkedList<>();
	
	private RemoteUpdater() {
		remotes = ConfigDAO.getRemotes();
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
		if(remotes.size() == 0)return;
		queue.offer(update);
	}	
	
	public void run() {
		while(!shutdown) {
			if(remotes.size() == 0) {
				synchronized(instance) {
					try {
						this.wait();
					} catch (InterruptedException e) {
						//interrupt to kill
					}
				}
			} else {
				//empty queue into a POST object & send it to host.
				if(queue.isEmpty()) {
					synchronized(instance) {
						try {
							this.wait(60000);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
					if(queue.isEmpty()) {
						continue;
					}
				}
				List<NameValuePair> form = new ArrayList<>();
				
				Gson gson = new Gson();
				List<Update> updates = new ArrayList<>(queue.size() + 2);
				synchronized(instance) {
					while(!queue.isEmpty()) {
						updates.add(queue.poll());
					}
				}
				form.add(new BasicNameValuePair("u", gson.toJson(updates)));
				synchronized(remotes) { //prevent concurrent modification from expired key removal
					for(Remote r : remotes) {
						Thread t = new Thread() {
							public void run() {
								r.sendPOST(form);
							}
						};
						t.start();					
					}
				}
				synchronized(instance) {
					try {
						this.wait(60000); //up this after testing
					} catch (InterruptedException e) {
					}
				}
			}
		}
	}
	private Thread hook = new Thread() {
		public void run() {
			//give all enqueued a (very quick) chance to finish.
			//if no con, hopefully they realize it quickly.
			System.err.println("Shutting down remote updater! (This will take 3 seconds)");
			synchronized(instance) {
				instance.notifyAll();
			}
			try {
				Thread.sleep(3000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			int n = 0;
			for(Remote r : remotes) {
				n += r.saveFailureQueue();
			}
			System.out.println("Remote updater shutdown. "+n+" unsent updates saved.");
		}
	};
	
	public static Thread getHook() {
		return instance.hook;
	}

	public void removeRemote(Remote r) {
		synchronized(remotes) {
			remotes.remove(r);
		}
		synchronized(instance) {
			instance.notify();
		}
	}
	
	public void removeRemote(String host, String event) {
		Remote target = null;
		synchronized(remotes) {
			for(Remote r : remotes) {
				if(r.getHost().equals(host) && r.getEvent().equals(event)) {
					target = r;
					break;
				}
			}
		}
		if(target != null) {
			removeRemote(target);
		}
	}

	public void addRemote(Remote remote) {
		synchronized(remotes){
			remotes.add(remote);
		}
		synchronized(this){
			this.notify();
		}
		
	}

	public void sendNow() {
		//Force send now for high-priority traffic! (like alliance selection updates)
		synchronized(instance) {
			instance.notifyAll();
		}
	}
}
