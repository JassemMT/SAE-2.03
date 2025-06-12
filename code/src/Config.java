
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Config {
    private int port = 80;
    private String documentRoot = ".";
    private String accessLog;
    private String errorLog;
    private String securityOrderFirst;
    private String securityOrderLast;
    private String securityDefault;
    private List<String> acceptIPs = new ArrayList<>();
    private List<String> rejectIPs = new ArrayList<>();
    private boolean acceptFirst = true;
    private String defaultPolicy = "reject"; // ou "accept"

    public void ajouterAccept(String ip) {
        acceptIPs.add(ip.trim());
    }

    public void ajouterReject(String ip) {
        rejectIPs.add(ip.trim());
    }

    public void setOrder(String first, String last) {
        acceptFirst = first.equalsIgnoreCase("accept");
    }

    public void setDefaultPolicy(String policy) {
        defaultPolicy = policy.trim().toLowerCase();
    }

    public boolean estAutorise(String ip) {
        ip = ip.trim();

        if (acceptFirst) {
            if (acceptIPs.contains(ip)) return true;
            if (rejectIPs.contains(ip)) return false;
        } else {
            if (rejectIPs.contains(ip)) return false;
            if (acceptIPs.contains(ip)) return true;
        }

        // Ni dans accept ni reject
        return defaultPolicy.equals("accept");
    }

    public Config(String fichierConfig) throws Exception {
        lireConfig(fichierConfig);
    }

    private void lireConfig(String fichierConfig) throws Exception {
        File fXmlFile = new File(fichierConfig);
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(fXmlFile);
        doc.getDocumentElement().normalize();

        Element root = doc.getDocumentElement(); // <webconf>

        port = Integer.parseInt(getTagValue("port", root));
        documentRoot = getTagValue("DocumentRoot", root);
        accessLog = getTagValue("accesslog", root);
        errorLog = getTagValue("errorlog", root);

        // Lire security
        NodeList securityList = root.getElementsByTagName("security");
        if (securityList.getLength() > 0) {
            Element security = (Element) securityList.item(0);

            // order
            Element order = (Element) security.getElementsByTagName("order").item(0);
            securityOrderFirst = getTagValue("first", order);
            securityOrderLast = getTagValue("last", order);

            // default
            securityDefault = getTagValue("default", security);

            // accept list (IP exactes)
            NodeList acceptNodes = security.getElementsByTagName("accept");
            for (int i = 0; i < acceptNodes.getLength(); i++) {
                String ip = acceptNodes.item(i).getTextContent().trim();
                if (!ip.isEmpty()) acceptIPs.add(ip);
                System.out.println(this.acceptIPs.get(i));

            }

            // reject list (IP exactes)
            NodeList rejectNodes = security.getElementsByTagName("reject");
            for (int i = 0; i < rejectNodes.getLength(); i++) {
                String ip = rejectNodes.item(i).getTextContent().trim();
                if (!ip.isEmpty()) rejectIPs.add(ip);
                System.out.println(this.rejectIPs.get(i));
            }

        }
    }

    private String getTagValue(String tag, Element element) {
        NodeList nl = element.getElementsByTagName(tag);
        if (nl != null && nl.getLength() > 0) {
            Node node = nl.item(0);
            if (node != null)
                return node.getTextContent().trim();
        }
        return null;
    }

    // Getters
    public int getPort() { return port; }
    public String getDocumentRoot() { return documentRoot; }
    public String getAccessLog() { return accessLog; }
    public String getErrorLog() { return errorLog; }
    public String getSecurityOrderFirst() { return securityOrderFirst; }
    public String getSecurityOrderLast() { return securityOrderLast; }
    public String getSecurityDefault() { return securityDefault; }
    public List<String> getAcceptIPs() { return acceptIPs; }
    public List<String> getRejectIPs() { return rejectIPs; }
}
