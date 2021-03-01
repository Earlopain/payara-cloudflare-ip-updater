package net.c5h8no4na.cloudflare;

import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.ejb.Schedule;
import javax.ejb.Singleton;
import javax.ejb.Startup;

import com.google.gson.JsonObject;

import eu.roboflax.cloudflare.CloudflareAccess;
import eu.roboflax.cloudflare.CloudflareCallback;
import eu.roboflax.cloudflare.CloudflareRequest;
import eu.roboflax.cloudflare.CloudflareResponse;
import eu.roboflax.cloudflare.constants.Category;
import eu.roboflax.cloudflare.objects.dns.DNSRecord;
import net.c5h8no4na.common.ServerConfig;
import net.c5h8no4na.common.network.NetworkUtils;

@Singleton
@Startup
public class CloudflareIpUpdater {

	private static final Logger LOG = Logger.getLogger(CloudflareIpUpdater.class.getCanonicalName());
	private CloudflareAccess client = new CloudflareAccess(ServerConfig.getCloudflareApiKey());
	private String zone = ServerConfig.getCloudflareZoneId();
	private String previousIp;

	@Schedule(minute = "*/10", hour = "*", persistent = false)
	public void start() {
		String currentIp = NetworkUtils.getCurrentIp();
		if (currentIp == null) {
			LOG.warning("Failed to get current ip");
		} else if (currentIp.equals(previousIp)) {
			LOG.info("No update required, ip is still " + currentIp);
		} else {
			LOG.info(() -> String.format("Previous ip was %s, new one is %s, updating", previousIp, currentIp));
			doUpdate(currentIp);
			LOG.info("Successfully updated cloudflare dns records");
		}
		this.previousIp = currentIp;
	}

	private void doUpdate(String ip) {
		CloudflareResponse<List<DNSRecord>> response = new CloudflareRequest(Category.LIST_DNS_RECORDS, client).identifiers(zone)
				.queryString("type", "A").asObjectList(DNSRecord.class);

		if (!response.isSuccessful()) {
			return;
		}

		if (!response.getErrors().isEmpty()) {
			return;
		}

		for (DNSRecord record : response.getObject()) {
			JsonObject json = new JsonObject();
			json.addProperty("type", record.getType());
			json.addProperty("proxied", record.getProxied());
			json.addProperty("name", record.getName());
			json.addProperty("content", ip);

			new CloudflareRequest(Category.UPDATE_DNS_RECORD, client).identifiers(record.getZoneId(), record.getId()).body(json)
					.asObject(new CloudflareCallback<CloudflareResponse<DNSRecord>>() {
						@Override
						public void onSuccess(CloudflareResponse<DNSRecord> r) {}

						@Override
						public void onFailure(Throwable t, int statusCode, String statusMessage, Map<Integer, String> errors) {
							// your code...
							LOG.warning("Error updating dns record: " + errors);
						}
					}, DNSRecord.class);
		}
	}
}
