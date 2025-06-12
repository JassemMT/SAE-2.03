import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;

public class Logger {

    private static String accessLogPath = "access.log";
    private static String errorLogPath = "error.log";

    public static void setAccessLog(String path) {
        accessLogPath = path;
        ensureFileExists(accessLogPath);
    }

    public static void setErrorLog(String path) {
        errorLogPath = path;
        ensureFileExists(errorLogPath);
    }

    public static void logAcces(String message) {
        log(accessLogPath, "[ACCES] " + message);
    }

    public static void logErreur(String message) {
        log(errorLogPath, "[ERREUR] " + message);
    }

    private static void log(String file, String message) {
        try (FileWriter fw = new FileWriter(file, true)) {
            fw.write(LocalDateTime.now() + " - " + message + "\n");
            fw.flush();
        } catch (IOException e) {
            System.err.println("Erreur de log dans " + file + ": " + e.getMessage());
        }
    }

    private static void ensureFileExists(String path) {
        try {
            File file = new File(path);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs(); // crée les dossiers parents si nécessaires
            }
            if (!file.exists()) {
                file.createNewFile(); // crée le fichier vide si absent
            }
        } catch (IOException e) {
            System.err.println("Impossible de créer le fichier de log : " + path);
        }
    }
}
