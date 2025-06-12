import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class HttpServer {

    static Config config;

    static void envoyerErreur404(Socket client) throws IOException {
        String contenuErreur = "<html><body><h1>404 - File not found</h1></body></html>";
        OutputStream sortie = client.getOutputStream();

        String enTeteHttp = "HTTP/1.1 404 Not Found\r\n" +
                "Content-Type: text/html\r\n" +
                "Content-Length: " + contenuErreur.length() + "\r\n" +
                "\r\n";

        System.out.println("chemin :  " + config.getDocumentRoot() + " ");
        sortie.write(enTeteHttp.getBytes("UTF-8"));
        sortie.write(contenuErreur.getBytes("UTF-8"));
        sortie.close();
        client.close();
    }

    static void envoyerPageHtml(Socket client, String cheminPage) throws Exception {
        File fichierHtml = new File(config.getDocumentRoot() + cheminPage);
        System.out.println("Chemin absolu demandé : " + fichierHtml.getAbsolutePath());

        System.out.println("path fichier html : " + fichierHtml.getAbsolutePath());
        System.out.println("chemin page : " + cheminPage);

        if (!fichierHtml.exists()) {
            if (cheminPage.endsWith("/index.html")) {
                // Extraire le répertoire du chemin demandé
                String cheminRepertoire = cheminPage.substring(0, cheminPage.lastIndexOf("/index.html"));
                File repertoire = new File(config.getDocumentRoot() + cheminRepertoire);
                envoyerListingRepertoire(client, repertoire, cheminRepertoire);
            } else {
                envoyerErreur404(client);
            }
            return;
        }

        byte[] contenuFichier = lireFichierEnOctets(fichierHtml);

        String enTeteHttp = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/html\r\n" +
                "Content-Length: " + contenuFichier.length + "\r\n" +
                "\r\n";

        OutputStream sortie = client.getOutputStream();
        sortie.write(enTeteHttp.getBytes("UTF-8"));
        sortie.write(contenuFichier);
        sortie.close();
        client.close();
    }

    /**
     * Si le repertoire ne contient pas de index.html, alors un listing des pages du repertoire est envoyé
     * @param client
     * @param repertoire
     * @param cheminRelatif le chemin relatif depuis la racine web (ex: "", "/dossier1", "/dossier1/sousdossier")
     * @throws IOException
     */
    static void envoyerListingRepertoire(Socket client, File repertoire, String cheminRelatif) throws IOException {
        if (!repertoire.isDirectory()) {
            envoyerErreur404(client);
            return;
        }

        // nom avec le chemin actuel
        String titreRepertoire = cheminRelatif.isEmpty() ? "/" : cheminRelatif + "/";
        StringBuilder html = new StringBuilder("<html><body><h1>Index of " + titreRepertoire + "</h1><ul>");

        // Ajouter un lien ".." si on n'est pas à la racine
        if (!cheminRelatif.isEmpty()) {
            String cheminParent = cheminRelatif.contains("/") ?
                    cheminRelatif.substring(0, cheminRelatif.lastIndexOf("/")) : "";
            String lienParent = cheminParent.isEmpty() ? "/index.html" : cheminParent + "/index.html";
            html.append("<li><a href=\"").append(lienParent).append("\">[..]</a></li>");
        }

        File[] fichiers = repertoire.listFiles();

        if (fichiers != null) {
            for (File f : fichiers) {
                String nom = f.getName();
                String lienComplet;

                if (f.isDirectory()) {
                    // Pour un répertoire, on construit le chemin vers son index.html
                    lienComplet = cheminRelatif + "/" + nom + "/index.html";
                } else {
                    // Pour un fichier, on construit le chemin direct
                    lienComplet = cheminRelatif + "/" + nom;
                }

                String affichage = f.isDirectory() ? nom + "/" : nom;
                html.append("<li><a href=\"").append(lienComplet).append("\">").append(affichage).append("</a></li>");
            }
        }

        html.append("</ul></body></html>");

        byte[] contenu = html.toString().getBytes("UTF-8");
        String enTeteHttp = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/html\r\n" +
                "Content-Length: " + contenu.length + "\r\n" +
                "\r\n";

        OutputStream sortie = client.getOutputStream();
        sortie.write(enTeteHttp.getBytes("UTF-8"));
        sortie.write(contenu);
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

        // Charger la configuration
        try {
            config = new Config("configuration.xml");
        } catch (Exception e) {
            System.err.println("Erreur de chargement du fichier de configuration : " + e.getMessage());
            return;
        }

        int port = config.getPort();

        try {
            socket_serv = new ServerSocket(port);
            System.out.println("Serveur démarré sur le port " + port);
        } catch (Exception e) {
            throw new Exception("Impossible de démarrer le serveur : " + e.getMessage());
        }

        while (true) {


            socket_client = socket_serv.accept();
            InputStream in = socket_client.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            String line = reader.readLine();
            String pageDemandee = "/index.html";

            String ipClient = socket_client.getInetAddress().getHostAddress();
            if (ipClient.equals("0:0:0:0:0:0:0:1")){
                ipClient = "127.0.0.1";
            }

            if (!config.estAutorise(ipClient)) {
                System.out.println("Connexion refusée depuis : " + ipClient);
                Logger.logErreur("Connexion refusée : " + ipClient);
                socket_client.close();
                continue;
            }

            Logger.logAcces("Connexion autorisée depuis : " + ipClient);

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
