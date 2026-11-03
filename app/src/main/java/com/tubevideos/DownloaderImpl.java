package com.tubevideos;

import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;

public class DownloaderImpl extends Downloader {
    private static DownloaderImpl instance;

    public static synchronized DownloaderImpl getInstance() {
        if (instance == null) {
            instance = new DownloaderImpl();
        }
        return instance;
    }

    @Override
    public Response execute(Request request) throws Exception {
        String url = request.url();
        String httpMethod = request.httpMethod();
        Map<String, List<String>> headers = request.headers();
        byte[] dataToSend = request.dataToSend();

        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod(httpMethod);
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);

        // Añadir headers si existen
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            for (String value : entry.getValue()) {
                connection.addRequestProperty(entry.getKey(), value);
            }
        }

        // Si hay datos para enviar (POST)
        if (dataToSend != null) {
            connection.setDoOutput(true);
            connection.getOutputStream().write(dataToSend);
        }

        int responseCode = connection.getResponseCode();
        InputStream inputStream = (responseCode >= 400) ? connection.getErrorStream() : connection.getInputStream();

        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder responseBody = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            responseBody.append(line).append("\n");
        }
        reader.close();

        return new Response(
                responseCode,
                connection.getResponseMessage(),
                connection.getHeaderFields(),
                responseBody.toString(),
                request.url()
        );
    }
  }
