package nc.ftc.inspection.model;

public class FormRow {
	String formID;
	int type;
	Object[] columnData; //String for header, int for cb (int is index)
	int[] req;//requirement level for each checkbox (if not header)
	boolean[] checked;//checked status for each checkbox (if not header)
	int row;
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
		this.columnData = new Object[columnCount];
		if(type == NON_HEADER){
			checked = new boolean[columnCount];
			req = new int[columnCount];
		}
		this.row = row;
		this.description = d;
		this.rule = rule;
		System.out.println("New Row: "+ row+": "+type);
		
	}
	public void addItemData(Object item){
		columnData[pointer++] = item;
	}
	public void addItemData(Object item, int req, boolean cb){
		this.req[pointer] = req;
		this.checked[pointer] = cb;
		addItemData(item);
	}
	public int getRow(){
		return row;
	}
	public int getType(){
		return type;
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
