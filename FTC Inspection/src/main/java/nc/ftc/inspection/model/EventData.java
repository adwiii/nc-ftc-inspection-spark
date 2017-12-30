package nc.ftc.inspection.model;

public class EventData implements Comparable<EventData> {
	private String code;
	private String name;
	private int status;
	private java.sql.Date date;
	public static final int FUTURE = 0;
	public static final int SETUP = 1;
	public static final int INSPECTION = 2;
	public static final int QUALS = 3;
	public static final int SELECTION = 4;
	public static final int ELIMS = 5;
	public static final int FINAL = 6;

	public EventData(String code, String name, int status, java.sql.Date date){
		this.code = code;
		this.name = name;
		this.status= status;
		this.date = date;
	}
	
	public String getCode() {
		return code;
	}

	public String getName() {
		return name;
	}

	public int getStatus() {
		return status;
	}

	public java.sql.Date getDate() {
		return date;
	}

	@Override
	public int compareTo(EventData other) {
		int comp = (this.getDate() != null && other.getDate() != null) ? this.getDate().compareTo(other.getDate()) : 0;
		if (comp == 0) {
			comp = (this.getCode() != null && other.getCode() != null) ? this.getCode().compareTo(other.getCode()) : 0;
		}
		return comp;
	}


}
