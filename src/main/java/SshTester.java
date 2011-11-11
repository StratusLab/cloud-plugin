import java.io.CharArrayWriter;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import com.trilead.ssh2.Connection;
import com.trilead.ssh2.SCPClient;

public class SshTester {

    /**
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {

        String host = args[0];
        int port = 22;

        Connection connection = new Connection(host, port);

        File key = new File("/Users/loomis/.ssh/id_rsa");
        FileReader reader = new FileReader(key);
        char[] buffer = new char[2048];
        CharArrayWriter writer = new CharArrayWriter();
        for (int n = reader.read(buffer); n >= 0; n = reader.read(buffer)) {
            for (int i = 0; i < n; i++) {
                writer.append(buffer[i]);
            }
        }
        char[] pemPrivateKey = writer.toCharArray();
        writer.close();

        try {

            connection.connect();
            System.err.println("connected");
            connection.authenticateWithPublicKey("root", pemPrivateKey,
                    "!eff@12%");
            System.err.println("authenticated");

            SCPClient scp = connection.createSCPClient();
            String data = "my transferred data";
            scp.put(data.getBytes(), "slave.jar", "/tmp");

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (connection != null) {
                connection.close();
            }
        }

    }
}
