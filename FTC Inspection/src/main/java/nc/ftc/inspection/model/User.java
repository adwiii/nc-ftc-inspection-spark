package nc.ftc.inspection.model;

public class User {
	private String username;
	private String realName;
	private String hashedPw;
	private String salt;
	private int type;
	
	public User(String username, String hashedPw, String salt, int type, String rn){
		this.username = username;
		this.hashedPw = hashedPw;
		this.salt = salt;
		this.type = type;
		this.realName = rn;
	}
	
	public String getUsername() {
		return username;
	}
	public void setUsername(String username) {
		this.username = username;
	}
	public String getHashedPw() {
		return hashedPw;
	}
	public void setHashedPw(String hashedPw) {
		this.hashedPw = hashedPw;
	}
	public String getSalt() {
		return salt;
	}
	public void setSalt(String salt) {
		this.salt = salt;
	}
	public int getType() {
		return type;
	}
	public void setType(int type) {
		this.type = type;
	}
	public String getRealName() {
		return realName;
	}
	public void setRealName(String realName) {
		this.realName = realName;
	}
	
	

}
