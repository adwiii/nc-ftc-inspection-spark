package nc.ftc.inspection.event;

import java.util.LinkedList;
import java.util.Queue;

import nc.ftc.inspection.Update;
import nc.ftc.inspection.dao.EventDAO;

public class BulkTransactionManager extends Thread{
	private Queue<Update> queue = new LinkedList<Update>();
	private volatile boolean shutdown = false;
	long interval = 0;
	public Event event;
	
	public BulkTransactionManager(Event e, long interval) {
		this.interval = interval;
		this.event = e;
		start();
	}
	public BulkTransactionManager(Event e) {
		this(e, 60*1000);//default to every minute
	}
	public synchronized void enqueue(Update update) {
		queue.offer(update);
	}
	
	public void run() {
		while(!shutdown) {
			synchronized(this) {
				try {
					this.wait(interval);
				} catch(InterruptedException e) {
					//TODO log this interrupt! 
					continue;
				}
			}
			if(queue.isEmpty()) {
				continue;
			}			
			//Initiate Bulk Transaction
			Queue<Update> clone = new LinkedList<Update>();
			synchronized(this) {
				while(!queue.isEmpty()) {
					clone.offer(queue.poll());
				}
			}
			//Call Bulk Transaction
			EventDAO.executeBulkTransaction(event.getData().getCode(), clone);
		}
	}
	
	public Queue<Update> getQueueClone(){
		Queue<Update> clone;
		synchronized(this) {
			clone = new LinkedList<Update>(queue);
		}
		return clone;
	}
	
	public void writeNow() {
		synchronized(this) {
			this.notifyAll();
		}
	}
	
	private Thread hook = new Thread() {
		public void run() {
			if(queue.isEmpty()){
				return;
			}
			System.out.println("Executing pending disk writes for " + getName() +" ("+ queue.size() + ")" );
			BulkTransactionManager.this.writeNow();
			System.out.println(getName()+ " complete.");
		}
	};
	
	public Thread getHook() {
		return this.hook;
	}
	

}
