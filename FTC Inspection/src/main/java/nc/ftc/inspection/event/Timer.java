/*
 * Copyright (c) 2016-2018 Thomas Barnette and Trey Woodlief
 * All Rights Reserved.
 */

package nc.ftc.inspection.event;

import nc.ftc.inspection.model.MatchStatus;

/**
 * The Timer class maintains the Server's match timer as a reference for the rendering of other pages 
 * and state of the backend. It it used primarily for notifying threads that are waiting for
 * a specific point in the match cycle. (Ex: waiting for match start, end of auto, or end of match).
 * It is also used to distribute timer commands, such as Start, Pause, Resume, Reset.
 */
public class Timer {
	public Object waitForStartLock = new Object();
	public Object timerCommandLock = new Object();
	public Object waitForEndLock = new Object();
	//When the timer first loads, it should check for the last command. Maybe.
	/**
	 * Stores the last command send to all Display Timers.
	 */
	TimerCommand lastCommand = TimerCommand.LOAD_MATCH;
	
	/**
	 * Set to true when the timer receives the start command. Cleared when reset or by Event class on match load.
	 */
	long start = 0;
	long pauseStart = 0;
	boolean started = false;
	volatile boolean paused = false;
	volatile boolean destroyed = false;
	
	public Event event;
	
	/**
	 * This Thread handles the notification of events at the end of autonomous and end of the match.
	 */
	private Thread eventDispatch = new Thread("Match Timer Thread") {
		public void run() {
			while(!destroyed) {
				try {
					//wait for start
					synchronized(this) {
						this.wait();						
					}
					
					//TODO this could be rewritten more intelligently to have less busy waiting
					//use wait(timeout) for auto/teleop where timeout is like 100ms before expected time
					//, pause calls interrupt, etc
					//for now it polls every 50ms to check for changes
					
					
					//check for end of auto
					while(!isInterrupted()) {
						//put syso to test pausing and stuff
						if(elapsed() > 30000) {
							//end of auto
							event.getCurrentMatch().setStatus(MatchStatus.TELEOP);
							break;
						} 
						if(paused) {
							synchronized(this) {
								this.wait();						
							}
						}
						Thread.sleep(50);
					}
					//check for end of teleop
					while(!isInterrupted()) {
						if(elapsed() > 158000) {
							//end of match
							event.getCurrentMatch().setStatus(MatchStatus.REVIEW);
							event.loadNextMatch();
							synchronized(waitForEndLock) {
								waitForEndLock.notifyAll();
							}
							break;
						}
						if(paused) {
							synchronized(this) {
								this.wait();						
							}
						}
						Thread.sleep(50);
					}					
				}catch(InterruptedException e) {
					//interrupt at any time resets timer
				}
			}
		}
	};
	/**
	 * Each Timer is tied to a specific event, and maintains a reference to that event
	 *  so it can modify the status of the Event's current match.
	 * @param e
	 */
	public Timer(Event e) {
		eventDispatch.start();
		this.event = e;
	}
	/**
	 * Kills the dispatch thread. Once this is called this Timer can no longer be used.
	 */
	public void destroy() {
		destroyed = true;
		eventDispatch.interrupt();
	}	
	/**
	 * Returns the last command send to Display Timers.
	 * @return The last command
	 */
	public TimerCommand getLastCommand() {
		return lastCommand;
	}
	
	/**
	 * Issues a command to all listening Display Timers by notifying all threads 
	 * that are waiting on the timerCommandLock.
	 * @param cmd The command to issue.
	 */
	public void issueCommand(TimerCommand cmd) {
		lastCommand = cmd;
		synchronized(timerCommandLock) {
			timerCommandLock.notifyAll();
		}
	}
	
	/**
	 * This method will block the calling thread until the next timer command is issued.
	 * This is used by endpoints servicing the displays. This forms the back-end of the
	 * long-polling method utilized by the timing system.
	 * @return The next Timer Command
	 * @throws InterruptedException if interrupted while blocking
	 */
	public TimerCommand blockForNextCommand() throws InterruptedException {
		synchronized(timerCommandLock) {
			timerCommandLock.wait();
		}
		return lastCommand;
	}
	
	/**
	 * Starts the timer. This stores a reference to the current time to privide millisecond resolution at any time queried. 
	 * This issues the START command to all display timers and notifies the dispatch thread to begin the timer. It then 
	 * notifies all threads waiting for match start.
	 */
	public void start() {
		start = System.currentTimeMillis();
		started = true;
		paused= false;
		issueCommand(TimerCommand.START);
		synchronized(eventDispatch) {
			eventDispatch.notify();
		}
		synchronized(waitForStartLock) {
			waitForStartLock.notifyAll();
		}
	}
	/**
	 * Pauses the timer. This sends the PAUSE command to all display timers, and stores the state of the timer.
	 */
	public void pause() {
		pauseStart = System.currentTimeMillis();
		paused = true;
		issueCommand(TimerCommand.PAUSE);
	}
	/**
	 * Resumes a paused timer. This sends the RESUME command to all display timers, and bumpts the start time 
	 * forward by the duration of the pause. It then awakens the dispatch thread.
	 */
	public void resume() {
		start += System.currentTimeMillis() - pauseStart;
		paused = false;
		issueCommand(TimerCommand.RESUME);
		synchronized(eventDispatch) {
			eventDispatch.notify();
		}
	}
	/**
	 * Resets the time on the timer.
	 */
	public void reset() {
		//need to notify eait for end, waiting method's responsibility to check if it was reset and hadle it
		eventDispatch.interrupt();
		issueCommand(TimerCommand.RESET);
		started = false;
	}
	/**
	 * Returns the amount of time elapsed. This is done by comparing the current system time
	 * to the stored start time.
	 * @return The time elapsed in ms.
	 */
	public long elapsed() {
		return paused ? pauseStart - start : System.currentTimeMillis() - start;
	}
	/**
	 * Returns true if the timer is paused.
	 * @return true is paused
	 */
	public boolean paused() {
		return paused;
	}
	/**
	 * Returns true is the timer has been started. This returns true until reset (match abort) or the load of the next match.
	 * @return
	 */
	public boolean isStarted() {
		return started;
	}
	/*Timer system:
	Start button pressed on control page:
			Send start POST to server
			server records START
			sends START command to all listening Field & Audience DIsplays
				They start timer - only comms is listening for pause/reset command
			Server starts its timer
				Server sends status info to control page (end auto, end teleop)
			Server responds to control page
				control page starts timer - control page timer is a slave timer and only displays time
			
	
	
	
	*/
}
