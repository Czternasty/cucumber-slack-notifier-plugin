package org.jenkinsci.plugins.slacknotifier;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.stream.JsonReader;
import hudson.FilePath;
import hudson.model.Run;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import jenkins.model.JenkinsLocationConfiguration;
import org.apache.tools.ant.DirectoryScanner;
import sun.rmi.runtime.Log;

public class CucumberSlackService {

	private static final Logger LOG = Logger.getLogger(CucumberSlackService.class.getName());

	private final String webhookUrl;
	private final String jenkinsUrl;

	public CucumberSlackService(String webhookUrl) {
		this.webhookUrl = webhookUrl;
		this.jenkinsUrl = JenkinsLocationConfiguration.get().getUrl();
	}

	public void sendCucumberReportToSlack(Run<?,?> build, FilePath workspace, String json, String channel, String extra, boolean hideSuccessfulResults) {
		LOG.info("Posting cucumber reports to slack for '" + build.getParent().getDisplayName() + "'");
		LOG.info("Cucumber reports are in '" + workspace + "'");

		List<JsonElement> jsonElements = getResultFileAsJsonElement(workspace, json);

		SlackClient client = new SlackClient(webhookUrl, jenkinsUrl, channel, hideSuccessfulResults);
		client.postToSlack(jsonElements, build.getParent().getDisplayName(), build.getNumber(), extra);
	}

	public static List<JsonElement> getResultFileAsJsonElement(FilePath workspace, String json) {
		final FilePath jsonPath = new FilePath(workspace, json);
		LOG.info("file path: " + jsonPath);

		DirectoryScanner scanner = new DirectoryScanner();
		scanner.setIncludes(new String[]{json});
		scanner.setBasedir(workspace.getRemote());
		scanner.setCaseSensitive(false);
		scanner.scan();
		String[] files = scanner.getIncludedFiles();

		List<JsonElement> result = new ArrayList<JsonElement>();

		for (String file : files) {
			final Gson gson = new Gson();
			try {
				final JsonReader jsonReader = new JsonReader(new InputStreamReader(new FileInputStream(workspace.getRemote()+"/"+file)));
				result.add((JsonElement) gson.fromJson(jsonReader, JsonElement.class));
			} catch (IOException e) {
				LOG.severe("Exception occurred while reading test results: " + e);
				throw new RuntimeException("Exception occurred while reading test results", e);
			}
		}

		return result;
	}
}
