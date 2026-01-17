package pl.minecodes.orm.connection;

import java.io.File;

public record ConnectionCredentials(String hostname, int port, String database, String username,
                                    String password, File databaseFile) {

}
