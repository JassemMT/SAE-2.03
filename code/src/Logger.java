import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;

public class Logger {

    static String accessLogPath = "access.log";
    static String errorLogPath = "error.log";

    public static void setAccessLog(String path) {
        accessLogPath = path;
    }

    public static void setErrorLog(String path) {
        errorLogPath = path;
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
        } catch (IOException e) {
            System.err.println("Erreur de log dans " + file + ": " + e.getMessage());
        }
    }
}
