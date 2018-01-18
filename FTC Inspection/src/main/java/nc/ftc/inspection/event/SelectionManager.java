package nc.ftc.inspection.event;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.internal.Excluder;

import nc.ftc.inspection.dao.EventDAO;
import nc.ftc.inspection.model.Selection;
import nc.ftc.inspection.model.Team;

public class SelectionManager {
	transient Event event;
	Team[][] alliances = new Team[4][3];
	List<Rank> available;
	transient List<Selection> selections;
	/**
	 * the Slot to fill:
	 * 0: A1 Captain
	 * 1: A1 Pick 1
	 * 2: A2 Captain
	 * 3: A2 Pick
	 * 4: A3 Captain
	 * 5: A3 Pick
	 * 6: A4 Captain
	 * 7: A4 Pick
	 * 8: A1 Pick
	 * 9: A2 Pick
	 * 10: A3 Pick
	 * 11: A4 Pick;
	 * 
	 * Math:
	 * 0,2,4,6 = captains
	 * 0,1,8 = A1
	 * 2,3,9 = A2
	 * 4,5,10 = A3
	 * 6,7,11 = A4
	 */
	transient int slot = 0;
	public SelectionManager(Event e) {
		this.event = e;
	}
	public void init() {
		slot = 0;
		event.calculateRankings();
		available = new ArrayList<Rank>(event.rankings);
		alliances = new Team[4][3];
		//if previously started, load state
		selections = EventDAO.getSelections(event.getData().getCode());
		for(Selection s : selections) {
			executeSelection(s);
		}
		event.getDisplay().issueCommand(DisplayCommand.SHOW_SELECTION);
	}
	
	/**
	 * op is either ACCEPT OR DECLINE. This method will determine if ACCEPT should be replaced with CAPTAIN
	 */
	public void createAndExecute(int team, int op) {
		//determine what the next selection is and execute it.
		int alliance = 0;
		if(op == Selection.ACCEPT) {
			if(slot < 8 && slot % 2 == 0) {
				op = Selection.CAPTAIN;
				alliance = (slot / 2) + 1;
			} else {
				alliance = slot < 8 ? (slot / 2) + 1 : slot - 7;
			}
		} else { //decline.
			alliance = slot < 8 ? (slot / 2) + 1 : slot - 7;
		}
		
		try {
			Selection s = new Selection(0, op, alliance, team);
			executeSelection(s);
			selections.add(s);
			EventDAO.saveSelection(event.getData().getCode(), op, alliance, team);
		} catch(Exception e2) {
			throw e2;
		}
		event.getDisplay().issueCommand(DisplayCommand.SHOW_SELECTION);
	}
	
	public void undoSelection() {
		Selection s = selections.remove(selections.size() - 1);
		EventDAO.undoSelection(event.getData().getCode());
		Iterator<Rank> it = null;
		Rank r = null;
		Team t = null;
		switch(s.getOp()) {
			case Selection.CAPTAIN:
				t = alliances[slot / 2][0];
				alliances[slot / 2][0] = null;
				r = null;
				it = event.rankings.iterator();
				while(it.hasNext()) {
					r = it.next();
					if(r.getTeam().getNumber() == t.getNumber()) {
						break;
					}
					r = null;
				}
				if(r == null) {
					throw new IllegalArgumentException("Invalid Selection!");
				}
				//captain has to be highest rank available.
				available.add(0, r);
				slot--;
				if(slot < 0 )slot = 0;
				break;
			case Selection.ACCEPT:
				//remove from alliances
				slot--;
				t = alliances[s.getAlliance() - 1][slot < 8 ? 1 : 2];
				alliances[s.getAlliance() - 1][slot < 8 ? 1 : 2] = null;
				if(t == null) {
					slot++;//this should never happen
				}
				//fall through to add back to available
			case Selection.DECLINE:
				r = null;
				it = event.rankings.iterator();
				while(it.hasNext()) {
					r = it.next();
					if(r.getTeam().getNumber() == s.getTeam()) {
						break;
					}
					r = null;
				}
				if(r == null) {
					//we really broke stuff!
					throw new IllegalArgumentException("Invalid Selection!");
				}
				available.add(r);
				available.sort(Comparator.comparingInt(Rank::getRank));
				break;
		}
		event.getDisplay().issueCommand(DisplayCommand.SHOW_SELECTION);
	}
	
	public void executeSelection(Selection s) {
		if(slot > 11) {
			throw new IllegalStateException("Alliances Full!");
		}
		Rank r = null;
		Iterator<Rank> it = null;
		boolean flag = false;
		switch(s.getOp()) {
			case Selection.CAPTAIN:
				it = available.iterator();
				r = null;
				flag = false;
				while(it.hasNext()) {
					r = it.next();
					if(r.getTeam().getNumber() == s.getTeam()) {
						it.remove();
						flag = true;
						break;
					}
				}
				if(!flag) {
					throw new IllegalArgumentException("Illegal Selection!");
				}
				alliances[s.getAlliance() - 1][0] = r.getTeam();
				slot++;
				break;
			case Selection.ACCEPT:
				it = available.iterator();
				r = null;
				flag = false;
				while(it.hasNext()) {
					r = it.next();
					if(r.getTeam().getNumber() == s.getTeam()) {
						it.remove();
						flag = true;
						break;
					}
				}
				if(!flag) {
					throw new IllegalArgumentException("Illegal Selection!");
				}
				alliances[s.getAlliance() - 1][slot < 8 ? 1 : 2] = r.getTeam();
				slot++;
				break;
			case Selection.DECLINE:
				it = available.iterator();
				r = null;
				flag = false;
				while(it.hasNext()) {
					r = it.next();
					if(r.getTeam().getNumber() == s.getTeam()) {
						it.remove();
						flag = true;
						break;
					}
				}
				if(!flag) {
					throw new IllegalArgumentException("Illegal Selection!");
				}
				break;
			default:throw new IllegalArgumentException("Illegal Selection!");				
		}
		
	}
	
	class Wrap1{
		public int team;
		public int rank;
		public String name;
		public Wrap1(Rank r) {
			team = r.getTeam().getNumber();
			name = r.getTeam().getName();
			rank = r.getRank();
		}
	}
	class Wrap2{
		public Team[][] alliances;
		public List<Wrap1> available;
	}
	
	public String getSelectionJSON() {
		
		Wrap2 obj = new Wrap2();
		obj.alliances = alliances;
		obj.available = new ArrayList<Wrap1>(available.size());
		for(int i = 0; i < available.size(); i++) {
			obj.available.add(new Wrap1(available.get(i)));
		}
		return new Gson().toJson(obj);
	}
	
	public void clearSelection() {
		EventDAO.clearSelections(event.getData().getCode());
		this.init();
		event.getDisplay().issueCommand(DisplayCommand.SHOW_SELECTION);
	}
	
	public void saveSelection() {
		//populate alliances table,
		//generate SF matches
	}
}
