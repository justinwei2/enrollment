import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Scraper implements Comparable<Scraper> {
    Attributes attributes;
    long lastRun;
    boolean mfd;

    Scraper(String url, String email, int[] disID) {
        attributes = new Attributes(url, email, disID);
        lastRun = 0;
        mfd = false;
    }

    // Returns the JSON stored at attribute in text as a JSONObject
    static JSONObject parseJSON(String attribute, String text) {
        Matcher m = Pattern.compile(attribute + "='(\\{.*})'").matcher(text);
        if (m.find()) {
            return new JSONObject(m.group(1));
        }

        return new JSONObject();
    }

    // Logs the response and additional info based on the current time and status code
    static void logResponse(HttpResponse<String> response) {
        long time = System.currentTimeMillis();
        String filename = String.format("%d-%d.log", time, response.statusCode());

        try (FileWriter writer = new FileWriter(filename)) {
            if (new File(filename).createNewFile()) {
                writer.write(String.format("%s\n\n".repeat(4),
                        new Date(time).toString(), response.toString(), response.headers(), response.body()));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Gets the target page and then hands it off to be parsed or logged
    void scrape() {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(attributes.url)).build();
        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            mfd = true;
            return;
        }

        int statusCode = response.statusCode();
        if (statusCode == 200) {
            lastRun = System.currentTimeMillis();
            attributes.parse(response);
        } else {
            logResponse(response);
            mfd = true;
        }
    }

    // validates that the request query is valid after scraping
    // TODO: check requested lecture/discussion validity
    boolean isValid() {
        return !mfd;
    }

    @Override
    public int compareTo(Scraper o) {
        return Long.compare(this.lastRun, o.lastRun);
    }

    // Class for extracting and storing attributes
    static class Attributes {
        int lecID;
        int[] disID;
        int enrolled;
        int capacity;
        int waitlist;
        int waitlistCapacity;
        String url;
        String email;

        Attributes(String url, String email, int[] disIds) {
            this.url = url;
            this.email = email;
            this.disID = disIds;
        }

        // Parses a successful request of the target page
        void parse(HttpResponse<String> response) {
            String body = response.body();
            JSONObject data = Scraper.parseJSON("data-json", body);

            JSONObject enrollmentStatus = data.getJSONObject("enrollmentStatus");
            lecID = data.getInt("id");
            enrolled = enrollmentStatus.getInt("enrolledCount");
            capacity = enrollmentStatus.getInt("maxEnroll");
            waitlist = enrollmentStatus.getInt("waitlistedCount");
            waitlistCapacity = enrollmentStatus.getInt("maxWaitlist");
        }
    }
}
