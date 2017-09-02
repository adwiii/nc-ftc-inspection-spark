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
		String label;
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
	}
	String formID;
	int type;
	Object[] columnData; //String for header, int for cb (int is index)
	int[] req;//requirement level for each checkbox (if not header)
	boolean[] checked;//checked status for each checkbox (if not header)
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
		this.columnData = new Object[columnCount];
		if(type == NON_HEADER){
			checked = new boolean[columnCount];
			req = new int[columnCount];
		}
		this.row = row;
		this.description = d;
		this.rule = rule.replaceAll("<", "&lt;");
		
	}
	public void addHeaderItem(int index, String label, int team){
//		System.out.println(item);
//		columnData[pointer++] = item;
		items[pointer++] = new HeaderItem(index, team, label);
	}
	public void addDataItem(int index, int req, boolean cb, int team){
		items[pointer++] = new DataItem(index, team, req, cb);
//		this.req[pointer] = req;
//		this.checked[pointer] = cb;
//		addItemData(item);
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
	public Object[] getColumnData(){
		return columnData;
	}
	public boolean[] getChecked(){
		return checked;
	}
	public int[] getReq(){
		return req;
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
		for(Object o : columnData){
			s += o + "|";
		}
		s += description + "|"+rule;
		return s;
	}
	//public void addColumn(int )

}
