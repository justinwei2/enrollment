import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

// For testing purposes only, should be a web client
public class ClientTester {
    static String hostname = "ThinkPad-P1G2";
    static int port = 8080;
    static String request = "{" +
            "\"url\": \"https://classes.berkeley.edu/content/2020-fall-econ-119-001-lec-001\", " +
            "\"email\": \"justinwei2@gmail.com\", " +
            "\"disIDs\": [0, 1]" +
            "}";

    public static void main(String[] args) {
        for (int i = 0; i < 5; i++) {
            System.out.println(i);
            new Thread(new SampleClient()).start();
        }
    }

    static class SampleClient implements Runnable {

        @Override
        public void run() {
            // open a socket with the server, change to use given arguments later
            try (Socket socket = new Socket(hostname, port)) {
                ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
                oos.writeObject(request);

                ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
                System.out.println((String) ois.readObject());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}