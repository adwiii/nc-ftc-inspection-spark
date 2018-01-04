package nc.ftc.inspection.model;

import java.text.Normalizer.Form;
import java.util.HashMap;
import java.util.Map;

public class Team {
	private int number;
	private String name;	
	private int numDigits;
	//TODO if need to keep status in RAM, add a byte[] and final ints HW, SW, etc for index
	byte[] status = new byte[5];
	
	public enum FormIndex{
		CI(0),
		SC(1),
		HW(2),
		SW(3),
		FD(4);
		public final int index;
		FormIndex(int i){
			this.index = i;
		}
		
	}

	
	public Team(int number, String name){
		this.number = number;
		this.name = name;
		if (number < 10) {
			numDigits = 1;
		} else if (number < 1E2) {
			numDigits = 2;
		} else if (number < 1E3) {
			numDigits = 3;
		} else if (number < 1E4) {
			numDigits = 4;
		} else if (number < 1E5) {
			numDigits = 5;
		} else if (number < 1E6) {
			numDigits = 6;
		}
	}
	
	public void setStatus(String field, byte status){
		setStatus(FormIndex.valueOf(field.toUpperCase()).index, status);
	}
	
	public void setStatus(int index, byte status){
		this.status[index] = status;
	}
	
	public byte getStatus(int index){
		return this.status[index];
	}
	
	public int getStatus(String field){
		return getStatus(FormIndex.valueOf(field.toUpperCase()).index);
	}
	
	public String getName(){
		return name;
	}
	public int getNumber(){
		return number;
	}
	public int getNumDigits() {
		return numDigits;
	}
	public String toString(){
		return "{\"number\":"+number+", \"name\":\""+name+"\"}";
	}
	public String toStatusString(){
		String r = "{\"number\":"+number+", \"name\":\""+name+"\"";
		for(FormIndex f : FormIndex.values()){
			r += ", \"" + f.toString() + "\": " + getStatus(f.index); 
		}
		return r + "}";
		
	}
}
