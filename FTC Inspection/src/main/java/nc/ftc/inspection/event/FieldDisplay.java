package nc.ftc.inspection.event;

public class FieldDisplay {
	public Object timerCommandLock = new Object();
	//When the timer first loads, it should check for the last command. Maybe.
	TimerCommand lastCommand = TimerCommand.LOAD_MATCH;
	
	public TimerCommand getLastCommand() {
		return lastCommand;
	}
	
	public void issueCommand(TimerCommand cmd) {
		lastCommand = cmd;
		synchronized(timerCommandLock) {
			timerCommandLock.notifyAll();
		}
	}
}
