package gg.playit.proto.rest;

import com.google.gson.Gson;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class ApiClient {
    public static final Gson GSON = new Gson();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private String agentKey = null;

    public void setAgentKey(String agentKey) {
        this.agentKey = agentKey;
    }

    public Object claimSetup(String code, String agent_type, String version) throws IOException, InterruptedException {
        var req = HttpRequest.newBuilder(URI.create("https://api.playit.gg/claim/setup"))
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(new ClaimSetupRequest(code, agent_type, version))))
                .setHeader("Content-Type", "application/json")
                .build();
        var resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        var parsed = GSON.fromJson(resp.body(), GenericRestResult.class);
        return parsed.specialize(ClaimSetupResponse.class, ClaimSetupError.class);
    }

    public Object claimExchange(String code) throws IOException, InterruptedException {
        var req = HttpRequest.newBuilder(URI.create("https://api.playit.gg/claim/exchange"))
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(new ClaimExchangeRequest(code))))
                .setHeader("Content-Type", "application/json")
                .build();
        var resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        var parsed = GSON.fromJson(resp.body(), GenericRestResult.class);
        return parsed.specialize(ClaimExchangeResponse.class, ClaimExchangeError.class);
    }

    public Object agentsRoutingGet() throws IOException, InterruptedException {
        var req = HttpRequest.newBuilder(URI.create("https://api.playit.gg/agents/routing/get"))
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .setHeader("Content-Type", "application/json")
                .setHeader("Authorization", "Agent-Key " + agentKey)
                .build();
        var resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        var parsed = GSON.fromJson(resp.body(), GenericRestResult.class);
        return parsed.specialize(AgentRoutingGetResponse.class, AgentRoutingGetError.class);
    }

    public Object protoRegister(ProtoRegisterRequest request) throws IOException, InterruptedException {
        var req = HttpRequest.newBuilder(URI.create("https://api.playit.gg/proto/register"))
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(request)))
                .setHeader("Content-Type", "application/json")
                .setHeader("Authorization", "Agent-Key " + agentKey)
                .build();
        var resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        var parsed = GSON.fromJson(resp.body(), GenericRestResult.class);
        return parsed.specialize(ProtoRegisterResponse.class, String.class);
    }
}
