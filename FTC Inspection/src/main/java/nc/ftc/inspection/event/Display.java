package nc.ftc.inspection.event;

import nc.ftc.inspection.model.MatchResult;

public class Display {
	public Object displayCommandLock = new Object();
	DisplayCommand lastCommand = DisplayCommand.SHOW_MATCH;
	MatchResult result;
	
	public DisplayCommand getLastCommand() {
		return lastCommand;
	}
	
	public void issueCommand(DisplayCommand cmd) {
		this.lastCommand = cmd;
		synchronized(displayCommandLock) {
			displayCommandLock.notifyAll();
		}
	}
	
	public DisplayCommand blockForNextCommand() throws InterruptedException {
		synchronized(displayCommandLock) {
			displayCommandLock.wait();
		}
		return lastCommand;
	}
}
