package nc.ftc.inspection.model;

import java.util.Arrays;
import java.util.Comparator;

public class FormRow {
	public class Item{
		int index;
		int team;
		public Item(int index, int team){
			this.index = index;
			this.team = team;
		}
		
		public String getId(){
			return formID+"_"+team+"_"+index;
		}
	}
	public  class HeaderItem extends Item{
		public String label;
		public HeaderItem(int index, int team, String label){
			super(index, team);
			this.label = label;
		}
		public String getLabel(){
			return label;
		}
	}
	public class DataItem extends Item{
		boolean checked;
		int required;
		public DataItem(int index, int team, int req, boolean checked){
			super(index, team);
			this.required = req;
			this.checked = checked;
		}
		public int getRequired(){
			return required;
		}
		public boolean getChecked(){
			return checked;
		}
	}
	String formID;
	int type;
	int row;
	Item[] items;
	String description;
	String rule;
	public static final int REQUIRED = 1;
	public static final int OPTIONAL = 2;
	public static final int NA = 0;
	public static final int HEADER = 1;
	public static final int NON_HEADER = 2;
	private int pointer = 0;
	public FormRow(String form, int type, int row, int columnCount, String d, String rule){
		this.formID = form;
		this.type = type;
		this.items = new Item[columnCount];
		this.row = row;
		this.description = d == null ? null : d.replaceAll("<", "&lt;");
		this.rule = rule == null ? null : rule.replaceAll("<", "&lt;");
		
	}
	public void addHeaderItem(int index, String label, int team){
		items[pointer++] = new HeaderItem(index, team, label);
	}
	public void addDataItem(int index, int req, boolean cb, int team){
		items[pointer++] = new DataItem(index, team, req, cb);
	}
	public int getRow(){
		return row;
	}
	public int getType(){
		return type;
	}
	public String getRule(){
		return rule;
	}
	public String getDescription(){
		return description;
	}
	public Item[] getItems(){
		return items;
	}
	public void postProcess(){
		//columnData.length / teams.length = number of columns per team
		/*
		 * ATM, all items are in order of index. This means for 2 teams, the items
		 * are like this:  (format= I<item #>:<team #>) I1:1, I1:2, I2:1, I2:2.
		 * We want I1:1, I2:1, I1:2, I2:2
		 *	Thus, sort by team, then index. 
		 */
		Arrays.sort(items, Comparator.comparingInt((Item item)->item.team).thenComparingInt((Item item)->item.index));
	}
	public String toString(){
		String s = "";
		for(Item o : items){
			s += o.index + "|";
		}
		s += description + "|"+rule;
		return s;
	}

}
