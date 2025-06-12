
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;

public class Logger {

    private String accessLogPath;
    private String errorLogPath;

    public Logger(String accessLogPath, String errorLogPath) {
        this.accessLogPath = accessLogPath;
        this.errorLogPath = errorLogPath;
        FichierExiste(accessLogPath);
        FichierExiste(errorLogPath);


    }

    public void logAcces(String message) {
        log(accessLogPath, "[ACCES] " + message);
        System.out.println(accessLogPath + " : ajout !!!!!!!!!!!! ");

    }

    public void logErreur(String message) {
        log(errorLogPath, "[ERREUR] " + message);
    }

    private void log(String file, String message) {
        try (FileWriter fw = new FileWriter(file, true)) {
            fw.write(LocalDateTime.now() + " - " + message + "\n");
            fw.flush();
        } catch (IOException e) {
            System.err.println("Erreur de log dans " + file + ": " + e.getMessage());
        }
    }

    private void FichierExiste(String path) {
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
