import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.lang.management.ManagementFactory;

public class HttpServer {

    static Config config;
    static int nbUtilisateurs;


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

        if(cheminPage.equals("/status")){
            envoyerStatus(client);
        }
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
     * La mémoire disponible (non utilisée)
     * L’espace disque disponible (idem)
     * Le nombre de processus
     * Le nombre d’utilisateurs
     * @param client
     * @throws IOException
     */
    static void envoyerStatus(Socket client) throws IOException {
        Runtime runtime = Runtime.getRuntime();

        long memLibre = runtime.freeMemory(); // mémoire disponible (non utilisée) en bytes

        // Espace disque disponible sur la partition racine (en bytes)
        long storageLibre = 0;
        for (var store : java.nio.file.FileSystems.getDefault().getFileStores()) {
            storageLibre += store.getUsableSpace();
        }

        long nbProc = ProcessHandle.allProcesses().count();

        int nbUtilisateurs = 1;

        String html = "<html><body>" +
                "<h1>Statut du serveur</h1>" +
                "<ul>" +
                "<li>Mémoire disponible (non utilisée) : " + memLibre / (1024 * 1024) + " Mo</li>" +
                "<li>Espace disque disponible : " + storageLibre / (1024 * 1024 * 1024) + " Go</li>" +
                "<li>Nombre de processus : " + nbProc + "</li>" +
                "<li>Nombre d’utilisateurs : " + nbUtilisateurs + "</li>" +
                "</ul>" +
                "</body></html>";

        byte[] contenu = html.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        String enteteHttp = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/html; charset=UTF-8\r\n" +
                "Content-Length: " + contenu.length + "\r\n\r\n";

        try (OutputStream os = client.getOutputStream()) {
            os.write(enteteHttp.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            os.write(contenu);
        }
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
        nbUtilisateurs ++;

        // Charger la configuration
        try {
            config = new Config("configuration.xml");
            Logger.setAccessLog(config.getAccessLog());
            Logger.setErrorLog(config.getErrorLog());
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
            if (ipClient.equals("0:0:0:0:0:0:0:1")){//gérer la connexion si navigateur utilise une IPV6
                ipClient = "127.0.0.1";
            }

            if (!config.estAutorise(ipClient)) {
                System.out.println("Connexion refusée depuis : " + ipClient);
                Logger.logErreur("Connexion refusée : " + ipClient);
                socket_client.close();
                nbUtilisateurs--;
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
