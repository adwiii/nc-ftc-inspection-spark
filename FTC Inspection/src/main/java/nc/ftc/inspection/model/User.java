package nc.ftc.inspection.model;

public class User {
	
	public static int SYSADMIN = -1;
	public static int ADMIN = 0;
	public static int KEY_VOLUNTEER = 10;
	public static int VOLUNTEER = 20;
	public static int TEAM = 30;
	public static int GENERAL = 40;
	
	public static int NONE = Integer.MAX_VALUE;
	
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

	/**
	 * This checks to see if this users type is at least the type given.
	 * @param type The type to check against
	 * @return if this user is at least the type given
	 */
	public boolean is(int type) {
		return this.type <= type;
	}
	
	/**
	 * Checks to see if this user strictly outranks the other user.
	 * @param other The user to check against
	 * @return if this user strictly outranks the other user
	 */
	public boolean outRanks(User other) {
		return this.type < other.type;
	}
	

}
