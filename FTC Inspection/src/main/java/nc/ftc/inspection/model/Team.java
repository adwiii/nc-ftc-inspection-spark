package nc.ftc.inspection.model;

public class Team {
	private int number;
	private String name;	
	
	public Team(int number, String name){
		this.number = number;
		this.name = name;
	}
	
	public String getName(){
		return name;
	}
	public int getNumber(){
		return number;
	}
	public String toString(){
		return "{\"number\":"+number+", \"name\":\""+name+"\"}";
	}

}
