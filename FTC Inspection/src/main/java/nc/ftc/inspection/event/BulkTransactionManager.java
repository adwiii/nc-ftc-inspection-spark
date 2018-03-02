package nc.ftc.inspection.event;

import java.util.LinkedList;
import java.util.Queue;

import nc.ftc.inspection.Update;
import nc.ftc.inspection.dao.EventDAO;

/**
 * This class provides a synchronized Queue for SQL updates for use in situations where a large number
 * of updates may occur in close time proximity. Every interval, it flushes the queue in one SQL transaction,
 * which is significantly faster than a bunch of individual transactions. Currently, this class is only compatible
 * with EventDAO SQL operations, but can easily be extended in the future for other uses. It utilizes the same Update class
 * used by the Remote Update system.
 * @author Thomas
 *
 */
public class BulkTransactionManager extends Thread{
	private Queue<Update> queue = new LinkedList<Update>();
	private volatile boolean shutdown = false;
	long interval = 0;
	public Event event;
	
	/**
	 * Creates a BulkTransactionManager tied to the specified event that commits 
	 * every interval.
	 * @param e The event associated with this BulkTransaction.
	 * @param interval The update interval in ms
	 */
	public BulkTransactionManager(Event e, long interval) {
		this.interval = interval;
		this.event = e;
		Runtime.getRuntime().addShutdownHook(getHook());
		start();
	}
	public BulkTransactionManager(Event e) {
		this(e, 60*1000);//default to every minute
	}
	/**
	 * Adds the given update to the queue.
	 * @param update
	 */
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
	
	/**
	 * Returns a copy of the Queue
	 * @return
	 */
	public Queue<Update> getQueueClone(){
		Queue<Update> clone;
		synchronized(this) {
			clone = new LinkedList<Update>(queue);
		}
		return clone;
	}
	
	/**
	 * Forces the bulk transaction to occur now.
	 */
	public void writeNow() {
		synchronized(this) {
			this.notifyAll();
		}
	}
	/**
	 * The shutdown hook to ensure data gets saved on graceful shutdown.
	 */
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
	
	/**
	 * Returns the shutdown hook.
	 * @return
	 */
	public Thread getHook() {
		return this.hook;
	}
	

}
