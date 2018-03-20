/*
 * Copyright (c) 2016-2018 Thomas Barnette and Trey Woodlief
 * All Rights Reserved.
 */

package nc.ftc.inspection.event;

import nc.ftc.inspection.model.MatchResult;

public class Display {
	public Object displayCommandLock = new Object();
	DisplayCommand lastCommand = DisplayCommand.SHOW_MATCH;
	public MatchResult lastResult;
	public int red1Dif;
	public int red2Dif;
	public int blue1Dif;
	public int blue2Dif;
	
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
			//Thread.currentThread().setName("Display Command Response");
			displayCommandLock.wait();
		}
		return lastCommand;
	}
}
