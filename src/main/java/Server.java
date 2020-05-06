import org.json.JSONObject;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.PriorityBlockingQueue;

public class Server {

    public static void main(String[] args) {
        // open a server on port with refresh time in milliseconds, update to use params later
        int port = 8080;
        long refreshInterval = 5000;

        try (ServerSocket server = new ServerSocket(port)) {
            System.out.println("Listening on " + port);

            // create background scraper thread for processing valid requests
            PriorityBlockingQueue<Scraper> queue = new PriorityBlockingQueue<>();
            Thread scraper = new Thread(new BackgroundScraper(queue, refreshInterval));
            scraper.start();

            // accept client connections, child thread replies with status
            while (true) {
                Socket socket = server.accept();
                Thread connection = new Thread(new ClientConnection(socket, queue));
                connection.start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static class BackgroundScraper implements Runnable {
        private final long refreshInterval;
        private final PriorityBlockingQueue<Scraper> queue;

        BackgroundScraper(PriorityBlockingQueue<Scraper> queue, long refreshInterval) {
            this.refreshInterval = refreshInterval;
            this.queue = queue;
        }

        @Override
        public void run() {
            while (true) {
                try {
                    Scraper scraper;
                    synchronized (queue) {
                        while (queue.isEmpty()) {
                            queue.wait();
                        }
                        scraper = queue.poll();
                    }

                    long sleepTime = scraper.lastRun + refreshInterval - System.currentTimeMillis();
                    if (sleepTime < 0) {
                        // TODO: (prob wont ever need) spawn a new thread
                        new Thread(new BackgroundScraper(queue, refreshInterval)).start();
                    } else {
                        Thread.sleep(sleepTime);
                    }

                    scraper.scrape();
                    if (scraper.isValid()) {
                        queue.offer(scraper);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    static class ClientConnection implements Runnable {
        private final Socket socket;
        private final PriorityBlockingQueue<Scraper> queue;

        ClientConnection(Socket socket, PriorityBlockingQueue<Scraper> queue) {
            this.socket = socket;
            this.queue = queue;
        }

        @Override
        public void run() {
            try {
                ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
                String request = (String) ois.readObject();

                // parse request parameters and create scraper
                JSONObject params = new JSONObject(request);
                String url = params.getString("url");
                String email = params.getString("email");
                List<Object> objDisIDs = params.getJSONArray("disIDs").toList();
                int[] disIDs = objDisIDs.stream().mapToInt(i -> (int) i).toArray();
                Scraper scraper = new Scraper(url, email, disIDs);

                // attempt to scrape and determine if request is valid
                scraper.scrape();
                boolean success = scraper.isValid();

                // TODO: add to queue if valid and inform client of status
                ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
                if (success) {
                    oos.writeObject("ok");
                    queue.offer(scraper);
                    synchronized (queue) {
                        queue.notify();
                    }
                } else {
                    oos.writeObject("not ok");
                }
                socket.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
