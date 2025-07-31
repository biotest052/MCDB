import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Scanner;

public class Main {

    public Main() {
        HttpClient client = HttpClient.newHttpClient();

        Scanner s = new Scanner(System.in);
        
        System.out.println("MCDB Query Client. Begin sending queries from this CLI!");

        while (s.hasNextLine()) {
            sendRequest(client, s.nextLine());
        }

    }

    public void sendRequest(HttpClient client, String args) {
        HttpRequest.Builder builder = HttpRequest.newBuilder();

        HttpRequest request = builder
                .uri(URI.create("http://localhost:8000/query"))
                .POST(HttpRequest.BodyPublishers.ofString(args))
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("Response code: " + response.statusCode());
            System.out.println("Response body: " + response.body());
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        new Main();
    }

}

