package com.mx.cryptomonitor.integration.controller;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MarketDataControllerIntegrationTest {
	
	@Autowired
	private MockMvc mockMvc;
	
	@Autowired
	private RestTemplateBuilder restTemplateBuilder;
	
	private MockRestServiceServer mockServer;
	
	@BeforeEach
	void setup() {
		RestTemplate restTemplate = restTemplateBuilder.build();
		mockServer = MockRestServiceServer.createServer(restTemplate);
	}	

	@Test
	void getHistoricalPrice_returnsCorrectValue() throws Exception{
		
		String symbol = "AMZN";
		LocalDate date = LocalDate.of(2025, 4, 17);
        String expectedUrl = "https://www.alphavantage.co/query?function=TIME_SERIES_DAILY&symbol=AMZN&apikey=demo";

		
		String jsonResponse = """
        		{
    		    "Meta Data": {
    		        "1. Information": "Daily Prices (open, high, low, close) and Volumes",
    		        "2. Symbol": "AMZN",
    		        "3. Last Refreshed": "2025-04-21",
    		        "4. Output Size": "Compact",
    		        "5. Time Zone": "US/Eastern"
    		    },
    		                "Time Series (Daily)": {                
    		        		    
    		        "2025-04-21": {
    		            "1. open": "169.6000",
    		            "2. high": "169.6000",
    		            "3. low": "165.2850",
    		            "4. close": "167.3200",
    		            "5. volume": "48126111"
    		        },
    		        "2025-04-17": {
    		            "1. open": "176.0000",
    		            "2. high": "176.2100",
    		            "3. low": "172.0000",
    		            "4. close": "172.6100",
    		            "5. volume": "44726453"
    		        },
    		        "2025-04-16": {
    		            "1. open": "176.2900",
    		            "2. high": "179.1046",
    		            "3. low": "171.4100",
    		            "4. close": "174.3300",
    		            "5. volume": "51875316"
    		        },
    		        "2025-04-15": {
    		            "1. open": "181.4100",
    		            "2. high": "182.3500",
    		            "3. low": "177.9331",
    		            "4. close": "179.5900",
    		            "5. volume": "43641952"
    		        }

    		                }
    		            }
    		            """;
		
		mockServer.expect(requestTo(expectedUrl))
			.andExpect(method(HttpMethod.GET))
			.andRespond(
					withSuccess(jsonResponse, MediaType.APPLICATION_JSON)
			);
		
        mockMvc.perform(get("/api/v1/marketdata/stock/historical/{symbol}/{date}", symbol, date)
		       .contentType(MediaType.APPLICATION_JSON))
			   .andExpect(status().isOk())
			   //.andExpect(jsonPath("$.symbol").value(symbol))
			   .andExpect(content().string("172.6100"))
			   .andDo(print());
		
		//mockServer.verify();
	}
}
