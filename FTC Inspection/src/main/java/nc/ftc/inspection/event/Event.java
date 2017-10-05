package nc.ftc.inspection.event;

import nc.ftc.inspection.model.EventData;
import nc.ftc.inspection.model.Match;

public class Event {
	EventData data;
	Match currentMatch;
	public Event(EventData ed){
		this.data = ed;
	}
	public Match getCurrentMatch(){
		return currentMatch;
	}
	public void setCurrentMatch(Match nextMatch) {
		currentMatch = nextMatch;
		
	}
}
