import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

import static java.lang.Integer.parseInt;

public class HttpServer {

    static void envoyerErreur404(Socket client) throws IOException {
        String contenuErreur = "<html><body><h1>404 - File not found</h1></body></html>";
        OutputStream sortie = client.getOutputStream();

        String enTeteHttp = "HTTP/1.1 404 Not Found\r\n" +
                "Content-Type: text/html\r\n" +
                "Content-Length: " + contenuErreur.length() + "\r\n" +
                "\r\n";

        sortie.write(enTeteHttp.getBytes("UTF-8"));
        sortie.write(contenuErreur.getBytes("UTF-8"));
        sortie.close();
        client.close();
    }

    static void envoyerPageHtml(Socket client, String cheminPage) throws Exception {
        File fichierHtml = new File("." + cheminPage); // cheminPage commence par "/"
        System.out.println("Chemin absolu demandé : " + fichierHtml.getAbsolutePath());

        if (!fichierHtml.exists()) {
            envoyerErreur404(client);
            return;
        }

        // Lire le contenu du fichier dans un tableau d'octets
        byte[] contenuFichier = lireFichierEnOctets(fichierHtml);

        // Construire l'en-tête HTTP
        String enTeteHttp = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/html\r\n" +
                "Content-Length: " + contenuFichier.length + "\r\n" +
                "\r\n";

        // Envoyer la réponse complète (en-tête + corps)
        OutputStream sortie = client.getOutputStream();
        sortie.write(enTeteHttp.getBytes("UTF-8"));
        sortie.write(contenuFichier);
        sortie.close();
        client.close();
    }

    static byte[] lireFichierEnOctets(File fichier) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        FileInputStream flux = new FileInputStream(fichier);

        int octetLu;
        while ((octetLu = flux.read()) != -1) {
            buffer.write(octetLu);
        }

        flux.close();
        return buffer.toByteArray();
    }



    public static void main(String[] args) throws Exception {
        ServerSocket socket_serv;
        Socket socket_client;
        int port = 80;

        if (args.length > 0) {
            port = parseInt(args[0]);
        }

        try {
            socket_serv = new ServerSocket(port);
        } catch (Exception e) {
            throw new Exception(e.getMessage());
        }

        while (true) {
            socket_client = socket_serv.accept();
            InputStream in = socket_client.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            String line = reader.readLine();
            String pageDemandee = "/index.html";
            if (line != null && !line.isEmpty()) {
                System.out.println(line);
                String[] tokens = line.split(" ");
                if (tokens.length >= 2 && tokens[0].equals("GET")) {
                    pageDemandee = tokens[1];
                    if (pageDemandee.equals("/")) {
                        pageDemandee = "/index.html";
                    }
                }
            }
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                System.out.println(line);
            }

            envoyerPageHtml(socket_client, pageDemandee);
        }
    }
}
