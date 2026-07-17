package com.okbeanok.marriagePlus.services.families;

import com.okbeanok.marriagePlus.MarriagePlus;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLConnection;
import java.nio.file.Files;

public class FamilyWebServer {

	private final MarriagePlus plugin;
	private HttpServer server;

	public FamilyWebServer(MarriagePlus plugin) {
		this.plugin = plugin;
	}

	public void start() {
		if (!plugin.configs().families().getBoolean("web.enabled", true)) {
			return;
		}

		String host = plugin.configs().families().getString("web.host", "0.0.0.0");
		int port = plugin.configs().families().getInt("web.port", 8091);
		String route = normalizeRoute(plugin.configs().families().getString("web.route", "/families"));

		String outputFolderPath = plugin.configs().families().getString("web.output-folder", "web/families");
		File webFolder = new File(plugin.getDataFolder(), outputFolderPath);

		if (!webFolder.exists() && !webFolder.mkdirs()) {
			plugin.getLogger().warning("Could not create family web folder.");
			return;
		}

		try {
			server = HttpServer.create(new InetSocketAddress(host, port), 0);
			server.createContext(route, exchange -> handleRequest(exchange, route, webFolder));
			server.setExecutor(null);
			server.start();

			plugin.getLogger().info("Family web server started at http://" + host + ":" + port + route);
		} catch (IOException exception) {
			plugin.getLogger().warning("Could not start family web server on " + host + ":" + port + ": " + exception.getMessage());
		}
	}

	public void stop() {
		if (server != null) {
			server.stop(0);
			server = null;
			plugin.getLogger().info("Family web server stopped.");
		}
	}

	private void handleRequest(HttpExchange exchange, String route, File webFolder) throws IOException {
		if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
			send(exchange, 405, "Method not allowed", "text/plain");
			return;
		}

		String requestPath = exchange.getRequestURI().getPath();

		if (!requestPath.startsWith(route)) {
			send(exchange, 404, "Not found", "text/plain");
			return;
		}

		String relativePath = requestPath.substring(route.length());

		if (relativePath.isBlank() || relativePath.equals("/")) {
			relativePath = plugin.configs().families().getString("web.file-name", "index.html");
		} else {
			relativePath = relativePath.replaceFirst("^/+", "");
		}

		File requestedFile = new File(webFolder, relativePath).getCanonicalFile();
		File canonicalWebFolder = webFolder.getCanonicalFile();

		if (!requestedFile.toPath().startsWith(canonicalWebFolder.toPath())) {
			send(exchange, 403, "Forbidden", "text/plain");
			return;
		}

		if (!requestedFile.exists() || !requestedFile.isFile()) {
			send(exchange, 404, "Not found", "text/plain");
			return;
		}

		byte[] content = Files.readAllBytes(requestedFile.toPath());
		String contentType = URLConnection.guessContentTypeFromName(requestedFile.getName());

		if (contentType == null) {
			contentType = "application/octet-stream";
		}

		exchange.getResponseHeaders().set("Content-Type", contentType);
		exchange.sendResponseHeaders(200, content.length);

		try (OutputStream outputStream = exchange.getResponseBody()) {
			outputStream.write(content);
		}
	}

	private void send(HttpExchange exchange, int statusCode, String message, String contentType) throws IOException {
		byte[] content = message.getBytes();

		exchange.getResponseHeaders().set("Content-Type", contentType);
		exchange.sendResponseHeaders(statusCode, content.length);

		try (OutputStream outputStream = exchange.getResponseBody()) {
			outputStream.write(content);
		}
	}

	private String normalizeRoute(String route) {
		if (route == null || route.isBlank()) {
			return "/families";
		}

		if (!route.startsWith("/")) {
			route = "/" + route;
		}

		if (route.length() > 1 && route.endsWith("/")) {
			route = route.substring(0, route.length() - 1);
		}

		return route;
	}
}