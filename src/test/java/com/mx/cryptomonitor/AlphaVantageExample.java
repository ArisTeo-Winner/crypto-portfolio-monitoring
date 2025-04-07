package com.mx.cryptomonitor;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.json.JSONException;
import org.json.JSONObject;

public class AlphaVantageExample {

    public static void main(String[] args) {
        // Configura tus parámetros
        String apiKey = "2B9V32C85SDUIX5Y"; // Reemplaza con tu API key real
        String symbol = "MSFT";
        String function = "TIME_SERIES_DAILY";
        // outputsize=full para obtener el histórico completo
        String url = "https://www.alphavantage.co/query?function=" + function
                   + "&symbol=" + symbol
                   + "&apikey=" + apiKey;

        // Crea el HttpClient y la solicitud GET
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        try {
            // Envía la solicitud y obtiene la respuesta como String
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String jsonResponse = response.body();
            
            // Parsea la respuesta JSON
            JSONObject jsonObject = new JSONObject(jsonResponse);
            
            // Verifica si la respuesta contiene un mensaje de error o aviso
            if (jsonObject.has("Error Message")) {
                System.out.println("Error: " + jsonObject.getString("Error Message"));
                return;
            } else if (jsonObject.has("Note")) {
                System.out.println("Note: " + jsonObject.getString("Note"));
                return;
            }
            
            // Determina cuál clave usar (puede ser "Time Series (Daily)" o "Time Series (Daily Adjusted)")
            String timeSeriesKey = null;
            if (jsonObject.has("Time Series (Daily)")) {
                timeSeriesKey = "Time Series (Daily)";
            } else if (jsonObject.has("Time Series (Daily Adjusted)")) {
                timeSeriesKey = "Time Series (Daily Adjusted)";
            } else {
                System.out.println("No hay datos de series temporales en la respuesta.");
                System.out.println(jsonResponse);
                return;
            }
            
            JSONObject timeSeries = jsonObject.getJSONObject(timeSeriesKey);
            String fechaDeseada = "2024-09-23";
            
            if (timeSeries.has(fechaDeseada)) {
                // Crea un nuevo JSONObject que solo contenga la fecha deseada
                JSONObject resultadoFiltrado = new JSONObject();
                resultadoFiltrado.put(fechaDeseada, timeSeries.getJSONObject(fechaDeseada));
                
                // Imprime el resultado con una indentación de 4 espacios
                System.out.println(resultadoFiltrado.toString(4));
            } else {
                System.out.println("No hay datos para la fecha " + fechaDeseada);
            }
            
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
  }
