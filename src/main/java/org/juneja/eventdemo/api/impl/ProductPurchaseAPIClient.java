package org.juneja.eventdemo.api.impl;

import java.io.BufferedReader;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.juneja.eventdemo.entity.Response;
import org.juneja.eventdemo.utils.AWSUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

/**
 * Makes sample call to the Product Purchase API
 * 
 * @author vivekjuneja
 *
 */

@RestController
public class ProductPurchaseAPIClient {

	AWSUtil aws = AWSUtil.newInstance();

	@Value("${isSubscriptionConfirmed}")
	private boolean isSubscriptionConfirmed;

	@Value("${uriForDataAPI}")
	private String uriForDataAPI;

	/**
	 * Health Check URL
	 * 
	 * @return
	 */
	@RequestMapping("/client/health")
	public Response healthCheck() {

		System.out.println("isSubscriptionConfirmed : "
				+ this.isSubscriptionConfirmed);
		System.out.println("uriForDataAPI : " + this.uriForDataAPI);

		return new Response("1", "Hello", "World");
	}
	
	@RequestMapping("/client/order")
	public Response orderReceived(HttpServletRequest request,
			HttpServletResponse response) {
		System.out.println("Order Received !");

		// If the Subscription has not been confirmed
		if (!isSubscriptionConfirmed) {
			return confirmSubscription(request, response);
		}

		// Get the Order from the SQS
		System.out.println("Reading and Deleting Message now");
		Response messageReturned = aws
				.receiveMessageFromQueue("TestQueue_OrderNum_2");
		System.out.println("Message Returned : " + messageReturned);

		// Process the messageReturned, extract the Product id and product
		// quantity to be purchased
		String[] messageReturnedArray = messageReturned.getResponseMessage()
				.split("[|]");
		String uuid = messageReturnedArray[0];
		String orderNum = messageReturnedArray[1];
		String callbackUri = messageReturnedArray[2];

		System.out.println("Order number " + orderNum + " is for uuid : "
				+ uuid);

		// Make call to the registered Callback
		RestTemplate rest = new RestTemplate();
		HttpHeaders headers = new HttpHeaders();

		headers.add("Content-Type", "application/json");
		headers.add("Accept", "*/*");

		// Find the current Quantity available for the relevant Product
		HttpEntity<String> requestEntity_Search = new HttpEntity<String>(
				headers);

		ResponseEntity<String> responseEntity_Callback = rest.exchange(
				(callbackUri + "?orderId=" + orderNum), HttpMethod.GET,
				requestEntity_Search, String.class);

		System.out.println("responseEntity_Search : "
				+ responseEntity_Callback.getStatusCode());
		System.out.println("responseEntity_Search : "
				+ responseEntity_Callback.getBody());

		// Delete the Order ID message from Queue
		if (responseEntity_Callback.getStatusCode().is2xxSuccessful()) {
			System.out
					.println("Success ! Now deleting the message from the Queue");
			aws.deleteMessageFromQueue("TestQueue_OrderNum_2", messageReturned);
		}

		return new Response("1", (uuid + " : " + orderNum), "");
	}

	/**
	 * Used for confirming subscription to Pub-Sub system
	 * 
	 * @param request
	 * @param response
	 * @return
	 */
	@RequestMapping("/client/subscription/confirm")
	public Response confirmSubscription(HttpServletRequest request,
			HttpServletResponse response) {
		StringBuffer jb = new StringBuffer();
		String line = null;
		try {
			BufferedReader reader = request.getReader();
			while ((line = reader.readLine()) != null)
				jb.append(line);
		} catch (Exception e) { /* report an error */
		}

		System.out.println("Json Body : " + jb);

		System.out.println("Caller : "
				+ request.getHeader("x-amz-sns-message-type"));

		/**
		 * TODO: Make call to confirm the subscription
		 */
		this.isSubscriptionConfirmed = true;

		return new Response("1", "Ok Works !", "200 OK");

	}

}
