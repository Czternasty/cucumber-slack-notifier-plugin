package org.jenkinsci.plugins.slacknotifier;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;

public class SlackClient {

	private static final Logger LOG = Logger.getLogger(SlackClient.class.getName());

	private static final String ENCODING = "UTF-8";
	private static final String CONTENT_TYPE = "application/json";

	private final String webhookUrl;
	private final String jenkinsUrl;
	private final String channel;
	private final boolean hideSuccessfulResults;

	public SlackClient(String webhookUrl, String jenkinsUrl, String channel, boolean hideSuccessfulResults) {
		this.webhookUrl = webhookUrl;
		this.jenkinsUrl = jenkinsUrl;
		this.channel = channel;
		this.hideSuccessfulResults = hideSuccessfulResults;
	}

	public void postToSlack(List<JsonElement> results, final String jobName, final int buildNumber, final String extra) {
		LOG.info("Publishing test report to slack channel: " + channel);
		CucumberResult result = results == null ? dummyResults() : processResults(results);
		String json = result.toSlackMessage(jobName, buildNumber, channel, jenkinsUrl, extra);
		postToSlack(json);
	}

	private CucumberResult dummyResults() {
		return new CucumberResult(Arrays.asList(new FeatureResult("Dummy Test", 100)),1,100);
	}

	
	private void postToSlack(String json) {
		LOG.fine("Json being posted: " + json);
		StringRequestEntity requestEntity = getStringRequestEntity(json);
		PostMethod postMethod = new PostMethod(webhookUrl);
		postMethod.setRequestEntity(requestEntity);
		postToSlack(postMethod);
	}

	private void postToSlack(PostMethod postMethod) {
		HttpClient http = new HttpClient();
		try {
			int status = http.executeMethod(postMethod);
			if (status != 200) {
				throw new RuntimeException("Received HTTP Status code [" + status + "] while posting to slack");
			}
		} catch (IOException e) {
			throw new RuntimeException("Message could not be posted", e);
		}
	}

	public CucumberResult processResults(List<JsonElement> resultsElements) {
		int totalScenarios = 0;
		int passPercent = 0;
		int failedScenarios = 0;
		List<FeatureResult> results = new ArrayList<FeatureResult>();
		for (JsonElement resultElement : resultsElements) {
			JsonArray features = resultElement.getAsJsonArray();
			for (JsonElement featureElement : features) {
				JsonObject feature = featureElement.getAsJsonObject();
				JsonArray elements = feature.get("elements").getAsJsonArray();
				int scenariosTotal = elements.size();
				int failed = 0;
				for (JsonElement scenarioElement : elements) {
					JsonObject scenario = scenarioElement.getAsJsonObject();
					JsonArray steps = scenario.get("steps").getAsJsonArray();
					for (JsonElement stepElement : steps) {
						JsonObject step = stepElement.getAsJsonObject();
						String result = step.get("result").getAsJsonObject().get("status").getAsString();
						if (!result.equals("passed")) {
							failed = failed + 1;
							failedScenarios = failedScenarios + 1;
							break;
						}
					}
				}
				totalScenarios = totalScenarios + scenariosTotal;
				final int scenarioPassPercent = Math.round(((scenariosTotal - failed) * 100) / scenariosTotal);
				if (scenarioPassPercent != 100 || !hideSuccessfulResults) {
					results.add(new FeatureResult(feature.get("uri").getAsString(), scenarioPassPercent));
				}
			}
		}

		passPercent = Math.round(((totalScenarios - failedScenarios) * 100) / totalScenarios);
		return new CucumberResult(results, totalScenarios, passPercent);
	}

	private StringRequestEntity getStringRequestEntity(String json) {
		try {
			return new StringRequestEntity(json, CONTENT_TYPE, ENCODING);
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(ENCODING + " encoding is not supported with [" + json + "]", e);
		}
	}
}
