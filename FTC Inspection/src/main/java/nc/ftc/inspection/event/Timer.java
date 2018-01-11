package nc.ftc.inspection.event;

import nc.ftc.inspection.model.MatchStatus;

public class Timer {
	public Object timerCommandLock = new Object();
	public Object waitForEndLock = new Object();
	//When the timer first loads, it should check for the last command. Maybe.
	TimerCommand lastCommand = TimerCommand.LOAD_MATCH;
	
	long start = 0;
	long pauseStart = 0;
	volatile boolean paused = false;
	volatile boolean destroyed = false;
	
	public Event event;
	
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
	
	public Timer(Event e) {
		eventDispatch.start();
		this.event = e;
	}
	
	public void destroy() {
		destroyed = true;
		eventDispatch.interrupt();
	}	
	
	public TimerCommand getLastCommand() {
		return lastCommand;
	}
	
	public void issueCommand(TimerCommand cmd) {
		lastCommand = cmd;
		synchronized(timerCommandLock) {
			timerCommandLock.notifyAll();
		}
	}
	
	public TimerCommand blockForNextCommand() throws InterruptedException {
		synchronized(timerCommandLock) {
			timerCommandLock.wait();
		}
		return lastCommand;
	}
	
	public void start() {
		start = System.currentTimeMillis();
		paused= false;
		issueCommand(TimerCommand.START);
		synchronized(eventDispatch) {
			eventDispatch.notify();
		}
	}
	public void pause() {
		pauseStart = System.currentTimeMillis();
		paused = true;
		issueCommand(TimerCommand.PAUSE);
	}
	public void resume() {
		start += System.currentTimeMillis() - pauseStart;
		paused = false;
		issueCommand(TimerCommand.RESUME);
		synchronized(eventDispatch) {
			eventDispatch.notify();
		}
	}
	public void reset() {
		//need to notify eait for end, waiting method's responsibility to check if it was reset and hadle it
		eventDispatch.interrupt();
		issueCommand(TimerCommand.RESET);
	}
	
	public long elapsed() {
		return paused ? pauseStart - start : System.currentTimeMillis() - start;
	}
	public boolean paused() {
		return paused;
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
