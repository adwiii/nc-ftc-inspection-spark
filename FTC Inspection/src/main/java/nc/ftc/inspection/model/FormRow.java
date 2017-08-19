package nc.ftc.inspection.model;

public class FormRow {
	String formID;
	char type;
	Object[] columnData; //String for header, int for cb (int is index)
	
	public FormRow(String form, char type, int columnCount){
		this.formID = form;
		this.type = type;
		this.columnData = new Object[columnCount];
		
		
	}
	//public void addColumn(int )

}
