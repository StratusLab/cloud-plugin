/*
 Created as part of the StratusLab project (http://stratuslab.eu),
 co-funded by the European Commission under the Grant Agreement
 INSFO-RI-261552.

 Copyright (c) 2011, Centre National de la Recherche Scientifique (CNRS)

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */
package eu.stratuslab.hudson;

import static eu.stratuslab.hudson.utils.CloudParameterUtils.isEmptyStringOrNull;
import static eu.stratuslab.hudson.utils.ProcessUtils.closeReliably;

import java.io.CharArrayWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.Arrays;

import org.kohsuke.stapler.DataBoundConstructor;

@SuppressWarnings("serial")
public class CloudParameters implements Serializable {

    public final String clientLocation;
    public final String endpoint;
    public final String username;
    public final String password;
    public final String sshPublicKey;
    public final String sshPrivateKey;
    public final String sshPrivateKeyPassword;
    public final int instanceLimit;

    private final char[] sshPrivateKeyData;

    @DataBoundConstructor
    public CloudParameters(String clientLocation, String endpoint,
            String username, String password, String sshPublicKey,
            String sshPrivateKey, String sshPrivateKeyPassword,
            int instanceLimit) {

        this.clientLocation = clientLocation;
        this.endpoint = endpoint;
        this.username = username;
        this.password = password;
        this.sshPublicKey = getKeyFile(sshPublicKey, ".pub").getAbsolutePath();
        this.sshPrivateKey = getKeyFile(sshPrivateKey, "").getAbsolutePath();
        this.sshPrivateKeyPassword = sshPrivateKeyPassword;
        this.instanceLimit = instanceLimit;

        sshPrivateKeyData = getSshPrivateKeyData(sshPrivateKey);
    }

    public char[] getSshPrivateKeyData() {
        return Arrays.copyOf(sshPrivateKeyData, sshPrivateKeyData.length);
    }

    public static File getKeyFile(String keyFilename, String suffix) {

        File keyFile = null;
        if (isEmptyStringOrNull(keyFilename)) {
            File home = new File(System.getProperty("user.home"));
            File sshDir = new File(home, ".ssh");
            keyFile = (new File(sshDir, "id_rsa" + suffix)).getAbsoluteFile();
        } else {
            keyFile = new File(keyFilename).getAbsoluteFile();
        }

        return keyFile;
    }

    public static char[] getSshPrivateKeyData(String sshPrivateKey) {
        File keyFile = getKeyFile(sshPrivateKey, "");
        return fileToCharArray(keyFile);
    }

    public static char[] fileToCharArray(File file) {

        char[] data = new char[0];

        Reader reader = null;
        try {
            reader = new InputStreamReader(new FileInputStream(file),
                    Charset.defaultCharset());
            char[] buffer = new char[2048];
            CharArrayWriter writer = null;
            try {
                writer = new CharArrayWriter();
                for (int n = reader.read(buffer); n >= 0; n = reader
                        .read(buffer)) {
                    for (int i = 0; i < n; i++) {
                        writer.append(buffer[i]);
                    }
                }
                data = writer.toCharArray();
            } catch (IOException consumed) {
            } finally {
                closeReliably(writer);
            }
        } catch (IOException consumed) {
            return new char[0];
        } finally {
            closeReliably(reader);
        }

        return data;

    }

}
