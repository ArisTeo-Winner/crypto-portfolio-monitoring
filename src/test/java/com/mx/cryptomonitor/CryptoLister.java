package com.mx.cryptomonitor;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import org.apache.http.HttpRequest;
import org.apache.http.client.HttpClient;

public class CryptoLister {

    public static void main(String[] args) {
        String apiUrl = "https://pro-api.coinmarketcap.com/v1/cryptocurrency/listings/latest?limit=1";
        String apiKey = "c629547d-6dc8-4f8f-8ece-dd7de11b6c57";

        try {
            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("X-CMC_PRO_API_KEY", apiKey);
            conn.setRequestProperty("Accept", "application/json");

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String inputLine;
                StringBuilder response = new StringBuilder();

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                System.out.println("Respuesta del Servidor:");
                System.out.println(response.toString());
            } else {
                System.out.println("Error: " + responseCode);
            }

            conn.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
