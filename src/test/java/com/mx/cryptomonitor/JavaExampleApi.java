package com.mx.cryptomonitor;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.NameValuePair;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;



public class JavaExampleApi {

	private static String apiKey = "c629547d-6dc8-4f8f-8ece-dd7de11b6c57";

	  public static void main(String[] args) {
	    String uri = "https://sandbox-api.coinmarketcap.com/v1/cryptocurrency/listings/latest";
	    List<NameValuePair> paratmers = new ArrayList<NameValuePair>();
	    paratmers.add(new BasicNameValuePair("start","1"));
	    paratmers.add(new BasicNameValuePair("limit","5000"));
	    paratmers.add(new BasicNameValuePair("convert","USD"));

	    try {
	      String result = makeAPICall(uri, paratmers);
	      System.out.println(result);
	    } catch (IOException e) {
	      System.out.println("Error: cannont access content - " + e.toString());
	    } catch (URISyntaxException e) {
	      System.out.println("Error: Invalid URL " + e.toString());
	    }
	  }

	  public static String makeAPICall(String uri, List<NameValuePair> parameters)
	      throws URISyntaxException, IOException {
	    String response_content = "";

	    URIBuilder query = new URIBuilder(uri);
	    query.addParameters(parameters);

	    CloseableHttpClient client = HttpClients.createDefault();
	    HttpGet request = new HttpGet(query.build());

	    request.setHeader(HttpHeaders.ACCEPT, "application/json");
	    request.addHeader("X-CMC_PRO_API_KEY", apiKey);

	    CloseableHttpResponse response = client.execute(request);

	    try {
	      System.out.println(response.getStatusLine());
	      HttpEntity entity = response.getEntity();
	      response_content = EntityUtils.toString(entity);
	      EntityUtils.consume(entity);
	    } finally {
	      response.close();
	    }

	    return response_content;
	  }
}
