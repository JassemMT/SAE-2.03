import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.lang.management.ManagementFactory;
import java.util.zip.GZIPOutputStream;

public class HttpServer {

    static Config config;
    static int nbUtilisateurs;
    static Logger logger;


    // Méthode pour déterminer le type MIME d'un fichier
    static String getTypeMedia(String nomFichier) {
        String extension = nomFichier.substring(nomFichier.lastIndexOf('.') + 1).toLowerCase();
        switch (extension) {
            case "jpg":
            case "jpeg": return "image/jpeg";
            case "png": return "image/png";
            case "gif": return "image/gif";
            case "bmp": return "image/bmp";
            case "webp": return "image/webp";
            case "svg": return "image/svg+xml";
            case "ico": return "image/x-icon";
            default: return "text/html";
        }
    }

    /**
     * Méthode pour vérifier si un fichier est une image qui doit être compressée
     * @param nomFichier
     * @return
     */
    static boolean estFichierMedia(String nomFichier) {
        String extension = nomFichier.substring(nomFichier.lastIndexOf('.') + 1).toLowerCase();
        return extension.matches("jpg|jpeg|png|gif|bmp|webp|svg|ico");
    }

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

        logger.logErreur("404!!!!!!!!!!!!!!!!!!: " + client.getInetAddress().getHostAddress());

    }

    static void envoyerPageHtml(Socket client, String cheminPage) throws Exception {
        if(cheminPage.equals("/status")){
            envoyerStatus(client);
            return;
        }
        File fichier = new File(config.getDocumentRoot() + cheminPage);
        System.out.println("Chemin absolu demandé : " + fichier.getAbsolutePath());

        if (!fichier.exists()) {
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

        String typeMedia = getTypeMedia(fichier.getName());

        // Lire le fichier
        byte[] contenuFichier = lireFichierEnOctets(fichier);
        OutputStream sortie = client.getOutputStream();

        // Si c'est un media
        if (estFichierMedia(fichier.getName())) {
            // On compress avec gzip
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (GZIPOutputStream gzipOut = new GZIPOutputStream(baos)) {
                gzipOut.write(contenuFichier);
                gzipOut.finish();
            }
            byte[] contenuCompresse = baos.toByteArray();

            String enTeteHttp = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: " + typeMedia + "\r\n" +
                    "Content-Encoding: gzip\r\n" +
                    "Content-Length: " + contenuCompresse.length + "\r\n" +
                    "\r\n";

            sortie.write(enTeteHttp.getBytes("UTF-8"));
            sortie.write(contenuCompresse);
        } else {
            // Fichier HTML ou autre, pas de compression
            String enTeteHttp = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: " + typeMedia + "\r\n" +
                    "Content-Length: " + contenuFichier.length + "\r\n" +
                    "\r\n";

            sortie.write(enTeteHttp.getBytes("UTF-8"));
            sortie.write(contenuFichier);
        }
        sortie.close();
        client.close();
    }

    /**
     * La mémoire disponible (non utilisée)
     * L'espace disque disponible (idem)
     * Le nombre de processus
     * Le nombre d'utilisateurs
     * @param client
     * @throws IOException
     */
    static void envoyerStatus(Socket client) throws IOException {
        Runtime runtime = Runtime.getRuntime();

        long memLibre = runtime.freeMemory();

        // Espace disque disponible
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
                "<li>Nombre d'utilisateurs : " + nbUtilisateurs + "</li>" +
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

    // Ajoutez cette méthode dans HttpServer.java
    static void traiterFormulaire(Socket client, BufferedReader reader) throws IOException {
        // Lire les en-têtes pour connaître la longueur du contenu
        int contentLength = 0;
        String line;
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            if (line.startsWith("Content-Length:")) {
                contentLength = Integer.parseInt(line.substring("Content-Length:".length()).trim());
            }
        }

        // Lire le corps de la requête
        char[] buffer = new char[contentLength];
        reader.read(buffer, 0, contentLength);
        String donnees = new String(buffer);

        // Parser les données (format: user_name=nom&user_mail=email)
        String[] paires = donnees.split("&");
        String nom = "", email = "";
        for (String paire : paires) {
            String[] kv = paire.split("=");
            if (kv.length == 2) {
                if (kv[0].equals("user_name")) {
                    nom = java.net.URLDecoder.decode(kv[1], "UTF-8");
                } else if (kv[0].equals("user_mail")) {
                    email = java.net.URLDecoder.decode(kv[1], "UTF-8");
                }
            }
        }

        // Enregistrer dans le fichier
        File fichierUsers = new File(config.getDocumentRoot() + "/userlist.txt");
        try (FileWriter fw = new FileWriter(fichierUsers, true)) {
            fw.write(nom + ";" + email + "\n");
        }

        // Répondre au client
        String reponse = "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n\r\n" +
                "<html><body>Merci pour votre soumission!</body></html>";

        OutputStream out = client.getOutputStream();
        out.write(reponse.getBytes("UTF-8"));
        out.close();
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
            logger = new Logger(config.getAccessLog(),config.getErrorLog());

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
                logger.logErreur("Connexion refusée : " + ipClient);
                socket_client.close();
                nbUtilisateurs--;
                continue;
            }

            logger.logAcces("Connexion autorisée depuis : " + ipClient);


            if (line != null && !line.isEmpty()) {
                System.out.println(line);
                String[] tokens = line.split(" ");

                if (tokens.length >= 2) {
                    if (tokens[0].equals("GET")) {
                        pageDemandee = tokens[1];
                        if (pageDemandee.equals("/")) {
                            pageDemandee = "/index.html";
                        }
                    } else if (tokens[0].equals("POST") && tokens[1].equals("/programme")) {
                        traiterFormulaire(socket_client, reader);
                        nbUtilisateurs--;
                        continue;
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