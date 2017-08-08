package nc.ftc.inspection;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

import nc.ftc.inspection.model.User;

public class AuthenticationManager {
	static long DEFAULT_SESSION_LENGTH = 100 * 60 * 60 * 24; //24hr default session length
	static Map<String, Session> sessions = new HashMap<>();

	public static boolean isLoggedIn(String sessionToken) {
		if (sessionToken != null) {
			Session session = sessions.get(sessionToken);
			if (System.currentTimeMillis() < session.getExpirationTime() || session.getExpirationTime() == -1) {
				return true;
			} else {
				sessions.remove(sessionToken);
			}
		}
		return false;
	}

	public static String getNewSession(User user, long expirationTime) {
		while (true) {
			SecureRandom random = new SecureRandom();
			byte bytes[] = new byte[128];
			random.nextBytes(bytes);
			String token = user.getUsername().hashCode() + bytes.toString();
			if (!sessions.containsKey(token)) {
				sessions.put(token, new Session(user, expirationTime));
				return token;
			}
		}
	}
	
	public static String getNewSession(User user) {
		return getNewSession(user, System.currentTimeMillis() + DEFAULT_SESSION_LENGTH);
	}

	public static class Session {
		private User user;
		private long expirationTime;

		public Session(User user, long expirationTime) {
			this.user = user;
			this.expirationTime = expirationTime;
		}

		
		public User getUser() {
			return user;
		}
		
		public long getExpirationTime() {
			return expirationTime;
		}
	}

	public static int getUserType(String sessionToken) {
		Session session = sessions.get(sessionToken);
		if (session != null) {
			return session.getUser().getType();
		}
		return User.NONE;
	}
}
