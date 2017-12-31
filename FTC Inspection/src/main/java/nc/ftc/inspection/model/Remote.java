package nc.ftc.inspection.model;

import java.io.IOException;
import java.util.List;

import org.apache.http.Consts;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

public class Remote {
	String host;
	String key;
	public Remote(String h, String k) {
		this.host = h;
		this.key = k;
	}
	public void sendPOST(List<NameValuePair> form) throws IOException {
		HttpPost post = new HttpPost("http://localhost/update/");
		UrlEncodedFormEntity entity = new UrlEncodedFormEntity(form, Consts.UTF_8);
		post.setEntity(entity);
		try(CloseableHttpClient client = HttpClients.createMinimal()){
			client.execute(post);
		}		
	}

}
