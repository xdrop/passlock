package me.xdrop.passlock.datasource.sqlite;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class SQLiteConnection {

    private final static Logger LOG = LoggerFactory.getLogger(SQLiteConnection.class);

    private static Connection cachedConnection;

    public static Connection connect(String jdbcUrl) {

        if(cachedConnection != null) return cachedConnection;

        try {

            // create a connection to the database
            cachedConnection = DriverManager.getConnection(jdbcUrl);

            LOG.debug("Connection to SQLite has been established.");

        } catch (SQLException e) {

            LOG.info("Failure to connect to database", e);

        }

        return cachedConnection;
    }


}
